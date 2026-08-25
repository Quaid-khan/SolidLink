package com.solidlink.transport.wifidirect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

public data class WifiDirectAvailability(
    val hardwareSupported: Boolean,
    val permissionsGranted: Boolean,
    val locationModeMayBeRequired: Boolean = true,
) {
    public val usable: Boolean get() = hardwareSupported && permissionsGranted
}

public object WifiDirectPolicy {
    public fun availability(context: Context): WifiDirectAvailability {
        val hardwareSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val nearbyGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        val locationGranted = android.os.Build.VERSION.SDK_INT >= 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val wifiStateGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        val wifiChangeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        return WifiDirectAvailability(hardwareSupported, nearbyGranted && locationGranted && wifiStateGranted && wifiChangeGranted)
    }
}

@Suppress("MissingPermission")
public class WifiDirectAdapter(
    context: Context,
    private val connectTimeoutMillis: Int = 5_000,
    private val maxEnvelopeBytes: Int = 1024 * 1024,
) : TransportAdapter {
    private val appContext = context.applicationContext
    private val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(appContext, appContext.mainLooper, null)
    private val ephemeralPeerAddresses = HashMap<String, String>()

    override val kind: TransportKind = TransportKind.WIFI_DIRECT
    override val capabilities: TransportCapabilities = TransportCapabilities(
        kind = TransportKind.WIFI_DIRECT,
        maxEnvelopeBytes = maxEnvelopeBytes,
        reliableOrdered = true,
        supportsMultipleReceivers = true,
        localOnlyRequired = true,
    )

    override fun discover(listener: (DiscoveredPeer) -> Unit): Closeable {
        val manager = manager ?: return Closeable {}
        val channel = channel ?: return Closeable {}
        if (!WifiDirectPolicy.availability(appContext).usable) return Closeable {}
        val closed = AtomicBoolean(false)
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (closed.get()) return
                if (intent.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                    manager.requestPeers(channel) { peerList ->
                        peerList.deviceList.forEach { device ->
                            val token = UUID.randomUUID().toString()
                            ephemeralPeerAddresses[token] = device.deviceAddress
                            listener(
                                DiscoveredPeer(
                                    peerId = "wifi-direct-$token",
                                    displayName = device.deviceName.ifBlank { "Nearby Android device" },
                                    endpoint = TransportEndpoint(
                                        hostAddress = "wifi-direct",
                                        port = 1,
                                        networkToken = "p2p:$token",
                                        transport = TransportKind.WIFI_DIRECT,
                                        locallyDiscovered = true,
                                    ),
                                    capabilities = capabilities,
                                ),
                            )
                        }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.discoverPeers(channel, actionListener { })
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Receiver was already unregistered during lifecycle teardown.
                }
                manager.stopPeerDiscovery(channel, actionListener { })
                ephemeralPeerAddresses.clear()
            }
        }
    }

    override fun connect(endpoint: TransportEndpoint): TransportResult<TransportConnection> {
        if (endpoint.transport != TransportKind.WIFI_DIRECT || !LocalEndpointPolicy.accepts(endpoint)) {
            return failure(TransportError.Code.INVALID_ENDPOINT, "Endpoint was not approved by local discovery", false)
        }
        return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Direct connection is asynchronous; use connectPeer", false)
    }

    public fun connectPeer(
        endpoint: TransportEndpoint,
        port: Int,
        onConnected: (TransportConnection) -> Unit,
    ): TransportResult<Closeable> {
        val manager = manager ?: return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Direct is unavailable", false)
        val channel = channel ?: return failure(TransportError.Code.UNSUPPORTED, "Wi-Fi Direct channel is unavailable", false)
        val token = endpoint.networkToken?.removePrefix("p2p:")
        val deviceAddress = token?.let(ephemeralPeerAddresses::get)
        if (endpoint.transport != TransportKind.WIFI_DIRECT || deviceAddress.isNullOrBlank() || port !in 1..65535) {
            return failure(TransportError.Code.INVALID_ENDPOINT, "Wi-Fi Direct endpoint is invalid or expired", false)
        }
        val closed = AtomicBoolean(false)
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (closed.get() || intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                    if (!info.groupFormed || info.groupOwnerAddress == null) return@requestConnectionInfo
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(info.groupOwnerAddress.hostAddress, port), connectTimeoutMillis)
                        onConnected(SocketTransportConnection(socket, endpoint, capabilities, maxEnvelopeBytes))
                    } catch (_: IOException) {
                        // The caller receives no connection; the durable transfer state will remain resumable.
                    }
                }
            }
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.connect(
            channel,
            WifiP2pConfig().apply {
                this.deviceAddress = deviceAddress
                wps.setup = WpsInfo.PBC
            },
            actionListener { },
        )
        return TransportResult.Success(Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Receiver was already unregistered.
                }
                manager.cancelConnect(channel, actionListener { })
            }
        })
    }

    private fun actionListener(onSuccess: () -> Unit): WifiP2pManager.ActionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = onSuccess()
        override fun onFailure(reason: Int) = Unit
    }

    private fun <T> failure(code: TransportError.Code, message: String, retryable: Boolean): TransportResult<T> =
        TransportResult.Failure(TransportError(code, message, retryable))
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
        return try {
            ProtoDelimitedIo.write(frame, output, maxFrameBytes)
            TransportResult.Success(Unit)
        } catch (_: IOException) {
            close()
            failure(TransportError.Code.IO_FAILURE, "Wi-Fi Direct frame write failed", true)
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
            failure(TransportError.Code.IO_FAILURE, "Wi-Fi Direct frame read failed", true)
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
