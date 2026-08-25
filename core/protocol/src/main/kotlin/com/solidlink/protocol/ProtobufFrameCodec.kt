package com.solidlink.protocol

import com.google.protobuf.InvalidProtocolBufferException
import com.solidlink.protocol.v1.Envelope

public object ProtobufFrameCodec {
    public fun encode(envelope: Envelope, maxEnvelopeBytes: Int = 1024 * 1024): ByteArray {
        if (envelope.bodyCase == Envelope.BodyCase.BODY_NOT_SET) {
            throw ProtocolCodecException(ProtocolCodecException.Code.BODY_MISSING, "Envelope must contain one body message")
        }
        val bytes = envelope.toByteArray()
        if (bytes.size > maxEnvelopeBytes) {
            throw ProtocolCodecException(ProtocolCodecException.Code.OVERSIZED, "Envelope exceeds the negotiated size limit")
        }
        return bytes
    }

    public fun decode(bytes: ByteArray, maxEnvelopeBytes: Int = 1024 * 1024): Envelope {
        if (bytes.size > maxEnvelopeBytes) {
            throw ProtocolCodecException(ProtocolCodecException.Code.OVERSIZED, "Envelope exceeds the negotiated size limit")
        }
        return try {
            val envelope = Envelope.parseFrom(bytes)
            if (envelope.bodyCase == Envelope.BodyCase.BODY_NOT_SET) {
                throw ProtocolCodecException(ProtocolCodecException.Code.BODY_MISSING, "Envelope must contain one body message")
            }
            envelope
        } catch (error: ProtocolCodecException) {
            throw error
        } catch (error: InvalidProtocolBufferException) {
            throw ProtocolCodecException(ProtocolCodecException.Code.MALFORMED, "Envelope protobuf is malformed", error)
        }
    }
}
