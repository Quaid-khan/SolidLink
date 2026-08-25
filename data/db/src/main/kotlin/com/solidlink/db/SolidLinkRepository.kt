package com.solidlink.db

import androidx.room.withTransaction
import com.solidlink.domain.Checkpoint
import com.solidlink.domain.ChunkState
import com.solidlink.domain.DeviceIdentity
import com.solidlink.domain.DiagnosticEvent
import com.solidlink.domain.ExportJob
import com.solidlink.domain.PeerRecord
import com.solidlink.domain.ShareSession
import com.solidlink.domain.TransferBatch
import com.solidlink.domain.TransferObject
import com.solidlink.domain.TransitionResult
import com.solidlink.domain.DomainError

class SolidLinkRepository(
    private val database: SolidLinkDatabase,
) {
    suspend fun saveShareSession(session: ShareSession): TransitionResult<Unit> = safe("save_share_session") {
        database.shareSessionDao().upsert(session.toEntity())
    }

    suspend fun loadShareSession(sessionId: String): TransitionResult<ShareSession?> = safe("load_share_session") {
        database.shareSessionDao().findById(sessionId)?.toDomain()
    }

    suspend fun deleteExpiredSessions(nowEpochMs: Long): TransitionResult<Int> = safe("delete_expired_sessions") {
        database.shareSessionDao().deleteExpired(nowEpochMs)
    }

    suspend fun savePeer(peer: PeerRecord): TransitionResult<Unit> = safe("save_peer") {
        database.peerRecordDao().upsert(peer.toEntity())
    }

    suspend fun loadPeers(sessionId: String): TransitionResult<List<PeerRecord>> = safe("load_peers") {
        database.peerRecordDao().forSession(sessionId).map { it.toDomain() }
    }

    suspend fun saveBatch(batch: TransferBatch): TransitionResult<Unit> = safe("save_batch") {
        database.transferBatchDao().upsert(batch.toEntity())
    }

    suspend fun loadBatch(batchId: String): TransitionResult<TransferBatch?> = safe("load_batch") {
        database.transferBatchDao().findById(batchId)?.toDomain()
    }

    suspend fun saveObject(objectModel: TransferObject): TransitionResult<Unit> = safe("save_object") {
        database.transferObjectDao().upsert(objectModel.toEntity())
    }

    suspend fun loadObject(objectId: String): TransitionResult<TransferObject?> = safe("load_object") {
        database.transferObjectDao().findById(objectId)?.toDomain()
    }

    suspend fun persistVerifiedChunkAndCheckpoint(
        chunk: ChunkState,
        checkpoint: Checkpoint,
    ): TransitionResult<Unit> = safe("persist_verified_chunk_and_checkpoint") {
        database.withTransaction {
            database.chunkStateDao().upsert(chunk.toEntity())
            database.checkpointDao().upsert(checkpoint.toEntity())
        }
    }

    suspend fun loadChunks(objectId: String): TransitionResult<List<ChunkState>> = safe("load_chunks") {
        database.chunkStateDao().forObject(objectId).map { it.toDomain() }
    }

    suspend fun loadCheckpoint(objectId: String): TransitionResult<Checkpoint?> = safe("load_checkpoint") {
        database.checkpointDao().findByObjectId(objectId)?.toDomain()
    }

    suspend fun saveIdentity(identity: DeviceIdentity): TransitionResult<Unit> = safe("save_identity") {
        database.deviceIdentityDao().upsert(identity.toEntity())
    }

    suspend fun loadIdentity(identityId: String): TransitionResult<DeviceIdentity?> = safe("load_identity") {
        database.deviceIdentityDao().findById(identityId)?.toDomain()
    }

    suspend fun saveExportJob(job: ExportJob): TransitionResult<Long> = safe("save_export_job") {
        database.exportJobDao().upsert(job.toEntity())
    }

    suspend fun saveDiagnostic(event: DiagnosticEvent): TransitionResult<Unit> = safe("save_diagnostic") {
        database.diagnosticEventDao().insert(event.toEntity())
    }

    private suspend fun <T> safe(
        operation: String,
        block: suspend () -> T,
    ): TransitionResult<T> = try {
        TransitionResult.Success(block())
    } catch (_: Throwable) {
        TransitionResult.Failure(
            DomainError.PersistenceFailure(
                operation = operation,
                safeMessage = "Local persistence operation failed",
            ),
        )
    }
}
