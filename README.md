# Glyph Progress

Reads **any** Android Live Update on the phone and draws it on the Nothing Glyph, the same idea as the system Glyph Progress feature. Nothing's own version only follows a short partner list. This app does not. It watches the notification shade.

Android 16 (API 36) introduced Live Updates. Android 17 (API 37) keeps that contract and adds `MetricStyle` plus semantic colors. A notification qualifies when the system sets `FLAG_PROMOTED_ONGOING`, when the app requested promotion, or when it uses `ProgressStyle` / `MetricStyle`. Ordinary ongoing progress (the older `setProgress` form, and ride notifications that only say "8 min away") is included too, with a switch to turn that off. Media players, group summaries, downloads, and Play Store installs are skipped.

The light level is a completion percent: empty at 0, full at 100.

- Progress segments use Android's rule: percent = progress / sum of segment lengths. The platform sample 456 on segments 41+552+253+94 is 49%.
- "5 min away" then "2 min away" fills the bar as the remaining time shrinks. The first sighting is 0%. "200 m" is a distance, not a minute count.
- No number ("on the way", "searching") blinks. Arrival and "delivered" fill the bar and flash.

Hardware, from the [Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit) `glyph-matrix-sdk-2.0.aar`:

- LED phones use `GlyphManager.displayProgress` on the progress channel (D on Phone (1), A on Phone (4a)/(4b), C on the others).
- Phone (3) and Phone (4a) Pro use `GlyphMatrixUtils.generateMatrixProgress` and `setAppMatrixFrame`.

The SDK only binds on a Nothing phone. Everywhere else the on-screen 16-segment preview is the test surface.

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
