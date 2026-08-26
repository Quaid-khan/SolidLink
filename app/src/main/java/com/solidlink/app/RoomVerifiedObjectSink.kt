package com.solidlink.app

import com.solidlink.db.SolidLinkRepository
import com.solidlink.domain.Checkpoint
import com.solidlink.domain.ChunkState
import com.solidlink.domain.ChunkStatus
import com.solidlink.common.OpaqueId
import com.solidlink.transfer.ResumeState
import com.solidlink.transfer.VerifiedObjectSink
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * A [VerifiedObjectSink] implementation backed by the Room database.
 *
 * Each verified chunk and its corresponding checkpoint are atomically persisted
 * via [SolidLinkRepository.persistVerifiedChunkAndCheckpoint]. The final
 * [verifyFinal] call does a digest equality check, and [commit] is a placeholder
 * that returns `true` until a staging PrivateStagingStore write is wired in.
 */
class RoomVerifiedObjectSink(
    override val objectId: String,
    private val repository: SolidLinkRepository,
    private val expectedFinalDigest: ByteArray,
) : VerifiedObjectSink {

    override fun persistVerifiedChunk(
        chunkIndex: Long,
        offset: Long,
        payload: ByteArray,
        digest: ByteArray,
        durableSequence: Long,
    ): Boolean {
        val chunkState = ChunkState(
            objectId = OpaqueId(objectId),
            chunkIndex = chunkIndex,
            offset = offset,
            length = payload.size,
            digest = digest.joinToString("") { "%02x".format(it) },
            state = ChunkStatus.VERIFIED,
            attempts = 1,
            durableAt = Instant.now(),
        )
        val checkpoint = Checkpoint(
            objectId = OpaqueId(objectId),
            highestDurableSequence = durableSequence,
            verifiedRanges = listOf(chunkIndex..chunkIndex),
            stagingLength = offset + payload.size,
            updatedAt = Instant.now(),
        )
        return runBlocking {
            val result = repository.persistVerifiedChunkAndCheckpoint(chunkState, checkpoint)
            result is com.solidlink.domain.TransitionResult.Success
        }
    }

    override fun resumeState(): ResumeState {
        return runBlocking {
            val chunksResult = repository.loadChunks(objectId)
            val checkpointResult = repository.loadCheckpoint(objectId)

            val chunks = (chunksResult as? com.solidlink.domain.TransitionResult.Success)?.value ?: emptyList()
            val checkpoint = (checkpointResult as? com.solidlink.domain.TransitionResult.Success)?.value

            ResumeState(
                verifiedChunkIndices = chunks.map { it.chunkIndex }.toSet(),
                stagingLength = checkpoint?.stagingLength ?: 0L,
                lastDurableSequence = checkpoint?.highestDurableSequence ?: 0L,
            )
        }
    }

    override fun verifyFinal(
        byteCount: Long,
        chunkCount: Long,
        finalDigest: ByteArray,
    ): Boolean = finalDigest.contentEquals(expectedFinalDigest)

    override fun commit(): Boolean {
        // Placeholder: in a full implementation this would atomically move
        // chunks from staging (PrivateStagingStore) to the export destination.
        return true
    }
}
