package dev.gelo.glyphprogress

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.gelo.glyphprogress.core.GlyphPhase
import dev.gelo.glyphprogress.core.GlyphTrack
import dev.gelo.glyphprogress.core.Semantic
import dev.gelo.glyphprogress.core.SuppressReason
import dev.gelo.glyphprogress.demo.SampleLiveUpdate
import dev.gelo.glyphprogress.glyph.GlyphLink
import dev.gelo.glyphprogress.listener.LiveUpdateListenerService

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = NothingColors) {
                ProgressScreen()
            }
        }
    }
}

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Ash = Color(0xFF8E8E93)
private val Hairline = Color(0xFF2A2A2A)
private val Group = Color(0xFF141414)
private val Caution = Color(0xFFFFB300)
private val Danger = Color(0xFFFF4D4D)

private val NothingColors = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    background = Ink,
    surface = Ink,
    onBackground = Paper,
    onSurface = Paper,
)

@Composable
private fun ProgressScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlyphProgressApp
    val board by app.repository.board.collectAsState()
    val manual by app.repository.manual.collectAsState()
    val includeStandard by app.repository.includeStandardProgress.collectAsState()
    val matchAnyOngoing by app.repository.matchAnyOngoingProgress.collectAsState()
    val link by app.glyphs.link.collectAsState()
    var listenerOn by remember { mutableStateOf(listenerEnabled(context)) }
    var previewOpen by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(40f) }
    val serif = remember { NothingType.serif() }
    val geist = remember { NothingType.geist() }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerOn = listenerEnabled(context)
                LiveUpdateListenerService.refreshIfConnected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phase = manual?.phase ?: board.active?.phase
    val percent = manual?.percent ?: board.active?.percent ?: 0
    val semantic = if (manual != null) Semantic.UNSPECIFIED else board.active?.semantic ?: Semantic.UNSPECIFIED
    val milestones = if (manual != null) emptyList() else board.active?.milestoneFractions.orEmpty()
    val headline = when {
        manual != null -> "Preview"
        board.active == null -> "No Live Update"
        else -> board.active!!.title.ifBlank { appLabel(context, board.active!!.packageName) }
    }
    val detail = when {
        manual != null -> "The slider is driving the Glyph."
        board.active == null -> "A ride, delivery, timer, or route will show up here."
        else -> board.active!!.detail
    }

    ProvideTextStyle(TextStyle(fontFamily = geist, fontWeight = FontWeight.Normal, color = Paper)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        "Glyph Progress",
                        color = Paper,
                        fontFamily = serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                    )
                    Text("Live Updates on the Glyph", color = Ash, fontSize = 14.sp)
                }
            }

            Column(Modifier.padding(horizontal = 8.dp)) {
                Spacer(Modifier.height(64.dp))
                Text(
                    text = figure(phase, percent, board.active == null && manual == null),
                    color = Paper,
                    fontFamily = serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 104.sp,
                    lineHeight = 108.sp,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(headline, color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    if (semantic >= Semantic.CAUTION) {
                        Text(
                            if (semantic >= Semantic.DANGER) "Danger" else "Caution",
                            color = if (semantic >= Semantic.DANGER) Danger else Caution,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                if (detail.isNotBlank()) {
                    Text(detail, color = Ash, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(20.dp))
                Strip(phase = phase, percent = percent, milestones = milestones)
            }

            Spacer(Modifier.height(56.dp))
            SectionLabel("Access")
            Group {
                SettingRow(
                    title = "Notification access",
                    value = if (listenerOn) "On" else "Allow",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                Hairline()
                SettingRow(
                    title = "Glyph",
                    value = linkValue(link),
                    onClick = null,
                )
            }
            if (!listenerOn) {
                Footnote("Allow notification access so Grab, Maps, and other Live Updates can drive the lights. Text stays on this phone.")
            }
            if (link is GlyphLink.Failed) {
                Footnote((link as GlyphLink.Failed).reason)
            }

            Spacer(Modifier.height(32.dp))
            SectionLabel("Live Updates")
            Group {
                if (board.tracks.isEmpty()) {
                    Text(
                        "Nothing matched yet",
                        color = Ash,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
                board.tracks.forEachIndexed { index, track ->
                    if (index > 0) Hairline()
                    val label = appLabel(context, track.packageName)
                    val title = track.title.ifBlank { label }
                    TrackRow(
                        title = title,
                        subtitle = if (title.equals(label, ignoreCase = true)) "" else label,
                        value = phaseLabel(track),
                        selected = track.key == board.activeKey && manual == null,
                        onUse = { app.repository.pin(track.key) },
                        onHide = { app.repository.ignore(track.packageName) },
                    )
                }
            }

            if (board.suppressed.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                SectionLabel("Off")
                Group {
                    board.suppressed.forEachIndexed { index, item ->
                        if (index > 0) Hairline()
                        val label = appLabel(context, item.packageName)
                        val title = item.title.ifBlank { label }
                        SuppressedRow(
                            title = title,
                            subtitle = if (title.equals(label, ignoreCase = true)) "" else label,
                            reason = item.reason,
                            onAllow = {
                                when (item.reason) {
                                    SuppressReason.UserHidden -> app.repository.unhide(item.packageName)
                                    SuppressReason.DefaultDisabled -> app.repository.allow(item.packageName)
                                }
                            },
                        )
                    }
                }
                Footnote("Uber, Zomato, Google Maps, and Google Calendar start off because Nothing OS already mirrors them on the Glyph. Anything else with a real progress signal turns on by itself.")
            }

            Spacer(Modifier.height(32.dp))
            SectionLabel("Options")
            Group {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Also follow progress bars", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Ongoing progress that is not a Live Update. Downloads and the Play Store stay ignored.",
                            color = Ash,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = includeStandard,
                        onCheckedChange = app.repository::setIncludeStandard,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Paper,
                            uncheckedThumbColor = Paper,
                            uncheckedTrackColor = Color(0xFF3A3A3A),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Follow anything ongoing", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Once an ongoing notification with no percent or ETA is seen to actually change, follow it too. Catches uploads and transfers this app can't otherwise read. A status icon that never changes (Bluetooth, VPN, Bedtime Mode) stays off the Glyph.",
                            color = Ash,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(
                        checked = matchAnyOngoing,
                        onCheckedChange = app.repository::setMatchAnyOngoing,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Paper,
                            uncheckedThumbColor = Paper,
                            uncheckedTrackColor = Color(0xFF3A3A3A),
                            uncheckedBorderColor = Color.Transparent,
                            checkedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Hairline()
                SettingRow(
                    title = "Preview a ride",
                    value = if (previewOpen) "Hide" else "Show",
                    onClick = { previewOpen = !previewOpen },
                )
                if (previewOpen) {
                    Hairline()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            QuietAction("Advance") { SampleLiveUpdate.advance(context) }
                            QuietAction("Clear") {
                                SampleLiveUpdate.cancel(context)
                                app.repository.setManual(null)
                            }
                        }
                        Slider(
                            value = slider,
                            onValueChange = {
                                slider = it
                                app.repository.setManual(ManualGlyph(GlyphPhase.Progress, it.toInt()))
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Paper,
                                activeTrackColor = Paper,
                                inactiveTrackColor = Hairline,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            QuietAction("Searching") {
                                app.repository.setManual(ManualGlyph(GlyphPhase.Indeterminate, slider.toInt()))
                            }
                            QuietAction("Follow the shade") { app.repository.setManual(null) }
                        }
                    }
                }
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 32.dp)) {
                Text("Angelo Laus", color = Paper, fontSize = 14.sp)
                Text("hello@gelolaus.com", color = Ash, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                Text(
                    "Version $version  ·  Notification text stays on this phone.",
                    color = Ash,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Ash,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        color = Ash,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Group),
        content = { content() },
    )
}

@Composable
private fun Hairline() {
    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = Ash, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun TrackRow(
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onUse: () -> Unit,
    onHide: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Ash, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(value, color = Ash, fontSize = 15.sp)
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!selected) QuietAction("Use", onUse) else Text("Showing", color = Ash, fontSize = 14.sp)
            QuietAction("Hide", onHide)
        }
    }
}

@Composable
private fun SuppressedRow(
    title: String,
    subtitle: String,
    reason: SuppressReason,
    onAllow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Ash, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                when (reason) {
                    SuppressReason.DefaultDisabled -> "Nothing OS already shows this"
                    SuppressReason.UserHidden -> "Hidden by you"
                },
                color = Ash,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        QuietAction(
            when (reason) {
                SuppressReason.DefaultDisabled -> "Allow"
                SuppressReason.UserHidden -> "Unhide"
            },
            onAllow,
        )
    }
}

@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Paper,
        fontSize = 14.sp,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Strip(phase: GlyphPhase?, percent: Int, milestones: List<Float> = emptyList()) {
    val fraction = when (phase) {
        null -> 0f
        GlyphPhase.Indeterminate -> 1f
        else -> percent.coerceIn(0, 100) / 100f
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Hairline),
        )
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(if (phase == GlyphPhase.Indeterminate) Color(0xFF8E8E93) else Paper),
            )
        }
        milestones.forEach { position ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * position - 0.5.dp)
                    .width(1.dp)
                    .height(6.dp)
                    .background(Ink),
            )
        }
    }
}

private fun figure(phase: GlyphPhase?, percent: Int, empty: Boolean): String = when {
    empty -> "—"
    phase == GlyphPhase.Indeterminate -> "··"
    else -> percent.coerceIn(0, 100).toString()
}

private fun phaseLabel(track: GlyphTrack): String = when (track.phase) {
    // "Searching" only fit the rideshare case this was written for; a timer, a call, or a
    // status notification caught by "Follow anything ongoing" isn't searching for anything.
    GlyphPhase.Indeterminate -> "Active"
    GlyphPhase.Complete -> "Done"
    GlyphPhase.Progress -> "${track.percent}%"
}

private fun appLabel(context: android.content.Context, packageName: String): String = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
} catch (_: Exception) {
    packageName.substringAfterLast('.')
}

private fun listenerEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, LiveUpdateListenerService::class.java)
    return flat.contains(component.flattenToString()) || flat.contains(component.flattenToShortString())
}

private fun linkValue(link: GlyphLink): String = when (link) {
    GlyphLink.PreviewOnly -> "Preview"
    GlyphLink.Connecting -> "Connecting"
    is GlyphLink.Ready -> "Ready"
    is GlyphLink.Failed -> "Unavailable"
}
