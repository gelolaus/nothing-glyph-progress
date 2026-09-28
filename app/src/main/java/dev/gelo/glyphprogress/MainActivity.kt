package dev.gelo.glyphprogress

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.gelo.glyphprogress.core.GlyphPhase
import dev.gelo.glyphprogress.core.GlyphTrack
import dev.gelo.glyphprogress.demo.SampleLiveUpdate
import dev.gelo.glyphprogress.glyph.GlyphLink
import dev.gelo.glyphprogress.listener.LiveUpdateListenerService

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { ProgressScreen() }
    }
}

private val Black = Color(0xFF0A0A0A)
private val Card = Color(0xFF161616)
private val Muted = Color(0xFF9A9A9A)
private val Line = Color(0xFF2C2C2C)
private val Lit = Color(0xFFF4F4F4)

@Composable
private fun ProgressScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlyphProgressApp
    val board by app.repository.board.collectAsState()
    val manual by app.repository.manual.collectAsState()
    val includeStandard by app.repository.includeStandardProgress.collectAsState()
    val link by app.glyphs.link.collectAsState()
    var listenerOn by remember { mutableStateOf(listenerEnabled(context)) }
    var banner by remember { mutableStateOf("") }
    var slider by remember { mutableFloatStateOf(40f) }
    val scroll = rememberScrollState()

    val shownPhase = manual?.phase ?: board.active?.phase
    val shownPercent = manual?.percent ?: board.active?.percent ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("GLYPH PROGRESS", color = Lit, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(
            "Every Live Update on this phone, drawn on the Glyph.",
            color = Muted,
            fontSize = 14.sp,
        )
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (listenerOn) "Notification access on" else "Notification access off", color = Lit)
                    Text(
                        "Required to read Grab, Maps, and every other Live Update.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Grant") }
            }
            TextButton(onClick = {
                listenerOn = listenerEnabled(context)
                LiveUpdateListenerService.refreshIfConnected()
            }) { Text("Refresh notifications") }
        }
        Section {
            Text(linkLabel(link), color = Lit)
            Text(
                "Nothing phones also need the debug flag once: adb shell settings put global nt_glyph_interface_debug_enable 1",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        GlyphPreview(phase = shownPhase, percent = shownPercent)
        Text(
            when {
                manual != null -> "Manual ${shownPercent}%"
                board.active == null -> "No Live Update on the shade"
                else -> "${board.active!!.percent}%  ${board.active!!.title}"
            },
            color = Lit,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
        )
        board.active?.detail?.takeIf { it.isNotBlank() && manual == null }?.let {
            Text(it, color = Muted)
        }
        if (banner.isNotEmpty()) Text(banner, color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { banner = SampleLiveUpdate.advance(context) }) { Text("Sample") }
            Button(onClick = {
                SampleLiveUpdate.cancel(context)
                banner = "Sample cleared"
            }) { Text("Clear") }
        }
        Text("Manual light", color = Muted, fontSize = 13.sp)
        Slider(
            value = slider,
            onValueChange = {
                slider = it
                app.repository.setManual(ManualGlyph(GlyphPhase.Progress, it.toInt()))
            },
            valueRange = 0f..100f,
        )
        Row {
            TextButton(onClick = {
                app.repository.setManual(ManualGlyph(GlyphPhase.Indeterminate, slider.toInt()))
            }) { Text("Blink") }
            TextButton(onClick = { app.repository.setManual(null) }) { Text("Follow notifications") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also follow ordinary progress", color = Lit, fontSize = 14.sp)
                Text(
                    "Ongoing progress notifications that are not promoted Live Updates. Downloads and the Play Store stay ignored.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = includeStandard, onCheckedChange = app.repository::setIncludeStandard)
        }
        HorizontalDivider(color = Line)
        Text("On the shade", color = Muted, fontSize = 13.sp)
        if (board.tracks.isEmpty()) {
            Text("Nothing matched. Post a sample, or wait for a ride, delivery, timer, or navigation Live Update.", color = Muted)
        }
        board.tracks.forEach { track ->
            TrackRow(
                track = track,
                selected = track.key == board.activeKey,
                onUse = { app.repository.pin(track.key) },
                onHide = { app.repository.ignore(track.packageName) },
            )
        }
        if (app.repository.ignoredPackages().isNotEmpty()) {
            TextButton(onClick = app.repository::clearIgnored) { Text("Unhide apps") }
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() },
    )
}

@Composable
private fun GlyphPreview(phase: GlyphPhase?, percent: Int) {
    val blinking = phase == GlyphPhase.Indeterminate || phase == GlyphPhase.Complete
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .alpha(if (phase == null) 0.35f else 1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val litCount = when (phase) {
            null -> 0
            GlyphPhase.Indeterminate -> 16
            else -> kotlin.math.ceil(percent.coerceIn(0, 100) / 100f * 16f).toInt()
        }
        repeat(16) { index ->
            val fromBottom = 15 - index
            val lit = fromBottom < litCount
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height((28 + index * 5).dp)
                    .alpha(if (!lit) 1f else if (blinking) 0.55f else 1f)
                    .background(if (lit) Lit else Line, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: GlyphTrack,
    selected: Boolean,
    onUse: () -> Unit,
    onHide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            buildString {
                if (selected) append("● ")
                append(track.title.ifBlank { track.packageName })
            },
            color = Lit,
        )
        Text(
            "${track.packageName}  ·  ${track.origin.name}  ·  ${track.phase.name} ${track.percent}%",
            color = Muted,
            fontSize = 12.sp,
        )
        if (track.detail.isNotBlank()) Text(track.detail, color = Muted, fontSize = 13.sp)
        Row {
            TextButton(onClick = onUse) { Text("Use this") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onHide) { Text("Hide app") }
        }
    }
}

private fun listenerEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, LiveUpdateListenerService::class.java)
    return flat.contains(component.flattenToString()) || flat.contains(component.flattenToShortString())
}

private fun linkLabel(link: GlyphLink): String = when (link) {
    GlyphLink.PreviewOnly -> "Glyph hardware not found. The preview still tracks Live Updates."
    GlyphLink.Connecting -> "Connecting to the Glyph."
    is GlyphLink.Ready -> "Glyph ready (${link.device})."
    is GlyphLink.Failed -> link.reason
}
