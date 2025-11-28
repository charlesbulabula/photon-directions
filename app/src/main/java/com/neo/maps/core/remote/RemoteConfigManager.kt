package com.neo.maps.core.remote

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around Firebase Remote Config.
 * Keys:
 *  - uploads_enabled (Boolean)
 *  - capture_interval_seconds (Long)
 */
class RemoteConfigManager(context: Context) {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val settings = remoteConfigSettings {
            // en prod tu peux mettre 3600; en debug, 0
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                // Par défaut on autorise les uploads en environnement de dev.
                // Tu peux ensuite surcharger cette valeur via Firebase Remote Config.
                "uploads_enabled" to true,
                "capture_interval_seconds" to 30L
            )
        )
    }

    suspend fun refresh() {
        suspendCancellableCoroutine<Unit> { cont ->
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener {
                    // On ne se préoccupe pas du succès/échec ici, on continue
                    cont.resume(Unit)
                }
        }
    }

    fun uploadsEnabled(): Boolean =
        remoteConfig.getBoolean("uploads_enabled")

    fun captureIntervalSeconds(): Long =
        remoteConfig
            .getLong("capture_interval_seconds")
            .coerceAtLeast(5L)
}
