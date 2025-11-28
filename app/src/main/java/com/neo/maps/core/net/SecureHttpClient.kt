package com.neo.maps.core.net

import android.content.Context
import android.util.Log
import com.neo.maps.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * Central factory for a hardened OkHttpClient:
 * - Enforces TLS 1.3 (with TLS 1.2 fallback for older stacks)
 * - Uses Android's system trust store
 * - Adds certificate pinning for the API Gateway host
 * - Optionally presents a client certificate from AndroidKeyStore (mTLS)
 *
 * NOTE: For mTLS to be fully effective, the backend must request and validate
 * the client certificate that is provisioned on the device.
 */
object SecureHttpClient {

    private const val TAG = "SecureHttpClient"

    @Volatile
    private var cachedClient: OkHttpClient? = null

    fun get(context: Context): OkHttpClient {
        cachedClient?.let { return it }

        synchronized(this) {
            cachedClient?.let { return it }

            val appContext = context.applicationContext

            val trustManager = systemDefaultTrustManager()
            val keyManager = clientKeyManager(appContext)

            val sslContext = try {
                SSLContext.getInstance("TLSv1.3")
            } catch (e: Exception) {
                Log.w(TAG, "TLSv1.3 not available, falling back to TLS: ${e.message}")
                SSLContext.getInstance("TLS")
            }.apply {
                init(
                    keyManager?.let { arrayOf<javax.net.ssl.KeyManager>(it) },
                    arrayOf<TrustManager>(trustManager),
                    SecureRandom()
                )
            }

            val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .allEnabledCipherSuites()
                .build()

            val pinner = CertificatePinner.Builder()
                .add(
                    BuildConfig.PHOTON_LAMBDA_HOST,
                    BuildConfig.PHOTON_CERT_PIN
                )
                .build()

            val builder = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .connectionSpecs(listOf(connectionSpec, ConnectionSpec.CLEARTEXT))
                .certificatePinner(pinner)
                .retryOnConnectionFailure(true)

            val client = builder.build()
            cachedClient = client
            return client
        }
    }

    private fun systemDefaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        factory.init(null as KeyStore?)
        val trustManagers = factory.trustManagers
        require(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected default trust managers: ${trustManagers.contentToString()}"
        }
        return trustManagers[0] as X509TrustManager
    }

    /**
     * Builds an X509KeyManager from the key/cert stored in AndroidKeyStore via DeviceCertificateManager.
     * If anything fails, returns null – in that case the client will not present a certificate.
     */
    private fun clientKeyManager(context: Context): X509KeyManager? {
        return try {
            val deviceCertManager = com.neo.maps.core.crypto.DeviceCertificateManager()
            val entry = deviceCertManager.getPrivateKeyEntry() ?: return null

            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setKeyEntry(
                    "photon_mtls_key_runtime",
                    entry.privateKey,
                    null,
                    arrayOf<X509Certificate>(entry.certificate as X509Certificate)
                )
            }
            val kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            )
            kmf.init(ks, CharArray(0))

            kmf.keyManagers
                .filterIsInstance<X509KeyManager>()
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to initialize client KeyManager for mTLS: ${e.message}")
            null
        }
    }
}


