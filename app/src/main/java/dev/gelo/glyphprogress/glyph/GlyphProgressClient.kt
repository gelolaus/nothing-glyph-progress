package dev.gelo.glyphprogress.glyph

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphMatrixUtils
import dev.gelo.glyphprogress.core.GlyphPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface GlyphLink {
    data object PreviewOnly : GlyphLink
    data object Connecting : GlyphLink
    data class Ready(val device: String) : GlyphLink
    data class Failed(val reason: String) : GlyphLink
}

/**
 * Drives Nothing's Glyph strip (displayProgress) or Glyph Matrix (generateMatrixProgress).
 * On any other phone this stays in preview-only mode and the on-screen bar still moves.
 */
class GlyphProgressClient(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val kind = detect()

    private var strip: GlyphManager? = null
    private var stripFrame: GlyphFrame? = null
    private var stripOpen = false

    private var matrix: GlyphMatrixManager? = null
    private var matrixOpen = false
    private var matrixSize = 25

    private var pending: Pair<GlyphPhase, Int>? = null
    private var patternJob: Job? = null
    private var started = false
    private var bound = false

    private val _link = MutableStateFlow<GlyphLink>(
        if (kind == Kind.None) GlyphLink.PreviewOnly else GlyphLink.Connecting,
    )
    val link: StateFlow<GlyphLink> = _link.asStateFlow()

    fun show(phase: GlyphPhase, percent: Int) {
        pending = phase to percent.coerceIn(0, 100)
        ensureStarted()
        if (bound && !isOpen()) reopen()
        if (isOpen()) render()
    }

    fun clear() {
        pending = null
        patternJob?.cancel()
        if (stripOpen) {
            runCatching { strip?.turnOff() }
            runCatching { strip?.closeSession() }
            stripOpen = false
        }
        if (matrixOpen) {
            runCatching { matrix?.closeAppMatrix() }
            runCatching { matrix?.turnOff() }
            matrixOpen = false
        }
        if (kind != Kind.None && _link.value is GlyphLink.Ready) {
            _link.value = GlyphLink.Connecting
        }
    }

    private fun ensureStarted() {
        if (started || kind == Kind.None) return
        started = true
        _link.value = GlyphLink.Connecting
        when (kind) {
            Kind.Strip -> startStrip()
            Kind.Matrix -> startMatrix()
            Kind.None -> Unit
        }
    }

    private fun startStrip() {
        val manager = GlyphManager.getInstance(appContext)
        strip = manager
        manager.init(object : GlyphManager.Callback {
            override fun onServiceConnected(name: ComponentName) {
                scope.launch {
                    val device = stripDevice()
                    val registered = if (device != null) manager.register(device) else manager.register()
                    try {
                        manager.openSession()
                        stripFrame = progressFrame(manager)
                        stripOpen = true
                        bound = true
                        _link.value = GlyphLink.Ready(device ?: "Glyph")
                        render()
                    } catch (error: Exception) {
                        _link.value = GlyphLink.Failed(sessionFailure(registered, error))
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                stripOpen = false
                scope.launch {
                    if (kind != Kind.None) _link.value = GlyphLink.Connecting
                }
            }
        })
    }

    private fun startMatrix() {
        val manager = GlyphMatrixManager.getInstance(appContext)
        matrix = manager
        matrixSize = runCatching { Common.getDeviceMatrixLength() }.getOrDefault(25).takeIf { it >= 13 } ?: 25
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName) {
                scope.launch {
                    val device = matrixDevice()
                    val registered = if (device != null) manager.register(device) else false
                    try {
                        manager.setGlyphMatrixTimeout(false)
                        matrixOpen = true
                        bound = true
                        _link.value = GlyphLink.Ready(device ?: "Glyph Matrix")
                        render()
                    } catch (error: Exception) {
                        _link.value = GlyphLink.Failed(sessionFailure(registered, error))
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                matrixOpen = false
                scope.launch { _link.value = GlyphLink.Connecting }
            }
        })
    }

    private fun render() {
        val target = pending ?: return
        if (!isOpen()) return
        patternJob?.cancel()
        when (target.first) {
            GlyphPhase.Progress -> push(target.second)
            GlyphPhase.Complete -> celebrate()
            GlyphPhase.Indeterminate -> blink()
        }
    }

    private fun celebrate() {
        patternJob = scope.launch {
            repeat(6) { tick ->
                push(if (tick % 2 == 0) 100 else 0)
                delay(220)
            }
            push(100)
        }
    }

    private fun blink() {
        patternJob = scope.launch {
            var on = false
            while (isActive) {
                on = !on
                push(if (on) 100 else 0)
                delay(480)
            }
        }
    }

    private fun push(percent: Int) {
        val level = percent.coerceIn(0, 100)
        if (stripOpen) {
            val frame = stripFrame ?: return
            runCatching { strip?.displayProgress(frame, level, false) }
                .onFailure { error -> _link.value = GlyphLink.Failed(error.message ?: "displayProgress failed") }
        }
        if (matrixOpen) {
            runCatching { pushMatrix(level) }
                .onFailure { error -> _link.value = GlyphLink.Failed(error.message ?: "Matrix update failed") }
        }
    }

    private fun pushMatrix(percent: Int) {
        val manager = matrix ?: return
        val thickness = if (matrixSize >= 25) 3 else 2
        val pixels = runCatching {
            GlyphMatrixUtils.generateMatrixProgress(
                matrixSize,
                thickness,
                percent,
                true,
                GlyphMatrixUtils.ARROW_3x2,
            )
        }.getOrElse { fallbackBar(matrixSize, percent) }
        val framed = runCatching {
            val label = GlyphMatrixObject.Builder()
                .setText(if (percent == 0) " " else percent.toString())
                .setBrightness(255)
                .build()
            GlyphMatrixFrame.Builder().addLow(pixels).addTop(label).build(appContext)
        }.getOrNull()
        if (framed != null) {
            manager.setAppMatrixFrame(framed)
        } else {
            manager.setAppMatrixFrame(pixels)
        }
    }

    private fun fallbackBar(size: Int, percent: Int): IntArray {
        val pixels = IntArray(size * size)
        val lit = kotlin.math.round(percent / 100f * size).toInt().coerceIn(0, size)
        val mid = size / 2
        for (row in (mid - 1)..(mid + 1)) {
            if (row !in 0 until size) continue
            for (column in 0 until lit) {
                pixels[row * size + column] = 255
            }
        }
        return pixels
    }

    private fun progressFrame(manager: GlyphManager): GlyphFrame {
        val builder = manager.glyphFrameBuilder
        return when {
            Common.is20111() -> builder.buildChannelD().build()
            Common.is25111() || Common.is25131() -> builder.buildChannelA().build()
            else -> builder.buildChannelC().build()
        }
    }

    private fun reopen() {
        val manager = strip
        if (kind == Kind.Strip && manager != null && !stripOpen) {
            try {
                manager.openSession()
                if (stripFrame == null) stripFrame = progressFrame(manager)
                stripOpen = true
                _link.value = GlyphLink.Ready(stripDevice() ?: "Glyph")
            } catch (error: Exception) {
                _link.value = GlyphLink.Failed(error.message ?: "openSession failed")
            }
        }
        if (kind == Kind.Matrix && matrix != null && !matrixOpen) {
            matrixOpen = true
            _link.value = GlyphLink.Ready(matrixDevice() ?: "Glyph Matrix")
        }
    }

    private fun isOpen(): Boolean = stripOpen || matrixOpen

    private fun sessionFailure(registered: Boolean, error: Exception): String {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return if (!registered) {
            "Glyph register failed. On the phone run: adb shell settings put global nt_glyph_interface_debug_enable 1 ($detail)"
        } else {
            detail
        }
    }

    private enum class Kind { None, Strip, Matrix }

    private fun detect(): Kind = try {
        when {
            Common.is23112() || Common.is25111p() -> Kind.Matrix
            stripDevice() != null -> Kind.Strip
            else -> Kind.None
        }
    } catch (_: Throwable) {
        Kind.None
    }

    private fun stripDevice(): String? = when {
        Common.is20111() -> Glyph.DEVICE_20111
        Common.is22111() -> Glyph.DEVICE_22111
        Common.is23111() -> Glyph.DEVICE_23111
        Common.is23113() -> Glyph.DEVICE_23113
        Common.is24111() -> Glyph.DEVICE_24111
        Common.is25111() -> Glyph.DEVICE_25111
        Common.is25131() -> Glyph.DEVICE_25131
        else -> null
    }

    private fun matrixDevice(): String? = when {
        Common.is23112() -> Glyph.DEVICE_23112
        Common.is25111p() -> Glyph.DEVICE_25111p
        else -> null
    }
}
