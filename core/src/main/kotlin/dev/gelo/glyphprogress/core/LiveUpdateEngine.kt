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
    val isOngoing: Boolean = false,
    val postTimeMillis: Long = 0L,
    val promotedOngoing: Boolean = false,
    val requestedPromotedOngoing: Boolean = false,
    val template: String = "",
    val progress: Int? = null,
    val progressMax: Int? = null,
    val indeterminate: Boolean = false,
    val segmentLengths: List<Int> = emptyList(),
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
)

data class Policy(
    val includeStandardProgress: Boolean = true,
    val ignoredPackages: Set<String> = emptySet(),
    val fallbackBlockedPackages: Set<String> = DEFAULT_FALLBACK_BLOCKLIST,
)

val DEFAULT_FALLBACK_BLOCKLIST: Set<String> = setOf(
    "com.android.providers.downloads",
    "com.android.vending",
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
)

data class Board(
    val tracks: List<GlyphTrack> = emptyList(),
    val activeKey: String? = null,
) {
    val active: GlyphTrack? get() = tracks.firstOrNull { it.key == activeKey }
}

data class BoardResult(
    val board: Board,
    val baselines: Map<String, Int>,
    val lastPercents: Map<String, Int>,
)

object LiveUpdateEngine {
    fun board(
        snapshots: List<NotificationSnapshot>,
        pinnedKey: String?,
        policy: Policy = Policy(),
        baselines: Map<String, Int> = emptyMap(),
        lastPercents: Map<String, Int> = emptyMap(),
        nowMillis: Long,
    ): BoardResult {
        val nextBaselines = linkedMapOf<String, Int>()
        val nextLast = linkedMapOf<String, Int>()
        val tracks = ArrayList<GlyphTrack>()
        for (snapshot in snapshots) {
            if (!isCandidate(snapshot, policy)) continue
            val origin = originOf(snapshot)
            val resolved = resolve(
                snapshot = snapshot,
                baseline = baselines[snapshot.key],
                lastPercent = lastPercents[snapshot.key] ?: 0,
                nowMillis = nowMillis,
            )
            resolved.baselineMinutes?.let { nextBaselines[snapshot.key] = it }
            nextLast[snapshot.key] = resolved.percent
            tracks += GlyphTrack(
                key = snapshot.key,
                packageName = snapshot.packageName,
                title = snapshot.title.ifBlank { snapshot.shortText.ifBlank { snapshot.packageName } },
                detail = snapshot.text.ifBlank { snapshot.shortText.ifBlank { snapshot.subText } },
                phase = resolved.phase,
                percent = resolved.percent.coerceIn(0, 100),
                postTimeMillis = snapshot.postTimeMillis,
                confidence = confidenceOf(origin),
                origin = origin,
            )
        }
        val ordered = tracks.sortedWith(
            compareByDescending<GlyphTrack> { it.confidence }.thenByDescending { it.postTimeMillis },
        )
        val active = pinnedKey?.let { key -> ordered.firstOrNull { it.key == key } } ?: ordered.firstOrNull()
        return BoardResult(
            board = Board(tracks = ordered, activeKey = active?.key),
            baselines = nextBaselines,
            lastPercents = nextLast,
        )
    }

    fun isCandidate(snapshot: NotificationSnapshot, policy: Policy): Boolean {
        if (snapshot.isInternal || snapshot.isGroupSummary || snapshot.hasMediaSession) return false
        if (snapshot.template.contains("MediaStyle", ignoreCase = true)) return false
        if (snapshot.packageName in policy.ignoredPackages) return false
        if (snapshot.promotedOngoing) return true
        if (isProgressTemplate(snapshot) || isMetricTemplate(snapshot)) return true
        if (snapshot.requestedPromotedOngoing && snapshot.isOngoing && !snapshot.isColorized) return true
        if (!policy.includeStandardProgress || !snapshot.isOngoing || snapshot.isColorized) return false
        if (snapshot.packageName in policy.fallbackBlockedPackages) return false
        return hasStandardSignal(snapshot)
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
        val haystack = listOf(snapshot.shortText, snapshot.title, snapshot.text).joinToString(" ")
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

    private fun textPercent(snapshot: NotificationSnapshot): Int? {
        for (field in listOf(snapshot.shortText, snapshot.title, snapshot.text)) {
            val match = PERCENT_TEXT.find(field) ?: continue
            return match.groupValues[1].toInt().coerceIn(0, 100)
        }
        return null
    }

    private fun findMinutes(snapshot: NotificationSnapshot, nowMillis: Long): Int? {
        for (field in listOf(snapshot.shortText, snapshot.title, snapshot.text)) {
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
        if (textPercent(snapshot) != null) return true
        if (textMinutes(snapshot.shortText) != null ||
            textMinutes(snapshot.title) != null ||
            textMinutes(snapshot.text) != null
        ) {
            return true
        }
        val haystack = listOf(snapshot.shortText, snapshot.title, snapshot.text).joinToString(" ")
        return COMPLETE_PHRASE.containsMatchIn(haystack) || INDETERMINATE_PHRASE.containsMatchIn(haystack)
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
