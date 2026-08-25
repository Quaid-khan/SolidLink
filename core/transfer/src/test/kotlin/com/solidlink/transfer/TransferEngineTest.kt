package com.solidlink.transfer

import com.google.protobuf.ByteString
import com.solidlink.protocol.v1.Envelope
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.TreeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferEngineTest {
    @Test
    fun senderAndReceiverCompleteOnlyAfterChunkAndFinalVerification() {
        val content = ByteArray(600_000) { (it % 251).toByte() }
        val snapshot = snapshot(content)
        val source = ByteArrayTransferSource(snapshot, content)
        val sender = TransferSender()
        val sink = MemoryVerifiedSink(snapshot.objectId)
        val receiver = TransferReceiver(sink)

        assertEquals(null, receiver.accept(sender.manifestEnvelope(snapshot, 10, SESSION_ID)))
        val chunks = sender.chunkEnvelopes(source, SESSION_ID, 11)
        assertEquals(3, chunks.size)
        chunks.forEachIndexed { index, envelope ->
            val event = receiver.accept(envelope) as TransferEvent.ChunkAccepted
            assertEquals(index.toLong(), event.chunkIndex)
            assertEquals(11L + index, event.durableSequence)
        }

        val finalEvent = receiver.accept(sender.finalEnvelope(snapshot, content.size.toLong(), 3, 14, SESSION_ID))
        assertTrue(finalEvent is TransferEvent.FinalAccepted)
        assertTrue(sink.committed)
        assertEquals(3, sink.durableSequences.size)
    }

    @Test
    fun senderSkipsDurablyVerifiedChunksDuringResume() {
        val content = ByteArray(600_000) { (it % 197).toByte() }
        val snapshot = snapshot(content)
        val sender = TransferSender()
        val envelopes = sender.chunkEnvelopes(
            ByteArrayTransferSource(snapshot, content),
            SESSION_ID,
            20,
            verifiedChunkIndices = setOf(0),
        )

        assertEquals(listOf(1L, 2L), envelopes.map { it.chunk.chunkIndex })
        assertEquals(listOf(20L, 21L), envelopes.map { it.sequence })
    }

    @Test
    fun receiverRejectsBadChunkDigestAndBadFinalDigestWithoutCommit() {
        val content = ByteArray(20_000) { (it % 97).toByte() }
        val snapshot = snapshot(content)
        val sender = TransferSender()
        val sink = MemoryVerifiedSink(snapshot.objectId)
        val receiver = TransferReceiver(sink)
        receiver.accept(sender.manifestEnvelope(snapshot, 1, SESSION_ID))
        val originalChunk = sender.chunkEnvelopes(ByteArrayTransferSource(snapshot, content), SESSION_ID, 2).single()
        val corruptChunk = originalChunk.toBuilder()
            .setChunk(originalChunk.chunk.toBuilder().setDigest(ByteString.copyFrom(ByteArray(32))).build())
            .build()

        val rejectedChunk = receiver.accept(corruptChunk) as TransferEvent.ChunkRejected
        assertEquals("CHUNK_DIGEST_MISMATCH", rejectedChunk.errorCode)
        assertFalse(sink.committed)

        val rejectedFinal = receiver.accept(sender.finalEnvelope(snapshot, content.size.toLong(), 1, 3, SESSION_ID).toBuilder()
            .setObjectFinal(sender.finalEnvelope(snapshot, content.size.toLong(), 1, 3, SESSION_ID).objectFinal.toBuilder()
                .setFinalDigest(ByteString.copyFrom(ByteArray(32))).build())
            .build()) as TransferEvent.FinalRejected
        assertEquals("FINAL_VERIFICATION_FAILED", rejectedFinal.commitAck.errorCode)
        assertFalse(sink.committed)
    }

    @Test
    fun receiverPersistsVerifiedChunkBeforeAcknowledgement() {
        val content = ByteArray(10_000) { 7 }
        val snapshot = snapshot(content)
        val sender = TransferSender()
        val sink = MemoryVerifiedSink(snapshot.objectId)
        val receiver = TransferReceiver(sink)
        receiver.accept(sender.manifestEnvelope(snapshot, 1, SESSION_ID))
        val chunk = sender.chunkEnvelopes(ByteArrayTransferSource(snapshot, content), SESSION_ID, 2).single()

        val event = receiver.accept(chunk) as TransferEvent.ChunkAccepted

        assertEquals(2L, event.durableSequence)
        assertEquals(2L, sink.durableSequences.single())
        assertEquals(2L, sink.resumeState().lastDurableSequence)
    }

    private fun snapshot(content: ByteArray): TransferSourceSnapshot = TransferSourceSnapshot(
        objectId = "object-1",
        batchId = "batch-1",
        displayName = "sample.bin",
        mimeType = "application/octet-stream",
        sizeBytes = content.size.toLong(),
        lastModifiedEpochMs = 1_700_000_000_000,
        chunkSize = 256 * 1024,
        finalDigest = testSha256(content),
        totalChunks = ((content.size + (256 * 1024) - 1) / (256 * 1024)).toLong(),
    )

    private class ByteArrayTransferSource(
        override val snapshot: TransferSourceSnapshot,
        private val content: ByteArray,
    ) : TransferSource {
        override fun open(): InputStream = ByteArrayInputStream(content)
    }

    private class MemoryVerifiedSink(override val objectId: String) : VerifiedObjectSink {
        private val chunks = TreeMap<Long, ByteArray>()
        val durableSequences = mutableListOf<Long>()
        var committed: Boolean = false
            private set

        override fun persistVerifiedChunk(
            chunkIndex: Long,
            offset: Long,
            payload: ByteArray,
            digest: ByteArray,
            durableSequence: Long,
        ): Boolean {
            if (!MessageDigest.isEqual(digest, testSha256(payload))) return false
            chunks[chunkIndex] = payload.copyOf()
            durableSequences += durableSequence
            return true
        }

        override fun resumeState(): ResumeState = ResumeState(
            verifiedChunkIndices = chunks.keys,
            stagingLength = chunks.values.sumOf { it.size.toLong() },
            lastDurableSequence = durableSequences.maxOrNull() ?: 0,
        )

        override fun verifyFinal(byteCount: Long, chunkCount: Long, finalDigest: ByteArray): Boolean {
            val bytes = chunks.values.fold(ByteArray(0)) { all, next -> all + next }
            return bytes.size.toLong() == byteCount &&
                chunks.size.toLong() == chunkCount &&
                MessageDigest.isEqual(testSha256(bytes), finalDigest)
        }

        override fun commit(): Boolean {
            committed = true
            return true
        }
    }

    private companion object {
        val SESSION_ID = byteArrayOf(1, 2, 3, 4)
    }
}

private fun testSha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
