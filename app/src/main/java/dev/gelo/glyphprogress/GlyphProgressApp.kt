package dev.gelo.glyphprogress

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.gelo.glyphprogress.core.GlyphPhase
import dev.gelo.glyphprogress.session.GlyphHoldService
import dev.gelo.glyphprogress.session.ProgressRepository
import dev.gelo.glyphprogress.glyph.GlyphProgressClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GlyphProgressApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: ProgressRepository
        private set

    lateinit var glyphs: GlyphProgressClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        repository = ProgressRepository(this)
        glyphs = GlyphProgressClient(this)
        scope.launch {
            combine(repository.board, repository.manual) { board, manual ->
                val active = board.active
                when {
                    manual != null -> manual.phase to manual.percent
                    active != null -> active.phase to active.percent
                    else -> null
                }
            }.collect { target ->
                if (target == null) {
                    glyphs.clear()
                    stopService(Intent(this@GlyphProgressApp, GlyphHoldService::class.java))
                } else {
                    holdProcess()
                    glyphs.show(target.first, target.second)
                }
            }
        }
    }

    private fun holdProcess() {
        val intent = Intent(this, GlyphHoldService::class.java)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Exception) {
            // Android blocks foreground starts from the background. The listener
            // still updates the lights while this process is bound.
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(SAMPLE_CHANNEL, "Live Update samples", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sample ride progress used to test Glyph Progress"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(HOLD_CHANNEL, "Glyph session", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the Glyph session alive while a Live Update is on screen"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val SAMPLE_CHANNEL = "live_update_samples"
        const val HOLD_CHANNEL = "glyph_hold"

        @Volatile
        var instance: GlyphProgressApp? = null
    }
}

data class ManualGlyph(
    val phase: GlyphPhase,
    val percent: Int,
)
