package dev.gelo.glyphprogress.listener

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.gelo.glyphprogress.GlyphProgressApp

class LiveUpdateListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        // Safe to call here (and only here or before onListenerConnected): asks the
        // system to rebind us instead of waiting for its own retry schedule, so a
        // listener killed by doze or an OEM battery rule comes back quickly.
        requestRebind(ComponentName(this, LiveUpdateListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        app()?.repository?.upsert(sbn.toSnapshot())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        app()?.repository?.remove(sbn.key)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        refresh()
    }

    fun refresh() {
        val items = runCatching { activeNotifications?.map { it.toSnapshot() }.orEmpty() }.getOrDefault(emptyList())
        app()?.repository?.replaceAll(items)
    }

    private fun app(): GlyphProgressApp? = application as? GlyphProgressApp

    companion object {
        @Volatile
        var instance: LiveUpdateListenerService? = null
            private set

        fun refreshIfConnected() {
            instance?.refresh()
        }
    }
}
