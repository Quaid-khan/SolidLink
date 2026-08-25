package com.solidlink.protocol

import com.google.protobuf.ByteString
import com.solidlink.protocol.v1.Envelope
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenVectorTest {
    @Test
    fun helloEnvelopeCanProduceRepositoryGoldenVector() {
        val envelope = Envelope.newBuilder()
            .setSequence(1)
            .setSessionId(ByteString.copyFromUtf8("session-1"))
            .setHello(
                com.solidlink.protocol.v1.Hello.newBuilder()
                    .setProtocolMajor(1)
                    .setProtocolMinor(0)
                    .setImplementationId("solidlink-android")
                    .addTransports("lan-nsd")
                    .addSecuritySuites("x25519-sha256-aead")
                    .setMaxEnvelopeBytes(1024 * 1024)
                    .setLocalOnlyRequired(true),
            )
            .build()
        val output = ByteArrayOutputStream()
        ProtobufStreamCodec().write(envelope, output)
        val bytes = output.toByteArray()
        assertTrue(bytes.isNotEmpty())
        val expected = javaClass.getResourceAsStream("/golden/hello_envelope.delimited.bin")!!.use { it.readBytes() }
        assertArrayEquals(expected, bytes)

        if (System.getProperty("writeGoldenVectors") == "true") {
            val target = File("src/test/resources/golden/hello_envelope.delimited.bin")
            target.parentFile.mkdirs()
            target.writeBytes(bytes)
        }
    }
}
