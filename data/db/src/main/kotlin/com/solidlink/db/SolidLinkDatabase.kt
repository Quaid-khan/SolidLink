package com.solidlink.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShareSessionEntity::class,
        PeerRecordEntity::class,
        TransferBatchEntity::class,
        TransferObjectEntity::class,
        ChunkStateEntity::class,
        CheckpointEntity::class,
        DeviceIdentityEntity::class,
        ExportJobEntity::class,
        DiagnosticEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SolidLinkDatabase : RoomDatabase() {
    abstract fun shareSessionDao(): ShareSessionDao
    abstract fun peerRecordDao(): PeerRecordDao
    abstract fun transferBatchDao(): TransferBatchDao
    abstract fun transferObjectDao(): TransferObjectDao
    abstract fun chunkStateDao(): ChunkStateDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun deviceIdentityDao(): DeviceIdentityDao
    abstract fun exportJobDao(): ExportJobDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao
}
