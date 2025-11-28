package com.neo.maps.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore

/**
 * Gère une paire de clés dans l'AndroidKeyStore pour mTLS.
 *
 * - Alias fixe: "photon_mtls_key"
 * - Crée une clé RSA si elle n'existe pas.
 * - Retourne la PrivateKeyEntry (clé privée + certificat X.509 généré par AndroidKeyStore).
 */
class DeviceCertificateManager {

    companion object {
        private const val KEY_ALIAS = "photon_mtls_key"
    }

    /**
     * Récupère (ou crée si nécessaire) l'entrée PrivateKeyEntry dans AndroidKeyStore.
     */
    fun getPrivateKeyEntry(): KeyStore.PrivateKeyEntry? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            // Crée une nouvelle paire de clés RSA
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
            )
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA512
                )
                .setSignaturePaddings(
                    KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
                )
                .setKeySize(2048)
                .build()

            kpg.initialize(spec)
            kpg.generateKeyPair()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null)
        return entry as? KeyStore.PrivateKeyEntry
    }
}
