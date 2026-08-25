package com.solidlink.transport.api

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

public object ProtoDelimitedIo {
    public fun write(frame: ByteArray, output: OutputStream, maxFrameBytes: Int) {
        if (frame.size > maxFrameBytes) throw IOException("frame exceeds negotiated limit")
        var value = frame.size
        while (value and 0x7f != 0) {
            output.write((value and 0x7f) or 0x80)
            value = value ushr 7
        }
        output.write(value)
        output.write(frame)
        output.flush()
    }

    public fun read(input: InputStream, maxFrameBytes: Int): ByteArray? {
        val first = input.read()
        if (first < 0) return null
        var value = first and 0x7f
        var shift = 7
        while (firstByteContinues(first, shift)) {
            if (shift > 28) throw IOException("frame size varint is too long")
            val next = input.read()
            if (next < 0) throw EOFException("frame size prefix is truncated")
            value = value or ((next and 0x7f) shl shift)
            if (next and 0x80 == 0) break
            shift += 7
        }
        if (value < 0 || value > maxFrameBytes) throw IOException("frame exceeds negotiated limit")
        val frame = ByteArray(value)
        var offset = 0
        while (offset < frame.size) {
            val read = input.read(frame, offset, frame.size - offset)
            if (read < 0) throw EOFException("frame payload is truncated")
            if (read == 0) continue
            offset += read
        }
        return frame
    }

    private fun firstByteContinues(first: Int, shift: Int): Boolean = first and 0x80 != 0 && shift <= 28
}
