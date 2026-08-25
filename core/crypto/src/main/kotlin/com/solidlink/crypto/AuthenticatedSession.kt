package com.solidlink.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import javax.crypto.KeyAgreement
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.XECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.math.BigInteger
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

public enum class CryptoRole {
    INITIATOR,
    RESPONDER,
}

public enum class PairingMethod {
    DEVICE_NAME_PIN,
    QR_BOOTSTRAP,
    ADVANCED_SAS,
}

public class EphemeralKeyPair internal constructor(
    internal val privateKey: PrivateKey,
    public val publicKey: ByteArray,
) {
    public fun destroy() {
        publicKey.fill(0)
    }
}

public data class TranscriptContext(
    public val protocolVersion: String,
    public val initiatorEphemeralPublic: ByteArray,
    public val responderEphemeralPublic: ByteArray,
    public val initiatorNonce: ByteArray,
    public val responderNonce: ByteArray,
    public val selectedTransport: String,
    public val capabilities: Set<String>,
    public val pairingMethod: PairingMethod,
) {
    public fun encoded(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeUtf8(protocolVersion)
            data.writeBytes(initiatorEphemeralPublic)
            data.writeBytes(responderEphemeralPublic)
            data.writeBytes(initiatorNonce)
            data.writeBytes(responderNonce)
            data.writeUtf8(selectedTransport)
            capabilities.toList().sorted().forEach { data.writeUtf8(it) }
            data.writeUtf8(pairingMethod.name)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeUtf8(value: String) = writeBytes(value.toByteArray(StandardCharsets.UTF_8))

    private fun DataOutputStream.writeBytes(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }
}

public class SessionKeys internal constructor(
    public val sendKey: ByteArray,
    public val receiveKey: ByteArray,
    public val authenticatorKey: ByteArray,
    public val transcriptHash: ByteArray,
    internal val sendDirectionLabel: String,
    internal val receiveDirectionLabel: String,
) {
    public fun transcriptAuthenticator(): ByteArray = hmacSha256(authenticatorKey, transcriptHash)

    public fun pairingCode(): String = PairingConfirmation.pinFrom(transcriptHash)

    public fun sas(): String = PairingConfirmation.sasFrom(transcriptHash)

    public fun clear() {
        sendKey.fill(0)
        receiveKey.fill(0)
        authenticatorKey.fill(0)
        transcriptHash.fill(0)
    }
}

public class CryptoSessionException(
    public val code: Code,
    override val message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause) {
    public enum class Code {
        UNSUPPORTED,
        INVALID_PEER_KEY,
        LOW_ORDER_PEER_KEY,
        DERIVATION_FAILED,
        INVALID_CIPHERTEXT,
        INVALID_NONCE,
        INVALID_SEQUENCE,
    }
}

public object X25519Session {
    private const val X25519_PUBLIC_KEY_SIZE = 32
    private const val X25519_SHARED_SECRET_SIZE = 32

    public fun isSupported(): Boolean = try {
        KeyPairGenerator.getInstance("X25519")
        KeyAgreement.getInstance("X25519")
        KeyFactory.getInstance("X25519")
        true
    } catch (_: GeneralSecurityException) {
        false
    }

    public fun generate(random: SecureRandom = SecureRandom()): EphemeralKeyPair = try {
        val keyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        EphemeralKeyPair(keyPair.private, rawPublicKey(keyPair.public))
    } catch (error: GeneralSecurityException) {
        throw CryptoSessionException(CryptoSessionException.Code.UNSUPPORTED, "X25519 is unavailable", error)
    }

    public fun derive(
        local: EphemeralKeyPair,
        peerPublicKey: ByteArray,
        context: TranscriptContext,
        role: CryptoRole,
    ): SessionKeys {
        if (peerPublicKey.isEmpty()) {
            throw CryptoSessionException(CryptoSessionException.Code.INVALID_PEER_KEY, "Peer key is empty")
        }
        return try {
            require(peerPublicKey.size == X25519_PUBLIC_KEY_SIZE) { "X25519 public key must be 32 raw bytes" }
            val peerKey = KeyFactory.getInstance("X25519")
                .generatePublic(X509EncodedKeySpec(SPKI_PREFIX + peerPublicKey))
            val agreement = KeyAgreement.getInstance("X25519")
            agreement.init(local.privateKey)
            agreement.doPhase(peerKey, true)
            val sharedSecret = agreement.generateSecret()
            if (sharedSecret.size != X25519_SHARED_SECRET_SIZE || sharedSecret.all { it == 0.toByte() }) {
                throw CryptoSessionException(CryptoSessionException.Code.LOW_ORDER_PEER_KEY, "Peer key produced an invalid shared secret")
            }

            val transcriptHash = sha256(context.encoded())
            val salt = sha256("SolidLink/session-salt/v1".toByteArray(StandardCharsets.UTF_8) + transcriptHash)
            val prk = HkdfSha256.extract(salt, sharedSecret)
            val info = "SolidLink/session-keys/v1".toByteArray(StandardCharsets.UTF_8) + transcriptHash
            val material = HkdfSha256.expand(prk, info, 96)
            val initiatorKey = material.copyOfRange(0, 32)
            val responderKey = material.copyOfRange(32, 64)
            val authenticatorKey = material.copyOfRange(64, 96)
            SessionKeys(
                sendKey = if (role == CryptoRole.INITIATOR) initiatorKey else responderKey,
                receiveKey = if (role == CryptoRole.INITIATOR) responderKey else initiatorKey,
                authenticatorKey = authenticatorKey,
                transcriptHash = transcriptHash,
                sendDirectionLabel = if (role == CryptoRole.INITIATOR) "initiator-to-responder" else "responder-to-initiator",
                receiveDirectionLabel = if (role == CryptoRole.INITIATOR) "responder-to-initiator" else "initiator-to-responder",
            )
        } catch (error: CryptoSessionException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw CryptoSessionException(CryptoSessionException.Code.DERIVATION_FAILED, "Session key derivation failed", error)
        }
    }

    private fun rawPublicKey(publicKey: java.security.PublicKey): ByteArray {
        val u = (publicKey as XECPublicKey).u.toByteArray()
        val unsigned = if (u.size > X25519_PUBLIC_KEY_SIZE && u.first() == 0.toByte()) u.copyOfRange(1, u.size) else u
        return ByteArray(X25519_PUBLIC_KEY_SIZE) { index -> unsigned.getOrNull(unsigned.size - 1 - index) ?: 0 }
    }

    private const val SPKI_PREFIX_HEX = "302a300506032b656e032100"
    private val SPKI_PREFIX: ByteArray = SPKI_PREFIX_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

public class SessionCipher internal constructor(
    private val key: ByteArray,
    private val directionLabel: String,
) {
    @Volatile
    private var cleared: Boolean = false

    public fun clear() {
        key.fill(0)
        cleared = true
    }

    public fun seal(sequence: Long, plaintext: ByteArray, aad: ByteArray): ByteArray {
        ensureActive()
        val nonce = nonceFor(sequence)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(plaintext)
            }
        } catch (error: GeneralSecurityException) {
            throw CryptoSessionException(CryptoSessionException.Code.DERIVATION_FAILED, "Session encryption failed", error)
        }
    }

    public fun open(sequence: Long, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        ensureActive()
        val nonce = nonceFor(sequence)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(ciphertext)
            }
        } catch (error: AEADBadTagException) {
            throw CryptoSessionException(CryptoSessionException.Code.INVALID_CIPHERTEXT, "Ciphertext authentication failed", error)
        } catch (error: GeneralSecurityException) {
            throw CryptoSessionException(CryptoSessionException.Code.INVALID_CIPHERTEXT, "Ciphertext could not be opened", error)
        }
    }

    private fun ensureActive() {
        if (cleared) {
            throw CryptoSessionException(CryptoSessionException.Code.INVALID_SEQUENCE, "Session cipher is closed")
        }
    }

    private fun nonceFor(sequence: Long): ByteArray {
        if (sequence < 0) {
            throw CryptoSessionException(CryptoSessionException.Code.INVALID_SEQUENCE, "Sequence must be non-negative")
        }
        val prefix = sha256("SolidLink/nonce/$directionLabel".toByteArray(StandardCharsets.UTF_8)).copyOfRange(0, 4)
        return ByteBuffer.allocate(12).put(prefix).putLong(sequence).array()
    }
}

public object PairingConfirmation {
    public fun pinFrom(transcriptHash: ByteArray): String {
        val value = ((transcriptHash[0].toInt() and 0xff) shl 16) or
            ((transcriptHash[1].toInt() and 0xff) shl 8) or
            (transcriptHash[2].toInt() and 0xff)
        return value.toString().padStart(6, '0').takeLast(6)
    }

    public fun sasFrom(transcriptHash: ByteArray): String = transcriptHash
        .copyOfRange(0, 6)
        .joinToString("") { "%02x".format(it) }
        .chunked(4)
        .joinToString(" ")

    public fun userConfirmed(expected: String, entered: String): Boolean =
        expected.length == entered.length && MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            entered.toByteArray(StandardCharsets.UTF_8),
        )
}

private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

