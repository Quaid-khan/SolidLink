package com.solidlink.crypto

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

public class PendingSession internal constructor(
    private val keys: SessionKeys,
    public val pairingMethod: PairingMethod,
) {
    public val transcriptHash: ByteArray = keys.transcriptHash.copyOf()
    public val expectedAuthenticator: ByteArray = keys.transcriptAuthenticator()

    public fun expectedConfirmation(): String = when (pairingMethod) {
        PairingMethod.ADVANCED_SAS -> keys.sas()
        PairingMethod.DEVICE_NAME_PIN, PairingMethod.QR_BOOTSTRAP -> keys.pairingCode()
    }

    public fun verifyPeerAuthenticator(received: ByteArray): Boolean =
        MessageDigest.isEqual(expectedAuthenticator, received)

    public fun confirmUserEntry(entered: String): EstablishedSession {
        if (!PairingConfirmation.userConfirmed(expectedConfirmation(), entered)) {
            throw CryptoSessionException(
                CryptoSessionException.Code.INVALID_CIPHERTEXT,
                "Peer confirmation did not match",
            )
        }
        return EstablishedSession(
            sendCipher = SessionCipher(keys.sendKey.copyOf(), keys.sendDirectionLabel),
            receiveCipher = SessionCipher(keys.receiveKey.copyOf(), keys.receiveDirectionLabel),
            transcriptHash = keys.transcriptHash.copyOf(),
        )
    }
}

public class EstablishedSession internal constructor(
    public val sendCipher: SessionCipher,
    public val receiveCipher: SessionCipher,
    public val transcriptHash: ByteArray,
) {
    public fun close() {
        sendCipher.clear()
        receiveCipher.clear()
        transcriptHash.fill(0)
    }
}

public object SessionHandshake {
    public fun start(
        localEphemeral: EphemeralKeyPair,
        peerPublicKey: ByteArray,
        context: TranscriptContext,
        role: CryptoRole,
    ): PendingSession {
        val keys = X25519Session.derive(localEphemeral, peerPublicKey, context, role)
        return PendingSession(keys, context.pairingMethod)
    }
}
