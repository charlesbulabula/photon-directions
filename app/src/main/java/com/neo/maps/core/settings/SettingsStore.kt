package com.neo.maps.core.settings

import android.content.Context

/**
 * Central place for user-facing settings and flags (Wi‑Fi only, onboarding, etc.).
 */
object SettingsStore {

    private const val PREFS_NAME = "photon_settings"
    private const val KEY_WIFI_ONLY = "wifi_only_uploads"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isWifiOnlyUploads(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnlyUploads(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }
}


