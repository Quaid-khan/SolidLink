package com.solidlink.protocol

import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException
import com.solidlink.protocol.v1.Envelope
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

public class ProtocolCodecException(
    public val code: Code,
    override val message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    public enum class Code {
        TRUNCATED,
        OVERSIZED,
        MALFORMED,
        BODY_MISSING,
    }
}

public class ProtobufStreamCodec(
    public val maxEnvelopeBytes: Int = DEFAULT_MAX_ENVELOPE_BYTES,
) {
    init {
        require(maxEnvelopeBytes in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            "maxEnvelopeBytes must be between $MIN_ENVELOPE_BYTES and $MAX_ENVELOPE_BYTES"
        }
    }

    public fun write(envelope: Envelope, output: OutputStream) {
        val serializedSize = envelope.serializedSize
        if (serializedSize > maxEnvelopeBytes) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.OVERSIZED,
                message = "Envelope exceeds the negotiated size limit",
            )
        }
        if (envelope.bodyCase == Envelope.BodyCase.BODY_NOT_SET) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.BODY_MISSING,
                message = "Envelope must contain one body message",
            )
        }
        try {
            envelope.writeDelimitedTo(output)
        } catch (error: IOException) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.MALFORMED,
                message = "Envelope could not be written",
                cause = error,
            )
        }
    }

    public fun reader(input: InputStream): ProtobufStreamReader =
        ProtobufStreamReader(CodedInputStream.newInstance(input), maxEnvelopeBytes)

    public fun read(input: InputStream): Envelope? = reader(input).read()

    private companion object {
        const val MIN_ENVELOPE_BYTES = 1
        const val DEFAULT_MAX_ENVELOPE_BYTES = 1 * 1024 * 1024
        const val MAX_ENVELOPE_BYTES = 1 * 1024 * 1024
    }
}

public class ProtobufStreamReader internal constructor(
    private val codedInput: CodedInputStream,
    private val maxEnvelopeBytes: Int,
) {
    public fun read(): Envelope? {
        val size = try {
            if (codedInput.isAtEnd) return null
            codedInput.readRawVarint32()
        } catch (error: EOFException) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.TRUNCATED,
                message = "Envelope size prefix is truncated",
                cause = error,
            )
        } catch (error: IOException) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.MALFORMED,
                message = "Envelope size prefix is malformed",
                cause = error,
            )
        }

        if (size < 0 || size > maxEnvelopeBytes) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.OVERSIZED,
                message = "Envelope size exceeds the negotiated limit",
            )
        }

        val oldLimit = codedInput.pushLimit(size)
        return try {
            val envelope = Envelope.parseFrom(codedInput)
            codedInput.checkLastTagWas(0)
            if (!codedInput.isAtEnd) {
                throw ProtocolCodecException(
                    code = ProtocolCodecException.Code.MALFORMED,
                    message = "Envelope contains bytes beyond its declared size",
                )
            }
            if (envelope.bodyCase == Envelope.BodyCase.BODY_NOT_SET) {
                throw ProtocolCodecException(
                    code = ProtocolCodecException.Code.BODY_MISSING,
                    message = "Envelope must contain one body message",
                )
            }
            envelope
        } catch (error: ProtocolCodecException) {
            throw error
        } catch (error: InvalidProtocolBufferException) {
            val truncated = error.cause is EOFException || error.message?.contains("ended unexpectedly", ignoreCase = true) == true
            throw ProtocolCodecException(
                code = if (truncated) ProtocolCodecException.Code.TRUNCATED else ProtocolCodecException.Code.MALFORMED,
                message = if (truncated) "Envelope protobuf is truncated" else "Envelope protobuf is malformed",
                cause = error,
            )
        } catch (error: IOException) {
            throw ProtocolCodecException(
                code = ProtocolCodecException.Code.TRUNCATED,
                message = "Envelope protobuf is truncated",
                cause = error,
            )
        } finally {
            codedInput.popLimit(oldLimit)
        }
    }
}
