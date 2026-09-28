package dev.gelo.glyphprogress.core

/**
 * A notification reduced to the fields a listener can actually read.
 * Progress is completion (0 empty, 100 done), matching Android Live Updates
 * and GlyphManager.displayProgress.
 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val title: String = "",
    val text: String = "",
    val subText: String = "",
    val shortText: String = "",
    val bigText: String = "",
    val infoText: String = "",
    val textLines: List<String> = emptyList(),
    val category: String = "",
    val isOngoing: Boolean = false,
    val postTimeMillis: Long = 0L,
    val promotedOngoing: Boolean = false,
    val requestedPromotedOngoing: Boolean = false,
    val template: String = "",
    val progress: Int? = null,
    val progressMax: Int? = null,
    val indeterminate: Boolean = false,
    val segmentLengths: List<Int> = emptyList(),
    val progressPoints: List<Int> = emptyList(),
    val semanticStyles: List<Int> = emptyList(),
    val metricLabels: List<String> = emptyList(),
    val hasMediaSession: Boolean = false,
    val isGroupSummary: Boolean = false,
    val isColorized: Boolean = false,
    val isInternal: Boolean = false,
    val whenMillis: Long = 0L,
    val showWhen: Boolean = true,
    val usesChronometer: Boolean = false,
    val chronometerCountdown: Boolean = false,
)

enum class GlyphPhase {
    Progress,
    Indeterminate,
    Complete,
}

enum class TrackOrigin {
    Promoted,
    ProgressStyle,
    MetricStyle,
    Standard,
}

/** Android's `Notification.SEMANTIC_STYLE_*` ints (API 37), 0 when a notification never set one. */
object Semantic {
    const val UNSPECIFIED = 0
    const val INFO = 1
    const val SAFE = 2
    const val CAUTION = 3
    const val DANGER = 4
}

/** Why a genuine live-update signal isn't currently lighting the Glyph. */
enum class SuppressReason {
    /** The user tapped "Hide" on this app. */
    UserHidden,

    /** One of the apps Nothing OS already mirrors natively (off by default, not hidden). */
    DefaultDisabled,
}

data class GlyphTrack(
    val key: String,
    val packageName: String,
    val title: String,
    val detail: String,
    val phase: GlyphPhase,
    val percent: Int,
    val postTimeMillis: Long,
    val confidence: Int,
    val origin: TrackOrigin,
    val semantic: Int = Semantic.UNSPECIFIED,
    val milestoneFractions: List<Float> = emptyList(),
)

/** A genuine live-update signal that isn't shown, and why. */
data class SuppressedTrack(
    val key: String,
    val packageName: String,
    val title: String,
    val reason: SuppressReason,
)

data class Policy(
    val includeStandardProgress: Boolean = true,
    val matchAnyOngoing: Boolean = false,
    val ignoredPackages: Set<String> = emptySet(),
    val fallbackBlockedPackages: Set<String> = DEFAULT_FALLBACK_BLOCKLIST,
    val defaultDisabledPackages: Set<String> = DEFAULT_DISABLED_PACKAGES,
    val allowedOverridePackages: Set<String> = emptySet(),
)

/** Never a candidate, and never worth surfacing even as suppressed: pure system noise. */
val DEFAULT_FALLBACK_BLOCKLIST: Set<String> = setOf(
    "com.android.providers.downloads",
    "com.android.vending",
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
)

/**
 * Nothing OS already mirrors these natively on the Glyph (the original Glyph Progress
 * partner list, later folded into the Android 16 Live Update path). Off by default so this
 * app doesn't fight the system for the same lights; a user who wants this app to drive them
 * instead can allow any of these individually.
 */
val DEFAULT_DISABLED_PACKAGES: Set<String> = setOf(
    "com.ubercab",
    "com.application.zomato",
    "com.google.android.apps.maps",
    "com.google.android.calendar",
)

data class Board(
    val tracks: List<GlyphTrack> = emptyList(),
    val activeKey: String? = null,
    val suppressed: List<SuppressedTrack> = emptyList(),
) {
    val active: GlyphTrack? get() = tracks.firstOrNull { it.key == activeKey }
}

data class BoardResult(
    val board: Board,
    val baselines: Map<String, Int>,
    val lastPercents: Map<String, Int>,
    val contentSignatures: Map<String, String> = emptyMap(),
)

object LiveUpdateEngine {
    fun board(
        snapshots: List<NotificationSnapshot>,
        pinnedKey: String?,
        policy: Policy = Policy(),
        baselines: Map<String, Int> = emptyMap(),
        lastPercents: Map<String, Int> = emptyMap(),
        contentSignatures: Map<String, String> = emptyMap(),
        nowMillis: Long,
    ): BoardResult {
        val nextBaselines = linkedMapOf<String, Int>()
        val nextLast = linkedMapOf<String, Int>()
        val nextSignatures = linkedMapOf<String, String>()
        val tracks = ArrayList<GlyphTrack>()
        val suppressed = ArrayList<SuppressedTrack>()
        for (snapshot in snapshots) {
            // Tracked for every ongoing notification, candidate or not, so a status icon
            // that later starts actually changing can be picked up under matchAnyOngoing.
            val signature = contentSignature(snapshot)
            val hasMoved = contentSignatures[snapshot.key]?.let { it != signature } ?: false
            nextSignatures[snapshot.key] = signature
            // No genuine progress/movement signal at all: fully invisible, same as before.
            // This is the "don't just put every app notification here" gate.
            if (!isCandidate(snapshot, policy, hasMoved)) continue

            val suppressReason = when {
                snapshot.packageName in policy.ignoredPackages -> SuppressReason.UserHidden
                snapshot.packageName in policy.defaultDisabledPackages &&
                    snapshot.packageName !in policy.allowedOverridePackages -> SuppressReason.DefaultDisabled
                else -> null
            }
            if (suppressReason != null) {
                suppressed += SuppressedTrack(
                    key = snapshot.key,
                    packageName = snapshot.packageName,
                    title = snapshot.title.ifBlank { snapshot.shortText.ifBlank { snapshot.packageName } },
                    reason = suppressReason,
                )
                continue
            }

            val origin = originOf(snapshot)
            val resolved = resolve(
                snapshot = snapshot,
                baseline = baselines[snapshot.key],
                lastPercent = lastPercents[snapshot.key] ?: 0,
                nowMillis = nowMillis,
            )
            resolved.baselineMinutes?.let { nextBaselines[snapshot.key] = it }
            nextLast[snapshot.key] = resolved.percent
            val detail = snapshot.text.ifBlank {
                snapshot.shortText.ifBlank {
                    snapshot.subText.ifBlank { snapshot.metricLabels.joinToString(" · ") }
                }
            }
            tracks += GlyphTrack(
                key = snapshot.key,
                packageName = snapshot.packageName,
                title = snapshot.title.ifBlank { snapshot.shortText.ifBlank { snapshot.packageName } },
                detail = detail,
                phase = resolved.phase,
                percent = resolved.percent.coerceIn(0, 100),
                postTimeMillis = snapshot.postTimeMillis,
                confidence = confidenceOf(origin),
                origin = origin,
                semantic = snapshot.semanticStyles.maxOrNull() ?: Semantic.UNSPECIFIED,
                milestoneFractions = milestoneFractions(snapshot),
            )
        }
        val ordered = tracks.sortedWith(
            compareByDescending<GlyphTrack> { it.confidence }.thenByDescending { it.postTimeMillis },
        )
        val active = pinnedKey?.let { key -> ordered.firstOrNull { it.key == key } } ?: ordered.firstOrNull()
        return BoardResult(
            board = Board(tracks = ordered, activeKey = active?.key, suppressed = suppressed),
            baselines = nextBaselines,
            lastPercents = nextLast,
            contentSignatures = nextSignatures,
        )
    }

    /**
     * Does this notification carry a genuine live-update / progress / movement signal at
     * all, independent of whether the user or the default policy currently hides it? This
     * is the detection gate: hiding is decided afterward, in [board].
     */
    fun isCandidate(snapshot: NotificationSnapshot, policy: Policy, hasMoved: Boolean = false): Boolean {
        if (snapshot.isInternal || snapshot.isGroupSummary || snapshot.hasMediaSession) return false
        if (snapshot.template.contains("MediaStyle", ignoreCase = true)) return false
        // The system's own promotion decision, or a real ProgressStyle/MetricStyle
        // template, outranks our package blocklist below - if Android decided this is a
        // genuine Live Update, second-guessing it with a heuristic denylist is wrong.
        if (snapshot.promotedOngoing) return true
        if (isProgressTemplate(snapshot) || isMetricTemplate(snapshot)) return true
        if (snapshot.requestedPromotedOngoing && snapshot.isOngoing && !snapshot.isColorized) return true
        if (!policy.includeStandardProgress || !snapshot.isOngoing) return false
        if (snapshot.packageName in policy.fallbackBlockedPackages) return false
        // Colorized is a real platform requirement for *system* promotion, but it says
        // nothing about whether a plain ongoing notification is worth showing on the
        // Glyph, so the generic tier below no longer excludes it.
        if (hasStandardSignal(snapshot)) return true
        // "Follow anything ongoing" is meant for a moving progress bar this app can't
        // otherwise parse, not for the many *static* ongoing notifications Android uses
        // for persistent status (Bluetooth, VPN, Bedtime Mode, a paired device's connection
        // state, ...). Those never change their own content, so only accept a signal-less
        // ongoing notification once it has actually been observed to change - and never a
        // notification Android itself categorizes as plain device/contextual status.
        return policy.matchAnyOngoing && hasMoved && snapshot.category != CATEGORY_STATUS
    }

    private fun resolve(
        snapshot: NotificationSnapshot,
        baseline: Int?,
        lastPercent: Int,
        nowMillis: Long,
    ): Resolved {
        if (snapshot.indeterminate) {
            return Resolved(GlyphPhase.Indeterminate, lastPercent.coerceIn(0, 100), baseline)
        }
        numericPercent(snapshot)?.let { percent ->
            val phase = if (percent >= 100) GlyphPhase.Complete else GlyphPhase.Progress
            return Resolved(phase, percent, null)
        }
        textPercent(snapshot)?.let { percent ->
            val phase = if (percent >= 100) GlyphPhase.Complete else GlyphPhase.Progress
            return Resolved(phase, percent, null)
        }
        findMinutes(snapshot, nowMillis)?.let { minutes ->
            val base = maxOf(baseline ?: minutes, minutes)
            if (minutes <= 0 || base <= 0) {
                return Resolved(GlyphPhase.Complete, 100, base)
            }
            val percent = kotlin.math.round(100.0 * (base - minutes) / base).toInt().coerceIn(0, 100)
            val phase = if (percent >= 100) GlyphPhase.Complete else GlyphPhase.Progress
            return Resolved(phase, percent, base)
        }
        val haystack = textFields(snapshot).joinToString(" ")
        if (COMPLETE_PHRASE.containsMatchIn(haystack)) {
            return Resolved(GlyphPhase.Complete, 100, null)
        }
        if (INDETERMINATE_PHRASE.containsMatchIn(haystack)) {
            return Resolved(GlyphPhase.Indeterminate, lastPercent.coerceIn(0, 100), baseline)
        }
        return Resolved(GlyphPhase.Indeterminate, lastPercent.coerceIn(0, 100), baseline)
    }

    private fun numericPercent(snapshot: NotificationSnapshot): Int? {
        val progress = snapshot.progress ?: return null
        val segmentMax = snapshot.segmentLengths.filter { it > 0 }.fold(0L) { acc, length -> acc + length }
        val max = when {
            segmentMax > 0L -> segmentMax
            else -> (snapshot.progressMax ?: 100).toLong()
        }
        if (max <= 0L) return null
        if (progress.toLong() >= max) return 100
        return kotlin.math.round(100.0 * progress / max).toInt().coerceIn(0, 100)
    }

    /** Positions of `android.progressPoints` as fractions along the bar, for tick marks. */
    private fun milestoneFractions(snapshot: NotificationSnapshot): List<Float> {
        if (snapshot.progressPoints.isEmpty()) return emptyList()
        val segmentMax = snapshot.segmentLengths.filter { it > 0 }.fold(0L) { acc, length -> acc + length }
        val max = when {
            segmentMax > 0L -> segmentMax
            (snapshot.progressMax ?: 0) > 0 -> (snapshot.progressMax ?: 0).toLong()
            else -> return emptyList()
        }
        // The platform never draws a point at 0 or at max; match that here.
        return snapshot.progressPoints
            .filter { it > 0 && it < max }
            .map { (it.toFloat() / max.toFloat()).coerceIn(0f, 1f) }
            .distinct()
            .sorted()
    }

    private fun textPercent(snapshot: NotificationSnapshot): Int? {
        for (field in textFields(snapshot)) {
            val match = PERCENT_TEXT.find(field) ?: continue
            return match.groupValues[1].toInt().coerceIn(0, 100)
        }
        return null
    }

    private fun findMinutes(snapshot: NotificationSnapshot, nowMillis: Long): Int? {
        for (field in textFields(snapshot)) {
            textMinutes(field)?.let { return it }
        }
        if (snapshot.usesChronometer && snapshot.chronometerCountdown && snapshot.whenMillis > nowMillis) {
            val delta = snapshot.whenMillis - nowMillis
            return ((delta + 30_000L) / 60_000L).toInt()
        }
        if (snapshot.showWhen && !snapshot.usesChronometer) {
            val delta = snapshot.whenMillis - nowMillis
            if (delta >= 120_000L) return (delta / 60_000L).toInt()
        }
        return null
    }

    private fun textMinutes(field: String): Int? {
        if (field.isBlank()) return null
        HOUR_MIN.find(field)?.let { match ->
            return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
        }
        HOUR.find(field)?.let { match ->
            return match.groupValues[1].toInt() * 60
        }
        MINUTE.find(field)?.let { match ->
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            return raw.toInt()
        }
        return null
    }

    private fun hasStandardSignal(snapshot: NotificationSnapshot): Boolean {
        if (snapshot.indeterminate) return true
        if (snapshot.progress != null) return true
        if (snapshot.segmentLengths.isNotEmpty()) return true
        // A running chronometer (call duration, a recording, a workout) is inherently
        // "moving" even with no percent to compute - Android itself is animating it.
        if (snapshot.usesChronometer) return true
        if (snapshot.category in PROGRESS_LIKE_CATEGORIES) return true
        if (textPercent(snapshot) != null) return true
        if (textFields(snapshot).any { textMinutes(it) != null }) return true
        val haystack = textFields(snapshot).joinToString(" ")
        return COMPLETE_PHRASE.containsMatchIn(haystack) || INDETERMINATE_PHRASE.containsMatchIn(haystack)
    }

    /** Every free-text field worth scanning, highest-priority first. */
    private fun textFields(snapshot: NotificationSnapshot): List<String> =
        listOf(snapshot.shortText, snapshot.title, snapshot.text, snapshot.bigText, snapshot.infoText) +
            snapshot.textLines

    /** A cheap fingerprint of everything that would make a notification look "moved". */
    private fun contentSignature(snapshot: NotificationSnapshot): String = buildString {
        append(snapshot.title).append('\u0001')
        append(snapshot.text).append('\u0001')
        append(snapshot.subText).append('\u0001')
        append(snapshot.shortText).append('\u0001')
        append(snapshot.bigText).append('\u0001')
        append(snapshot.infoText).append('\u0001')
        append(snapshot.textLines.joinToString("\u0002")).append('\u0001')
        append(snapshot.progress).append('\u0001')
        append(snapshot.indeterminate)
    }

    private fun originOf(snapshot: NotificationSnapshot): TrackOrigin = when {
        snapshot.promotedOngoing || snapshot.requestedPromotedOngoing -> TrackOrigin.Promoted
        isProgressTemplate(snapshot) -> TrackOrigin.ProgressStyle
        isMetricTemplate(snapshot) -> TrackOrigin.MetricStyle
        else -> TrackOrigin.Standard
    }

    private fun confidenceOf(origin: TrackOrigin): Int = when (origin) {
        TrackOrigin.Promoted -> 100
        TrackOrigin.ProgressStyle -> 80
        TrackOrigin.MetricStyle -> 70
        TrackOrigin.Standard -> 50
    }

    private fun isProgressTemplate(snapshot: NotificationSnapshot): Boolean =
        snapshot.template.contains("ProgressStyle")

    private fun isMetricTemplate(snapshot: NotificationSnapshot): Boolean =
        snapshot.template.contains("MetricStyle")

    private data class Resolved(
        val phase: GlyphPhase,
        val percent: Int,
        val baselineMinutes: Int?,
    )

    // Android's Notification.CATEGORY_* string values (reference:
    // developer.android.com/reference/android/app/Notification). Kept as plain strings
    // because the core module has no Android dependency.
    private const val CATEGORY_STATUS = "status"
    private val PROGRESS_LIKE_CATEGORIES = setOf(
        "progress",
        "navigation",
        "call",
        "workout",
        "stopwatch",
        "alarm",
        "location_sharing",
    )

    private val PERCENT_TEXT = Regex("""(?<!\d)(\d{1,3})\s*%""")
    private val HOUR_MIN = Regex(
        """\b(\d+)\s*h(?:ours?|rs?)?\s*(\d+)\s*m(?:in(?:ute)?s?)?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val HOUR = Regex("""\b(\d+)\s*(?:hours|hour|hrs|hr)\b""", RegexOption.IGNORE_CASE)
    private val MINUTE = Regex(
        """\b(\d+)\s*(?:minutes|minute|mins|min)\b|\b(\d+)min\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COMPLETE_PHRASE = Regex(
        """\b(arriving now|dropped off|arrived|delivered|completed|complete|arriving|here)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val INDETERMINATE_PHRASE = Regex(
        """\b(picking up|looking for|finding a driver|being prepared|preparing|searching|confirmed|waiting|matching|on the way|rerouting|thinking)\b""",
        RegexOption.IGNORE_CASE,
    )
}
