package com.neo.maps.core.remoteconfig

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager {

    private val rc: FirebaseRemoteConfig = Firebase.remoteConfig.apply {
        setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60
            }
        )
        setDefaultsAsync(
            mapOf(
                "capture_interval_seconds" to 5L,
                "uploads_enabled" to true
            )
        )
    }

    suspend fun refresh() {
        rc.fetchAndActivate().await()
    }

    fun captureIntervalSeconds(): Long =
        rc.getLong("capture_interval_seconds").coerceAtLeast(1L)

    fun uploadsEnabled(): Boolean =
        rc.getBoolean("uploads_enabled")
}
