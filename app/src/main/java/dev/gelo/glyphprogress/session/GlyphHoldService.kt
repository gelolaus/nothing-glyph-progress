package dev.gelo.glyphprogress.session

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import dev.gelo.glyphprogress.GlyphProgressApp
import dev.gelo.glyphprogress.R

class GlyphHoldService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, GlyphProgressApp.HOLD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Glyph Progress")
            .setContentText("Following a Live Update")
            .setOngoing(true)
            .setSilent(true)
            .addExtras(Bundle().apply { putBoolean(INTERNAL_EXTRA, true) })
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        const val INTERNAL_EXTRA = "dev.gelo.glyphprogress.internal"
    }
}
