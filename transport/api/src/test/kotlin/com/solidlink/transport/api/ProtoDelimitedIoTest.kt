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

        ProtoDelimitedIo.write(frame, output, maxMessageBytes = 1_024)

        assertArrayEquals(frame, ProtoDelimitedIo.read(ByteArrayInputStream(output.toByteArray()), 1_024))
        assertNull(ProtoDelimitedIo.read(ByteArrayInputStream(byteArrayOf()), 1_024))
    }

    @Test
    fun writerRejectsOversizedFrame() {
        assertThrows(IOException::class.java) {
            ProtoDelimitedIo.write(ByteArray(9), ByteArrayOutputStream(), maxMessageBytes = 8)
        }
    }

    @Test
    fun statefulReaderPreservesSequentialMessages() {
        val output = ByteArrayOutputStream()
        val writer = ProtoDelimitedIo.writer(output, 1_024)
        writer.write(byteArrayOf(1, 2, 3))
        writer.write(byteArrayOf(4, 5, 6, 7))

        val reader = ProtoDelimitedIo.reader(ByteArrayInputStream(output.toByteArray()), 1_024)
        assertArrayEquals(byteArrayOf(1, 2, 3), reader.read())
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), reader.read())
        assertNull(reader.read())
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
