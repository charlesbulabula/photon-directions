package com.neo.maps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neo.maps.R

/**
 * Lightweight foreground service used to keep the app clearly visible to the user
 * while capture is enabled. The actual camera work remains tied to the activity's
 * lifecycle for privacy and stability.
 */
class CaptureForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: getString(R.string.dir_status_idle)
        startForeground(NOTIFICATION_ID, buildNotification(status))
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(status: String): Notification {
        val channelId = ensureChannel()

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel(): String {
        val channelId = "photon_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = mgr.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                mgr.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    companion object {
        private const val NOTIFICATION_ID = 100
        private const val EXTRA_STATUS = "status"

        fun start(context: Context, status: String) {
            val intent = Intent(context, CaptureForegroundService::class.java)
                .putExtra(EXTRA_STATUS, status)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CaptureForegroundService::class.java)
            context.stopService(intent)
        }
    }
}


