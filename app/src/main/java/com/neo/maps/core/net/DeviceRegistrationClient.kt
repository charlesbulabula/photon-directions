package com.neo.maps.core.net

import android.content.Context
import android.provider.Settings
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sends the device public key (Ed25519) and, optionally, the mTLS certificate
 * fingerprint to the backend once.
 *
 * Backend URL is provided by the caller (DirectionsActivity).
 */
class DeviceRegistrationClient(
    context: Context,
    private val registerUrl: String,
    private val env: String
) {

    private val appContext = context.applicationContext

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "photon_registration_prefs",
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val client = SecureHttpClient.get(appContext)

    companion object {
        private const val KEY_REGISTERED = "device_registered"
    }

    fun isRegistered(): Boolean = prefs.getBoolean(KEY_REGISTERED, false)

    suspend fun registerIfNeeded(
        ed25519PublicKey: ByteArray,
        mtlsCertDer: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRegistered()) return@withContext true
        if (registerUrl.isBlank()) return@withContext false

        val deviceId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val payload = JSONObject().apply {
            put("device_id", deviceId)
            put("platform", "android")
            put("env", env)
            put(
                "ed25519_pub",
                Base64.encodeToString(ed25519PublicKey, Base64.NO_WRAP)
            )
            mtlsCertDer?.let { cert ->
                put(
                    "mtls_cert_der",
                    Base64.encodeToString(cert, Base64.NO_WRAP)
                )
            }
        }

        val jsonMediaType = "application/json".toMediaType()
        val body = payload.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(registerUrl)
            // Utilise method("POST", ...) pour éviter tout problème de version
            .method("POST", body)
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (resp.isSuccessful) {
                prefs.edit().putBoolean(KEY_REGISTERED, true).apply()
                return@withContext true
            }
        }

        false
    }
}
