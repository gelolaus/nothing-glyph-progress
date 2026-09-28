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
    fun ignoredPackageIsDroppedButStillReportedAsSuppressed() {
        val result = LiveUpdateEngine.board(
            listOf(snap(packageName = "com.grab", promotedOngoing = true, isOngoing = true, title = "Grab")),
            pinnedKey = null,
            policy = Policy(ignoredPackages = setOf("com.grab")),
            nowMillis = now,
        )
        assertNull(result.board.active)
        assertTrue(result.board.tracks.isEmpty())
        assertEquals(1, result.board.suppressed.size)
        assertEquals(SuppressReason.UserHidden, result.board.suppressed.single().reason)
        assertEquals("com.grab", result.board.suppressed.single().packageName)
    }

    @Test
    fun colorizedStandardProgressIsStillPickedUpByTheGenericTier() {
        // Colorized disqualifies *system promotion*, but a plain ongoing notification
        // that already carries explicit progress extras (e.g. a Messenger upload) is
        // still worth showing on the Glyph.
        val track = only(snap(isOngoing = true, isColorized = true, progress = 50, progressMax = 100))
        assertEquals(50, track.percent)
    }

    @Test
    fun colorizedRequestedPromotionDoesNotQualifyOnRequestAlone() {
        val result = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, isColorized = true, requestedPromotedOngoing = true, title = "Ride")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(result.board.active)
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

    @Test
    fun percentInBigTextIsFound() {
        val track = only(snap(isOngoing = true, bigText = "Uploading, 62% done"))
        assertEquals(62, track.percent)
    }

    @Test
    fun percentInInfoTextIsFound() {
        val track = only(snap(isOngoing = true, infoText = "33%"))
        assertEquals(33, track.percent)
    }

    @Test
    fun percentInTextLinesIsFound() {
        val track = only(snap(isOngoing = true, textLines = listOf("File 2 of 4", "45% complete")))
        assertEquals(45, track.percent)
    }

    @Test
    fun matchAnyOngoingIgnoresAStaticStatusNotification() {
        // A Bluetooth/VPN/Bedtime-Mode style notification that never changes its own
        // content should never light up, even with the catch-everything switch on.
        val policy = Policy(matchAnyOngoing = true)
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "bt", isOngoing = true, title = "Bluetooth connected")),
            pinnedKey = null,
            policy = policy,
            nowMillis = now,
        )
        assertNull(first.board.active)
        val stillSame = LiveUpdateEngine.board(
            listOf(snap(key = "bt", isOngoing = true, title = "Bluetooth connected")),
            pinnedKey = null,
            policy = policy,
            contentSignatures = first.contentSignatures,
            nowMillis = now,
        )
        assertNull(stillSame.board.active)
    }

    @Test
    fun matchAnyOngoingCatchesASignalLessNotificationOnceItActuallyChanges() {
        val policy = Policy(matchAnyOngoing = true)
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "xfer", isOngoing = true, title = "Backing up photos")),
            pinnedKey = null,
            policy = policy,
            nowMillis = now,
        )
        assertNull(first.board.active)
        val changed = LiveUpdateEngine.board(
            listOf(snap(key = "xfer", isOngoing = true, title = "Backing up photos: 120 of 400")),
            pinnedKey = null,
            policy = policy,
            contentSignatures = first.contentSignatures,
            nowMillis = now,
        )
        assertEquals(GlyphPhase.Indeterminate, changed.board.active!!.phase)
    }

    @Test
    fun matchAnyOngoingOffNeverMatchesASignalLessNotificationEvenIfItChanges() {
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "xfer", isOngoing = true, title = "Backing up photos")),
            pinnedKey = null,
            nowMillis = now,
        )
        val changed = LiveUpdateEngine.board(
            listOf(snap(key = "xfer", isOngoing = true, title = "Backing up photos: 120 of 400")),
            pinnedKey = null,
            contentSignatures = first.contentSignatures,
            nowMillis = now,
        )
        assertNull(changed.board.active)
    }

    @Test
    fun blocklistedPackageStaysInvisibleEvenUnderMatchAnyOngoing() {
        // Pure system noise (downloads, the Play Store) is never even reported as
        // suppressed - there is nothing for the user to review or allow.
        val result = LiveUpdateEngine.board(
            listOf(snap(packageName = "com.android.providers.downloads", isOngoing = true, title = "File.zip")),
            pinnedKey = null,
            policy = Policy(matchAnyOngoing = true),
            nowMillis = now,
        )
        assertTrue(result.board.tracks.isEmpty())
        assertTrue(result.board.suppressed.isEmpty())
    }

    @Test
    fun ignoredPackageStaysDroppedUnderMatchAnyOngoingOnceItMoves() {
        val policy = Policy(matchAnyOngoing = true, ignoredPackages = setOf("com.ignored"))
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "ig", packageName = "com.ignored", isOngoing = true, title = "Anything")),
            pinnedKey = null,
            policy = policy,
            nowMillis = now,
        )
        val moved = LiveUpdateEngine.board(
            listOf(snap(key = "ig", packageName = "com.ignored", isOngoing = true, title = "Anything else now")),
            pinnedKey = null,
            policy = policy,
            contentSignatures = first.contentSignatures,
            nowMillis = now,
        )
        assertTrue(moved.board.tracks.isEmpty())
        assertEquals(1, moved.board.suppressed.size)
        assertEquals(SuppressReason.UserHidden, moved.board.suppressed.single().reason)
    }

    @Test
    fun progressPointsBecomeMilestoneFractions() {
        val track = only(
            snap(
                isOngoing = true,
                progress = 50,
                progressMax = 100,
                progressPoints = listOf(25, 50, 75),
            ),
        )
        assertEquals(listOf(0.25f, 0.5f, 0.75f), track.milestoneFractions)
    }

    @Test
    fun pointsAtZeroOrMaxAreNotDrawn() {
        val track = only(
            snap(
                isOngoing = true,
                progress = 50,
                progressMax = 100,
                progressPoints = listOf(0, 40, 100),
            ),
        )
        assertEquals(listOf(0.4f), track.milestoneFractions)
    }

    @Test
    fun semanticStyleIsTheStrongestAcrossSegmentsAndPoints() {
        val track = only(
            snap(
                isOngoing = true,
                progress = 10,
                progressMax = 100,
                semanticStyles = listOf(Semantic.INFO, Semantic.DANGER, Semantic.CAUTION),
            ),
        )
        assertEquals(Semantic.DANGER, track.semantic)
    }

    @Test
    fun chronometerAloneIsAStandardSignal() {
        // A call, a screen recording, a workout timer that counts *up* - no percent to
        // compute, but Android is visibly animating it, so it deserves an Active glyph.
        val track = only(snap(isOngoing = true, usesChronometer = true, title = "Recording"))
        assertEquals(GlyphPhase.Indeterminate, track.phase)
    }

    @Test
    fun progressLikeCategoryIsAStandardSignalWithNoOtherText() {
        for (category in listOf("progress", "navigation", "call", "workout", "stopwatch", "alarm", "location_sharing")) {
            val track = only(snap(isOngoing = true, category = category, title = "Untitled"))
            assertEquals(GlyphPhase.Indeterminate, track.phase, "category=$category should be a signal")
        }
    }

    @Test
    fun statusCategoryIsNeverAStandardSignal() {
        val result = LiveUpdateEngine.board(
            listOf(snap(isOngoing = true, category = "status", title = "Bedtime Mode is paused")),
            pinnedKey = null,
            nowMillis = now,
        )
        assertNull(result.board.active)
    }

    @Test
    fun statusCategoryNeverQualifiesUnderMatchAnyOngoingEvenAfterItMoves() {
        val policy = Policy(matchAnyOngoing = true)
        val first = LiveUpdateEngine.board(
            listOf(snap(key = "status", isOngoing = true, category = "status", title = "Disconnected")),
            pinnedKey = null,
            policy = policy,
            nowMillis = now,
        )
        val moved = LiveUpdateEngine.board(
            listOf(snap(key = "status", isOngoing = true, category = "status", title = "Connected")),
            pinnedKey = null,
            policy = policy,
            contentSignatures = first.contentSignatures,
            nowMillis = now,
        )
        assertNull(moved.board.active)
    }

    @Test
    fun theFourNothingPartnerAppsAreOffByDefault() {
        for (packageName in DEFAULT_DISABLED_PACKAGES) {
            val result = LiveUpdateEngine.board(
                listOf(snap(packageName = packageName, promotedOngoing = true, isOngoing = true, title = "A ride")),
                pinnedKey = null,
                nowMillis = now,
            )
            assertNull(result.board.active, "$packageName should be off by default")
            assertEquals(SuppressReason.DefaultDisabled, result.board.suppressed.single().reason)
        }
    }

    @Test
    fun aNothingPartnerAppCanBeAllowedBackOn() {
        val track = only(
            snap(packageName = "com.ubercab", promotedOngoing = true, isOngoing = true, title = "Uber"),
            policy = Policy(allowedOverridePackages = setOf("com.ubercab")),
        )
        assertEquals("com.ubercab", track.packageName)
    }

    @Test
    fun metricLabelsFillDetailWhenThereIsNoOtherText() {
        val track = only(
            snap(
                isOngoing = true,
                promotedOngoing = true,
                template = "android.app.Notification\$MetricStyle",
                metricLabels = listOf("Pace", "Distance"),
            ),
        )
        assertEquals("Pace · Distance", track.detail)
    }

    private fun only(snapshot: NotificationSnapshot, policy: Policy = Policy()): GlyphTrack =
        LiveUpdateEngine.board(listOf(snapshot), pinnedKey = null, policy = policy, nowMillis = now).board.active!!

    private fun snap(
        key: String = "n",
        packageName: String = "com.example.ride",
        title: String = "",
        text: String = "",
        shortText: String = "",
        bigText: String = "",
        infoText: String = "",
        textLines: List<String> = emptyList(),
        category: String = "",
        isOngoing: Boolean = false,
        usesChronometer: Boolean = false,
        chronometerCountdown: Boolean = false,
        postTimeMillis: Long = 1,
        promotedOngoing: Boolean = false,
        requestedPromotedOngoing: Boolean = false,
        template: String = "",
        progress: Int? = null,
        progressMax: Int? = null,
        indeterminate: Boolean = false,
        segmentLengths: List<Int> = emptyList(),
        progressPoints: List<Int> = emptyList(),
        semanticStyles: List<Int> = emptyList(),
        metricLabels: List<String> = emptyList(),
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
        bigText = bigText,
        infoText = infoText,
        textLines = textLines,
        category = category,
        isOngoing = isOngoing,
        postTimeMillis = postTimeMillis,
        promotedOngoing = promotedOngoing,
        requestedPromotedOngoing = requestedPromotedOngoing,
        template = template,
        progress = progress,
        progressMax = progressMax,
        indeterminate = indeterminate,
        segmentLengths = segmentLengths,
        progressPoints = progressPoints,
        semanticStyles = semanticStyles,
        metricLabels = metricLabels,
        hasMediaSession = hasMediaSession,
        isGroupSummary = isGroupSummary,
        isColorized = isColorized,
        whenMillis = whenMillis,
        usesChronometer = usesChronometer,
        chronometerCountdown = chronometerCountdown,
    )
}
