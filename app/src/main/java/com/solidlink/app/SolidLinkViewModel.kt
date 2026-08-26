package com.solidlink.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.ByteString
import com.solidlink.db.SolidLinkDatabase
import com.solidlink.db.SolidLinkRepository
import com.solidlink.domain.TransferBatch
import com.solidlink.protocol.ProtobufFrameCodec
import com.solidlink.protocol.v1.Envelope
import com.solidlink.protocol.v1.Hello
import com.solidlink.transfer.TransferEvent
import com.solidlink.transfer.TransferReceiver
import com.solidlink.transfer.TransferSender
import com.solidlink.transport.api.DiscoveredPeer
import com.solidlink.transport.api.TransportConnection
import com.solidlink.transport.api.TransportResult
import com.solidlink.transport.lannsd.LanNsdAdapter
import com.solidlink.transport.lannsd.LanNsdConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    val notificationsEnabled: Boolean = false,
    val requirePeerApproval: Boolean = true,
    val allowSasConfirmation: Boolean = true,
    val localOnlyRouting: Boolean = true,
)

public class SolidLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val database = androidx.room.Room.databaseBuilder(
        application,
        com.solidlink.db.SolidLinkDatabase::class.java,
        "solidlink.db"
    ).build()
    private val repository = com.solidlink.db.SolidLinkRepository(database)

    private val _state = MutableStateFlow(SolidLinkUiState())
    public val state: StateFlow<SolidLinkUiState> = _state.asStateFlow()

    public val transferHistory: Flow<List<TransferBatch>> = repository.getAllBatches()

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

    public fun setNotificationsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(notificationsEnabled = enabled)
    }

    public fun setRequirePeerApproval(enabled: Boolean) {
        _state.value = _state.value.copy(requirePeerApproval = enabled)
    }

    public fun setAllowSasConfirmation(enabled: Boolean) {
        _state.value = _state.value.copy(allowSasConfirmation = enabled)
    }

    public fun setLocalOnlyRouting(enabled: Boolean) {
        _state.value = _state.value.copy(localOnlyRouting = enabled)
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
            // Keep the peers list intact
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
                    
                    if (outcome.isSuccess) {
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(
                                status = "Connected to ${peer.peer.displayName}. Starting transfer...",
                                connectedPeer = peer.peer.peerId,
                                error = null,
                            )
                        }
                        
                        runCatching {
                            // Start transfer
                            val uris = _state.value.selectedUris
                            if (uris.isNotEmpty()) {
                                val batchId = "batch-${UUID.randomUUID()}"
                                val sender = TransferSender()
                                
                                uris.forEach { uri ->
                                    val objectId = "obj-${UUID.randomUUID()}"
                                    val source = ContentResolverTransferSource(
                                        contentResolver = getApplication<Application>().contentResolver,
                                        uri = uri,
                                        objectId = objectId,
                                        batchId = batchId
                                    )
                                    val snapshot = source.snapshot
                                    
                                    val manifest = sender.manifestEnvelope(snapshot, 2L, localPeerId.toByteArray())
                                    connection.send(ProtobufFrameCodec.encode(manifest))
                                    
                                    val chunkEnvelopes = sender.chunkEnvelopes(source, localPeerId.toByteArray(), 3L)
                                    chunkEnvelopes.forEach { envelope ->
                                        connection.send(ProtobufFrameCodec.encode(envelope))
                                    }
                                    
                                    val finalEnv = sender.finalEnvelope(
                                        snapshot = snapshot,
                                        byteCount = snapshot.sizeBytes ?: 0L,
                                        chunkCount = snapshot.totalChunks ?: 0L,
                                        sequence = 3L + chunkEnvelopes.size,
                                        sessionId = localPeerId.toByteArray()
                                    )
                                    connection.send(ProtobufFrameCodec.encode(finalEnv))
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            updatePeer(peer.peer.peerId) { it.copy(isConnecting = false) }
                            _state.value = _state.value.copy(status = "Transfer to ${peer.peer.displayName} complete")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updatePeer(peer.peer.peerId) { it.copy(isConnecting = false) }
                            _state.value = _state.value.copy(
                                status = "Peer connection opened but handshake failed",
                                error = outcome.exceptionOrNull()?.message ?: "The peer did not answer the HELLO message.",
                            )
                        }
                    }
                    connection.close()
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
                // Step 1: HELLO handshake
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
                        status = "Peer handshake complete. Receiving transfer...",
                        connectedPeer = requestEnvelope.hello.implementationId,
                        error = null,
                    )
                }

                // Step 2: Receive transfer (BATCH_MANIFEST, CHUNK*, OBJECT_FINAL)
                var receiver: TransferReceiver? = null
                var objectId: String? = null
                var totalChunksReceived = 0

                loop@ while (true) {
                    val incoming = connection.receive(30_000)
                    val incomingBytes = when (incoming) {
                        is TransportResult.Success -> incoming.value
                        is TransportResult.Failure -> break@loop
                    }
                    val envelope = ProtobufFrameCodec.decode(incomingBytes)

                    when (envelope.bodyCase) {
                        Envelope.BodyCase.BATCH_MANIFEST -> {
                            val manifest = envelope.batchManifest.filesList.firstOrNull()
                            if (manifest != null) {
                                objectId = manifest.objectId.toStringUtf8()
                                val sink = RoomVerifiedObjectSink(
                                    objectId = objectId,
                                    repository = repository,
                                    expectedFinalDigest = manifest.finalDigest.toByteArray()
                                )
                                receiver = TransferReceiver(sink)
                                receiver.accept(envelope)
                                withContext(Dispatchers.Main) {
                                    _state.value = _state.value.copy(
                                        status = "Receiving: ${manifest.displayName}"
                                    )
                                }
                            }
                        }
                        Envelope.BodyCase.CHUNK -> {
                            val event = receiver?.accept(envelope)
                            if (event is TransferEvent.ChunkAccepted) {
                                totalChunksReceived++
                            }
                        }
                        Envelope.BodyCase.OBJECT_FINAL -> {
                            val event = receiver?.accept(envelope)
                            val accepted = event is TransferEvent.FinalAccepted
                            withContext(Dispatchers.Main) {
                                _state.value = _state.value.copy(
                                    status = if (accepted)
                                        "Transfer complete. $totalChunksReceived chunks received."
                                    else
                                        "Transfer ended but final verification failed.",
                                    error = if (!accepted) "Final digest mismatch or commit failure" else null,
                                )
                            }
                            break@loop
                        }
                        else -> { /* Ignore unknown envelope types */ }
                    }
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
