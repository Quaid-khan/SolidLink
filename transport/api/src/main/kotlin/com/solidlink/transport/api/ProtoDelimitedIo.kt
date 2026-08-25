package com.solidlink.transport.api

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Delimits already-serialized Protobuf messages using the official runtime.
 * The generated message owns its wire serialization; this class only provides
 * the bounded length-delimited stream envelope required for sequential messages.
 */
public object ProtoDelimitedIo {
    public fun writer(output: OutputStream, maxMessageBytes: Int): Writer = Writer(output, maxMessageBytes)

    public fun reader(input: InputStream, maxMessageBytes: Int): Reader = Reader(input, maxMessageBytes)

    public fun write(messageBytes: ByteArray, output: OutputStream, maxMessageBytes: Int) {
        writer(output, maxMessageBytes).write(messageBytes)
    }

    public fun read(input: InputStream, maxMessageBytes: Int): ByteArray? {
        return reader(input, maxMessageBytes).read()
    }

    public class Writer internal constructor(
        output: OutputStream,
        private val maxMessageBytes: Int,
    ) {
        private val codedOutput = CodedOutputStream.newInstance(output)

        init {
            require(maxMessageBytes > 0) { "maxMessageBytes must be positive" }
        }

        public fun write(messageBytes: ByteArray) {
            if (messageBytes.size > maxMessageBytes) {
                throw IOException("message exceeds negotiated limit")
            }
            codedOutput.writeUInt32NoTag(messageBytes.size)
            codedOutput.writeRawBytes(messageBytes)
            codedOutput.flush()
        }
    }

    public class Reader internal constructor(
        input: InputStream,
        private val maxMessageBytes: Int,
    ) {
        private val codedInput = CodedInputStream.newInstance(input)

        init {
            require(maxMessageBytes > 0) { "maxMessageBytes must be positive" }
        }

        public fun read(): ByteArray? {
            if (codedInput.isAtEnd) return null
            val size = try {
                codedInput.readRawVarint32()
            } catch (error: EOFException) {
                throw EOFException("message size prefix is truncated: ${error.message}")
            }
            if (size < 0 || size > maxMessageBytes) {
                throw IOException("message exceeds negotiated limit")
            }
            return try {
                codedInput.readRawBytes(size)
            } catch (error: IOException) {
                throw EOFException("message payload is truncated: ${error.message}")
            }
        }
    }
}
