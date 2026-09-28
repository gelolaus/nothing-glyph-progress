package dev.gelo.glyphprogress.listener

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import dev.gelo.glyphprogress.core.NotificationSnapshot

private const val INTERNAL_EXTRA = "dev.gelo.glyphprogress.internal"

fun StatusBarNotification.toSnapshot(): NotificationSnapshot {
    val notification = this.notification
    val extras = notification.extras ?: Bundle()
    val template = extras.string(ExtraKeys.template)
    val segments = segmentLengths(extras)
    return NotificationSnapshot(
        key = key,
        packageName = packageName,
        title = extras.string(Notification.EXTRA_TITLE),
        text = extras.string(Notification.EXTRA_TEXT),
        subText = extras.string(Notification.EXTRA_SUB_TEXT),
        shortText = extras.string(ExtraKeys.shortCritical),
        isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        postTimeMillis = postTime,
        promotedOngoing = isPromoted(notification),
        requestedPromotedOngoing = extras.getBoolean(ExtraKeys.requestPromoted),
        template = template,
        progress = extras.optionalInt(Notification.EXTRA_PROGRESS),
        progressMax = extras.optionalInt(Notification.EXTRA_PROGRESS_MAX),
        indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
        segmentLengths = segments,
        hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            template.contains("MediaStyle", ignoreCase = true),
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        isColorized = extras.getBoolean("android.colorized"),
        isInternal = extras.getBoolean(INTERNAL_EXTRA),
        whenMillis = notification.`when`,
        showWhen = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true),
        usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
        chronometerCountdown = extras.getBoolean(ExtraKeys.chronometerCountdown),
    )
}

private object ExtraKeys {
    val template: String = notificationString("EXTRA_TEMPLATE", "android.template")
    val requestPromoted: String = notificationString("EXTRA_REQUEST_PROMOTED_ONGOING", "android.requestPromotedOngoing")
    val shortCritical: String = notificationString("EXTRA_SHORT_CRITICAL_TEXT", "android.shortCriticalText")
    val segments: String = notificationString("EXTRA_PROGRESS_SEGMENTS", "android.progressSegments")
    val chronometerCountdown: String = notificationString("EXTRA_CHRONOMETER_COUNT_DOWN", "android.chronometerCountDown")

    private fun notificationString(field: String, fallback: String): String =
        runCatching { Notification::class.java.getField(field).get(null) as String }.getOrDefault(fallback)
}

private fun isPromoted(notification: Notification): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    val flagged = runCatching {
        val flag = Notification::class.java.getField("FLAG_PROMOTED_ONGOING").getInt(null)
        flag != 0 && notification.flags and flag != 0
    }.getOrDefault(false)
    if (flagged) return true
    return runCatching {
        val method = Notification::class.java.getMethod("isPromotedOngoing")
        method.invoke(notification) as? Boolean
    }.getOrNull() == true
}

private fun segmentLengths(extras: Bundle): List<Int> {
    val lengths = ArrayList<Int>()
    val list = extras.getParcelableArrayList(ExtraKeys.segments, Parcelable::class.java)
    if (list != null) {
        list.mapNotNullTo(lengths) { lengthOf(it) }
        return lengths
    }
    val array = extras.getParcelableArray(ExtraKeys.segments, Parcelable::class.java) ?: return emptyList()
    array.mapNotNullTo(lengths) { item -> item?.let { lengthOf(it) } }
    return lengths
}

private fun lengthOf(item: Any): Int? = when (item) {
    is Bundle -> if (item.containsKey("length")) item.getInt("length") else null
    else -> runCatching {
        val method = item.javaClass.methods.firstOrNull { it.name == "getLength" && it.parameterCount == 0 }
        method?.invoke(item) as? Int
    }.getOrNull()
}

private fun Bundle.string(key: String): String = getCharSequence(key)?.toString().orEmpty()

private fun Bundle.optionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null
