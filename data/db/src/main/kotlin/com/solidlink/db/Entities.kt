package com.solidlink.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "share_sessions")
data class ShareSessionEntity(
    @PrimaryKey val sessionId: String,
    val role: String,
    val status: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val hostCodeHash: String,
    val advertisedName: String,
    val transportHints: String,
    val securityPolicy: String,
)

@Entity(
    tableName = "peer_records",
    indices = [Index(value = ["sessionId"]), Index(value = ["sessionId", "peerId"], unique = true)],
)
data class PeerRecordEntity(
    @PrimaryKey val peerId: String,
    val sessionId: String,
    val displayName: String,
    val transport: String,
    val endpointMetadata: String,
    val capabilities: String,
    val trustState: String,
    val lastSeenAtEpochMs: Long,
)

@Entity(tableName = "transfer_batches", indices = [Index(value = ["sessionId"])])
data class TransferBatchEntity(
    @PrimaryKey val batchId: String,
    val sessionId: String,
    val direction: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val allowMultipleReceivers: Boolean,
    val maxInFlightChunks: Int,
    val maxInFlightBytes: Long,
    val totalBytes: Long,
    val verifiedBytes: Long,
)

@Entity(
    tableName = "transfer_objects",
    indices = [Index(value = ["batchId"]), Index(value = ["peerId"])],
)
data class TransferObjectEntity(
    @PrimaryKey val objectId: String,
    val batchId: String,
    val peerId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val chunkSize: Int,
    val chunkCount: Long?,
    val sourceUri: String?,
    val stagingPath: String?,
    val finalDigest: String?,
    val status: String,
    val finalVerified: Boolean,
    val committed: Boolean,
)

@Entity(
    tableName = "chunk_states",
    primaryKeys = ["objectId", "chunkIndex"],
    indices = [Index(value = ["objectId"])],
)
data class ChunkStateEntity(
    val objectId: String,
    val chunkIndex: Long,
    val offset: Long,
    val length: Int,
    val digest: String?,
    val state: String,
    val attempts: Int,
    val durableAtEpochMs: Long?,
)

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey val objectId: String,
    val highestDurableSequence: Long,
    val verifiedRanges: String,
    val stagingLength: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "device_identities")
data class DeviceIdentityEntity(
    @PrimaryKey val identityId: String,
    val publicKey: ByteArray,
    val label: String,
    val createdAtEpochMs: Long,
    val trustState: String,
)

@Entity(tableName = "export_jobs", indices = [Index(value = ["objectId"])])
data class ExportJobEntity(
    @PrimaryKey(autoGenerate = true) val jobId: Long = 0,
    val objectId: String,
    val destinationUri: String,
    val status: String,
    val bytesWritten: Long,
    val errorCode: String?,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "diagnostic_events", indices = [Index(value = ["sessionId"])])
data class DiagnosticEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String?,
    val category: String,
    val code: String,
    val severity: String,
    val safeMessage: String,
    val createdAtEpochMs: Long,
)
