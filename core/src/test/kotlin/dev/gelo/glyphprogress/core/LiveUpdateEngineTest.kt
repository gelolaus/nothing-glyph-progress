package dev.gelo.glyphprogress.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveUpdateEngineTest {
    private val now = 1_700_000_000_000L

    @Test
    fun progressStyleSegmentsBecomeCompletionPercent() {
        val track = only(
            snap(
                template = "android.app.Notification\$ProgressStyle",
                progress = 456,
                segmentLengths = listOf(41, 552, 253, 94),
                isOngoing = true,
            ),
        )
        assertEquals(GlyphPhase.Progress, track.phase)
        assertEquals(49, track.percent)
        assertEquals(TrackOrigin.ProgressStyle, track.origin)
    }

    @Test
    fun zeroProgressStaysAnEmptyBar() {
        val track = only(snap(isOngoing = true, progress = 0, progressMax = 100))
        assertEquals(GlyphPhase.Progress, track.phase)
        assertEquals(0, track.percent)
    }

    @Test
    fun fullProgressIsComplete() {
        val track = only(snap(isOngoing = true, progress = 100, progressMax = 100, promotedOngoing = true))
        assertEquals(GlyphPhase.Complete, track.phase)
        assertEquals(100, track.percent)
    }

    @Test
    fun indeterminateIgnoresNumericProgressAndKeepsLastPercent() {
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "ride", isOngoing = true, progress = 40, progressMax = 100)),
            pinnedKey = null,
            nowMillis = now,
        )
        val second = LiveUpdateEngine.board(
            listOf(snap(key = "ride", isOngoing = true, indeterminate = true, progress = 90, progressMax = 100, text = "5 min away")),
            pinnedKey = null,
            lastPercents = first.lastPercents,
            nowMillis = now,
        )
        val track = second.board.active!!
        assertEquals(GlyphPhase.Indeterminate, track.phase)
        assertEquals(40, track.percent)
    }

    @Test
    fun explicitPercentInTextWinsOverABareTitle() {
        val track = only(snap(isOngoing = true, title = "Order", shortText = "75%"))
        assertEquals(75, track.percent)
        assertEquals(GlyphPhase.Progress, track.phase)
    }

    @Test
    fun etaWindowGrowsTheBarAsMinutesFall() {
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "grab", isOngoing = true, text = "Driver is 5 min away")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertEquals(0, first.board.active!!.percent)
        val second = LiveUpdateEngine.board(
            listOf(snap(key = "grab", isOngoing = true, text = "Driver is 2 min away")),
            pinnedKey = null,
            baselines = first.baselines,
            nowMillis = now,
        )
        assertEquals(GlyphPhase.Progress, second.board.active!!.phase)
        assertEquals(60, second.board.active!!.percent)
    }

    @Test
    fun laterLongerEtaResetsAgainstTheNewBaseline() {
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "grab", isOngoing = true, text = "5 min away")),
            pinnedKey = null,
            nowMillis = now,
        )
        val second = LiveUpdateEngine.board(
            listOf(snap(key = "grab", isOngoing = true, text = "8 min away")),
            pinnedKey = null,
            baselines = first.baselines,
            nowMillis = now,
        )
        assertEquals(0, second.board.active!!.percent)
        assertEquals(8, second.baselines["grab"])
    }

    @Test
    fun metersAreNotMinutes() {
        val result = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, text = "In 200 m")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(result.board.active)
    }

    @Test
    fun compactChipMinutesCount() {
        val result = LiveUpdateEngine.board(
            listOf(snap(key = "nav", isOngoing = true, shortText = "5min", title = "Navigation")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertEquals(0, result.board.active!!.percent)
        assertEquals(5, result.baselines["nav"])
    }

    @Test
    fun arrivingWithAMinuteCountUsesTheEta() {
        val track = only(snap(isOngoing = true, title = "Arriving in 3 min"))
        assertEquals(GlyphPhase.Progress, track.phase)
        assertEquals(0, track.percent)
    }

    @Test
    fun arrivingAloneIsComplete() {
        val track = only(snap(isOngoing = true, title = "Arriving", promotedOngoing = true))
        assertEquals(GlyphPhase.Complete, track.phase)
        assertEquals(100, track.percent)
    }

    @Test
    fun numericProgressBeatsEtaText() {
        val track = only(
            snap(
                isOngoing = true,
                progress = 20,
                progressMax = 100,
                text = "8 min away",
                template = "android.app.Notification\$ProgressStyle",
            ),
        )
        assertEquals(20, track.percent)
    }

    @Test
    fun mediaAndGroupSummariesAreIgnored() {
        val result = LiveUpdateEngine.board(
            listOf(
                snap(key = "media", isOngoing = true, progress = 10, progressMax = 100, hasMediaSession = true),
                snap(key = "group", isOngoing = true, promotedOngoing = true, isGroupSummary = true),
            ),
            pinnedKey = null,
            nowMillis = now,
        )
        assertTrue(result.board.tracks.isEmpty())
    }

    @Test
    fun plainOngoingNotificationIsNotALiveUpdate() {
        val result = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, title = "VPN connected")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(result.board.active)
    }

    @Test
    fun promotedNotificationWithoutNumbersBreathes() {
        val track = only(snap(promotedOngoing = true, isOngoing = true, title = "GrabCar"))
        assertEquals(GlyphPhase.Indeterminate, track.phase)
        assertEquals(TrackOrigin.Promoted, track.origin)
    }

    @Test
    fun downloadsAreIgnoredUnlessTheSystemPromotedThem() {
        val blocked = LiveUpdateEngine.board(
            listOf(
                snap(
                    key = "dl",
                    packageName = "com.android.providers.downloads",
                    isOngoing = true,
                    progress = 40,
                    progressMax = 100,
                ),
            ),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(blocked.board.active)
        val promoted = only(
            snap(
                packageName = "com.android.providers.downloads",
                isOngoing = true,
                progress = 40,
                progressMax = 100,
                promotedOngoing = true,
            ),
        )
        assertEquals(40, promoted.percent)
    }

    @Test
    fun standardProgressCanBeTurnedOff() {
        val result = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, progress = 30, progressMax = 100)),
            pinnedKey = null,
            policy = Policy(includeStandardProgress = false),
            nowMillis = now,
        )
        assertNull(result.board.active)
    }

    @Test
    fun progressStyleStillCountsWhenStandardFallbackIsOff() {
        val track = LiveUpdateEngine.board(
            listOf(
                snap(
                    isOngoing = true,
                    progress = 30,
                    progressMax = 100,
                    template = "android.app.Notification\$MetricStyle",
                ),
            ),
            pinnedKey = null,
            policy = Policy(includeStandardProgress = false),
            nowMillis = now,
        ).board.active!!
        assertEquals(TrackOrigin.MetricStyle, track.origin)
        assertEquals(30, track.percent)
    }

    @Test
    fun promotedTrackBeatsANewerStandardOne() {
        val board = LiveUpdateEngine.board(
            listOf(
                snap(key = "old", promotedOngoing = true, isOngoing = true, title = "Grab", postTimeMillis = 10),
                snap(key = "new", isOngoing = true, progress = 10, progressMax = 100, postTimeMillis = 99),
            ),
            pinnedKey = null,
            nowMillis = now,
        ).board
        assertEquals("old", board.activeKey)
        assertEquals(2, board.tracks.size)
    }

    @Test
    fun pinOverridesPriority() {
        val board = LiveUpdateEngine.board(
            listOf(
                snap(key = "promoted", promotedOngoing = true, isOngoing = true, title = "Grab", postTimeMillis = 50),
                snap(key = "maps", isOngoing = true, progress = 80, progressMax = 100, postTimeMillis = 10),
            ),
            pinnedKey = "maps",
            nowMillis = now,
        ).board
        assertEquals("maps", board.activeKey)
    }

    @Test
    fun ignoredPackageIsDropped() {
        val result = LiveUpdateEngine.board(
            listOf(snap(packageName = "com.grab", promotedOngoing = true, isOngoing = true, title = "Grab")),
            pinnedKey = null,
            policy = Policy(ignoredPackages = setOf("com.grab")),
            nowMillis = now,
        )
        assertNull(result.board.active)
    }

    @Test
    fun colorizedProgressIsNotALiveUpdateUnlessPromoted() {
        val hidden = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, isColorized = true, progress = 50, progressMax = 100)),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(hidden.board.active)
    }

    @Test
    fun onTheWayWithoutANumberIsIndeterminate() {
        val track = only(snap(isOngoing = true, text = "Driver is on the way"))
        assertEquals(GlyphPhase.Indeterminate, track.phase)
    }

    @Test
    fun futureWhenAtLeastTwoMinutesIsAnEta() {
        val result = LiveUpdateEngine.board(
            listOf(
                snap(
                    key = "cal",
                    isOngoing = true,
                    title = "Standup",
                    promotedOngoing = true,
                    whenMillis = now + 5 * 60_000L,
                ),
            ),
            pinnedKey = null,
            nowMillis = now,
        )
        assertEquals(0, result.board.active!!.percent)
        assertEquals(5, result.baselines["cal"])
    }

    private fun only(snapshot: NotificationSnapshot): GlyphTrack =
        LiveUpdateEngine.board(listOf(snapshot), pinnedKey = null, nowMillis = now).board.active!!

    private fun snap(
        key: String = "n",
        packageName: String = "com.example.ride",
        title: String = "",
        text: String = "",
        shortText: String = "",
        isOngoing: Boolean = false,
        postTimeMillis: Long = 1,
        promotedOngoing: Boolean = false,
        template: String = "",
        progress: Int? = null,
        progressMax: Int? = null,
        indeterminate: Boolean = false,
        segmentLengths: List<Int> = emptyList(),
        hasMediaSession: Boolean = false,
        isGroupSummary: Boolean = false,
        isColorized: Boolean = false,
        whenMillis: Long = 0L,
    ) = NotificationSnapshot(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        shortText = shortText,
        isOngoing = isOngoing,
        postTimeMillis = postTimeMillis,
        promotedOngoing = promotedOngoing,
        template = template,
        progress = progress,
        progressMax = progressMax,
        indeterminate = indeterminate,
        segmentLengths = segmentLengths,
        hasMediaSession = hasMediaSession,
        isGroupSummary = isGroupSummary,
        isColorized = isColorized,
        whenMillis = whenMillis,
    )
}
