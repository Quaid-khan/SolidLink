package com.solidlink.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

public object HkdfSha256 {
    public fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray =
        hmacSha256(salt, inputKeyMaterial)

    public fun expand(pseudorandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 0..(255 * HASH_LENGTH)) { "HKDF output length is outside RFC 5869 bounds" }
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmacSha256(pseudorandomKey, previous + info + byteArrayOf(counter.toByte()))
            val copyLength = minOf(previous.size, length - offset)
            previous.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter++
        }
        previous.fill(0)
        return result
    }

    private const val HASH_LENGTH = 32
}

internal fun hmacSha256(key: ByteArray, input: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(input)
    }
