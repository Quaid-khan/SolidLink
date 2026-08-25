package com.solidlink.transport.wifiaware

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
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
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

public data class WifiAwareAvailability(
    val hardwareSupported: Boolean,
    val permissionsGranted: Boolean,
    val currentlyAvailable: Boolean,
) {
    public val usable: Boolean get() = hardwareSupported && permissionsGranted && currentlyAvailable
}

public object WifiAwarePolicy {
    @Suppress("MissingPermission", "ObsoleteSdkInt")
    public fun availability(context: Context, manager: WifiAwareManager?): WifiAwareAvailability {
        val hardwareSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        val permissionsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
            (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) &&
            (android.os.Build.VERSION.SDK_INT >= 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        return WifiAwareAvailability(hardwareSupported, permissionsGranted, manager?.isAvailable == true)
    }
}

@Suppress("MissingPermission")
public class WifiAwareAdapter(
    context: Context,
    private val maxEnvelopeBytes: Int = 1024 * 1024,
) : TransportAdapter {
    private val appContext = context.applicationContext
    private val manager: WifiAwareManager? = appContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private val connectivity: ConnectivityManager? = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val peers = HashMap<String, AwarePeer>()
    private var awareSession: WifiAwareSession? = null

    override val kind: TransportKind = TransportKind.WIFI_AWARE
    override val capabilities: TransportCapabilities = TransportCapabilities(
        kind = TransportKind.WIFI_AWARE,
        maxEnvelopeBytes = maxEnvelopeBytes,
        reliableOrdered = true,
        supportsMultipleReceivers = false,
        localOnlyRequired = true,
    )

    override fun discover(listener: (DiscoveredPeer) -> Unit): Closeable {
        val manager = manager ?: return Closeable {}
        if (!WifiAwarePolicy.availability(appContext, manager).usable) return Closeable {}
        val closed = AtomicBoolean(false)
        val callback = object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                currentDiscoverySession = session
                if (closed.get()) session.close()
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: MutableList<ByteArray>,
            ) {
                if (closed.get()) return
                val token = UUID.randomUUID().toString()
                peers[token] = AwarePeer(session = currentDiscoverySession, handle = peerHandle)
                listener(
                    DiscoveredPeer(
                        peerId = "wifi-aware-$token",
                        displayName = serviceSpecificInfo.toString(Charsets.UTF_8).ifBlank { "Nearby Aware device" },
                        endpoint = TransportEndpoint(
                            hostAddress = "wifi-aware",
                            port = 0,
                            networkToken = "aware:$token",
                            transport = TransportKind.WIFI_AWARE,
                            locallyDiscovered = true,
                        ),
                        capabilities = capabilities,
                    ),
                )
            }

            override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                peers.entries.removeIf { it.value.handle == peerHandle }
            }
        }
        var localDiscoverySession: DiscoverySession? = null
        currentDiscoverySession = null
        val receiver = availabilityReceiver(closed) { closeSessions() }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        manager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                if (closed.get()) {
                    session.close()
                    return
                }
                awareSession = session
                session.subscribe(SubscribeConfig.Builder().setServiceName(SERVICE_NAME).build(), callback, handler)
            }

            override fun onAttachFailed() {
                closed.set(true)
            }
        }, handler)
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Already unregistered.
                }
                localDiscoverySession?.close()
                awareSession?.close()
                awareSession = null
                peers.clear()
            }
        }
    }

    override fun connect(endpoint: TransportEndpoint): TransportResult<TransportConnection> =
        failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Aware requires connectPeer with an ephemeral peer handle and port", false)

    public fun connectPeer(
        endpoint: TransportEndpoint,
        port: Int,
        passphrase: String,
        onConnected: (TransportConnection) -> Unit,
    ): TransportResult<Closeable> {
        if (endpoint.transport != TransportKind.WIFI_AWARE || !LocalEndpointPolicy.accepts(endpoint) || port !in 1..65535 || passphrase.length !in 8..63) {
            return failure(TransportError.Code.INVALID_ENDPOINT, "Wi-Fi Aware endpoint or passphrase is invalid", false)
        }
        val manager = manager ?: return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Aware is unavailable", false)
        val connectivity = connectivity ?: return failure(TransportError.Code.UNSUPPORTED, "Connectivity service is unavailable", false)
        if (!WifiAwarePolicy.availability(appContext, manager).usable) return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Aware is unavailable on this device", false)
        val token = endpoint.networkToken?.removePrefix("aware:")
        val awarePeer = token?.let(peers::get) ?: return failure(TransportError.Code.INVALID_ENDPOINT, "Wi-Fi Aware peer handle is expired", false)
        val discoverySession = awarePeer.session ?: return failure(TransportError.Code.INVALID_ENDPOINT, "Wi-Fi Aware discovery session is closed", false)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Aware secure data paths require Android 10 or newer", false)
        }
        val request = buildNetworkRequest(discoverySession, awarePeer.handle, port, passphrase)
        val closed = AtomicBoolean(false)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (closed.get()) return
                try {
                    val socket = network.socketFactory.createSocket()
                    socket.connect(java.net.InetSocketAddress("::1", port), 5_000)
                    onConnected(SocketTransportConnection(socket, endpoint, capabilities, maxEnvelopeBytes))
                } catch (_: IOException) {
                    // The caller retains resumable durable state and can retry.
                }
            }

            override fun onLost(network: Network) {
                if (!closed.get()) closed.set(true)
            }
        }
        connectivity.requestNetwork(request, callback)
        return TransportResult.Success(Closeable {
            if (closed.compareAndSet(false, true)) connectivity.unregisterNetworkCallback(callback)
        })
    }

    @RequiresApi(29)
    private fun buildNetworkRequest(
        discoverySession: DiscoverySession,
        peerHandle: PeerHandle,
        port: Int,
        passphrase: String,
    ): NetworkRequest {
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle)
            .setPskPassphrase(passphrase)
            .setPort(port)
            .build()
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
    }

    private fun availabilityReceiver(closed: AtomicBoolean, onUnavailable: () -> Unit): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!closed.get() && intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) onUnavailable()
        }
    }

    private fun closeSessions() {
        peers.clear()
        awareSession?.close()
        awareSession = null
    }

    private fun <T> failure(code: TransportError.Code, message: String, retryable: Boolean): TransportResult<T> =
        TransportResult.Failure(TransportError(code, message, retryable))

    private companion object {
        const val SERVICE_NAME = "solidlink-file-transfer"
    }

    private var currentDiscoverySession: DiscoverySession? = null

    private data class AwarePeer(val session: DiscoverySession?, val handle: PeerHandle)
}

private class SocketTransportConnection(
    private val socket: Socket,
    override val endpoint: TransportEndpoint,
    override val capabilities: TransportCapabilities,
    private val maxFrameBytes: Int,
) : StreamTransportConnection {
    override val input = socket.getInputStream()
    override val output = socket.getOutputStream()
    private val closed = AtomicBoolean(false)

    override fun send(frame: ByteArray): TransportResult<Unit> = synchronized(output) {
        if (!isOpen()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        try {
            ProtoDelimitedIo.write(frame, output, maxFrameBytes)
            TransportResult.Success(Unit)
        } catch (_: IOException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "Wi-Fi Aware frame write failed", true)
        }
    }

    override fun receive(timeoutMillis: Long): TransportResult<ByteArray> {
        if (!isOpen()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        return try {
            socket.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val frame = ProtoDelimitedIo.read(input, maxFrameBytes)
            if (frame == null) {
                close()
                failure(TransportError.Code.CLOSED, "Peer closed the connection", false)
            } else TransportResult.Success(frame)
        } catch (_: java.net.SocketTimeoutException) {
            failure(TransportError.Code.TIMEOUT, "No frame arrived before the deadline", true)
        } catch (_: EOFException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "Peer sent a truncated frame", true)
        } catch (_: IOException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "Wi-Fi Aware frame read failed", true)
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
