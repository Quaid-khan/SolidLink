package com.solidlink.transport.api

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtoDelimitedIoTest {
    @Test
    fun roundTripHandlesVarintLengthBoundary() {
        val frame = ByteArray(300) { it.toByte() }
        val output = ByteArrayOutputStream()

        ProtoDelimitedIo.write(frame, output, maxFrameBytes = 1_024)

        assertArrayEquals(frame, ProtoDelimitedIo.read(ByteArrayInputStream(output.toByteArray()), 1_024))
        assertNull(ProtoDelimitedIo.read(ByteArrayInputStream(byteArrayOf()), 1_024))
    }

    @Test
    fun writerRejectsOversizedFrame() {
        assertThrows(IOException::class.java) {
            ProtoDelimitedIo.write(ByteArray(9), ByteArrayOutputStream(), maxFrameBytes = 8)
        }
    }

    @Test
    fun readerRejectsOversizedAndTruncatedFrames() {
        assertThrows(IOException::class.java) {
            ProtoDelimitedIo.read(ByteArrayInputStream(byteArrayOf(0x09)), 8)
        }
        assertThrows(EOFException::class.java) {
            ProtoDelimitedIo.read(ByteArrayInputStream(byteArrayOf(0x05, 0x01)), 8)
        }
    }
}
