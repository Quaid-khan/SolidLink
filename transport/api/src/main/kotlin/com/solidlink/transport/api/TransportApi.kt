package com.solidlink.transport.api

import java.io.Closeable
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.io.OutputStream

public enum class TransportKind {
    LAN_NSD,
    WIFI_DIRECT,
    WIFI_AWARE,
    LOOPBACK,
}

public data class TransportEndpoint(
    val hostAddress: String,
    val port: Int,
    val networkToken: String?,
    val transport: TransportKind,
    val locallyDiscovered: Boolean,
) {
    init {
        require(port in 1..65535) { "port must be in the TCP range" }
        require(hostAddress.isNotBlank()) { "hostAddress must not be blank" }
    }
}

public data class TransportCapabilities(
    val kind: TransportKind,
    val maxEnvelopeBytes: Int,
    val reliableOrdered: Boolean,
    val supportsMultipleReceivers: Boolean,
    val localOnlyRequired: Boolean,
)

public data class DiscoveredPeer(
    val peerId: String,
    val displayName: String,
    val endpoint: TransportEndpoint,
    val capabilities: TransportCapabilities,
)

public sealed interface TransportResult<out T> {
    public data class Success<T>(val value: T) : TransportResult<T>

    public data class Failure(val error: TransportError) : TransportResult<Nothing>
}

public data class TransportError(
    val code: Code,
    val safeMessage: String,
    val retryable: Boolean,
) {
    public enum class Code {
        CLOSED,
        TIMEOUT,
        BACKPRESSURE,
        INVALID_ENDPOINT,
        UNSUPPORTED,
        IO_FAILURE,
    }
}

public interface TransportConnection : Closeable {
    public val endpoint: TransportEndpoint
    public val capabilities: TransportCapabilities

    public fun send(frame: ByteArray): TransportResult<Unit>

    public fun receive(timeoutMillis: Long): TransportResult<ByteArray>

    public fun isOpen(): Boolean
}

public interface StreamTransportConnection : TransportConnection {
    public val input: InputStream
    public val output: OutputStream
}

public interface TransportAdapter {
    public val kind: TransportKind
    public val capabilities: TransportCapabilities

    public fun discover(listener: (DiscoveredPeer) -> Unit): Closeable

    public fun connect(endpoint: TransportEndpoint): TransportResult<TransportConnection>
}

public object LocalEndpointPolicy {
    public fun accepts(endpoint: TransportEndpoint): Boolean {
        if (!endpoint.locallyDiscovered || endpoint.networkToken.isNullOrBlank() || endpoint.hostAddress.isBlank()) return false
        if (endpoint.port !in 1..65535) return false
        if (endpoint.transport == TransportKind.LOOPBACK) return endpoint.hostAddress == "127.0.0.1" || endpoint.hostAddress == "::1"
        return isLocalAddress(endpoint.hostAddress)
    }

    private fun isLocalAddress(hostAddress: String): Boolean = runCatching {
        val address = InetAddress.getByName(hostAddress)
        when {
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress -> false
            address is Inet4Address -> {
                val bytes = address.address.map { it.toInt() and 0xff }
                bytes[0] == 10 ||
                    (bytes[0] == 172 && bytes[1] in 16..31) ||
                    (bytes[0] == 192 && bytes[1] == 168) ||
                    (bytes[0] == 169 && bytes[1] == 254)
            }
            address is Inet6Address -> address.isLinkLocalAddress || address.isSiteLocalAddress
            else -> false
        }
    }.getOrDefault(false)
}
