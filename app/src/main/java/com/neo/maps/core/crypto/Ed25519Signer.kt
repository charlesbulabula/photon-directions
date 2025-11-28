package com.neo.maps.core.crypto

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.signers.Ed25519Signer as BcEd25519Signer

/**
 * Signs payloads with the Ed25519 private key managed by Ed25519KeyManager.
 */
class Ed25519Signer(
    context: Context,
    private val keyManager: Ed25519KeyManager = Ed25519KeyManager(context)
) {

    fun sign(payload: ByteArray): String {
        val (priv, _) = keyManager.getOrCreateKeyPair()

        val signer = BcEd25519Signer()
        signer.init(true, priv)
        signer.update(payload, 0, payload.size)
        val sigBytes = signer.generateSignature()

        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    fun publicKeyBase64(): String {
        val pubBytes = keyManager.publicKeyBytes()
        return Base64.encodeToString(pubBytes, Base64.NO_WRAP)
    }
}
