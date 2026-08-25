package com.solidlink.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedSessionTest {
    @Test
    fun hkdfMatchesRfc5869Sha256TestVectorOne() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")

        val prk = HkdfSha256.extract(salt, ikm)
        val output = HkdfSha256.expand(prk, info, 42)

        assertArrayEquals(expected, output)
    }

    @Test
    fun bothPeersDeriveMatchingDirectionalKeysAndAuthenticateTranscript() {
        assertTrue("X25519 is required for this phase", X25519Session.isSupported())
        val initiatorEphemeral = X25519Session.generate()
        val responderEphemeral = X25519Session.generate()
        val context = context(initiatorEphemeral, responderEphemeral, "lan-nsd")

        val initiatorPending = SessionHandshake.start(
            initiatorEphemeral,
            responderEphemeral.publicKey,
            context,
            CryptoRole.INITIATOR,
        )
        val responderPending = SessionHandshake.start(
            responderEphemeral,
            initiatorEphemeral.publicKey,
            context,
            CryptoRole.RESPONDER,
        )

        assertArrayEquals(initiatorPending.transcriptHash, responderPending.transcriptHash)
        assertArrayEquals(initiatorPending.expectedAuthenticator, responderPending.expectedAuthenticator)
        assertTrue(responderPending.verifyPeerAuthenticator(initiatorPending.expectedAuthenticator))
        assertEquals(initiatorPending.expectedConfirmation(), responderPending.expectedConfirmation())

        val initiator = initiatorPending.confirmUserEntry(initiatorPending.expectedConfirmation())
        val responder = responderPending.confirmUserEntry(responderPending.expectedConfirmation())
        val plaintext = "local-only payload".toByteArray(StandardCharsets.UTF_8)
        val aad = initiatorPending.transcriptHash
        val ciphertext = initiator.sendCipher.seal(9, plaintext, aad)

        assertArrayEquals(plaintext, responder.receiveCipher.open(9, ciphertext, aad))
        assertFalse(responderPending.verifyPeerAuthenticator(ByteArray(32)))
    }

    @Test
    fun transcriptChangesWhenTransportOrPairingChanges() {
        val initiator = X25519Session.generate()
        val responder = X25519Session.generate()
        val lan = context(initiator, responder, "lan-nsd")
        val direct = context(initiator, responder, "wifi-direct")
        val sas = lan.copy(pairingMethod = PairingMethod.ADVANCED_SAS)

        assertFalse(MessageDigest.isEqual(lan.encoded(), direct.encoded()))
        assertFalse(MessageDigest.isEqual(lan.encoded(), sas.encoded()))
    }

    @Test
    fun confirmationRejectsWrongEntryAndCipherRejectsTampering() {
        val initiator = X25519Session.generate()
        val responder = X25519Session.generate()
        val pending = SessionHandshake.start(
            initiator,
            responder.publicKey,
            context(initiator, responder, "lan-nsd"),
            CryptoRole.INITIATOR,
        )
        val wrong = org.junit.Assert.assertThrows(CryptoSessionException::class.java) {
            pending.confirmUserEntry("000000")
        }
        assertEquals(CryptoSessionException.Code.INVALID_CIPHERTEXT, wrong.code)

        val established = pending.confirmUserEntry(pending.expectedConfirmation())
        val ciphertext = established.sendCipher.seal(1, byteArrayOf(1, 2, 3), pending.transcriptHash)
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 0x01).toByte()
        val tampered = org.junit.Assert.assertThrows(CryptoSessionException::class.java) {
            established.receiveCipher.open(1, ciphertext, pending.transcriptHash)
        }
        assertEquals(CryptoSessionException.Code.INVALID_CIPHERTEXT, tampered.code)
    }

    private fun context(
        initiator: EphemeralKeyPair,
        responder: EphemeralKeyPair,
        transport: String,
    ): TranscriptContext = TranscriptContext(
        protocolVersion = "1.0",
        initiatorEphemeralPublic = initiator.publicKey,
        responderEphemeralPublic = responder.publicKey,
        initiatorNonce = ByteArray(32) { 1 },
        responderNonce = ByteArray(32) { 2 },
        selectedTransport = transport,
        capabilities = setOf("resume", "multi-peer"),
        pairingMethod = PairingMethod.DEVICE_NAME_PIN,
    )

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
