# Glyph Progress behavior and Live Update mapping

Research notes for generalizing Glyph-style progress beyond the original Nothing partners (Uber, Zomato, Google Calendar) and the later Google Maps Live Update example. This document does not specify app code.

Sources are public posts, Nothing’s own release notes, and the Android 16 notification APIs. Nothing has not published the parser inside Nothing OS. Where behavior is inferred, it is labeled as such.

## 1. User-visible behavior of official Glyph Progress

### What the feature is

Glyph Progress shows a journey on the rear lights while the phone can stay face down. It is separate from Essential Notifications (a strip that stays lit until an important notification is opened) and from Glyph Timer (a user-set countdown).

Two generations of the feature matter:

| Era | What drives the lights | What the user sees |
| --- | --- | --- |
| Nothing OS 2.x–3.x, partner apps | Nothing OS estimates progress from notifications of apps the user enabled under Settings → Glyph Interface → Glyph Progress. Reviews say English notifications work more reliably, and that the feature is flaky when the app’s notification has no tracking text. | Uber, Zomato, and Google Calendar only, toggled per app. |
| Nothing OS 4.0 (Android 16) and 4.1 | Android Live Updates. Nothing’s 4.0 release note: rides, deliveries, and timers sync to the screen and the Glyph, and Nothing is no longer limited to specific applications. OS 4.1: Live Updates on the lock screen, notification panel, and Glyph, “tracking real-time progress from apps, like Google Maps.” Compatibility still depends on the app and region. | Promoted ongoing notification on the lock screen and shade, plus a light or matrix progress graphic. |

### Which lights move

Hardware, from Nothing’s Glyph developer pages and contemporary reviews:

- **Phone (1):** 5 segments, 12 zones. No 16-zone progress strip. Glyph Progress did not ship. The Glyph SDK’s progress call is limited to zone D1.
- **Phone (2):** 33 addressable zones. The curved strip under the upper-right diagonal has **16 zones**. Glyph Timer and Glyph Progress both use that strip. Volume uses the same strip as a fill level (empty = min, full = max).
- **Phone (2a):** 26 zones. Users report a Glyph Progress toggle; the same “progress strip” idea applies, with a different layout.
- **Phone (3a) and other LED Glyph phones on OS 4.0:** The Verge, describing Nothing’s GIF: a light **gradually lights up** to match Live Update progress.
- **Phone (3) and later Glyph Matrix phones:** A dot-matrix **animated progress bar**, not a 16-step LED drain. Phone (3) pocket mode turns the matrix off in a pocket. Flip-to-Glyph always-on for the matrix is tied to Flip to Glyph so the back is not lit when it is not visible.

The SDK method `displayProgress` draws an integer progress value on that progress channel (C1 on Phone (2), D1 only on Phone (1)), with an optional reverse flag. The public docs describe it as a progress value on that channel. Treat **0 as empty and 100 as full** for SDK calls. Reverse draws the complement, which matches the older countdown look.

### When the lights move

Classic partner behavior (phone face down):

- **Uber.** Android Authority (launch): the strip counts down time left until the driver arrives. A later how-to: after booking, the light **blinks** when the ride is confirmed, then the strip shows arrival progress, then it **blinks** when the ride has arrived.
- **Zomato.** Beebom: no progress light until the restaurant confirms; then a **periodic flash** until the courier picks up the order; then a **countdown** of the delivery estimate. A how-to phrases the countdown as LEDs turning off while the order is on the way, and a **blink** when it arrives. Users on Reddit report blink-only until arrival when the notification has no tracking line, and that the toggle plus notification permission has to be on. Ordering for someone else is reported as less reliable.
- **Google Calendar.** Nothing’s OS 2.5 Open Beta 2 note, quoted by 9to5Google: a **5-minute countdown** before the event. Beebom: the strip lights about five minutes before the meeting, counts down, and **flashes for a few minutes** after the countdown ends. One how-to said 10 minutes; the only figure Nothing itself published in that beta note is 5. The blink is the event boundary, not a percent.
- **Glyph Timer (same strip, not a partner app):** pulse when it starts, LEDs **fade out** as time remaining falls, flash (and optional sound) at zero. This is a remaining-time bar, the inverse of a fill bar.

OS 4.0+ Live Updates:

- Progress is a **fill**: more light means more of the journey is done, on the Phone (3a)-class GIF. The Phone (3) matrix shows a moving bar.
- The same update is also on the lock screen and the notification shade, so the user does not have to flip the phone to see it. The Glyph is the face-down view of that same update.
- A Phone (3) user on the OS 4.1 thread: Google Maps **driving** navigation keeps the live-update card up; **bus** location tracking collapses quickly to the status-bar pill. That is a Maps/system presentation difference, not proof of two Glyph algorithms.

### What 0% and 100% look like

Nothing never published a percent scale. Map the two metaphors as follows:

- **Legacy countdown (Timer, Calendar, and the Uber/Zomato ETA strip):** a full strip means “all of the remaining window is left.” Lights go out as time passes. At the end the strip blinks. For Calendar the window is about 5 minutes, so the strip is full at T−5 and empty/blinking at the event time. 0% completion is a **full** strip; 100% completion is an **empty** strip plus a blink.
- **OS 4 Live Update fill, and the Glyph SDK `displayProgress` value:** 0 is dark/empty, 100 is fully lit. Completion blink is a separate end cue (partner apps blink on arrival; the SDK does not define a blink).

Recommended internal integer is **completion percent** (0 = not started, 100 = done), which matches Android’s progress value and the OS 4 fill. A preview that wants the Phone (2) countdown look displays `100 - percent` on the 16 segments. Sixteen zones cannot show every integer: zone `i` (1-based) is lit when `percent >= ceil(i * 100 / 16)` for a fill, or when remaining percent clears that threshold for a countdown.

### Indeterminate (“on the way” before an ETA)

Partner apps do not show a shrinking or filling bar for the whole trip.

- Zomato **before pickup:** periodic blink, no countdown. That is indeterminate.
- Uber **just confirmed:** blink, then a bar only once arrival progress exists.
- Android’s own guidance for Live Updates: if the ETA is still calculating, show placeholder text such as “Thinking…” or “Rerouting…”, not an empty card. The status-bar chip for an indeterminate update shows the **small icon only**, with no time text.
- `ProgressStyle.setProgressIndeterminate(true)` means the numeric progress, segments, points, tracker icon, and styled-by-progress flag are ignored. One segment, if present, only colors the indeterminate bar.

So “on the way,” “picking up,” or “preparing,” with no number, is a blink, not a percent.

### Several notifications

Nothing documents a per-app Glyph Progress switch, not a mixer. There is one progress channel. Only enabled apps participate. Nothing has not published what happens if Uber and Maps are both live. OS 4 shows each Live Update as its own promoted notification; the Glyph still has one physical bar, so the system must pick one journey. Treat multi-app Glyph behavior as unspecified and use the priority rule in section 3 for this project.

### Screen off, face down, pocket

- The feature is defined for the phone **screen down**. Flip to Glyph is the face-down mode (silent or vibrate on later phones). Lights run while the main display is off; that is the point of the rear interface.
- OS 4 also draws the Live Update on the lock screen, so screen-off does not hide the update from the front if the user wakes the lock screen.
- Phone (3) pocket mode **suppresses the matrix** in a pocket even if a journey is active. A test on hardware should include pocket mode so a dark matrix is not mistaken for 0%.
- Music visualization, charging, volume, calls, and Essential Notifications can also own LEDs. Progress uses the progress strip; those other patterns can override or share the back. Do not assume the progress strip is exclusively ours while a call or timer is running.

### What Nothing OS is known to read

High level only. Nothing OS, for the original partners, estimates progress from notifications of apps the user enabled, and it is sensitive to language and to whether the notification actually contains tracking text. Community reports match that: if Zomato posts no tracking line, the lights blink or do nothing. OS 4.0+ follows Android Live Updates instead of a private per-app contract. A community navigation toy for the Phone (4a) Pro Glyph Matrix reads the Google Maps Live Update’s title and the distance to the next turn, which shows those fields are present on a real navigation notification. That is turn distance, not trip percent.

Do not spoof another app’s identity, bind private system services, or record notification contents to reproduce Glyph Progress. The supported ways to light the Glyph are the Glyph SDK and, on OS 4+, a normal Live Update that Nothing OS chooses to mirror.

## 2. Fields real Live Update apps expose

Android 16 (API 36) progress-centric notifications are the portable contract. Rideshare, delivery, and navigation are the documented use cases. Grab’s exact production payload is not in public docs; treat Grab like any other Live Update and prefer numeric fields over brand strings. The same rules cover Uber, delivery apps, timers, and Maps when they use this style. Older Uber/Zomato builds predate the API and often only have title and text.

### Promoted Live Update requirements

A notification is promotable when it:

- Uses a standard style, `BigTextStyle`, `CallStyle`, `ProgressStyle`, or `MetricStyle` (no custom `RemoteViews`)
- Declares `POST_PROMOTED_NOTIFICATIONS` and sets `android.requestPromotedOngoing` (`EXTRA_REQUEST_PROMOTED_ONGOING`)
- Is ongoing (`FLAG_ONGOING_EVENT`)
- Has a content title
- Is not a group summary and is not colorized
- Uses a channel that is not `IMPORTANCE_MIN`

OEMs, including Nothing, may add requirements. `FLAG_PROMOTED_ONGOING` is the system’s decision, not the app’s request.

### Fields worth reading

| Field | Extra or API | What apps put there |
| --- | --- | --- |
| Title | `android.title` | Journey state in a short line: destination, “Arrive 10:08 AM,” order name. Google’s rideshare sample title is an arrival clock time; the text is the place. |
| Text | `android.text` | State sentence: driver, stop, “Picking up,” “On the way.” |
| Subtext | `android.subText` | Header context (app sample uses it for a secondary label). Not a percent. |
| Short critical text | `android.shortCriticalText` | Chip text. Suggested max **7 characters**. Highest precedence on the chip. Examples in the docs are an absolute time or a short status, not a paragraph. |
| When | `Notification.when` | If at least 2 minutes in the future, the chip shows a minute countdown (“5min” when now is 10:05 and `when` is 10:10). Past times are hidden. |
| Chronometer | `android.showChronometer`, `android.chronometerCountDown` | Count-up or count-down timer in the chip while the value stays positive. This is how a timer Live Update exposes remaining time without a percent. |
| Classic progress | `android.progress`, `android.progressMax`, `android.progressIndeterminate` | `setProgress(max, progress, indeterminate)`. Indeterminate ignores the numbers. |
| ProgressStyle progress | `ProgressStyle.getProgress()` / same extras when the platform copies them | Position of the tracker **in the same units as segment lengths**. Default max is **100** when there are no segments. |
| Segments | `android.progressSegments` bundles: `length`, `colorInt`, `id`, `semanticStyle` | Relative lengths, not percents. Google’s rideshare sample uses lengths 41, 552, 253, and 94 (sum 940) with progress 456, i.e. about 49% along the bar, colored like traffic. A delivery tutorial uses equal lengths (100 per stage) and moves the tracker by stage. |
| Points | `android.progressPoints` bundles: `position` | Milestones (pickup, stop, handover). They do not define the percent. Position uses the same units as progress. |
| Styled by progress | `android.styledByProgress` | Default true: segments ahead of the tracker look unfilled. False means the app colored the segments itself. Still use the numeric progress for the integer. |
| Indeterminate | `ProgressStyle.setProgressIndeterminate` | Initialization with no known amount. Ignores progress, segments, points, and the tracker. |
| Tracker icon | `android.progressTrackerIcon` | Car, courier, or chevron. Ignore for the integer. |
| Start/end icons | `android.progressStartIcon`, `android.progressEndIcon` | Ignore for the integer. |

`Builder.setProgress` extras are overridden when a `ProgressStyle` is applied. On API 36 and above, read the style’s progress and segments if they are present; also read `android.progress` and `android.progressMax` because pre-36 compat writes those, and some apps still call `setProgress` only. If segments exist, **max = sum of positive `length` values**. If that sum is missing or overflows, max = 100.

Chip precedence, from `NotificationCompat`: short critical text, then a metric-style critical value, then `when` / chronometer. A parser that wants the ETA string should look at short critical text first, then `when`.

### What those apps do *not* reliably publish

- A 0–100 percent in the title. The platform example is an absolute progress of 456 on a bar whose segments sum to 940.
- A stable English status. Nothing’s own reviews say Glyph Progress works best in English. Locale strings will not match a fixed word list.
- Turn-by-turn distance as trip completion. “200 m” to the next maneuver is not a journey percent. If that is the only number, do not invent a percent from it.
- RemoteViews layouts. Live Updates forbid them. Older Uber notifications may still be custom; those will not yield `android.progress`. Fall back to title/text, and accept indeterminate when the text has no number.

### State enum

`INACTIVE`, `INDETERMINATE`, `PROGRESS`, `COMPLETE`.

One integer `percent` in 0–100 accompanies every non-inactive state. For `INDETERMINATE`, keep the last percent for that notification key but do not move the bar; the UI blinks. For `INACTIVE`, percent is unused.

Notification key: package + notification id + tag. ETA baselines are per key, in memory only.

## 3. Progress-mapping rules

Apply in order. Stop at the first step that sets a state.

1. **No candidate.** Notification is gone, or it is not ongoing and its text is not a completion phrase: `INACTIVE`.
2. **Indeterminate flag.** `android.progressIndeterminate` is true, or ProgressStyle indeterminate is true: `INDETERMINATE`. Ignore the numeric progress.
3. **Segment or classic progress.** If segments exist, `max` is the sum of lengths; otherwise `max` is `android.progressMax`. If `max > 0` and a progress value is present:
   - `percent = clamp(0, 100, round(100 * progress / max))`
   - If `progress >= max`: `COMPLETE`, percent 100
   - Else: `PROGRESS`
   - Points do not change this. Colors do not change this.
4. **Explicit percent in text.** Scan short critical text, then title, then text. First `\b(\d{1,3})\s*%` wins. Values above 100 clamp to 100. `100` → `COMPLETE`. Otherwise `PROGRESS`.
5. **Remaining time.** First match in that same field order:
   - `\b(\d+)\s*(?:min|mins|minutes|m)\b` → minutes
   - `\b(\d+)\s*(?:hr|hrs|hour|hours|h)\b` → minutes × 60
   - `\b(\d+)\s*h\s*(\d+)\s*m\b` → hours × 60 + minutes
   - A future `when` at least 2 minutes ahead, or a countdown chronometer that is still positive → remaining minutes from that timestamp
   - Bare chip text that is only a number plus `m` / `min` (the “5min” chip) counts as minutes
   - Then apply the ETA rule below. State is `PROGRESS`, unless remaining minutes are 0.
6. **Completion phrases**, only if steps 3–5 did not find a number. Case-insensitive whole words: `arrived`, `arriving now`, `delivered`, `dropped off`, `completed`, `complete`, `here`. Also `arriving` with no minute count. → `COMPLETE`, percent 100.
7. **Indeterminate phrases**, only if no number was found: `picking up`, `looking for`, `searching`, `finding a driver`, `preparing`, `being prepared`, `confirmed`, `waiting`, `matching`, `on the way`, `rerouting`, `thinking`. → `INDETERMINATE`.
8. **Ongoing with a title and none of the above.** → `INDETERMINATE`. Do not guess a percent from an address or a person’s name.

### ETA rule (“5 min away”)

Remaining time is not a percent until a baseline exists for that notification key.

- Remember `baselineMinutes` in memory. On each update, `baselineMinutes = max(baselineMinutes, remainingMinutes)`.
- `percent = round(100 * (baselineMinutes - remainingMinutes) / baselineMinutes)`.
- First sight of “5 min away” is **0%** (the whole observed window is still left). “2 min away” after that is **60%**. A later “8 min away” raises the baseline to 8 and the percent falls (the trip got longer).
- Remaining 0, or a completion phrase plus a number that is 0: `COMPLETE`, 100.
- Do not use a fixed 15- or 30-minute window. A 5-minute trip and a 50-minute trip both start at “5 min away” only at the end.
- “Arriving” **with** a positive minute count uses the minute count (step 5), not the completion phrase. “Arriving” alone is step 6.
- Distance tokens (`m`, `km`, `ft`, `mi`, “meters”) are not minutes. “200 m” must not match the minute pattern. Require a minute or hour unit, or the chip’s compact `5min` form.
- Calendar-style 5-minute countdowns fall out of this rule: the first post at T−5 has baseline 5 and percent 0; the event time is 100 and then `COMPLETE`.

### Worked examples

| Input | Result |
| --- | --- |
| ProgressStyle progress 456, segments 41+552+253+94 | `PROGRESS`, 49 (456/940) |
| `setProgress(100, 0, false)`, ongoing | `PROGRESS`, 0 (empty bar, not inactive) |
| `setProgress(100, 100, false)` | `COMPLETE`, 100 |
| Indeterminate true, title “Picking up” | `INDETERMINATE` |
| Title “5 min away”, first time | `PROGRESS`, 0, baseline 5 |
| Same key later, “Arriving” | `COMPLETE`, 100 |
| Title “Picking up”, no number | `INDETERMINATE` |
| Four segments of length 100, progress 200 | `PROGRESS`, 50 (tracker in the third stage) |
| Text “In 200 m turn right”, no progress extra | `INDETERMINATE` (maneuver distance is not a trip percent) |
| Notification removed | `INACTIVE` |

0% on a fill preview is a dark strip. 100% is a full strip. `COMPLETE` may blink and then go dark once the notification is gone (`INACTIVE`). `INDETERMINATE` blinks and holds the last percent.

## 4. Priority when several live updates exist

Nothing does not document this. Use the following rule so one integer drives the preview.

A **candidate** is a posted notification that maps to `INDETERMINATE`, `PROGRESS`, or `COMPLETE`.

1. If the user has pinned a package and that package has a candidate, the pinned package wins. Among that package’s candidates, pick the one with the latest post time.
2. Otherwise the candidate with the **latest post time** wins. “Most recently posted ongoing progress wins.” Record post time when the listener sees `onNotificationPosted`, because `getPostTime()` is not refreshed on every OEM.
3. If post times tie, prefer `PROGRESS` over `INDETERMINATE`, then the higher notification rank if the platform provides it.
4. A pin with no live candidate does not freeze the lights. Fall through to rule 2.
5. No candidates → `INACTIVE`, and clear the preview.
6. `COMPLETE` stays the winner only while that notification is still posted. When it is dismissed, recompute.

Switching the winner moves the single bar. Do not average two trips.

## 5. Privacy

The only fields this feature needs are:

- package name (which app, and whether it is pinned or enabled)
- title (short label on the preview)
- the integer percent and the state enum

Read progress extras, segment **lengths** (not icons or colors), short critical text, and the title/text **only long enough to run the rules above**. Discard the strings after the parse. Do not write notification bodies, big text, message lists, person names, addresses, or raw extras to disk, logs, or analytics. An ETA baseline is a few integers in memory, keyed by package and notification id, dropped when the notification goes away.

If a notification listener is used, the user grants it, and it should ignore packages the user did not enable. The system Glyph path on OS 4 does not require a third-party listener at all: the app under test posts its own Live Update, and Nothing OS mirrors it.

Do not keep a history of titles. The preview can show the current title in memory and forget it on `INACTIVE`.

## 6. Test plan

### (a) Emulator, no Nothing hardware

Goal: prove the mapping and the priority rule with notifications this test app posts itself, drawn on an on-screen 16-segment glyph. No notification listener over other apps is required.

1. Boot an API 36 emulator image. If API 36 is unavailable, API 34 still covers classic `setProgress` and the text rules; mark ProgressStyle cases skipped.
2. Open the preview screen. It shows 16 segments, the state name, the percent, the title, and the winning package.
3. Post an ongoing notification from the test app with `setProgress(100, 0, false)` and title `Trip`. Expect `PROGRESS`, 0, all segments off.
4. Update the same id to progress 50. Expect 50 and 8 of 16 segments lit (`ceil(i * 100 / 16)`).
5. Update to progress 100. Expect `COMPLETE`, 16 segments, then a short blink.
6. Post `setProgress(0, 0, true)` with title `Picking up`. Expect `INDETERMINATE` and a blink, not a new percent.
7. Cancel it and post title `5 min away` with no progress extra. Expect `PROGRESS`, 0. Update the same id to `2 min away`. Expect 60. Update to `Arriving`. Expect `COMPLETE`, 100.
8. Post title `In 200 m` with no progress extra. Expect `INDETERMINATE`, not a percent parsed from 200.
9. On API 36, post a ProgressStyle with segments 41, 552, 253, 94 and progress 456. Expect `PROGRESS`, 49.
10. Post a second ongoing notification from a second test package (or a second id labeled as another package in the fake parser input) at a later time with progress 10. Expect the newer one to win. Pin the first package and expect the pin to win while it is ongoing. Unpin and expect the newer post to win again. Cancel the winner and expect the other candidate, or `INACTIVE` if none remain.
11. Turn the emulator screen off with the power key, update the notification with `adb`, then wake. The stored percent matches the update that happened while the screen was off. The on-screen preview cannot be watched while the panel is off; check the in-memory state after wake.
12. Confirm logcat does not contain the notification body. Titles used above are synthetic.

### (b) Nothing phone with the real SDK

1. Use a Phone (2) or newer with Glyph Progress in Settings. Register the test build through Nothing’s Glyph developer program and call the public SDK only. Do not bind private Glyph services.
2. With the preview still on screen, call `displayProgress` with 0, 50, and 100 on the progress channel. Face the phone down. Expect the progress strip dark, about half lit, then full. Compare with the on-screen 16-segment preview.
3. Call the reverse-progress variant at 25. Expect the hardware to look like 75 on the fill scale (legacy countdown). The on-screen integer stays 25 completion percent.
4. On OS 4 / 4.1, post the test app’s own promoted ongoing ProgressStyle (steps 3–6 and 9 above) and enable Glyph Progress for that app if the settings list requires it. Face down: the system Glyph should move with the same percents. If Nothing has not enabled the test package, the SDK calls in step 2 are still the hardware check; record that system mirroring did not run.
5. Optional real-app check, no extra logging: start a Google Maps drive or an Uber/Zomato trip the user already has, with that app enabled in Glyph Progress. Observe only. Driving Maps should keep a Live Update; the Glyph should animate. Do not attach a listener to copy that notification.
6. Pocket the Phone (3) if available. The matrix should go dark because of pocket mode, while a face-down table should show the bar.
7. Start a Glyph Timer during a test progress notification and note whether the timer takes the strip. That interaction is system-owned; the preview should not fight it.

## Sources

- Nothing OS 4.0 general release, Nothing Community: Glyph Progress for rides, deliveries, and timers via Android 16, no longer limited to specific apps. https://nothing.community/d/47265-nothing-os-40-general-release
- Nothing OS 4.1 roll-out, Nothing Community: Live Updates on lock screen, notification panel, and Glyph, with Google Maps as the example. https://nothing.community/d/56170-nothing-os-41-roll-out
- The Verge, 21 Nov 2025: Phone (3a) light fills with Live Update progress; Phone (3) matrix shows a bar. https://www.theverge.com/news/826648/nothing-android-16-live-updates-glyph
- 9to5Google, 9 Nov 2023: Nothing’s wording, “5-minute countdown” for Google Calendar. https://9to5google.com/2023/11/09/nothing-phone-2-google-calendar-integration/
- Android Authority, 11 Jul 2023: Phone (2) 16-zone strip; Glyph Timer fades out; Uber Glyph Progress counts down driver arrival. https://www.androidauthority.com/nothing-phone-2-essential-glyph-3343103/
- Beebom: Zomato confirm → blink until pickup → ETA countdown; Calendar lights about five minutes before and flashes after. https://beebom.com/nothing-phone-2-glyph-interface-uses/
- Techibee review: per-app toggle; Uber blink, then strip, then blink; Zomato LEDs turn off on the way and blink on arrival; English works best; progress comes from notifications. https://techibeereview.com/how-to-use-glyph-progress-on-nothing-phone-2/
- Reddit reports of Zomato blink-only or no tracking text, and of notification permission under Glyph Progress (r/NOTHING, r/NothingTech, 2024–2025).
- Android Developers, progress-centric notifications and Create live update notifications (updated 2026-09-23): segments, points, chip, `when`, indeterminate placeholder text. https://developer.android.com/about/versions/16/features/progress-centric-notifications and https://developer.android.com/develop/ui/compose/notifications/live-update
- AndroidX `NotificationCompat` (`EXTRA_PROGRESS`, `EXTRA_PROGRESS_MAX`, `EXTRA_PROGRESS_INDETERMINATE`, `EXTRA_PROGRESS_SEGMENTS`, `EXTRA_SHORT_CRITICAL_TEXT`, `EXTRA_REQUEST_PROMOTED_ONGOING`; `ProgressStyle` default max 100 and indeterminate behavior).
- Nothing Glyph Developer Kit README: `displayProgress` on C1/D1, D1 only on Phone (1). https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
