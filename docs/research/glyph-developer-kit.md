# Nothing Glyph Developer Kit — integration research

Researched 2026-09-28 from the official kits and from the public API inside the current AAR. This is an integration note, not an app.

Primary sources:

- [Glyph-Developer-Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit) README (also mirrored as the GitHub wiki; the wiki has no extra pages). HEAD commit `8ee807a` (2026-07-16), message “Add Nothing Phone (4b) info”.
- AAR inspected: [`sdk/glyph-matrix-sdk-2.0.aar`](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit/blob/main/sdk/glyph-matrix-sdk-2.0.aar) (blob `7fdd2e8ec41e6fef2aaa3f31963d06807421dfaf`, 114,689 bytes). Public methods and constants below were read from `classes.jar` in that file.
- [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) README and [`LICENSE.md`](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md).
- [nothing.tech/pages/glyph-developer-kit](https://nothing.tech/pages/glyph-developer-kit) (marketing zone counts only).
- [GlyphMatrix-Example-Project](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Example-Project) is a Glyph Toy demo. It is not a progress-bar sample. The zone kit has **no sample app**, only README Example 1 and Example 2.

There is **no Maven Central or JitPack coordinate**. The repo has no `build.gradle`. GitHub Releases for Glyph-Developer-Kit is empty. The version you can pin is the filename plus the commit that last touched the AAR.

## 1. How to add the SDK

Use the **combined** AAR from Glyph-Developer-Kit, not the smaller file checked into the matrix repo.

| File | SHA-256 blob (git) | Size | What it contains |
| --- | --- | --- | --- |
| [Glyph-Developer-Kit `sdk/glyph-matrix-sdk-2.0.aar`](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit/raw/main/sdk/glyph-matrix-sdk-2.0.aar) | `7fdd2e8ec41e6fef2aaa3f31963d06807421dfaf` | 114,689 | `GlyphManager` **and** `GlyphMatrixManager`. This is the one to vendor. |
| [GlyphMatrix-Developer-Kit `glyph-matrix-sdk-2.0.aar`](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/glyph-matrix-sdk-2.0.aar) | `6d24fb81b5a72240a92b66fba0dd400eb04b9aa3` | 58,757 | Different binary. Do not assume it is the same library. |

Current as of 2026-09-28:

- Filename version: **2.0**
- Last AAR commit: **2026-07-16** (`8ee807a9312a640b0d43051450924e3446bc1d78`)
- Previous AAR commit: 2026-03-27 (`894ab1ce`, “Update Glyph SDK to 25111”)
- Internal constant `Common` field `NOTHING_SDK_VERSION` / package-private `getSDKVersion()`: **140101**
- AAR `AndroidManifest.xml`: `package="com.nothing.thirdparty"`, `minSdkVersion="33"`
- No `proguard.txt` inside the AAR

The zone README says “Glyph SDK and GlyphMatrix SDK are the same. Simply use the single AAR.” That sentence refers to the file under `sdk/` in Glyph-Developer-Kit. The two GitHub blobs are not byte-identical.

### Gradle (exact lines)

There is no `implementation("com.nothing:…")` coordinate.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}
```

```groovy
// app/build.gradle
dependencies {
    implementation files("libs/glyph-matrix-sdk-2.0.aar")
}
```

Copy the AAR to `app/libs/glyph-matrix-sdk-2.0.aar`. Download URL:

`https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit/raw/main/sdk/glyph-matrix-sdk-2.0.aar`

JitPack will not resolve this repo. It is not a Gradle project.

### Manifest

From the zone README and the matrix README:

```xml
<uses-permission android:name="com.nothing.ketchum.permission.ENABLE" />

<application>
    <!-- Debug builds: value "test". Release: the key Nothing issued you. -->
    <meta-data
        android:name="NothingKey"
        android:value="test" />
</application>
```

`GlyphManager.register()` reads that meta-data (`Common.getAppKey` looks up the string `NothingKey`) and passes it to `IGlyphService.registerSDK(String key, String device)`.

API-key note from the README (commit `e0e3ef3`, 2026-02-25, “Remove API Key” only changed the surrounding text; the meta-data is still documented):

- Android 16 (“Android B”) and later: Nothing no longer requires you to apply for a key.
- They still tell you to keep the `NothingKey` meta-data on every OS version.

Debug unlock, from the README (not from the AAR):

```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```

Debug mode turns itself off after 48 hours. A notification is shown when it activates. Only a **foreground** app may drive the lights. The README limits the SDK to **Nothing phones on Android 14+**. The AAR’s min SDK is 33.

Glyph Toy `<service>` actions (`com.nothing.glyph.TOY`) are only required if you ship a toy. A normal in-app progress bar does not register a toy service.

## 2. Supported devices

`Glyph.DEVICE_*` strings are `Build.MODEL` codes, assigned in `Glyph.<clinit>`. `Common.is*()` compares `Build.MODEL` to those strings.

| Marketing name | `Common` check | Match | `Glyph` constant | Value | Hardware |
| --- | --- | --- | --- | --- | --- |
| Phone (1) | `is20111()` | `equals` | `DEVICE_20111` | `A063` | 5 segments, 15 indexes (README: 12 addressable zones on the marketing page) |
| Phone (2) | `is22111()` | `equals` `A065` **or** `AIN065` | `DEVICE_22111` / `DEVICE_22111I` | `A065` / `AIN065` | Zone lights, 33 indexes |
| Phone (2a) | `is23111()` | `equals` | `DEVICE_23111` | `A142` | 26 indexes (A, B, C1–C24) |
| Phone (2a) Plus | `is23113()` | `equals` | `DEVICE_23113` | `A142P` | Same zone map as (2a). `isTargetDevice23111` is true for both `A142` and `A142P` |
| Phone (3) | `is23112()` | `MODEL.contains("A024")` | `DEVICE_23112` | `A024` | **Glyph Matrix 25×25**. Not a zone phone |
| Phone (3a) and (3a) Pro | `is24111()` | `MODEL.contains("A059")` | `DEVICE_24111` | `A059` | Zones, 36 indexes. README groups Pro with (3a) |
| Phone (4a) | `is25111()` | `equals` | `DEVICE_25111` | `A069` | 6 zone indexes (A1–A6) |
| Phone (4a) Pro | `is25111p()` | `equals` | `DEVICE_25111p` | `A069P` | **Glyph Matrix 13×13**, AOD toys only, no Glyph Touch |
| Phone (4b) | `is25131()` | `MODEL.contains("A009P")` | `DEVICE_25131` | `A009P` | 4 zone indexes (A1–A4) |

The README’s `Common` table omits `is23112()` and `is25111p()`. Both are public on the 2.0 AAR.

Channel counts stored on `Glyph` (package-private `*_SIZE`, public matrix lengths):

| Constant | Value |
| --- | --- |
| `DEVICE_20111_SIZE` | 15 |
| `DEVICE_22111_SIZE` | 33 |
| `DEVICE_23111_SIZE` | 26 (also used for 23113) |
| `DEVICE_24111_SIZE` | 36 |
| `DEVICE_25111_SIZE` | 6 |
| `DEVICE_25131_SIZE` | 4 |
| `DEVICE_23112_MATRIX_LENGTH` | 25 |
| `DEVICE_25111p_MATRIX_LENGTH` | 13 |
| `DEVICE_23112_SIZE` | 625 (25×25) |
| `DEVICE_25111p_SIZE` | 169 (13×13) |

`Common.getDeviceMatrixLength()` returns 25 on Phone (3), 13 on Phone (4a) Pro, and **0** on every zone phone.

### Zones vs matrix

- **Zone phones** (1, 2, 2a, 2a Plus, 3a / 3a Pro, 4a, 4b): `com.nothing.ketchum.GlyphManager`. You select LED indexes, then `toggle`, `animate`, or `displayProgress`.
- **Matrix phones** (Phone (3), Phone (4a) Pro): `com.nothing.ketchum.GlyphMatrixManager`. You push a `size*size` `int[]` with `setAppMatrixFrame`. `GlyphManager.displayProgress` has **no branch** for `23112` or `25111p`.

### These are indexes, not bitmasks

`buildChannel(int)` does `channelArray.set(index, DEFAULT_LIGHT)`. `DEFAULT_LIGHT` is **4000** (`GlyphFrame.<clinit>`). `buildChannel(int channel, int light)` stores `light` at that index. `GlyphFrame.getChannel()` returns that `int[]` of brightness values (0 = not selected). There is no bitmask field anywhere in the public API.

README Example 2 writes `Glyph.B1` and `Glyph.C1_4`. **Those fields are not on `Glyph`.** They live on the nested classes:

- `com.nothing.ketchum.Glyph.Code_20111`
- `Glyph.Code_22111`
- `Glyph.Code_23111` (also the map for 2a Plus)
- `Glyph.Code_24111`
- `Glyph.Code_25111`
- `Glyph.Code_25131`

Kotlin: `Glyph.Code_22111.C1_1`. There is no `Code_*` class for Phone (3) or Phone (4a) Pro.

### Channel indexes (from `<clinit>`, matches the README tables)

**Phone (1)** `Glyph.Code_20111` — array length 15. D1_1 is the bottom, D1_8 is the top.

| Constant | Index |
| --- | --- |
| `A1` | 0 |
| `B1` | 1 |
| `C1` `C2` `C3` `C4` | 2 3 4 5 |
| `E1` | 6 |
| `D1_1` … `D1_8` | 7 … 14 |

**Phone (2)** `Glyph.Code_22111` — array length 33. C1_1 is bottom-right of the C ring, C1_16 is top-left. D1_1 bottom, D1_8 top.

| Constant | Index |
| --- | --- |
| `A1` | 0 |
| `A2` | 1 |
| `B1` | 2 |
| `C1_1` … `C1_16` | 3 … 18 |
| `C2` `C3` `C4` `C5` `C6` | 19 20 21 22 23 |
| `E1` | 24 |
| `D1_1` … `D1_8` | 25 … 32 |

**Phone (2a) and (2a) Plus** `Glyph.Code_23111` — array length 26. C_1 is bottom-left, C_24 is top-right.

| Constant | Index |
| --- | --- |
| `C_1` … `C_24` | 0 … 23 |
| `B` | 24 |
| `A` | 25 |

**Phone (3a) / (3a) Pro** `Glyph.Code_24111` — array length 36. A_1 is top, A_11 is bottom. B_1 is bottom-right, B_5 is top-left. C_1 is bottom-left, C_20 is top-right.

| Constant | Index |
| --- | --- |
| `C_1` … `C_20` | 0 … 19 |
| `A_1` … `A_11` | 20 … 30 |
| `B_1` … `B_5` | 31 … 35 |

**Phone (4a)** `Glyph.Code_25111` — A_1 top, A_6 bottom: `A_1`…`A_6` = 0…5.

**Phone (4b)** `Glyph.Code_25131` — A_1 top, A_4 bottom: `A_1`…`A_4` = 0…3.

`buildChannelA()` through `buildChannelE()` switch on the device string stored in the builder and set every index in that letter group to 4000. On a device that does not have that letter, the call is a no-op.

### How unsupported hardware is reported

There is **no error-code enum** and no `onUnsupported` callback.

`GlyphManager.Callback` / `GlyphMatrixManager.Callback` only have:

- `onServiceConnected(ComponentName)`
- `onServiceDisconnected(ComponentName)`

If `Build.MODEL` matches nothing, every `Common.is*()` is false. The README sample then never calls `register`, so `mDevice` stays null and `getGlyphFrameBuilder()` returns **null**.

If you call `register()` (no argument) it still sets `mDevice` to `Build.MODEL` and asks the system service `registerSDK(apiKey, Build.MODEL)`. On a phone with no Glyph service that call throws, the SDK logs it, and `register` returns **false** (`mHasAuthorized` stays false).

Later calls (`openSession`, `closeSession`, `toggle`, `animate`, `displayProgress`, `setFrameColors`, `turnOff`) check `mHasAuthorized`. If it is false they log `Non registed` and return. They do not throw for that case.

`openSession` / `closeSession` / `setFrameColors` throw `GlyphException("Please use it after service connected.")` when the binder is null **and** the app was marked authorized.

`GlyphException` extends `java.lang.Exception` (checked). Its only constructor is `GlyphException(String)`. There is no `code` field.

## 3. Session lifecycle

Package: `com.nothing.ketchum`.

### Zone phones — `GlyphManager`

| Method | Role |
| --- | --- |
| `static GlyphManager getInstance(Context)` | Singleton. Keep the application context. |
| `void init(Callback)` | Bind. Call from `onCreate` / when the screen starts. |
| `void unInit()` | `unbindService`. Call from `onDestroy`. |
| `boolean register()` | `registerSDK(NothingKey, Build.MODEL)` and sets `mDevice` to `Build.MODEL`. |
| `boolean register(String targetDevice)` | Same, but `mDevice` and the service argument are the string you pass (`Glyph.DEVICE_*`). **This is the one the README sample uses.** |
| `GlyphFrame.Builder getGlyphFrameBuilder()` | Null if `register` never set `mDevice`. Otherwise `new GlyphFrame.Builder(mDevice)`. |
| `void openSession()` | Only after `onServiceConnected`. Throws if the binder is null. No-op with log `Non registed` if `register` returned false. |
| `void closeSession()` | Stops the in-flight frame task, then `IGlyphService.closeSession()`. |
| `void turnOff()` | Stops the task and pushes an all-off frame. |

Bind intent, from `GlyphManager.init` (same intent in `GlyphMatrixManager.init`):

- package `com.nothing.thirdparty`
- action `com.nothing.thirdparty.bind_glyphservice`
- component `com.nothing.thirdparty` / `com.nothing.thirdparty.GlyphService`
- `Context.bindService(..., BIND_AUTO_CREATE)` (flag `1`)

AIDL `com.nothing.thirdparty.IGlyphService`:

| Transaction | Method |
| --- | --- |
| 1 | `setFrameColors(int[])` |
| 2 | `openSession()` |
| 3 | `closeSession()` |
| 4 | `register(String)` |
| 5 | `registerSDK(String, String)` — zone `GlyphManager.register` |
| 6 | `registerMatrixSDK(String)` — `GlyphMatrixManager.register` |
| 7 | `setMatrixColors(int[])` |
| 8 | `setGlyphMatrixTimeout(boolean)` |
| 9 | `setAppMatrixColors(int[])` |
| 10 | `closeAppMatrix()` |

Order that works:

1. `getInstance` → `init(callback)`
2. Inside `onServiceConnected`: `register(Glyph.DEVICE_…)` then `openSession()`
3. `displayProgress` / `toggle` / `animate` / `setFrameColors`
4. `onDestroy`: `turnOff()` (optional), `closeSession()`, `unInit()`
5. `onServiceDisconnected`: `closeSession()`

`openSession` before the service is connected throws. `closeSession` before `register` succeeds does not throw; it logs `Non registed`.

### Matrix phones — `GlyphMatrixManager`

There is **no** `openSession` / `closeSession` on this class.

| Method | Role |
| --- | --- |
| `static GlyphMatrixManager getInstance(Context)` | Singleton |
| `void init(Callback)` | Same bind as above |
| `void unInit()` | Unbind |
| `boolean register(String target)` | `registerMatrixSDK(target)`. Phone (3): `Glyph.DEVICE_23112`. Phone (4a) Pro: `Glyph.DEVICE_25111p` |
| `void setAppMatrixFrame(int[])` | In-app frame. Use this, not `setMatrixFrame` |
| `void setAppMatrixFrame(GlyphMatrixFrame)` | Calls `frame.render()` then the same binder method |
| `void setMatrixFrame(int[] or GlyphMatrixFrame)` | Toy path. A Glyph Toy wins over app content |
| `void closeAppMatrix()` | Stop the app matrix. OS requirement documented as system version **20250801+** |
| `void setGlyphMatrixTimeout(boolean)` | Public. Timeout behaviour is not described in the README |
| `void turnOff()` | Off |

`setAppMatrixFrame` / `setMatrixFrame` throw `GlyphException("Please use it after service connected.")` if the binder is null. They do not check `mHasAuthorized` before the binder call (unlike `GlyphManager`).

Nothing’s matrix README: Glyph Toys outrank third-party app frames. A Glyph Button press opens the toy carousel and replaces your progress bar. `setAppMatrixFrame` needs system version **20250801 or later**. That check is not in the Java method we inspected; the system service enforces it.

### ProGuard

The AAR ships **no** consumer rules. Nothing’s docs do not mention ProGuard. If you minify, keep the SDK and the AIDL types so the binder and callbacks survive:

```
-keep class com.nothing.ketchum.** { *; }
-keep class com.nothing.thirdparty.** { *; }
```

That keep rule is a recommendation, not an official snippet. Mark it as unverified against a release build.

## 4. Lighting a progress value

### Dedicated zone API (this is the progress API)

All three public methods delegate to a private `displayProgress(GlyphFrame, int, boolean reverse, boolean toggle)`:

```text
void displayProgress(GlyphFrame frame, int progress)
void displayProgress(GlyphFrame frame, int progress, boolean reverse)
void displayProgressAndToggle(GlyphFrame frame, int progress, boolean isReverse)
```

README wording: progress is drawn on C1 / D1, and Phone (1) is D1 only. The 2.0 AAR also implements (2a), (3a), (4a), and (4b). The README table was not updated for those strips.

`progress` is **not documented as 0–100** in the README. The implementation treats it as a unit:

```text
budget       = progress * scale          // scale depends on device and strip
fullSegments = budget / 4096
partial      = budget % 4096
if partial < 800 and budget != 0:
    partial = 800                         // DEFAULT_MIN_LIGHT
```

Each full segment is `buildChannel(index)` (brightness **4000**). The next segment, if it is still inside the strip, is `buildChannel(index, partial)`. `reverse == false` walks from the low index toward the high index (bottom → top, or C_1 → C_24). `reverse == true` walks the other way.

`displayProgressAndToggle` also turns on the other letters that were already selected in `frame` (A/B/C/E on Phone 1, and the equivalent non-progress zones on later phones). Plain `displayProgress` does not.

The input frame is a **strip selector**. The SDK rebuilds the actual lit indexes. The anchor index must be non-zero or it throws:

| `mDevice` matches | Required non-zero index | Strip that lights | Count | `scale` | `progress == 100` fills the strip? |
| --- | --- | --- | --- | --- | --- |
| 20111 | `Code_20111.D1_1` | D1_1…D1_8 | 8 | 400 | Yes (saturates around 82) |
| 22111 | `C1_1` **xor** `D1_1` | C1_1…C1_16 **or** D1_1…D1_8 | 16 or 8 | 700 or 400 | Yes |
| 23111 or 23113 | `Code_23111.C_1` | C_1…C_24 | 24 | 1000 | Yes (about 98) |
| 24111 | any of `A_1`, `B_1`, `C_1` | that letter only; if several are set, **C wins over B wins over A** (last write) | 11 / 5 / 20 | 1000 / 700 / 1000 | A and B are already full well before 100 (A ~45, B ~29). C fills around 82 |
| 25111 | `Code_25111.A_1` | A_1…A_6 | 6 | 250 | Yes (~98) |
| 25131 | `Code_25131.A_1` | A_1…A_4 | 4 | 160 | Almost (3 full + a bright partial at 100) |
| 23112 / 25111p | none | `displayProgress` does not target the matrix | | | Use section 5 |

Throws (message is the whole error; there is no numeric code):

- `Please choose D1_1 while using display progress in 20111.`
- `Please choose C1 or D1 while using display progress in 22111` (also thrown if **both** C1_1 and D1_1 are non-zero)
- `Please choose C_1 while using display progress in 23111.`
- `Please choose A_1 while using display progress in 25111.`
- `Please choose A_1 while using display progress in 25131.`

Phone (3a) does **not** throw when A, B, and C are all off. `finalLight` stays 0 and the runnable builds an empty frame.

`period`, `cycles`, and `interval` are ignored by `displayProgress`.

### `GlyphFrame.Builder` (every public method)

Constructors:

- `Builder()` — device string = `Build.MODEL`. Defaults: `period = 0`, `cycles = 1`, `interval = 0`. Channel list length = `Common.getTargetDeviceGlyphChannelSize(MODEL)`, which falls through to **6** (Phone 4a size) for an unknown model.
- `Builder(String device)` — null device is replaced with `Glyph.DEVICE_22111` (`A065`). README: “Default value is 22111.”

| Method | Behaviour |
| --- | --- |
| `buildPeriod(int period)` | Milliseconds the frame stays on. Used by `animate`. |
| `buildCycles(int cycles)` | Repeat count. Default 1. |
| `buildInterval(int interval)` | Gap between cycles, milliseconds. |
| `buildChannel(int channel)` | `channels[channel] = 4000` |
| `buildChannel(int channel, int light)` | `channels[channel] = light`. **Not in the README.** Out of range throws `IndexOutOfBoundsException` from `ArrayList.set` |
| `buildChannelA()` … `buildChannelE()` | Set that letter’s indexes to 4000 for the builder’s device |
| `build()` | Returns `GlyphFrame` |

`GlyphFrame` getters: `getPeriod()`, `getCycles()`, `getInterval()`, `getChannel(): int[]`.

### `toggle` vs `animate` vs raw levels

| Method | What it does |
| --- | --- |
| `toggle(GlyphFrame)` | Turns on exactly the indexes in the frame (brightness from `getChannel()`). README Example 2: toggling frame 2 turns frame 1 off. |
| `animate(GlyphFrame)` | Breathing loop using `period`, `cycles`, `interval`, and the selected channels. |
| `setFrameColors(int[])` | **Public, not in the README.** Pushes a raw brightness array via `IGlyphService.setFrameColors`. 0 off, 4000 is the SDK’s “on”. Private caps in `GlyphManager`: `NO_LIGHT = 0`, `DEFAULT_MIN_LIGHT = 800`, `DEFAULT_MAX_LIGHT = 4096`. |
| `turnOff()` | All off. |

`toggle` / `animate` are the wrong tool for a 0–100 bar. Use `displayProgress` on zone phones. Use `setFrameColors` only if you want to light a prefix of indexes yourself (for example to avoid the Phone (3a) scale curve).

## 5. Glyph Matrix (Phone (3) and Phone (4a) Pro)

Manager: `GlyphMatrixManager`, not `GlyphManager`.

Frame size:

- Phone (3): `Glyph.DEVICE_23112_MATRIX_LENGTH` = **25**, buffer length **625**
- Phone (4a) Pro: `Glyph.DEVICE_25111p_MATRIX_LENGTH` = **13**, buffer length **169**
- Runtime: `Common.getDeviceMatrixLength()`

Row-major `int[]`, index `y * size + x`. Lit cells from the official helper are **4095** (`GlyphMatrixUtils` constants `ON` and `MAX_BRIGHTNESS`). A freshly allocated buffer is 0 (off). `GlyphMatrixObject.setBrightness` is a separate 0–255 knob (values above 255 are capped). That 0–255 range is for object rendering, not for `generateMatrixProgress`.

### Official progress helper

```text
public static int[] generateMatrixProgress(int size, int thickness, int progressPercent, boolean leftToRight, int[][] arrowMask)
public static int[] generateMatrixProgress(int size, int thickness, int progressPercent, boolean leftToRight, int arrowPercent, int[][] arrowMask)
```

Verified in `GlyphMatrixUtils`:

- `size <= 0` throws `IllegalArgumentException("size must be > 0")`.
- `thickness` is clamped to `1..size`.
- `progressPercent` and `arrowPercent` are clamped to `0..100`.
- The 5-arg overload calls the 6-arg one with `arrowPercent = progressPercent`.
- Return length is `size * size`.
- Filled pixels are written as **4095**.
- `arrowMask == null` is skipped (`ifnull` jumps past the arrow draw). Passing `null` is safe.
- Local names (`fillStartX`, `fillEndX`, `leftToRight`, `circleSpanAtRow`, `thickness`) show a horizontal fill with rounded ends, plus an optional arrow. The exact pixel silhouette is the SDK’s, not a rectangle you define.

App update:

```kotlin
val n = Common.getDeviceMatrixLength()
val colors = GlyphMatrixUtils.generateMatrixProgress(n, 3, percent, true, null)
matrix.setAppMatrixFrame(colors)
```

`GlyphMatrixFrame.Builder` is the toy/object path (`addTop` / `addMid` / `addLow`, max one object per layer, `build(Context)`, then `render()`). You do not need it for a progress bar. `setText`, marquee, and `GlyphMatrixNTypeUtils` are unrelated.

`closeAppMatrix()` when the UI goes away. `turnOff()` also exists.

## 6. Recommended progress frame

One code path, two backends.

**Zone phones — let `displayProgress` fill the long strip.** Select it by setting the anchor index (or the whole letter). Do not set both C and D on Phone (2).

| Phone | Register | Frame to build | Indexes the SDK walks |
| --- | --- | --- | --- |
| (1) | `Glyph.DEVICE_20111` | `buildChannel(Glyph.Code_20111.D1_1)` or `buildChannelD()` | 7, 8, 9, 10, 11, 12, 13, 14 |
| (2) | `Glyph.DEVICE_22111` | `buildChannel(Glyph.Code_22111.C1_1)` **or** `buildChannelD()` | C: 3–18 (16). D: 25–32 (8). Pick one |
| (2a), (2a) Plus | `DEVICE_23111` or `DEVICE_23113` | `buildChannel(Glyph.Code_23111.C_1)` or `buildChannelC()` | 0–23 |
| (3a), (3a) Pro | `Glyph.DEVICE_24111` | `buildChannel(Glyph.Code_24111.C_1)` or `buildChannelC()` | 0–19. Prefer C. A/B hit 100% fill early (section 4) |
| (4a) | `Glyph.DEVICE_25111` | `buildChannel(Glyph.Code_25111.A_1)` or `buildChannelA()` | 0–5 |
| (4b) | `Glyph.DEVICE_25131` | `buildChannel(Glyph.Code_25131.A_1)` or `buildChannelA()` | 0–3 |

Pass `percent` in `0..100` into `displayProgress(frame, percent)`. That matches a full strip on (1), (2), (2a), (4a), and nearly (4b). On (3a), prefer the C strip; A and B are full before the counter reaches 100 because their scale constants are large relative to the segment count.

**Matrix — do not call `displayProgress`.**

| Phone | Register | Draw |
| --- | --- | --- |
| (3) | `Glyph.DEVICE_23112` | `generateMatrixProgress(25, thickness, percent, true, null)` then `setAppMatrixFrame` |
| (4a) Pro | `Glyph.DEVICE_25111p` | same with `getDeviceMatrixLength()` (13) |

`thickness` 3 is a reasonable bar height on a 25-wide matrix. It is clamped, not a documented design constant.

Build the zone `GlyphFrame` once after `openSession`. Reuse it on every percent tick. `displayProgress` does not mutate the selector frame.

## 7. Minimal Kotlin

Matches the 2.0 AAR. Checked exceptions from Java are unchecked in Kotlin; the `try/catch` matches the README anyway.

```kotlin
import android.content.ComponentName
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixUtils

class GlyphProgressController(
    private val appContext: android.content.Context,
) {
    private val zones = GlyphManager.getInstance(appContext)
    private val matrix = GlyphMatrixManager.getInstance(appContext)
    private var progressFrame: GlyphFrame? = null
    private var useMatrix = false

    private val zoneCallback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            val ok = when {
                Common.is20111() -> zones.register(Glyph.DEVICE_20111)
                Common.is22111() -> zones.register(Glyph.DEVICE_22111)
                Common.is23111() -> zones.register(Glyph.DEVICE_23111)
                Common.is23113() -> zones.register(Glyph.DEVICE_23113)
                Common.is24111() -> zones.register(Glyph.DEVICE_24111)
                Common.is25111() -> zones.register(Glyph.DEVICE_25111)
                Common.is25131() -> zones.register(Glyph.DEVICE_25131)
                else -> false
            }
            if (!ok) return
            try {
                zones.openSession()
            } catch (e: GlyphException) {
                Log.e(TAG, e.message.orEmpty())
                return
            }
            val builder = zones.glyphFrameBuilder ?: return
            progressFrame = when {
                Common.is20111() -> builder.buildChannel(Glyph.Code_20111.D1_1)
                Common.is22111() -> builder.buildChannel(Glyph.Code_22111.C1_1)
                Common.is23111() || Common.is23113() -> builder.buildChannel(Glyph.Code_23111.C_1)
                Common.is24111() -> builder.buildChannel(Glyph.Code_24111.C_1)
                Common.is25111() -> builder.buildChannel(Glyph.Code_25111.A_1)
                Common.is25131() -> builder.buildChannel(Glyph.Code_25131.A_1)
                else -> return
            }.build()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            try {
                zones.closeSession()
            } catch (e: GlyphException) {
                Log.e(TAG, e.message.orEmpty())
            }
            progressFrame = null
        }
    }

    private val matrixCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            val id = when {
                Common.is23112() -> Glyph.DEVICE_23112
                Common.is25111p() -> Glyph.DEVICE_25111p
                else -> return
            }
            useMatrix = matrix.register(id)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            useMatrix = false
        }
    }

    fun bind() {
        if (Common.is23112() || Common.is25111p()) {
            matrix.init(matrixCallback)
        } else {
            zones.init(zoneCallback)
        }
    }

    /** @param percent 0..100 */
    fun onProgress(percent: Int) {
        val p = percent.coerceIn(0, 100)
        if (useMatrix) {
            val n = Common.getDeviceMatrixLength()
            if (n <= 0) return
            val colors = GlyphMatrixUtils.generateMatrixProgress(n, 3, p, true, null)
            matrix.setAppMatrixFrame(colors)
            return
        }
        val frame = progressFrame ?: return
        zones.displayProgress(frame, p)
    }

    fun release() {
        if (useMatrix) {
            try {
                matrix.closeAppMatrix()
            } catch (e: GlyphException) {
                Log.e(TAG, e.message.orEmpty())
            }
            matrix.unInit()
            useMatrix = false
            return
        }
        try {
            zones.turnOff()
            zones.closeSession()
        } catch (e: GlyphException) {
            Log.e(TAG, e.message.orEmpty())
        }
        zones.unInit()
        progressFrame = null
    }

    private companion object {
        const val TAG = "GlyphProgress"
    }
}
```

Call `bind()` from `onCreate` and `release()` from `onDestroy`. `onProgress` is only valid after the matching `onServiceConnected` has registered.

UNVERIFIED against a physical phone: this file was checked against the AAR, not against `GlyphService` on hardware. In particular, `setAppMatrixFrame` on a build older than system `20250801` is documented to fail, and the Phone (3a) A/B scale curve is unusual (section 4).

## 8. Emulator and non-Nothing phones

What the SDK actually does:

| Situation | Result |
| --- | --- |
| Emulator / Pixel / any non-Nothing `Build.MODEL` | All `Common.is*()` false. `getDeviceMatrixLength()` is 0. No `register` in the sample, so no session |
| `getGlyphFrameBuilder()` before `register` | Returns **null** |
| `register()` / `openSession()` with no Glyph service | `register` returns false (exception logged). `openSession` logs `Non registed` and returns |
| `openSession()` after a true `register` but a null binder | Throws `GlyphException` message `Please use it after service connected.` |
| Wrong strip for `displayProgress` | Throws `GlyphException` with one of the `Please choose …` messages in section 4 |
| `generateMatrixProgress(0, …)` | `IllegalArgumentException`: `size must be > 0` |
| Service present but the app may not bind | Android `SecurityException: Not allowed to bind to service Intent { act=com.nothing.thirdparty.bind_glyphservice pkg=com.nothing.thirdparty cmp=com.nothing.thirdparty/.GlyphService }` — reported by third-party write-up [Qiita](https://qiita.com/SousiOmine/items/23beaf685d573d5f6472), not by Nothing’s README. Typical causes: missing `com.nothing.ketchum.permission.ENABLE`, missing `NothingKey`, or debug flag off |
| Package `com.nothing.thirdparty` missing | `bindService` does not call `onServiceConnected`. UNVERIFIED whether it returns false or throws; either way the callback never succeeds |

There are no callback error codes. Log tag for both managers is the class name (`GlyphManager`, `GlyphMatrixManager`).

## 9. License and how to ship the binary

[Glyph-Developer-Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit) has **no LICENSE file**. The GitHub API `license` field is `null`. The README does not mention a license.

The license that does exist is on the matrix kit: [Glyph SDK End User License Agreement](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md) (Nothing Technology Limited, England and Wales).

Relevant terms:

- Limited, non-exclusive, non-transferable license to integrate the SDK into your applications.
- Closed source. No right to modify, decompile, or create derivative works of the SDK, except where the law says otherwise.
- Do not redistribute the SDK except as the agreement allows.
- **Section 2.2: commercial use is prohibited without prior written permission.** Contact `GDKsupport@nothing.tech` (also listed in the matrix README).
- Third-party pieces: Android (Apache 2.0) and OpenJDK (GPL-2.0 with Classpath Exception).
- Updates stay under this EULA unless a newer package ships a replacement license.

How to include the binary:

- **Vendor** `glyph-matrix-sdk-2.0.aar` from Glyph-Developer-Kit into `app/libs` and depend on the file (section 1). That is the integration path Nothing documents.
- Do **not** publish the AAR to Maven/JitPack. The EULA restricts redistribution, and there is no official remote coordinate.
- A commercial app needs a written OK from Nothing before you ship. The EULA does not spell out whether a free sideload app is “commercial”; ask `GDKsupport@nothing.tech` if that matters.

## README mistakes worth not copying

- `Glyph.B1` / `Glyph.C1_4` in Example 2 do not compile against this AAR. Use `Glyph.Code_22111.B1` and `Glyph.Code_22111.C1_4`.
- `displayProgress(..., bool reverse)` in the README table is `boolean` in bytecode.
- `Common` in the README omits `is23112()` and `is25111p()`.
- Phone (3) is absent from the zone README’s progress paragraph because it is a matrix.
- `buildChannel(int, int)` and `setFrameColors(int[])` exist and are undocumented.
- The two `glyph-matrix-sdk-2.0.aar` files in the two repos are different blobs.
