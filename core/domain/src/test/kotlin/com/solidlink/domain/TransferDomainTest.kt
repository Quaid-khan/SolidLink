package com.solidlink.domain

import com.solidlink.common.OpaqueId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDomainTest {
    @Test
    fun sessionLifecycleAcceptsOnlyDocumentedForwardTransitions() {
        assertTrue(SessionStatus.CREATED.canTransitionTo(SessionStatus.ADVERTISING))
        assertTrue(SessionStatus.ADVERTISING.canTransitionTo(SessionStatus.PEER_PENDING))
        assertTrue(SessionStatus.PEER_PENDING.canTransitionTo(SessionStatus.AUTHENTICATING))
        assertTrue(SessionStatus.AUTHENTICATING.canTransitionTo(SessionStatus.ACTIVE))
        assertTrue(SessionStatus.ACTIVE.canTransitionTo(SessionStatus.CLOSING))
        assertTrue(SessionStatus.CLOSING.canTransitionTo(SessionStatus.CLOSED))
        assertFalse(SessionStatus.CREATED.canTransitionTo(SessionStatus.ACTIVE))
        assertFalse(SessionStatus.CLOSED.canTransitionTo(SessionStatus.ACTIVE))
    }

    @Test
    fun transferLifecycleRejectsCompletionBeforeCommit() {
        assertTrue(TransferStatus.COMMITTING.canTransitionTo(TransferStatus.COMPLETED))
        assertFalse(TransferStatus.VERIFYING.canTransitionTo(TransferStatus.COMPLETED))
        assertFalse(TransferStatus.TRANSFERRING.canTransitionTo(TransferStatus.COMPLETED))
    }

    @Test
    fun invalidTransitionReturnsTypedFailure() {
        val result = TransferStatus.QUEUED.transitionTo(TransferStatus.COMPLETED)

        assertEquals(
            TransitionResult.Failure(
                DomainError.InvalidTransition(
                    aggregate = "TransferObject",
                    from = "QUEUED",
                    to = "COMPLETED",
                ),
            ),
            result,
        )
    }

    @Test
    fun completionRequiresVerificationAndCommitState() {
        val objectId = OpaqueId("object-1")
        val base = TransferObject(
            objectId = objectId,
            batchId = OpaqueId("batch-1"),
            peerId = OpaqueId("peer-1"),
            displayName = "sample.bin",
            mimeType = "application/octet-stream",
            sizeBytes = 8,
            chunkSize = 4,
            chunkCount = 2,
            sourceUri = null,
            stagingPath = "/private/staging/object-1",
            finalDigest = "digest",
            status = TransferStatus.COMMITTING,
            finalVerified = false,
            committed = false,
        )

        assertEquals(
            TransitionResult.Failure(DomainError.VerificationRequired(objectId)),
            base.canBeMarkedCompleted(),
        )

        val verified = base.copy(finalVerified = true)
        val completed = (verified.canBeMarkedCompleted() as TransitionResult.Success).value
        assertEquals(TransferStatus.COMPLETED, completed.status)
        assertTrue(completed.committed)
    }

    @Test
    fun sessionExpirationIsInclusiveAtExpiryInstant() {
        val expiry = Instant.parse("2026-08-25T00:00:00Z")
        val session = ShareSession(
            sessionId = OpaqueId("session-1"),
            role = SessionRole.HOST,
            status = SessionStatus.ADVERTISING,
            createdAt = expiry.minusSeconds(60),
            expiresAt = expiry,
            hostCodeHash = "hash",
            advertisedName = "Test device",
            transportHints = setOf("lan-nsd"),
            securityPolicy = SecurityPolicy(),
        )

        assertFalse(session.isExpired(expiry.minusNanos(1)))
        assertTrue(session.isExpired(expiry))
    }
}
