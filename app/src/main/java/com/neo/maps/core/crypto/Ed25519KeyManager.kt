package com.neo.maps.core.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Manages an Ed25519 keypair stored in EncryptedSharedPreferences.
 * - Not in plain text
 * - Ed25519 via BouncyCastle lightweight API
 *
 * NOTE: This keypair is for signing metadata JSON (not the mTLS cert).
 */
class Ed25519KeyManager(
    context: Context,
    private val prefsName: String = "photon_ed25519_prefs",
    private val keyAliasPrivate: String = "ed25519_priv",
    private val keyAliasPublic: String = "ed25519_pub"
) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        prefsName,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val random = SecureRandom()

    @Volatile
    private var cachedPrivate: Ed25519PrivateKeyParameters? = null

    @Volatile
    private var cachedPublic: Ed25519PublicKeyParameters? = null

    fun getOrCreateKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val existingPriv = cachedPrivate
        val existingPub = cachedPublic
        if (existingPriv != null && existingPub != null) {
            return existingPriv to existingPub
        }

        val privBase64 = prefs.getString(keyAliasPrivate, null)
        val pubBase64 = prefs.getString(keyAliasPublic, null)

        return if (privBase64 != null && pubBase64 != null) {
            val privBytes = Base64.decode(privBase64, Base64.NO_WRAP)
            val pubBytes = Base64.decode(pubBase64, Base64.NO_WRAP)
            val priv = Ed25519PrivateKeyParameters(privBytes, 0)
            val pub = Ed25519PublicKeyParameters(pubBytes, 0)
            cachedPrivate = priv
            cachedPublic = pub
            priv to pub
        } else {
            generateAndStore()
        }
    }

    private fun generateAndStore(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val priv = Ed25519PrivateKeyParameters(random)
        val pub = priv.generatePublicKey()

        val privBytes = priv.encoded
        val pubBytes = pub.encoded

        prefs.edit()
            .putString(keyAliasPrivate, Base64.encodeToString(privBytes, Base64.NO_WRAP))
            .putString(keyAliasPublic, Base64.encodeToString(pubBytes, Base64.NO_WRAP))
            .apply()

        cachedPrivate = priv
        cachedPublic = pub
        return priv to pub
    }

    fun publicKeyBytes(): ByteArray {
        val (_, pub) = getOrCreateKeyPair()
        return pub.encoded
    }
}
