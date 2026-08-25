package com.solidlink.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ShareSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ShareSessionEntity)

    @Query("SELECT * FROM share_sessions WHERE sessionId = :sessionId")
    suspend fun findById(sessionId: String): ShareSessionEntity?

    @Query("DELETE FROM share_sessions WHERE expiresAtEpochMs <= :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long): Int
}

@Dao
interface PeerRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerRecordEntity)

    @Query("SELECT * FROM peer_records WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: String): List<PeerRecordEntity>
}

@Dao
interface TransferBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(batch: TransferBatchEntity)

    @Query("SELECT * FROM transfer_batches WHERE batchId = :batchId")
    suspend fun findById(batchId: String): TransferBatchEntity?
}

@Dao
interface TransferObjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(objectEntity: TransferObjectEntity)

    @Query("SELECT * FROM transfer_objects WHERE objectId = :objectId")
    suspend fun findById(objectId: String): TransferObjectEntity?

    @Query("SELECT * FROM transfer_objects WHERE batchId = :batchId")
    suspend fun forBatch(batchId: String): List<TransferObjectEntity>
}

@Dao
interface CheckpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: CheckpointEntity)

    @Query("SELECT * FROM checkpoints WHERE objectId = :objectId")
    suspend fun findByObjectId(objectId: String): CheckpointEntity?

}

@Dao
interface ChunkStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chunk: ChunkStateEntity)

    @Query("SELECT * FROM chunk_states WHERE objectId = :objectId ORDER BY chunkIndex")
    suspend fun forObject(objectId: String): List<ChunkStateEntity>
}

@Dao
interface DeviceIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: DeviceIdentityEntity)

    @Query("SELECT * FROM device_identities WHERE identityId = :identityId")
    suspend fun findById(identityId: String): DeviceIdentityEntity?
}

@Dao
interface ExportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ExportJobEntity): Long

    @Query("SELECT * FROM export_jobs WHERE objectId = :objectId ORDER BY createdAtEpochMs DESC")
    suspend fun forObject(objectId: String): List<ExportJobEntity>
}

@Dao
interface DiagnosticEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events WHERE sessionId = :sessionId ORDER BY createdAtEpochMs")
    suspend fun forSession(sessionId: String): List<DiagnosticEventEntity>
}
