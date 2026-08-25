package com.solidlink.db

import com.solidlink.common.OpaqueId
import com.solidlink.domain.Checkpoint
import com.solidlink.domain.SecurityPolicy
import com.solidlink.domain.SessionRole
import com.solidlink.domain.SessionStatus
import com.solidlink.domain.ShareSession
import com.solidlink.domain.TransferDirection
import com.solidlink.domain.TransferPolicy
import com.solidlink.domain.TransferStatus
import com.solidlink.domain.TrustState
import com.solidlink.domain.TransferBatch
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMappingsTest {
    private val now = Instant.parse("2026-08-25T10:00:00Z")

    @Test
    fun shareSessionRoundTripPreservesLocalState() {
        val source = ShareSession(
            sessionId = OpaqueId("session-1"),
            role = SessionRole.HOST,
            status = SessionStatus.ADVERTISING,
            createdAt = now,
            expiresAt = now.plusSeconds(300),
            hostCodeHash = "hash-only",
            advertisedName = "Alice phone",
            transportHints = setOf("lan-nsd", "wifi-direct"),
            securityPolicy = SecurityPolicy(
                requirePeerApproval = true,
                allowAdvancedSas = false,
                localOnlyRequired = true,
            ),
        )

        val restored = source.toEntity().toDomain()

        assertEquals(source, restored)
        assertEquals("hash-only", restored.hostCodeHash)
    }

    @Test
    fun checkpointRoundTripPreservesLongRanges() {
        val source = Checkpoint(
            objectId = OpaqueId("object-1"),
            highestDurableSequence = 42,
            verifiedRanges = listOf(0L..3L, 8L..12L),
            stagingLength = 13,
            updatedAt = now,
        )

        assertEquals(source, source.toEntity().toDomain())
    }

    @Test
    fun roomEntityDoesNotContainPrivateKeyOrSessionSecretFields() {
        val fieldNames = DeviceIdentityEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("publicKey" in fieldNames)
        assertTrue("privateKey" !in fieldNames)
        assertTrue("sessionKey" !in fieldNames)
    }

    @Test
    fun transferBatchRoundTripPreservesPolicy() {
        val source = TransferBatch(
            batchId = OpaqueId("batch-1"),
            sessionId = OpaqueId("session-1"),
            direction = TransferDirection.RECEIVE,
            status = TransferStatus.PAUSED,
            createdAt = now,
            updatedAt = now.plusSeconds(1),
            policy = TransferPolicy(
                allowMultipleReceivers = false,
                maxInFlightChunks = 2,
                maxInFlightBytes = 1024,
            ),
            totalBytes = 2048,
            verifiedBytes = 1024,
        )

        assertEquals(source, source.toEntity().toDomain())
        assertEquals(TrustState.UNKNOWN, TrustState.valueOf("UNKNOWN"))
    }
}
