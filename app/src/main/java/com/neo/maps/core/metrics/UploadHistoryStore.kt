package com.neo.maps.core.metrics

import android.content.Context
import com.neo.maps.core.net.LambdaUploadResult
import org.json.JSONArray
import org.json.JSONObject

data class UploadEntry(
    val timestamp: Long,
    val code: Int,
    val success: Boolean
)

/**
 * Lightweight upload history store – keeps the last N uploads in SharedPreferences
 * for display in the Settings screen and basic diagnostics.
 */
object UploadHistoryStore {

    private const val PREFS_NAME = "photon_upload_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 50

    fun record(context: Context, result: LambdaUploadResult) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_HISTORY, "[]") ?: "[]"

        val existing = try {
            JSONArray(current)
        } catch (_: Exception) {
            JSONArray()
        }

        val entry = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("code", result.code)
            put("success", result.success)
        }

        val next = JSONArray().apply {
            put(entry)
            val limit = minOf(existing.length(), MAX_ENTRIES - 1)
            for (i in 0 until limit) {
                put(existing.getJSONObject(i))
            }
        }

        prefs.edit().putString(KEY_HISTORY, next.toString()).apply()
    }

    fun load(context: Context): List<UploadEntry> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        UploadEntry(
                            timestamp = obj.optLong("ts"),
                            code = obj.optInt("code"),
                            success = obj.optBoolean("success")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}


