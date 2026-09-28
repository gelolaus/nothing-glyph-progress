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
    val segments = parcelableBundleList(extras, ExtraKeys.segments)
    val points = parcelableBundleList(extras, ExtraKeys.points)
    val metrics = parcelableBundleList(extras, ExtraKeys.metrics)
    return NotificationSnapshot(
        key = key,
        packageName = packageName,
        title = extras.string(Notification.EXTRA_TITLE),
        text = extras.string(Notification.EXTRA_TEXT),
        subText = extras.string(Notification.EXTRA_SUB_TEXT),
        shortText = extras.string(ExtraKeys.shortCritical),
        bigText = extras.string(Notification.EXTRA_BIG_TEXT),
        infoText = extras.string(Notification.EXTRA_INFO_TEXT),
        textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            .orEmpty(),
        isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        postTimeMillis = postTime,
        promotedOngoing = isPromoted(notification),
        requestedPromotedOngoing = extras.getBoolean(ExtraKeys.requestPromoted),
        template = template,
        progress = extras.optionalInt(Notification.EXTRA_PROGRESS),
        progressMax = extras.optionalInt(Notification.EXTRA_PROGRESS_MAX),
        indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
        segmentLengths = segments.mapNotNull { intField(it, "length") },
        progressPoints = points.mapNotNull { intField(it, "position") },
        // Segments, points, and metrics can each carry an API 37 semantic style; the
        // engine only cares about the strongest one across all of them.
        semanticStyles = (segments + points + metrics)
            .mapNotNull { intField(it, "semanticStyle") }
            .filter { it > 0 },
        // "value" inside a metric bundle is an undocumented internal parcel (see
        // docs/research/android-live-updates.md); only the label is safe to read.
        metricLabels = metrics.mapNotNull { stringField(it, "label") }.filter { it.isNotBlank() },
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
    val points: String = notificationString("EXTRA_PROGRESS_POINTS", "android.progressPoints")
    // API 37, @hide on the platform and not yet a public NotificationCompat constant.
    val metrics: String = "android.metrics"
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

/** Reads an `ArrayList<Bundle>` progress/points/metrics extra as opaque bundle-like items. */
private fun parcelableBundleList(extras: Bundle, key: String): List<Any> {
    val list = extras.getParcelableArrayList(key, Parcelable::class.java)
    if (list != null) return list
    val array = extras.getParcelableArray(key, Parcelable::class.java) ?: return emptyList()
    return array.filterNotNull()
}

private fun intField(item: Any, name: String): Int? = when (item) {
    is Bundle -> if (item.containsKey(name)) item.getInt(name) else null
    else -> runCatching {
        val getter = "get" + name.replaceFirstChar { it.uppercase() }
        val method = item.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
        method?.invoke(item) as? Int
    }.getOrNull()
}

private fun stringField(item: Any, name: String): String? = when (item) {
    is Bundle -> item.getCharSequence(name)?.toString() ?: item.getString(name)
    else -> runCatching {
        val getter = "get" + name.replaceFirstChar { it.uppercase() }
        val method = item.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
        (method?.invoke(item) as? CharSequence)?.toString()
    }.getOrNull()
}

private fun Bundle.string(key: String): String = getCharSequence(key)?.toString().orEmpty()

private fun Bundle.optionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null
