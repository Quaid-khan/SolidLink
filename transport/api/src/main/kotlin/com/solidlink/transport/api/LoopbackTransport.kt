package com.solidlink.transport.api

import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

public class LoopbackTransportAdapter(
    private val queueCapacity: Int = 8,
    override val capabilities: TransportCapabilities = TransportCapabilities(
        kind = TransportKind.LOOPBACK,
        maxEnvelopeBytes = 1024 * 1024,
        reliableOrdered = true,
        supportsMultipleReceivers = true,
        localOnlyRequired = true,
    ),
) : TransportAdapter {
    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
    }

    override val kind: TransportKind = TransportKind.LOOPBACK

    private var listener: ((DiscoveredPeer) -> Unit)? = null

    override fun discover(listener: (DiscoveredPeer) -> Unit): Closeable {
        this.listener = listener
        return Closeable { this.listener = null }
    }

    override fun connect(endpoint: TransportEndpoint): TransportResult<TransportConnection> {
        if (endpoint.transport != TransportKind.LOOPBACK) {
            return TransportResult.Failure(TransportError(TransportError.Code.INVALID_ENDPOINT, "Endpoint is not a loopback endpoint", false))
        }
        if (!LocalEndpointPolicy.accepts(endpoint)) {
            return TransportResult.Failure(TransportError(TransportError.Code.INVALID_ENDPOINT, "Endpoint is not approved as local", false))
        }
        val pair = createPair(queueCapacity, endpoint, capabilities)
        return TransportResult.Success(pair.first)
    }

    public fun announce(peer: DiscoveredPeer) {
        listener?.invoke(peer)
    }

    public companion object {
        public fun createPair(
            queueCapacity: Int = 8,
            endpoint: TransportEndpoint = TransportEndpoint("127.0.0.1", 1, "loopback", TransportKind.LOOPBACK, true),
            capabilities: TransportCapabilities = TransportCapabilities(
                kind = TransportKind.LOOPBACK,
                maxEnvelopeBytes = 1024 * 1024,
                reliableOrdered = true,
                supportsMultipleReceivers = true,
                localOnlyRequired = true,
            ),
        ): Pair<TransportConnection, TransportConnection> {
            val firstToSecond = LinkedBlockingQueue<ByteArray>(queueCapacity)
            val secondToFirst = LinkedBlockingQueue<ByteArray>(queueCapacity)
            val firstOpen = AtomicBoolean(true)
            val secondOpen = AtomicBoolean(true)
            val first = LoopbackConnection(endpoint, capabilities, firstToSecond, secondToFirst, firstOpen, secondOpen)
            val second = LoopbackConnection(endpoint, capabilities, secondToFirst, firstToSecond, secondOpen, firstOpen)
            return first to second
        }
    }
}

private class LoopbackConnection(
    override val endpoint: TransportEndpoint,
    override val capabilities: TransportCapabilities,
    private val outbound: LinkedBlockingQueue<ByteArray>,
    private val inbound: LinkedBlockingQueue<ByteArray>,
    private val open: AtomicBoolean,
    private val peerOpen: AtomicBoolean,
) : TransportConnection {
    override fun send(frame: ByteArray): TransportResult<Unit> {
        if (!open.get() || !peerOpen.get()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        if (frame.size > capabilities.maxEnvelopeBytes) return failure(TransportError.Code.BACKPRESSURE, "Frame exceeds the transport envelope limit", false)
        return if (outbound.offer(frame.copyOf())) TransportResult.Success(Unit)
        else failure(TransportError.Code.BACKPRESSURE, "Transport send queue is full", true)
    }

    override fun receive(timeoutMillis: Long): TransportResult<ByteArray> {
        if (timeoutMillis < 0) return failure(TransportError.Code.TIMEOUT, "Receive timeout must be non-negative", false)
        if (!open.get()) return failure(TransportError.Code.CLOSED, "Connection is closed", false)
        val frame = inbound.poll(timeoutMillis, TimeUnit.MILLISECONDS)
            ?: return failure(TransportError.Code.TIMEOUT, "No frame arrived before the deadline", true)
        return TransportResult.Success(frame)
    }

    override fun isOpen(): Boolean = open.get() && peerOpen.get()

    override fun close() {
        open.set(false)
    }

    private fun <T> failure(code: TransportError.Code, message: String, retryable: Boolean): TransportResult<T> =
        TransportResult.Failure(TransportError(code, message, retryable))
}
