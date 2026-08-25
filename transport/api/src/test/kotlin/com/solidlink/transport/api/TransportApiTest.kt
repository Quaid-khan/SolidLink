package com.solidlink.transport.api

import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportApiTest {
    @Test
    fun loopbackPreservesOrderAndCopiesFrames() {
        val (sender, receiver) = LoopbackTransportAdapter.createPair(queueCapacity = 4)
        val first = byteArrayOf(1, 2, 3)
        assertTrue(sender.send(first) is TransportResult.Success)
        first[0] = 99
        assertTrue(sender.send(byteArrayOf(4, 5, 6)) is TransportResult.Success)

        val one = (receiver.receive(10) as TransportResult.Success).value
        val two = (receiver.receive(10) as TransportResult.Success).value

        assertArrayEquals(byteArrayOf(1, 2, 3), one)
        assertArrayEquals(byteArrayOf(4, 5, 6), two)
    }

    @Test
    fun loopbackReportsBackpressureAndTimeout() {
        val (sender, receiver) = LoopbackTransportAdapter.createPair(queueCapacity = 1)
        assertTrue(sender.send(byteArrayOf(1)) is TransportResult.Success)
        val backpressure = sender.send(byteArrayOf(2)) as TransportResult.Failure
        assertEquals(TransportError.Code.BACKPRESSURE, backpressure.error.code)
        assertTrue(receiver.receive(0) is TransportResult.Success)
        val timeout = receiver.receive(0) as TransportResult.Failure
        assertEquals(TransportError.Code.TIMEOUT, timeout.error.code)
    }

    @Test
    fun closePropagatesAsClosedOnPeerAndOwnOperations() {
        val (first, second) = LoopbackTransportAdapter.createPair()
        first.close()

        assertFalse(first.isOpen())
        assertFalse(second.isOpen())
        assertEquals(TransportError.Code.CLOSED, (first.send(byteArrayOf(1)) as TransportResult.Failure).error.code)
        assertEquals(TransportError.Code.CLOSED, (second.send(byteArrayOf(1)) as TransportResult.Failure).error.code)
    }

    @Test
    fun discoveryCallbackCanBeRegisteredAndRemoved() {
        val adapter = LoopbackTransportAdapter()
        val observed = AtomicReference<DiscoveredPeer?>()
        val registration = adapter.discover { observed.set(it) }
        val peer = DiscoveredPeer(
            peerId = "peer-1",
            displayName = "Test peer",
            endpoint = TransportEndpoint("127.0.0.1", 1234, "wifi-1", TransportKind.LOOPBACK, true),
            capabilities = adapter.capabilities,
        )

        adapter.announce(peer)
        assertEquals(peer, observed.get())
        registration.close()
        observed.set(null)
        adapter.announce(peer)
        assertEquals(null, observed.get())
    }

    @Test
    fun localPolicyRejectsUnapprovedEndpoints() {
        val endpoint = TransportEndpoint("203.0.113.10", 1234, null, TransportKind.LOOPBACK, false)
        assertFalse(LocalEndpointPolicy.accepts(endpoint))
    }
}
