package com.solidlink.transfer

import com.solidlink.domain.TransferPolicy
import com.solidlink.domain.TrustState
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public data class TransferPeer(
    val peerId: String,
    val displayName: String,
    val trustState: TrustState,
    val connectedAt: Instant,
)

public data class PeerAssignment(
    val peerId: String,
    val chunkIndex: Long,
    val assignedAt: Instant,
)

public sealed interface PeerJoinResult {
    public data class Accepted(val peer: TransferPeer) : PeerJoinResult
    public data class Rejected(val code: Code, val safeMessage: String) : PeerJoinResult

    public enum class Code {
        SESSION_CLOSED,
        PEER_NOT_APPROVED,
        RECEIVER_LIMIT_REACHED,
        DUPLICATE_PEER,
    }
}

public class MultiPeerCoordinator(
    private val policy: TransferPolicy,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val peers = LinkedHashMap<String, TransferPeer>()
    private val assignments = LinkedHashMap<Long, String>()
    private var closed = false

    @Synchronized
    public fun join(peer: TransferPeer): PeerJoinResult {
        if (closed) return PeerJoinResult.Rejected(PeerJoinResult.Code.SESSION_CLOSED, "Sharing session is closed")
        if (peer.trustState != TrustState.APPROVED) return PeerJoinResult.Rejected(PeerJoinResult.Code.PEER_NOT_APPROVED, "Peer approval is required")
        if (peers.containsKey(peer.peerId)) return PeerJoinResult.Rejected(PeerJoinResult.Code.DUPLICATE_PEER, "Peer is already connected")
        if (!policy.allowMultipleReceivers && peers.isNotEmpty()) {
            return PeerJoinResult.Rejected(PeerJoinResult.Code.RECEIVER_LIMIT_REACHED, "This session allows one receiver")
        }
        peers[peer.peerId] = peer
        return PeerJoinResult.Accepted(peer)
    }

    @Synchronized
    public fun leave(peerId: String) {
        peers.remove(peerId)
    }

    @Synchronized
    public fun assignNextChunk(nextChunkIndex: Long): PeerAssignment? {
        if (closed || peers.isEmpty() || assignments.size >= policy.maxInFlightChunks) return null
        if (nextChunkIndex < 0 || assignments.containsKey(nextChunkIndex)) return null
        val selected = peers.values.minWithOrNull(
            compareBy<TransferPeer> { peer -> assignments.values.count { it == peer.peerId } }
                .thenBy { it.connectedAt }
                .thenBy { it.peerId },
        ) ?: return null
        assignments[nextChunkIndex] = selected.peerId
        return PeerAssignment(selected.peerId, nextChunkIndex, clock.instant())
    }

    @Synchronized
    public fun acknowledgeChunk(chunkIndex: Long): Boolean = assignments.remove(chunkIndex) != null

    @Synchronized
    public fun connectedPeers(): List<TransferPeer> = peers.values.toList()

    @Synchronized
    public fun inFlightCount(): Int = assignments.size

    @Synchronized
    public fun close() {
        closed = true
        peers.clear()
        assignments.clear()
    }
}

public data class TemporaryShareSession(
    val sessionId: String,
    val displayName: String,
    val code: String,
    val codeHash: ByteArray,
    val expiresAt: Instant,
) {
    public fun isExpired(at: Instant): Boolean = !at.isBefore(expiresAt)
}

public sealed interface ShareSessionAccessResult {
    public data class Accepted(val session: TemporaryShareSession) : ShareSessionAccessResult
    public data class Rejected(val code: Code, val safeMessage: String) : ShareSessionAccessResult

    public enum class Code {
        NOT_FOUND,
        EXPIRED,
        INVALID_CODE,
        CLOSED,
    }
}

public class TemporaryShareSessionManager(
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val lifetime: Duration = Duration.ofMinutes(10),
) {
    private val sessions = LinkedHashMap<String, TemporaryShareSession>()
    private var closed = false

    init {
        require(!lifetime.isZero && !lifetime.isNegative) { "lifetime must be positive" }
    }

    @Synchronized
    public fun create(sessionId: String, displayName: String): TemporaryShareSession {
        check(!closed) { "manager is closed" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val session = TemporaryShareSession(
            sessionId = sessionId,
            displayName = displayName.take(80),
            code = code,
            codeHash = hashCode(code),
            expiresAt = clock.instant().plus(lifetime),
        )
        sessions[sessionId] = session
        return session
    }

    @Synchronized
    public fun accept(sessionId: String, enteredCode: String): ShareSessionAccessResult {
        if (closed) return ShareSessionAccessResult.Rejected(ShareSessionAccessResult.Code.CLOSED, "Sharing is closed")
        val session = sessions[sessionId]
            ?: return ShareSessionAccessResult.Rejected(ShareSessionAccessResult.Code.NOT_FOUND, "Sharing session was not found")
        if (session.isExpired(clock.instant())) {
            sessions.remove(sessionId)
            return ShareSessionAccessResult.Rejected(ShareSessionAccessResult.Code.EXPIRED, "Sharing session has expired")
        }
        return if (MessageDigest.isEqual(session.codeHash, hashCode(enteredCode))) {
            ShareSessionAccessResult.Accepted(session)
        } else {
            ShareSessionAccessResult.Rejected(ShareSessionAccessResult.Code.INVALID_CODE, "Share code is invalid")
        }
    }

    @Synchronized
    public fun expireNow(): List<String> {
        val now = clock.instant()
        val expired = sessions.values.filter { it.isExpired(now) }.map { it.sessionId }
        expired.forEach(sessions::remove)
        return expired
    }

    @Synchronized
    public fun close() {
        closed = true
        sessions.clear()
    }

    private fun hashCode(code: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(CODE_HASH_KEY, "HmacSHA256"))
        return mac.doFinal(code.toByteArray(StandardCharsets.UTF_8))
    }

    private companion object {
        val CODE_HASH_KEY = "SolidLink/share-code/v1".toByteArray(StandardCharsets.UTF_8)
    }
}
