package com.solidlink.domain

import com.solidlink.common.OpaqueId
import java.time.Instant

public enum class SessionRole {
    HOST,
    RECEIVER,
}

public enum class SessionStatus {
    CREATED,
    ADVERTISING,
    PEER_PENDING,
    AUTHENTICATING,
    ACTIVE,
    CLOSING,
    EXPIRED,
    CLOSED,
    FAILED,
}

public enum class TransferDirection {
    SEND,
    RECEIVE,
}

public enum class TransferStatus {
    QUEUED,
    NEGOTIATING,
    TRANSFERRING,
    VERIFYING,
    COMMITTING,
    COMPLETED,
    PAUSED,
    CANCEL_REQUESTED,
    RETRY_WAIT,
    FAILED,
}

public enum class TrustState {
    UNKNOWN,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
}

public enum class ChunkStatus {
    EXPECTED,
    RECEIVING,
    VERIFIED,
    FAILED,
}

public enum class ExportStatus {
    QUEUED,
    WRITING,
    COMPLETED,
    FAILED,
}

public enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

public sealed interface DomainError {
    public data class InvalidTransition(
        val aggregate: String,
        val from: String,
        val to: String,
    ) : DomainError

    public data class ExpiredSession(val sessionId: OpaqueId) : DomainError

    public data class VerificationRequired(val objectId: OpaqueId) : DomainError

    public data class InvalidPolicy(val message: String) : DomainError

    public data class PersistenceFailure(
        val operation: String,
        val safeMessage: String,
    ) : DomainError
}

public sealed interface TransitionResult<out T> {
    public data class Success<T>(val value: T) : TransitionResult<T>

    public data class Failure(val error: DomainError) : TransitionResult<Nothing>
}

public fun SessionStatus.canTransitionTo(next: SessionStatus): Boolean = when (this) {
    SessionStatus.CREATED -> next in setOf(SessionStatus.ADVERTISING, SessionStatus.CLOSED, SessionStatus.FAILED)
    SessionStatus.ADVERTISING -> next in setOf(SessionStatus.PEER_PENDING, SessionStatus.CLOSING, SessionStatus.EXPIRED, SessionStatus.FAILED)
    SessionStatus.PEER_PENDING -> next in setOf(SessionStatus.AUTHENTICATING, SessionStatus.CLOSING, SessionStatus.EXPIRED, SessionStatus.FAILED)
    SessionStatus.AUTHENTICATING -> next in setOf(SessionStatus.ACTIVE, SessionStatus.CLOSING, SessionStatus.FAILED)
    SessionStatus.ACTIVE -> next in setOf(SessionStatus.CLOSING, SessionStatus.EXPIRED, SessionStatus.FAILED)
    SessionStatus.CLOSING -> next in setOf(SessionStatus.CLOSED, SessionStatus.FAILED)
    SessionStatus.EXPIRED, SessionStatus.CLOSED, SessionStatus.FAILED -> false
}

public fun TransferStatus.canTransitionTo(next: TransferStatus): Boolean = when (this) {
    TransferStatus.QUEUED -> next in setOf(TransferStatus.NEGOTIATING, TransferStatus.CANCEL_REQUESTED, TransferStatus.FAILED)
    TransferStatus.NEGOTIATING -> next in setOf(TransferStatus.TRANSFERRING, TransferStatus.PAUSED, TransferStatus.CANCEL_REQUESTED, TransferStatus.RETRY_WAIT, TransferStatus.FAILED)
    TransferStatus.TRANSFERRING -> next in setOf(TransferStatus.VERIFYING, TransferStatus.PAUSED, TransferStatus.CANCEL_REQUESTED, TransferStatus.RETRY_WAIT, TransferStatus.FAILED)
    TransferStatus.VERIFYING -> next in setOf(TransferStatus.COMMITTING, TransferStatus.RETRY_WAIT, TransferStatus.FAILED)
    TransferStatus.COMMITTING -> next in setOf(TransferStatus.COMPLETED, TransferStatus.RETRY_WAIT, TransferStatus.FAILED)
    TransferStatus.COMPLETED, TransferStatus.FAILED -> false
    TransferStatus.PAUSED -> next in setOf(TransferStatus.NEGOTIATING, TransferStatus.TRANSFERRING, TransferStatus.CANCEL_REQUESTED, TransferStatus.FAILED)
    TransferStatus.CANCEL_REQUESTED -> next in setOf(TransferStatus.PAUSED, TransferStatus.FAILED)
    TransferStatus.RETRY_WAIT -> next in setOf(TransferStatus.NEGOTIATING, TransferStatus.TRANSFERRING, TransferStatus.FAILED, TransferStatus.CANCEL_REQUESTED)
}

public fun SessionStatus.transitionTo(next: SessionStatus): TransitionResult<SessionStatus> =
    if (canTransitionTo(next)) {
        TransitionResult.Success(next)
    } else {
        TransitionResult.Failure(DomainError.InvalidTransition("ShareSession", name, next.name))
    }

public fun TransferStatus.transitionTo(next: TransferStatus): TransitionResult<TransferStatus> =
    if (canTransitionTo(next)) {
        TransitionResult.Success(next)
    } else {
        TransitionResult.Failure(DomainError.InvalidTransition("TransferObject", name, next.name))
    }

public data class ShareSession(
    val sessionId: OpaqueId,
    val role: SessionRole,
    val status: SessionStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val hostCodeHash: String,
    val advertisedName: String,
    val transportHints: Set<String>,
    val securityPolicy: SecurityPolicy,
) {
    public fun isExpired(at: Instant): Boolean = !at.isBefore(expiresAt)
}

public data class PeerRecord(
    val peerId: OpaqueId,
    val sessionId: OpaqueId,
    val displayName: String,
    val transport: String,
    val endpointMetadata: Map<String, String>,
    val capabilities: Set<String>,
    val trustState: TrustState,
    val lastSeenAt: Instant,
)

public data class TransferBatch(
    val batchId: OpaqueId,
    val sessionId: OpaqueId,
    val direction: TransferDirection,
    val status: TransferStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val policy: TransferPolicy,
    val totalBytes: Long,
    val verifiedBytes: Long,
)

public data class TransferObject(
    val objectId: OpaqueId,
    val batchId: OpaqueId,
    val peerId: OpaqueId,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val chunkSize: Int,
    val chunkCount: Long?,
    val sourceUri: String?,
    val stagingPath: String?,
    val finalDigest: String?,
    val status: TransferStatus,
    val finalVerified: Boolean,
    val committed: Boolean,
) {
    public fun canBeMarkedCompleted(): TransitionResult<TransferObject> = when {
        status != TransferStatus.COMMITTING -> TransitionResult.Failure(
            DomainError.InvalidTransition("TransferObject", status.name, TransferStatus.COMPLETED.name),
        )
        !finalVerified -> TransitionResult.Failure(DomainError.VerificationRequired(objectId))
        else -> TransitionResult.Success(copy(status = TransferStatus.COMPLETED, committed = true))
    }
}

public data class ChunkState(
    val objectId: OpaqueId,
    val chunkIndex: Long,
    val offset: Long,
    val length: Int,
    val digest: String?,
    val state: ChunkStatus,
    val attempts: Int,
    val durableAt: Instant?,
)

public data class Checkpoint(
    val objectId: OpaqueId,
    val highestDurableSequence: Long,
    val verifiedRanges: List<LongRange>,
    val stagingLength: Long,
    val updatedAt: Instant,
)

public data class DeviceIdentity(
    val identityId: OpaqueId,
    val publicKey: ByteArray,
    val label: String,
    val createdAt: Instant,
    val trustState: TrustState,
) {
    init {
        require(publicKey.isNotEmpty()) { "publicKey must not be empty" }
    }

    override fun equals(other: Any?): Boolean = other is DeviceIdentity &&
        identityId == other.identityId &&
        publicKey.contentEquals(other.publicKey) &&
        label == other.label &&
        createdAt == other.createdAt &&
        trustState == other.trustState

    override fun hashCode(): Int = 31 * identityId.hashCode() + publicKey.contentHashCode()
}

public data class ExportJob(
    val objectId: OpaqueId,
    val destinationUri: String,
    val status: ExportStatus,
    val bytesWritten: Long,
    val errorCode: String?,
    val createdAt: Instant,
)

public data class DiagnosticEvent(
    val eventId: OpaqueId,
    val sessionId: OpaqueId?,
    val category: String,
    val code: String,
    val severity: DiagnosticSeverity,
    val safeMessage: String,
    val createdAt: Instant,
)

public data class SecurityPolicy(
    val requirePeerApproval: Boolean = true,
    val allowAdvancedSas: Boolean = true,
    val localOnlyRequired: Boolean = true,
)

public data class TransferPolicy(
    val allowMultipleReceivers: Boolean = true,
    val maxInFlightChunks: Int = 4,
    val maxInFlightBytes: Long = 4L * 1024L * 1024L,
) {
    init {
        require(maxInFlightChunks > 0) { "maxInFlightChunks must be positive" }
        require(maxInFlightBytes > 0) { "maxInFlightBytes must be positive" }
    }
}
