package com.solidlink.protocol

import com.google.protobuf.ByteString
import com.solidlink.protocol.v1.Chunk
import com.solidlink.protocol.v1.Envelope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtobufStreamCodecTest {
    @Test
    fun roundTripPreservesComplexManifestAndBoundedChunk() {
        val codec = ProtobufStreamCodec()
        // Leave room for the Envelope and Chunk protobuf field tags/metadata.
        val payload = ByteArray(1024 * 1024 - 256) { index -> (index % 251).toByte() }
        val manifest = Envelope.newBuilder()
            .setSequence(7)
            .setSessionId(ByteString.copyFromUtf8("session-1"))
            .setBatchManifest(
                com.solidlink.protocol.v1.BatchManifest.newBuilder()
                    .setBatchId(ByteString.copyFromUtf8("batch-1"))
                    .addFiles(
                        com.solidlink.protocol.v1.FileManifest.newBuilder()
                            .setObjectId(ByteString.copyFromUtf8("object-1"))
                            .setDisplayName("sample.bin")
                            .setMimeType("application/octet-stream")
                            .setSizeBytes(1024 * 1024L)
                            .setChunkSize(1024 * 1024)
                            .setTotalChunks(1)
                            .setIntegrityMode("sha256")
                            .setFinalDigest(ByteString.copyFromUtf8("digest")),
                    ),
            )
            .build()
        val chunk = Envelope.newBuilder()
            .setSequence(8)
            .setSessionId(ByteString.copyFromUtf8("session-1"))
            .setChunk(
                Chunk.newBuilder()
                    .setObjectId(ByteString.copyFromUtf8("object-1"))
                    .setChunkIndex(0)
                    .setPlaintextLength(payload.size)
                    .setDigest(ByteString.copyFromUtf8("digest"))
                    .setPayload(ByteString.copyFrom(payload)),
            )
            .build()
        val output = ByteArrayOutputStream()

        codec.write(manifest, output)
        codec.write(chunk, output)

        val reader = codec.reader(ByteArrayInputStream(output.toByteArray()))
        assertEquals(manifest, reader.read())
        assertEquals(chunk, reader.read())
        assertNull(reader.read())
    }

    @Test
    fun writeRejectsEnvelopeWithoutBody() {
        val error = assertThrows(ProtocolCodecException::class.java) {
            ProtobufStreamCodec().write(Envelope.newBuilder().setSequence(1).build(), ByteArrayOutputStream())
        }

        assertEquals(ProtocolCodecException.Code.BODY_MISSING, error.code)
    }

    @Test
    fun writeRejectsEnvelopeAboveNegotiatedLimit() {
        val envelope = Envelope.newBuilder()
            .setSequence(1)
            .setHello(com.solidlink.protocol.v1.Hello.newBuilder().setImplementationId("x"))
            .build()

        val error = assertThrows(ProtocolCodecException::class.java) {
            ProtobufStreamCodec(maxEnvelopeBytes = 4).write(envelope, ByteArrayOutputStream())
        }

        assertEquals(ProtocolCodecException.Code.OVERSIZED, error.code)
    }

    @Test
    fun readRejectsOversizedDeclaredMessageBeforeAllocation() {
        val error = assertThrows(ProtocolCodecException::class.java) {
            ProtobufStreamCodec(maxEnvelopeBytes = 64).read(ByteArrayInputStream(byteArrayOf(0x65)))
        }

        assertEquals(ProtocolCodecException.Code.OVERSIZED, error.code)
    }

    @Test
    fun readRejectsTruncatedMessageWithTypedError() {
        val error = assertThrows(ProtocolCodecException::class.java) {
            ProtobufStreamCodec().read(ByteArrayInputStream(byteArrayOf(0x05, 0x08)))
        }

        assertEquals(ProtocolCodecException.Code.TRUNCATED, error.code)
    }
}
