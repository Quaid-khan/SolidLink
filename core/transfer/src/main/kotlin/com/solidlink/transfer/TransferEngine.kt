package com.solidlink.transfer

import com.google.protobuf.ByteString
import com.solidlink.protocol.ProtobufFrameCodec
import com.solidlink.protocol.v1.BatchManifest
import com.solidlink.protocol.v1.Chunk
import com.solidlink.protocol.v1.ChunkAck
import com.solidlink.protocol.v1.ChunkNack
import com.solidlink.protocol.v1.Envelope
import com.solidlink.protocol.v1.FileManifest
import com.solidlink.protocol.v1.ObjectCommitAck
import com.solidlink.protocol.v1.ObjectFinal
import com.solidlink.protocol.v1.ResumePlan
import com.solidlink.protocol.v1.ResumeQuery
import java.io.InputStream
import java.security.MessageDigest

public data class TransferSourceSnapshot(
    val objectId: String,
    val batchId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedEpochMs: Long?,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    val finalDigest: ByteArray,
    val totalChunks: Long?,
) {
    init {
        require(objectId.isNotBlank()) { "objectId must not be blank" }
        require(batchId.isNotBlank()) { "batchId must not be blank" }
        require(chunkSize in 1..MAX_CHUNK_BYTES) { "chunkSize must be between 1 and 1 MiB" }
        require(finalDigest.size == SHA256_BYTES) { "finalDigest must be SHA-256" }
        require(sizeBytes == null || sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(totalChunks == null || totalChunks >= 0) { "totalChunks must be non-negative" }
    }
}

public interface TransferSource {
    public val snapshot: TransferSourceSnapshot
    public fun open(): InputStream
}

public data class ResumeState(
    val verifiedChunkIndices: Set<Long>,
    val stagingLength: Long,
    val lastDurableSequence: Long,
)

public interface VerifiedObjectSink {
    public val objectId: String

    public fun persistVerifiedChunk(
        chunkIndex: Long,
        offset: Long,
        payload: ByteArray,
        digest: ByteArray,
        durableSequence: Long,
    ): Boolean

    public fun resumeState(): ResumeState

    public fun verifyFinal(
        byteCount: Long,
        chunkCount: Long,
        finalDigest: ByteArray,
    ): Boolean

    public fun commit(): Boolean
}

public sealed interface TransferEvent {
    public data class ChunkAccepted(val chunkIndex: Long, val durableSequence: Long) : TransferEvent
    public data class ChunkRejected(val errorCode: String, val retryable: Boolean) : TransferEvent
    public data class FinalAccepted(val commitAck: ObjectCommitAck) : TransferEvent
    public data class FinalRejected(val commitAck: ObjectCommitAck) : TransferEvent
}

public class TransferSender(
    private val maxEnvelopeBytes: Int = DEFAULT_MAX_ENVELOPE_BYTES,
) {
    public fun manifestEnvelope(snapshot: TransferSourceSnapshot, sequence: Long, sessionId: ByteArray): Envelope =
        Envelope.newBuilder()
            .setSequence(sequence)
            .setSessionId(ByteString.copyFrom(sessionId))
            .setBatchManifest(
                BatchManifest.newBuilder()
                    .setBatchId(ByteString.copyFromUtf8(snapshot.batchId))
                    .addFiles(
                        FileManifest.newBuilder()
                            .setObjectId(ByteString.copyFromUtf8(snapshot.objectId))
                            .setDisplayName(snapshot.displayName)
                            .setMimeType(snapshot.mimeType.orEmpty())
                            .apply {
                                snapshot.sizeBytes?.let(::setSizeBytes)
                                snapshot.lastModifiedEpochMs?.let(::setLastModifiedEpochMs)
                                snapshot.totalChunks?.let(::setTotalChunks)
                            }
                            .setChunkSize(snapshot.chunkSize)
                            .setIntegrityMode(INTEGRITY_MODE_SHA256)
                            .setFinalDigest(ByteString.copyFrom(snapshot.finalDigest)),
                    ),
            )
            .build()

    public fun resumeQueryEnvelope(
        snapshot: TransferSourceSnapshot,
        state: ResumeState,
        sequence: Long,
        sessionId: ByteArray,
    ): Envelope = Envelope.newBuilder()
        .setSequence(sequence)
        .setSessionId(ByteString.copyFrom(sessionId))
        .setResumeQuery(
            ResumeQuery.newBuilder()
                .setObjectId(ByteString.copyFromUtf8(snapshot.objectId))
                .addAllVerifiedRanges(toRanges(state.verifiedChunkIndices))
                .setStagingLength(state.stagingLength)
                .setLastDurableSequence(state.lastDurableSequence),
        )
        .build()

    public fun resumePlanEnvelope(
        snapshot: TransferSourceSnapshot,
        state: ResumeState,
        sequence: Long,
        sessionId: ByteArray,
    ): Envelope = Envelope.newBuilder()
        .setSequence(sequence)
        .setSessionId(ByteString.copyFrom(sessionId))
        .setResumePlan(
            ResumePlan.newBuilder()
                .setObjectId(ByteString.copyFromUtf8(snapshot.objectId))
                .addAllSendRanges(toRanges((0 until (snapshot.totalChunks ?: Long.MAX_VALUE).coerceAtMost(MAX_RESUME_ENUMERATION)).map { it }.filterNot(state.verifiedChunkIndices::contains)))
                .setCleanRestart(false)
                .setReason("resume-from-durable-checkpoint"),
        )
        .build()

    public fun chunkEnvelopes(
        source: TransferSource,
        sessionId: ByteArray,
        firstSequence: Long,
        verifiedChunkIndices: Set<Long> = emptySet(),
    ): List<Envelope> {
        val snapshot = source.snapshot
        val output = ArrayList<Envelope>()
        source.open().use { input ->
            val buffer = ByteArray(snapshot.chunkSize)
            var chunkIndex = 0L
            var offset = 0L
            while (true) {
                val length = readChunk(input, buffer)
                if (length == 0) break
                val payload = buffer.copyOf(length)
                val digest = sha256(payload)
                if (chunkIndex !in verifiedChunkIndices) {
                    output += Envelope.newBuilder()
                        .setSequence(firstSequence + output.size)
                        .setSessionId(ByteString.copyFrom(sessionId))
                        .setChunk(
                            Chunk.newBuilder()
                                .setObjectId(ByteString.copyFromUtf8(snapshot.objectId))
                                .setChunkIndex(chunkIndex)
                                .setPlaintextLength(length)
                                .setDigest(ByteString.copyFrom(digest))
                                .setPayload(ByteString.copyFrom(payload)),
                        )
                        .build()
                }
                offset += length
                chunkIndex++
            }
            if (snapshot.sizeBytes != null && snapshot.sizeBytes != offset) {
                throw TransferEngineException("SOURCE_SIZE_MISMATCH", "Source changed while it was being read")
            }
            if (snapshot.totalChunks != null && snapshot.totalChunks != chunkIndex) {
                throw TransferEngineException("SOURCE_CHUNK_COUNT_MISMATCH", "Source chunk count changed while it was being read")
            }
        }
        output.forEach { envelope -> ProtobufFrameCodec.encode(envelope, maxEnvelopeBytes) }
        return output
    }

    public fun finalEnvelope(
        snapshot: TransferSourceSnapshot,
        byteCount: Long,
        chunkCount: Long,
        sequence: Long,
        sessionId: ByteArray,
    ): Envelope = Envelope.newBuilder()
        .setSequence(sequence)
        .setSessionId(ByteString.copyFrom(sessionId))
        .setObjectFinal(
            ObjectFinal.newBuilder()
                .setObjectId(ByteString.copyFromUtf8(snapshot.objectId))
                .setByteCount(byteCount)
                .setChunkCount(chunkCount)
                .setFinalDigest(ByteString.copyFrom(snapshot.finalDigest)),
        )
        .build()

    private fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return offset
    }

    private fun toRanges(indices: Iterable<Long>): List<com.solidlink.protocol.v1.ChunkRange> {
        val sorted = indices.toList().distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val ranges = ArrayList<com.solidlink.protocol.v1.ChunkRange>()
        var start = sorted.first()
        var last = start
        for (index in sorted.drop(1)) {
            if (index == last + 1) last = index else {
                ranges += com.solidlink.protocol.v1.ChunkRange.newBuilder().setFirstIndex(start).setLastIndexInclusive(last).build()
                start = index
                last = index
            }
        }
        ranges += com.solidlink.protocol.v1.ChunkRange.newBuilder().setFirstIndex(start).setLastIndexInclusive(last).build()
        return ranges
    }
}

public class TransferReceiver(
    private val sink: VerifiedObjectSink,
    private val maxEnvelopeBytes: Int = DEFAULT_MAX_ENVELOPE_BYTES,
) {
    private var manifest: FileManifest? = null
    private var receivedBytes: Long = 0
    private var receivedChunks: Long = 0

    public fun accept(envelope: Envelope): TransferEvent? {
        ProtobufFrameCodec.encode(envelope, maxEnvelopeBytes)
        return when (envelope.bodyCase) {
            Envelope.BodyCase.BATCH_MANIFEST -> acceptManifest(envelope)
            Envelope.BodyCase.CHUNK -> acceptChunk(envelope)
            Envelope.BodyCase.OBJECT_FINAL -> acceptFinal(envelope)
            else -> null
        }
    }

    public fun resumeQuery(sequence: Long, sessionId: ByteArray): Envelope {
        val current = manifest ?: throw TransferEngineException("MANIFEST_REQUIRED", "Resume requires a manifest first")
        val state = sink.resumeState()
        return Envelope.newBuilder()
            .setSequence(sequence)
            .setSessionId(ByteString.copyFrom(sessionId))
            .setResumeQuery(
                ResumeQuery.newBuilder()
                    .setObjectId(current.objectId)
                    .addAllVerifiedRanges(state.verifiedChunkIndices.sorted().map {
                        com.solidlink.protocol.v1.ChunkRange.newBuilder().setFirstIndex(it).setLastIndexInclusive(it).build()
                    })
                    .setStagingLength(state.stagingLength)
                    .setLastDurableSequence(state.lastDurableSequence),
            )
            .build()
    }

    private fun acceptManifest(envelope: Envelope): TransferEvent? {
        val incoming = envelope.batchManifest.filesList.singleOrNull()
            ?: throw TransferEngineException("MANIFEST_INVALID", "Exactly one file is required in this transfer slice")
        if (incoming.chunkSize !in 1..MAX_CHUNK_BYTES) throw TransferEngineException("CHUNK_SIZE_INVALID", "Chunk size exceeds protocol bounds")
        if (incoming.integrityMode != INTEGRITY_MODE_SHA256 || incoming.finalDigest.size() != SHA256_BYTES) {
            throw TransferEngineException("INTEGRITY_MODE_INVALID", "Only SHA-256 final verification is supported")
        }
        if (sink.objectId != incoming.objectId.toStringUtf8()) throw TransferEngineException("OBJECT_ID_MISMATCH", "Manifest object does not match the staging sink")
        manifest = incoming
        return null
    }

    private fun acceptChunk(envelope: Envelope): TransferEvent {
        val current = manifest ?: throw TransferEngineException("MANIFEST_REQUIRED", "Chunk arrived before manifest")
        val chunk = envelope.chunk
        val objectId = chunk.objectId.toStringUtf8()
        if (objectId != current.objectId.toStringUtf8()) return TransferEvent.ChunkRejected("OBJECT_ID_MISMATCH", false)
        if (chunk.payload.size() > current.chunkSize || chunk.payload.size() > MAX_CHUNK_BYTES) return TransferEvent.ChunkRejected("CHUNK_TOO_LARGE", false)
        if (chunk.plaintextLength != chunk.payload.size()) return TransferEvent.ChunkRejected("PLAINTEXT_LENGTH_MISMATCH", false)
        val digest = sha256(chunk.payload.toByteArray())
        if (!MessageDigest.isEqual(digest, chunk.digest.toByteArray())) return TransferEvent.ChunkRejected("CHUNK_DIGEST_MISMATCH", true)
        val offset = chunk.chunkIndex * current.chunkSize
        if (current.hasTotalChunks() && chunk.chunkIndex >= current.totalChunks) return TransferEvent.ChunkRejected("CHUNK_INDEX_OUT_OF_RANGE", false)
        if (!sink.persistVerifiedChunk(chunk.chunkIndex, offset, chunk.payload.toByteArray(), digest, envelope.sequence)) {
            return TransferEvent.ChunkRejected("CHECKPOINT_WRITE_FAILED", true)
        }
        receivedBytes += chunk.payload.size()
        receivedChunks++
        return TransferEvent.ChunkAccepted(chunk.chunkIndex, envelope.sequence)
    }

    private fun acceptFinal(envelope: Envelope): TransferEvent {
        val current = manifest ?: throw TransferEngineException("MANIFEST_REQUIRED", "Final arrived before manifest")
        val final = envelope.objectFinal
        if (final.objectId.toStringUtf8() != current.objectId.toStringUtf8()) {
            return rejectedFinal("OBJECT_ID_MISMATCH")
        }
        val verified = final.finalDigest == current.finalDigest &&
            sink.verifyFinal(final.byteCount, final.chunkCount, final.finalDigest.toByteArray())
        if (!verified) return rejectedFinal("FINAL_VERIFICATION_FAILED")
        if (!sink.commit()) return rejectedFinal("ATOMIC_COMMIT_FAILED")
        return TransferEvent.FinalAccepted(
            ObjectCommitAck.newBuilder()
                .setObjectId(final.objectId)
                .setVerified(true)
                .build(),
        )
    }

    private fun rejectedFinal(code: String): TransferEvent.FinalRejected = TransferEvent.FinalRejected(
        ObjectCommitAck.newBuilder()
            .setObjectId(manifest?.objectId ?: ByteString.EMPTY)
            .setVerified(false)
            .setErrorCode(code)
            .setSafeMessage("Transfer verification did not complete")
            .build(),
    )
}

public class TransferEngineException(
    public val code: String,
    override val message: String,
) : IllegalStateException(message)

private const val DEFAULT_CHUNK_SIZE = 256 * 1024
private const val DEFAULT_MAX_ENVELOPE_BYTES = 1024 * 1024
private const val MAX_CHUNK_BYTES = 1024 * 1024
private const val MAX_RESUME_ENUMERATION = 100_000L
private const val SHA256_BYTES = 32
private const val INTEGRITY_MODE_SHA256 = "sha256"

private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
