package dev.gelo.glyphprogress.demo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.gelo.glyphprogress.GlyphProgressApp
import dev.gelo.glyphprogress.MainActivity
import dev.gelo.glyphprogress.core.NotificationSnapshot
import dev.gelo.glyphprogress.listener.LiveUpdateListenerService

object SampleLiveUpdate {
    const val NOTIFICATION_ID = 42

    private val steps = listOf(
        SampleStep(percent = 0, indeterminate = true, title = "Finding your driver", text = "Searching", shortText = ""),
        SampleStep(percent = 15, indeterminate = false, title = "GrabCar", text = "Driver is 8 min away", shortText = "8 min"),
        SampleStep(percent = 45, indeterminate = false, title = "GrabCar", text = "Driver is on the way", shortText = "5min"),
        SampleStep(percent = 80, indeterminate = false, title = "GrabCar", text = "Driver is nearby", shortText = "2 min"),
        SampleStep(percent = 100, indeterminate = false, title = "GrabCar", text = "Driver has arrived", shortText = "Here"),
    )

    fun advance(context: Context): String {
        val next = (currentIndex + 1) % steps.size
        currentIndex = next
        return post(context, steps[next])
    }

    fun postCurrent(context: Context): String = post(context, steps[currentIndex.coerceAtLeast(0)])

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        currentIndex = -1
        LiveUpdateListenerService.refreshIfConnected()
        if (LiveUpdateListenerService.instance == null) {
            GlyphProgressApp.instance?.repository?.remove(LOCAL_KEY)
        }
    }

    private fun post(context: Context, step: SampleStep): String {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "Allow notifications, then post the sample again."
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style: NotificationCompat.ProgressStyle = NotificationCompat.ProgressStyle()
        if (step.indeterminate) {
            style.setProgressIndeterminate(true)
        } else {
            style.setProgress(step.percent)
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(100))
        }
        val notification = NotificationCompat.Builder(context, GlyphProgressApp.SAMPLE_CHANNEL)
            .setSmallIcon(dev.gelo.glyphprogress.R.drawable.ic_notification)
            .setContentTitle(step.title)
            .setContentText(step.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setRequestPromotedOngoing(true)
            .setProgress(100, step.percent, step.indeterminate)
            .setStyle(style)
            .apply {
                if (step.shortText.isNotEmpty()) {
                    runCatching { setShortCriticalText(step.shortText) }
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        LiveUpdateListenerService.refreshIfConnected()
        if (LiveUpdateListenerService.instance == null) {
            GlyphProgressApp.instance?.repository?.upsert(localSnapshot(step))
        }
        return if (step.indeterminate) "Sample: searching" else "Sample: ${step.percent}%"
    }

    private fun localSnapshot(step: SampleStep) = NotificationSnapshot(
        key = LOCAL_KEY,
        packageName = "dev.gelo.glyphprogress",
        title = step.title,
        text = step.text,
        shortText = step.shortText,
        isOngoing = true,
        postTimeMillis = System.currentTimeMillis(),
        requestedPromotedOngoing = true,
        template = "android.app.Notification\$ProgressStyle",
        progress = if (step.indeterminate) null else step.percent,
        progressMax = if (step.indeterminate) null else 100,
        indeterminate = step.indeterminate,
        segmentLengths = if (step.indeterminate) emptyList() else listOf(100),
    )

    private const val LOCAL_KEY = "local:sample"
    private var currentIndex = -1
}

private data class SampleStep(
    val percent: Int,
    val indeterminate: Boolean,
    val title: String,
    val text: String,
    val shortText: String,
)
