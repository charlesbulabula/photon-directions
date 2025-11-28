package com.neo.maps.core.metadata

import android.location.Location
import org.json.JSONObject
import java.util.UUID

object MetadataBuilder {

    fun nonce(): String {
        val now = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().replace("-", "")
        return "${now}_${random}"
    }

    fun buildMetadataJson(
        gps: Location?,
        advertiserId: String?,
        timestampMillis: Long = System.currentTimeMillis(),
        providedNonce: String = nonce()
    ): String {
        val obj = JSONObject()
        gps?.let {
            obj.put("lat", it.latitude)
            obj.put("lon", it.longitude)
            obj.put("accuracy", it.accuracy)
        }
        obj.put("advertiser_id", advertiserId ?: "")
        obj.put("timestamp", timestampMillis)
        obj.put("nonce", providedNonce)
        return obj.toString()
    }
}
