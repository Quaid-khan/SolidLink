package com.solidlink.transport.lannsd

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.Closeable
import com.solidlink.transport.api.DiscoveredPeer
import com.solidlink.transport.api.LocalEndpointPolicy
import com.solidlink.transport.api.ProtoDelimitedIo
import com.solidlink.transport.api.StreamTransportConnection
import com.solidlink.transport.api.TransportAdapter
import com.solidlink.transport.api.TransportCapabilities
import com.solidlink.transport.api.TransportConnection
import com.solidlink.transport.api.TransportEndpoint
import com.solidlink.transport.api.TransportError
import com.solidlink.transport.api.TransportKind
import com.solidlink.transport.api.TransportResult
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

public object LanNsdProtocol {
    public const val SERVICE_TYPE: String = "_solidlink._tcp."
    public const val SERVICE_NAME_PREFIX: String = "SolidLink"
    public const val ATTRIBUTE_PEER_ID: String = "peer_id"
    public const val ATTRIBUTE_DISPLAY_NAME: String = "display_name"
    public const val ATTRIBUTE_CAPABILITIES: String = "capabilities"
}

public data class LanNsdConfig(
    val maxEnvelopeBytes: Int = 1024 * 1024,
    val connectTimeoutMillis: Int = 5_000,
    val receiveTimeoutMillis: Int = 15_000,
)

@Suppress("DEPRECATION")
public class LanNsdAdapter(
    context: Context,
    private val network: Network? = null,
    private val config: LanNsdConfig = LanNsdConfig(),
) : TransportAdapter {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    override val kind: TransportKind = TransportKind.LAN_NSD
    override val capabilities: TransportCapabilities = TransportCapabilities(
        kind = TransportKind.LAN_NSD,
        maxEnvelopeBytes = config.maxEnvelopeBytes,
        reliableOrdered = true,
        supportsMultipleReceivers = true,
        localOnlyRequired = true,
    )

    override fun discover(listener: (DiscoveredPeer) -> Unit): java.io.Closeable {
        val closed = AtomicBoolean(false)
        lateinit var discoveryListener: NsdManager.DiscoveryListener
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (closed.get() || serviceInfo.serviceType != LanNsdProtocol.SERVICE_TYPE) return
                val attributes = serviceInfo.attributes
                val peerId = attributes[LanNsdProtocol.ATTRIBUTE_PEER_ID]?.toString(Charsets.UTF_8)?.trim().orEmpty()
                if (peerId.isEmpty() || serviceInfo.host == null || serviceInfo.port !in 1..65535) return
                val displayName = attributes[LanNsdProtocol.ATTRIBUTE_DISPLAY_NAME]
                    ?.toString(Charsets.UTF_8)
                    ?.ifBlank { serviceInfo.serviceName }
                    ?: serviceInfo.serviceName
                val endpoint = TransportEndpoint(
                    hostAddress = serviceInfo.host.hostAddress ?: return,
                    port = serviceInfo.port,
                    networkToken = network?.networkHandle?.toString() ?: "nsd-local",
                    transport = TransportKind.LAN_NSD,
                    locallyDiscovered = true,
                )
                val peerCapabilities = TransportCapabilities(
                    kind = TransportKind.LAN_NSD,
                    maxEnvelopeBytes = attributes[LanNsdProtocol.ATTRIBUTE_CAPABILITIES]
                        ?.toString(Charsets.UTF_8)
                        ?.substringAfter("maxEnvelopeBytes=", "")
                        ?.substringBefore(';')
                        ?.toIntOrNull()
                        ?.coerceIn(1, config.maxEnvelopeBytes)
                        ?: config.maxEnvelopeBytes,
                    reliableOrdered = true,
                    supportsMultipleReceivers = true,
                    localOnlyRequired = true,
                )
                listener(
                    DiscoveredPeer(
                        peerId = peerId,
                        displayName = displayName,
                        endpoint = endpoint,
                        capabilities = peerCapabilities,
                    ),
                )
            }
        }
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!closed.get() && serviceInfo.serviceType == LanNsdProtocol.SERVICE_TYPE) {
                    try {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    } catch (_: RuntimeException) {
                        // Treat provider races as a lost discovery event.
                    }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!closed.get()) closed.set(true)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!closed.get()) closed.set(true)
            }
        }
        nsdManager.discoverServices(LanNsdProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        return java.io.Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (_: RuntimeException) {
                    // NSD may already have stopped during lifecycle teardown.
                }
            }
        }
    }

    override fun connect(endpoint: TransportEndpoint): TransportResult<TransportConnection> {
        if (endpoint.transport != TransportKind.LAN_NSD || !LocalEndpointPolicy.accepts(endpoint)) {
            return failure(TransportError.Code.INVALID_ENDPOINT, "Endpoint was not approved by local discovery", false)
        }
        return try {
            val socket = if (network == null) Socket() else network.socketFactory.createSocket()
            socket.connect(InetSocketAddress(endpoint.hostAddress, endpoint.port), config.connectTimeoutMillis)
            socket.soTimeout = config.receiveTimeoutMillis
            TransportResult.Success(TcpTransportConnection(socket, endpoint, capabilities, config.maxEnvelopeBytes))
        } catch (_: SocketTimeoutException) {
            failure(TransportError.Code.TIMEOUT, "LAN connection timed out", true)
        } catch (_: SecurityException) {
            failure(TransportError.Code.IO_FAILURE, "LAN connection was blocked by platform policy", false)
        } catch (_: IOException) {
            failure(TransportError.Code.IO_FAILURE, "LAN connection failed", true)
        }
    }

    public fun advertise(
        peerId: String,
        displayName: String,
        networkToken: String?,
        onConnection: (TransportConnection) -> Unit,
    ): java.io.Closeable {
        require(peerId.isNotBlank()) { "peerId must not be blank" }
        val server = ServerSocket(0)
        val closed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val advertisedName = "${LanNsdProtocol.SERVICE_NAME_PREFIX}-${peerId.take(12)}"
        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = advertisedName
            serviceType = LanNsdProtocol.SERVICE_TYPE
            port = server.localPort
            setAttribute(LanNsdProtocol.ATTRIBUTE_PEER_ID, peerId)
            setAttribute(LanNsdProtocol.ATTRIBUTE_DISPLAY_NAME, displayName.take(240))
            setAttribute(
                LanNsdProtocol.ATTRIBUTE_CAPABILITIES,
                "maxEnvelopeBytes=${config.maxEnvelopeBytes};network=${networkToken ?: "local"}",
            )
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        executor.execute {
            while (!closed.get()) {
                try {
                    val socket = server.accept()
                    if (closed.get()) {
                        socket.close()
                    } else {
                        socket.soTimeout = config.receiveTimeoutMillis
                        onConnection(TcpTransportConnection(socket, localEndpoint(server), capabilities, config.maxEnvelopeBytes))
                    }
                } catch (_: IOException) {
                    if (!closed.get()) closed.set(true)
                }
            }
        }
        return java.io.Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    nsdManager.unregisterService(registrationListener)
                } catch (_: RuntimeException) {
                    // Registration may not have completed or may already be gone.
                }
                try {
                    server.close()
                } catch (_: IOException) {
                    // Already closed.
                }
                executor.shutdownNow()
            }
        }
    }

    private fun localEndpoint(server: ServerSocket): TransportEndpoint = TransportEndpoint(
        hostAddress = "0.0.0.0",
        port = server.localPort,
        networkToken = network?.networkHandle?.toString() ?: "nsd-local",
        transport = TransportKind.LAN_NSD,
        locallyDiscovered = true,
    )

    private fun <T> failure(code: TransportError.Code, message: String, retryable: Boolean): TransportResult<T> =
        TransportResult.Failure(TransportError(code, message, retryable))
}

private class TcpTransportConnection(
    private val socket: Socket,
    override val endpoint: TransportEndpoint,
    override val capabilities: TransportCapabilities,
    private val maxMessageBytes: Int,
) : StreamTransportConnection {
    override val input = socket.getInputStream()
    override val output = socket.getOutputStream()
    private val delimitedReader = ProtoDelimitedIo.reader(input, maxMessageBytes)
    private val delimitedWriter = ProtoDelimitedIo.writer(output, maxMessageBytes)
    private val closed = AtomicBoolean(false)

    override fun send(frame: ByteArray): TransportResult<Unit> = synchronized(output) {
        if (!isOpen()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        try {
            delimitedWriter.write(frame)
            TransportResult.Success(Unit)
        } catch (_: IOException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "LAN message write failed", true)
        }
    }

    override fun receive(timeoutMillis: Long): TransportResult<ByteArray> {
        if (timeoutMillis < 0) return failure(TransportError.Code.TIMEOUT, "Receive timeout must be non-negative", false)
        if (!isOpen()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        return try {
            socket.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val message = delimitedReader.read()
            if (message == null) {
                close()
                failure(TransportError.Code.CLOSED, "Peer closed the connection", false)
            } else TransportResult.Success(message)
        } catch (_: SocketTimeoutException) {
            failure(TransportError.Code.TIMEOUT, "No message arrived before the deadline", true)
        } catch (_: EOFException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "Peer sent a truncated message", true)
        } catch (_: IOException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "LAN message read failed", true)
        }
    }

    override fun isOpen(): Boolean = !closed.get() && !socket.isClosed

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close()
            } catch (_: IOException) {
                // Already closed.
            }
        }
    }

    private fun <T> failure(code: TransportError.Code, message: String, retryable: Boolean): TransportResult<T> =
        TransportResult.Failure(TransportError(code, message, retryable))
}
