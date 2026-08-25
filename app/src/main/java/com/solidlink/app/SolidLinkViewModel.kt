package com.solidlink.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import com.solidlink.protocol.ProtobufFrameCodec
import com.solidlink.protocol.v1.Envelope
import com.solidlink.protocol.v1.Hello
import com.solidlink.transport.api.DiscoveredPeer
import com.solidlink.transport.api.TransportConnection
import com.solidlink.transport.api.TransportResult
import com.solidlink.transport.lannsd.LanNsdAdapter
import com.solidlink.transport.lannsd.LanNsdConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID

public data class PeerRow(
    val peer: DiscoveredPeer,
    val isConnecting: Boolean = false,
)

public data class SolidLinkUiState(
    val selectedUris: List<Uri> = emptyList(),
    val peers: List<PeerRow> = emptyList(),
    val isRunning: Boolean = false,
    val status: String = "Local discovery is stopped",
    val error: String? = null,
    val connectedPeer: String? = null,
)

public class SolidLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SolidLinkUiState())
    public val state: StateFlow<SolidLinkUiState> = _state.asStateFlow()

    private val adapter: LanNsdAdapter = LanNsdAdapter(
        context = application,
        config = LanNsdConfig(),
    )
    private val localPeerId = "android-${UUID.randomUUID()}"
    private var discoveryRegistration: Closeable? = null
    private var advertisementRegistration: Closeable? = null

    public fun setSelectedUris(uris: List<Uri>) {
        _state.value = _state.value.copy(selectedUris = uris.distinct())
    }

    public fun startLocalDiscovery() {
        if (_state.value.isRunning) return
        runCatching {
            advertisementRegistration = adapter.advertise(
                peerId = localPeerId,
                displayName = "SolidLink Android",
                networkToken = "nsd-local",
                onConnection = ::handleIncomingConnection,
            )
            discoveryRegistration = adapter.discover { discovered ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    val existing = _state.value.peers
                    val next = existing.filterNot { it.peer.peerId == discovered.peerId } + PeerRow(discovered)
                    _state.value = _state.value.copy(
                        peers = next,
                        status = "Found ${next.size} local peer${if (next.size == 1) "" else "s"}",
                        error = null,
                    )
                }
            }
            _state.value = _state.value.copy(
                isRunning = true,
                status = "Discovering peers on the local Wi-Fi network",
                error = null,
            )
        }.onFailure { error ->
            stopLocalDiscovery()
            _state.value = _state.value.copy(
                status = "Local discovery could not start",
                error = error.message ?: "Check Wi-Fi and nearby-device permissions.",
            )
        }
    }

    public fun stopLocalDiscovery() {
        discoveryRegistration?.close()
        advertisementRegistration?.close()
        discoveryRegistration = null
        advertisementRegistration = null
        _state.value = _state.value.copy(
            isRunning = false,
            peers = emptyList(),
            status = "Local discovery is stopped",
        )
    }

    public fun connect(peer: PeerRow) {
        if (peer.isConnecting) return
        updatePeer(peer.peer.peerId) { it.copy(isConnecting = true) }
        _state.value = _state.value.copy(status = "Connecting to ${peer.peer.displayName}", error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = adapter.connect(peer.peer.endpoint)
            when (result) {
                is TransportResult.Success -> {
                    val connection = result.value
                    val outcome = runCatching { performHello(connection) }
                    connection.close()
                    withContext(Dispatchers.Main) {
                        updatePeer(peer.peer.peerId) { it.copy(isConnecting = false) }
                        outcome.onSuccess {
                            _state.value = _state.value.copy(
                                status = "Connected to ${peer.peer.displayName} over local Wi-Fi",
                                connectedPeer = peer.peer.peerId,
                                error = null,
                            )
                        }.onFailure { error ->
                            _state.value = _state.value.copy(
                                status = "Peer connection opened but handshake failed",
                                error = error.message ?: "The peer did not answer the HELLO message.",
                            )
                        }
                    }
                }
                is TransportResult.Failure -> withContext(Dispatchers.Main) {
                    updatePeer(peer.peer.peerId) { it.copy(isConnecting = false) }
                    _state.value = _state.value.copy(
                        status = "Could not connect to ${peer.peer.displayName}",
                        error = result.error.safeMessage,
                    )
                }
            }
        }
    }

    private fun buildHello(): Hello = Hello.newBuilder()
        .setProtocolMajor(1)
        .setProtocolMinor(0)
        .setImplementationId("solidlink-android")
        .setMaxEnvelopeBytes(1024 * 1024)
        .setLocalOnlyRequired(true)
        .addTransports("LAN_NSD")
        .addSecuritySuites("pending-authenticated-channel")
        .setEphemeralPublicKey(ByteString.EMPTY)
        .setNonce(ByteString.EMPTY)
        .build()

    private fun performHello(connection: TransportConnection) {
        val hello = buildHello()
        val envelope = Envelope.newBuilder()
            .setSequence(1)
            .setSessionId(ByteString.copyFromUtf8(localPeerId))
            .setHello(hello)
            .build()
        val bytes = ProtobufFrameCodec.encode(envelope)
        when (val send = connection.send(bytes)) {
            is TransportResult.Failure -> error(send.error.safeMessage)
            is TransportResult.Success -> Unit
        }
        val response = connection.receive(15_000)
        val responseBytes = when (response) {
            is TransportResult.Success -> response.value
            is TransportResult.Failure -> error(response.error.safeMessage)
        }
        val responseEnvelope = ProtobufFrameCodec.decode(responseBytes)
        check(responseEnvelope.bodyCase == Envelope.BodyCase.HELLO) { "Peer did not answer with HELLO" }
        check(responseEnvelope.hello.localOnlyRequired) { "Peer did not require local-only routing" }
    }

    private fun handleIncomingConnection(connection: TransportConnection) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val request = connection.receive(15_000)
                val requestBytes = when (request) {
                    is TransportResult.Success -> request.value
                    is TransportResult.Failure -> error(request.error.safeMessage)
                }
                val requestEnvelope = ProtobufFrameCodec.decode(requestBytes)
                check(requestEnvelope.bodyCase == Envelope.BodyCase.HELLO) { "Peer did not start with HELLO" }
                check(requestEnvelope.hello.localOnlyRequired) { "Peer did not require local-only routing" }
                val response = Envelope.newBuilder()
                    .setSequence(requestEnvelope.sequence + 1)
                    .setSessionId(requestEnvelope.sessionId)
                    .setHello(buildHello())
                    .build()
                when (val send = connection.send(ProtobufFrameCodec.encode(response))) {
                    is TransportResult.Failure -> error(send.error.safeMessage)
                    is TransportResult.Success -> Unit
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        status = "Peer handshake completed over local Wi-Fi",
                        connectedPeer = requestEnvelope.hello.implementationId,
                        error = null,
                    )
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        status = "Incoming peer connection rejected",
                        error = error.message ?: "The peer sent an invalid HELLO message.",
                    )
                }
            }
            connection.close()
        }
    }

    private fun updatePeer(peerId: String, transform: (PeerRow) -> PeerRow) {
        _state.value = _state.value.copy(
            peers = _state.value.peers.map { row -> if (row.peer.peerId == peerId) transform(row) else row },
        )
    }

    override fun onCleared() {
        stopLocalDiscovery()
    }
}
