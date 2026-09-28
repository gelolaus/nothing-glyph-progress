package dev.gelo.glyphprogress.session

import android.content.Context
import dev.gelo.glyphprogress.ManualGlyph
import dev.gelo.glyphprogress.core.Board
import dev.gelo.glyphprogress.core.LiveUpdateEngine
import dev.gelo.glyphprogress.core.NotificationSnapshot
import dev.gelo.glyphprogress.core.Policy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProgressRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val snapshots = MutableStateFlow<List<NotificationSnapshot>>(emptyList())
    private val pinned = MutableStateFlow(prefs.getString(KEY_PIN, null))
    private val includeStandard = MutableStateFlow(prefs.getBoolean(KEY_STANDARD, true))
    private val matchAnyOngoing = MutableStateFlow(prefs.getBoolean(KEY_ANY_ONGOING, false))
    private val ignored = MutableStateFlow(prefs.getStringSet(KEY_IGNORED, emptySet()).orEmpty())
    private var baselines: Map<String, Int> = emptyMap()
    private var lastPercents: Map<String, Int> = emptyMap()

    private val _board = MutableStateFlow(Board())
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _manual = MutableStateFlow<ManualGlyph?>(null)
    val manual: StateFlow<ManualGlyph?> = _manual.asStateFlow()

    val includeStandardProgress: StateFlow<Boolean> = includeStandard.asStateFlow()
    val matchAnyOngoingProgress: StateFlow<Boolean> = matchAnyOngoing.asStateFlow()

    fun replaceAll(items: List<NotificationSnapshot>) {
        snapshots.value = items.filterNot { it.isInternal }
        recompute()
    }

    fun upsert(item: NotificationSnapshot) {
        if (item.isInternal) return
        snapshots.value = snapshots.value.filterNot { it.key == item.key } + item
        recompute()
    }

    fun remove(key: String) {
        snapshots.value = snapshots.value.filterNot { it.key == key }
        recompute()
    }

    fun pin(key: String?) {
        pinned.value = key
        prefs.edit().putString(KEY_PIN, key).apply()
        recompute()
    }

    /** Cycles the pin to the next candidate, for the Quick Settings tile. Wraps around. */
    fun pinNext() {
        val tracks = board.value.tracks
        if (tracks.isEmpty()) {
            pin(null)
            return
        }
        val currentIndex = tracks.indexOfFirst { it.key == pinned.value }
        val next = if (currentIndex == -1 || currentIndex == tracks.lastIndex) tracks.first() else tracks[currentIndex + 1]
        pin(next.key)
    }

    fun ignore(packageName: String) {
        val next = ignored.value + packageName
        ignored.value = next
        prefs.edit().putStringSet(KEY_IGNORED, next).apply()
        if (pinned.value != null && snapshots.value.any { it.key == pinned.value && it.packageName == packageName }) {
            pin(null)
        }
        recompute()
    }

    fun clearIgnored() {
        ignored.value = emptySet()
        prefs.edit().remove(KEY_IGNORED).apply()
        recompute()
    }

    fun setIncludeStandard(enabled: Boolean) {
        includeStandard.value = enabled
        prefs.edit().putBoolean(KEY_STANDARD, enabled).apply()
        recompute()
    }

    fun setMatchAnyOngoing(enabled: Boolean) {
        matchAnyOngoing.value = enabled
        prefs.edit().putBoolean(KEY_ANY_ONGOING, enabled).apply()
        recompute()
    }

    fun setManual(manual: ManualGlyph?) {
        _manual.value = manual
    }

    fun ignoredPackages(): Set<String> = ignored.value

    private fun recompute() {
        val result = LiveUpdateEngine.board(
            snapshots = snapshots.value,
            pinnedKey = pinned.value,
            policy = Policy(
                includeStandardProgress = includeStandard.value,
                matchAnyOngoing = matchAnyOngoing.value,
                ignoredPackages = ignored.value,
            ),
            baselines = baselines,
            lastPercents = lastPercents,
            nowMillis = System.currentTimeMillis(),
        )
        baselines = result.baselines
        lastPercents = result.lastPercents
        _board.value = result.board
    }

    private companion object {
        const val PREFS = "glyph_progress"
        const val KEY_PIN = "pinned_key"
        const val KEY_STANDARD = "include_standard"
        const val KEY_ANY_ONGOING = "match_any_ongoing"
        const val KEY_IGNORED = "ignored_packages"
    }
}
