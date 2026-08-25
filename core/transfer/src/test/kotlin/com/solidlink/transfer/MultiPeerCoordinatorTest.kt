package com.solidlink.transfer

import com.solidlink.domain.TransferPolicy
import com.solidlink.domain.TrustState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPeerCoordinatorTest {
    @Test
    fun schedulerRejectsUnapprovedPeersAndBalancesApprovedPeers() {
        val now = Instant.parse("2026-08-25T00:00:00Z")
        val coordinator = MultiPeerCoordinator(
            TransferPolicy(allowMultipleReceivers = true, maxInFlightChunks = 4, maxInFlightBytes = 1_000_000),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val unapproved = peer("unapproved", TrustState.PENDING_APPROVAL, now)
        assertEquals(PeerJoinResult.Code.PEER_NOT_APPROVED, (coordinator.join(unapproved) as PeerJoinResult.Rejected).code)
        assertTrue(coordinator.join(peer("peer-a", TrustState.APPROVED, now)).let { it is PeerJoinResult.Accepted })
        assertTrue(coordinator.join(peer("peer-b", TrustState.APPROVED, now.plusSeconds(1))).let { it is PeerJoinResult.Accepted })

        val first = coordinator.assignNextChunk(0)
        val second = coordinator.assignNextChunk(1)
        val third = coordinator.assignNextChunk(2)

        assertEquals("peer-a", first?.peerId)
        assertEquals("peer-b", second?.peerId)
        assertEquals("peer-a", third?.peerId)
        assertEquals(3, coordinator.inFlightCount())
        assertFalse(coordinator.acknowledgeChunk(99))
        assertTrue(coordinator.acknowledgeChunk(1))
    }

    @Test
    fun schedulerEnforcesSingleReceiverAndInFlightLimit() {
        val coordinator = MultiPeerCoordinator(
            TransferPolicy(allowMultipleReceivers = false, maxInFlightChunks = 1, maxInFlightBytes = 100),
        )
        assertTrue(coordinator.join(peer("peer-a", TrustState.APPROVED, Instant.EPOCH)) is PeerJoinResult.Accepted)
        assertEquals(PeerJoinResult.Code.RECEIVER_LIMIT_REACHED, (coordinator.join(peer("peer-b", TrustState.APPROVED, Instant.EPOCH)) as PeerJoinResult.Rejected).code)
        assertEquals("peer-a", coordinator.assignNextChunk(0)?.peerId)
        assertEquals(null, coordinator.assignNextChunk(1))
        coordinator.close()
        assertEquals(null, coordinator.assignNextChunk(2))
    }

    @Test
    fun temporaryShareSessionAcceptsOnlyCorrectCodeBeforeExpiration() {
        val clock = MutableClock(Instant.parse("2026-08-25T00:00:00Z"))
        val manager = TemporaryShareSessionManager(clock = clock, lifetime = Duration.ofMinutes(5))
        val session = manager.create("session-1", "Host device")

        assertEquals(6, session.code.length)
        assertTrue(manager.accept("session-1", session.code) is ShareSessionAccessResult.Accepted)
        assertEquals(
            ShareSessionAccessResult.Code.INVALID_CODE,
            (manager.accept("session-1", "999999") as ShareSessionAccessResult.Rejected).code,
        )
        clock.advance(Duration.ofMinutes(5))
        assertEquals(
            ShareSessionAccessResult.Code.EXPIRED,
            (manager.accept("session-1", session.code) as ShareSessionAccessResult.Rejected).code,
        )
        assertTrue(manager.expireNow().isEmpty())
    }

    @Test
    fun closedShareManagerRejectsNewAndExistingAccess() {
        val manager = TemporaryShareSessionManager()
        val session = manager.create("session-1", "Host device")
        manager.close()
        assertEquals(
            ShareSessionAccessResult.Code.CLOSED,
            (manager.accept(session.sessionId, session.code) as ShareSessionAccessResult.Rejected).code,
        )
        assertThrows(IllegalStateException::class.java) { manager.create("session-2", "Other") }
    }

    private fun peer(id: String, trustState: TrustState, connectedAt: Instant): TransferPeer =
        TransferPeer(id, id, trustState, connectedAt)

    private class MutableClock(initial: Instant) : Clock() {
        private var current = initial
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
