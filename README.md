# Glyph Progress

Reads **any** Android notification that looks like progress or movement and draws it on the Nothing Glyph — the same idea as the system Glyph Progress feature, minus the short partner list. This app watches the whole notification shade, decides for itself whether something is a real live update, and only then lights the Glyph.

Android 16 (API 36) introduced Live Updates. Android 17 (API 37) keeps that contract and adds `MetricStyle` plus semantic colors. A notification qualifies when the system sets `FLAG_PROMOTED_ONGOING`, when the app requested promotion, or when it uses `ProgressStyle` / `MetricStyle`. Ordinary ongoing progress (the older `setProgress` form, and ride notifications that only say "8 min away") is included too, with a switch to turn that off. A third switch, **Follow anything ongoing**, goes further still: once an ongoing notification with no percent, ETA, or recognizable phrase is actually seen to *change* (a plain upload or transfer notification the parser can't otherwise read), it drives the Glyph too. A static status icon that never changes its own content — Bluetooth connected, VPN active, Bedtime Mode paused, a paired device's connection state — never lights up, even with that switch on, because it never gets the one thing the switch is looking for: proof of movement, and Android's own `status` notification category is refused outright regardless of whether it moved. Media players, group summaries, downloads, and Play Store installs are always skipped, even with that switch on.

The light level is a completion percent: empty at 0, full at 100.

## Detecting "is this actually progress?"

The goal is "light up for anything that's genuinely moving," never "light up for every notification." Three independent signals feed the decision, roughly strongest to weakest:

1. **The system already decided.** `FLAG_PROMOTED_ONGOING`, or a `ProgressStyle` / `MetricStyle` template — Android itself calls this a Live Update.
2. **Explicit progress data.** `android.progress` / segment lengths, a parsed percent or ETA from text, or a completion/searching phrase — read from every text field a notification exposes (short critical text, title, text, expanded big text, info text, and inbox-style text lines), not just the title.
3. **A structural hint that something is moving**, even with zero percent to compute:
   - A running chronometer (`android.showChronometer`) — a call, a recording, a workout that counts up rather than down.
   - Android's own notification category, when it's one of `progress`, `navigation`, `call`, `workout`, `stopwatch`, `alarm`, or `location_sharing`.
   - Under **Follow anything ongoing** only: the notification's own content has actually changed since it was last seen. `category = status` is excluded from this path outright — that's Android's own label for plain device/contextual status (Bluetooth, VPN, Bedtime Mode), never progress, so it can never light the Glyph even if its text happens to change.

Anything that doesn't clear one of these bars is completely invisible — not shown, not logged, not surfaced anywhere. "Don't just put every app notification there" is the design constraint, not an afterthought.

- Progress segments use Android's rule: percent = progress / sum of segment lengths. The platform sample 456 on segments 41+552+253+94 is 49%.
- "5 min away" then "2 min away" fills the bar as the remaining time shrinks. The first sighting is 0%. "200 m" is a distance, not a minute count.
- No number ("on the way", "searching") blinks. Arrival and "delivered" fill the bar and flash.
- `ProgressStyle` milestones (`android.progressPoints`) show up as tick marks on the on-screen bar.
- An API 37 semantic style (`caution`/`danger`) on a segment, point, or metric shows a matching label on screen and makes the Glyph pulse every few seconds on top of the steady level, so an alert-worthy update stands out even face down.
- A `MetricStyle` Live Update with no progress bar (Android 17) still shows its metric labels as the detail line.
- The board re-derives itself every 20 seconds even with no new notification event, so a chronometer countdown or a "5 min away" ETA keeps counting down between reposts instead of sitting frozen.
- A chronometer-based countdown (a timer app whose notification updates its display client-side, without reposting) can only be read when it actually sets Android's chronometer extras (`android.showChronometer` / `android.chronometerCountDown` / `when`). Some system timer apps instead draw the countdown inside a fully custom notification view, which a listener has no reliable way to read; that shows as Active rather than a percent.
- A photo/video upload bar (Messenger and similar) needs no special case: apps that post a real progress notification almost always use the standard `setProgress` extras, which the base parser already reads. **Follow anything ongoing** exists only for the apps that don't.

## Off by default: Uber, Zomato, Google Maps, Google Calendar

These four are Nothing's original Glyph Progress partner list, and Nothing OS keeps mirroring them on the Glyph natively even after moving to the Android 16 Live Update contract. This app detects their Live Updates like any other app's — but leaves them off by default so it isn't fighting Nothing's own system feature for the same lights. A detected update from one of them shows up in an **Off** section with why ("Nothing OS already shows this") and an **Allow** button; tap it to let this app drive the lights for that one app instead. Everything else that clears the detection bar above turns on by itself, no setup needed — that's the actual point of this app over Nothing's short list.

Hiding any other app works the same way in reverse: **Hide** moves it to the same **Off** section, tagged "Hidden by you," with an **Unhide** button.

## Quick Settings tile

Add the **Glyph Progress** tile to Quick Settings to see the active Live Update's percent without unlocking the phone, and tap it to cycle the pin to the next candidate.

Hardware, verified against the decompiled [Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit) `glyph-matrix-sdk-2.0.aar` (`docs/research/glyph-developer-kit.md` has the full method-by-method notes):

- Zone/strip phones — (1), (2), (2a) / (2a) Plus, **(3a) / (3a) Pro**, (4a), (4b) — use `GlyphManager.displayProgress` on one strip: D on Phone (1), A on (4a) and (4b), **C on (3a)** (this app's default fallback channel, which matches Nothing's own recommendation — A and B on (3a) saturate well before 100%). Zero setup needed beyond registering the right `Glyph.DEVICE_*` string; the SDK draws the fill.
- Phone (3) and Phone (4a) Pro have a dot-matrix display instead of a strip and use `GlyphMatrixUtils.generateMatrixProgress` / `setAppMatrixFrame`.

The SDK only binds on a Nothing phone. A small foreground service (`GlyphHoldService`) keeps the process alive while a Live Update is on screen, so the lights keep following it with the screen off — you don't need to keep the app open. Everywhere else (emulator, non-Nothing hardware) the on-screen bar is the test surface.

## Try it

1. Open the project in Android Studio (or `gradlew.bat :app:installDebug` with a device connected).
2. Allow notifications when asked.
3. Tap **Grant** and enable Glyph Progress in notification access. Come back and tap **Refresh notifications**.
4. Tap **Sample**. Each tap posts a Grab-style Live Update: searching, 15%, 45%, 80%, arrived. The preview should move. With notification access on, the shade entry is what the listener reads.
5. The slider drives the lights directly, without a notification. **Follow notifications** returns to the shade.
6. On a Nothing phone, run this once (it expires after 48 hours):

```
adb shell settings put global nt_glyph_interface_debug_enable 1
```

The manifest already carries Nothing's debug API key (`NothingKey` = `test`). Android 16 and 17 do not require a production key.

## Tests

Parser tests do not need a phone:

```
gradlew.bat :core:test
```
