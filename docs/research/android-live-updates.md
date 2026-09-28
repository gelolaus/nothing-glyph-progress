# Android Live Updates: detecting and reading other apps

Research date: 2026-09-28. This note is about a **third-party** `NotificationListenerService` that observes Live Updates posted by other packages (Grab, Uber, delivery, navigation). Publisher APIs are included only because they are the fields the listener parses.

This is not an implementation spec for the app in this repo.

## Sources

Official pages, fetched 2026-09-28:

- [Create live update notifications (Compose)](https://developer.android.com/develop/ui/compose/notifications/live-update) — last updated **2026-09-23 UTC**
- [Create live update notifications (Views)](https://developer.android.com/develop/ui/views/notifications/live-update) — last updated **2026-08-26 UTC** (same contract)
- [Progress-centric notifications (Android 16)](https://developer.android.com/about/versions/16/features/progress-centric-notifications) — last updated **2026-03-03 UTC**
- [Android 17 features](https://developer.android.com/about/versions/17/features) — Live Update semantic color
- [Android 17 is here](https://developer.android.com/blog/posts/android-17-is-here) — 16 Jun 2026, **API level 37**
- [Notification](https://developer.android.com/reference/android/app/Notification)
- [Notification.ProgressStyle](https://developer.android.com/reference/android/app/Notification.ProgressStyle)
- [Notification.ProgressStyle.Segment](https://developer.android.com/reference/android/app/Notification.ProgressStyle.Segment) — last updated 2026-08-03
- [Notification.ProgressStyle.Point](https://developer.android.com/reference/android/app/Notification.ProgressStyle.Point) — last updated 2026-08-03
- [NotificationCompat](https://developer.android.com/reference/androidx/core/app/NotificationCompat) and [ProgressStyle](https://developer.android.com/reference/androidx/core/app/NotificationCompat.ProgressStyle)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [NotificationListenerService.Ranking](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking)
- [Settings](https://developer.android.com/reference/android/provider/Settings) — listener and promotion intents
- [Android 15 behavior changes: OTP redaction](https://developer.android.com/about/versions/15/behavior-changes-all)
- [Core Jetpack releases](https://developer.android.com/jetpack/androidx/releases/core) — stable `androidx.core` **1.19.0**
- API diffs: [35→36 Notification](https://developer.android.com/sdk/api_diff/36/changes/android.app.Notification), [36→36.1 Notification](https://developer.android.com/sdk/api_diff/36.1/changes/android.app.Notification), [36→36.1 Builder](https://developer.android.com/sdk/api_diff/36.1/changes/android.app.Notification.Builder), [36→37 Notification](https://developer.android.com/sdk/api_diff/37/changes/android.app.Notification)

AOSP-style sources (string keys that are `@hide` on the platform but present in the posted `extras`):

- [`Notification.java` on `android17-release`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/Notification.java)
- [`NotificationCompat.java` on `androidx-main`](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/core/core/src/main/java/androidx/core/app/NotificationCompat.java)

Where a constant is `@hide` in `android.app.Notification`, the listener still sees the bundle entry. Read it with the **string key** (or the public `NotificationCompat` constant of the same string). Do not depend on the hidden Java field being in the SDK stub.

---

## 1. What a Live Update is

From the Compose guide (2026-09-23):

> Live updates provide a summary of important updates so users can track progress without opening the app. The system promotes Live Update notifications although users can temporarily dismiss or demote a live update notification to a standard notification. Promoted notifications appear more prominently on system surfaces, including at the top of the notification drawer and the lock screen, and as a chip in the status bar.

Promoted cards are expanded by default and uncollapsible. Appropriate uses named in that guide: active navigation, ongoing phone calls, active rideshare tracking, active food delivery tracking. Inappropriate: ads, chat, alerts, upcoming calendar events, package tracking that does not need constant monitoring.

**Progress-centric** and **Live Update** are not the same thing.

- `Notification.ProgressStyle` is a template (segments, points, tracker icon). Android 16 introduced it. A progress-centric notification is not a Live Update until the system **promotes** it.
- A Live Update is a notification the system has promoted: `Notification.flags` has `FLAG_PROMOTED_ONGOING`. Eligible styles are standard (no style), `BigTextStyle`, `CallStyle`, `ProgressStyle`, or `MetricStyle`. A call or a chronometer can be a Live Update with no progress bar.

### API levels

| Release | API | What landed for this feature |
| --- | --- | --- |
| Android 16 | **36** | `Notification.ProgressStyle`, `FLAG_PROMOTED_ONGOING` (`0x00040000`), `hasPromotableCharacteristics()`, `getShortCriticalText()`, `NotificationManager.canPostPromotedNotifications()`, `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`. API diff [35→36](https://developer.android.com/sdk/api_diff/36/changes/android.app.Notification). |
| Android 16 minor | **36.1** | `Notification.Builder.setRequestPromotedOngoing(boolean)`, `Notification.EXTRA_REQUEST_PROMOTED_ONGOING`, `Notification.isRequestPromotedOngoing()`. API diff [36→36.1](https://developer.android.com/sdk/api_diff/36.1/changes/android.app.Notification). Without this request, `hasPromotableCharacteristics()` is false. |
| Android 17 | **37** | Semantic color API and `Notification.Metric` / `MetricStyle`. Blog: “Android 17 (API level 37)”, 16 Jun 2026. The [36→37 diff](https://developer.android.com/sdk/api_diff/37/changes/android.app.Notification) also lists `EXTRA_REQUEST_PROMOTED_ONGOING` and `isRequestPromotedOngoing()` because that diff is **from 36, not from 36.1**. Those two were already public at 36.1. |

`androidx.core` 1.17.0 added `NotificationCompat.ProgressStyle` and `Builder.setRequestPromotedOngoing()`. 1.18.0 moved `compileSdk` to 36.1. **1.19.0** (current stable) adds `MetricStyle` and semantic style.

### Promotion requirements (publisher contract a listener can observe)

Quoted from the Compose guide. A notification qualifies only if all of these hold:

- Must be Standard Style, `BigTextStyle`, `CallStyle`, `ProgressStyle`, or `MetricStyle`.
- Manifest permission `android.permission.POST_PROMOTED_NOTIFICATIONS` (install-time, not a runtime permission).
- Request promotion with `EXTRA_REQUEST_PROMOTED_ONGOING` or `NotificationCompat.Builder#setRequestPromotedOngoing`.
- `ongoing` (`FLAG_ONGOING_EVENT`).
- `contentTitle` set.
- No `customContentView` / `RemoteViews`.
- Not a group summary (`setGroupSummary`).
- Not `setColorized(true)`.
- Channel importance is not `IMPORTANCE_MIN`.

AOSP `hasPromotableCharacteristics()` (android17-release) is the structural check and does **not** include the channel or the user setting:

```text
isRequestPromotedOngoing()
&& isOngoingEvent()
&& hasTitle()
&& hasPromotableStyle()
&& !isGroupSummary()
&& !containsCustomViews()
&& !isColorizedRequested()
```

`hasPromotableStyle()` is true when the style class is null (standard), `BigTextStyle`, `CallStyle`, `MetricStyle`, or `ProgressStyle`.

The guide’s own APIs for the **posting** app:

- `Notification.FLAG_PROMOTED_ONGOING` — system bit. “Applications cannot set this flag directly, but the posting app and `NotificationListenerService` can read it.” Value **262144** (`0x00040000`). Added in API 36.
- `Notification.hasPromotableCharacteristics()` — structure only. Does not consider whether the user disabled Live Updates. Added in API 36.
- `NotificationManager.canPostPromotedNotifications()` — user/app setting for the **calling** app. A listener cannot use this to ask whether Grab is allowed; it only answers for the listener’s own uid. Added in API 36.
- The Compose/Views guides name `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`. **That field is not in the Settings reference.** The reference field is `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`), API 36, extra `Settings.EXTRA_APP_PACKAGE`. `NotificationManager.canPostPromotedNotifications()` documents that same action.

OEMs may add criteria. The guide says so. Nothing OS is one of those OEMs; this note does not describe Glyph behavior.

Users can demote a Live Update back to a normal notification. After demotion, `FLAG_PROMOTED_ONGOING` is the bit to trust. The request extra can stay true while the flag is clear.

---

## 2. Publisher API (what gets written into the notification)

### `Notification.ProgressStyle` (API 36)

Reference: [ProgressStyle](https://developer.android.com/reference/android/app/Notification.ProgressStyle). “Added in API level 36” on `addProgressSegment`.

Attach with `Notification.Builder.setStyle(Style)`. The style **overrides** `Builder.setProgress` extras when the notification is built. The reference says: “The extras set by `Notification.Builder.setProgress` will be overridden by the values set on this style object when the notification is built. Therefore, that method is not used with this style.”

| Method | Meaning |
| --- | --- |
| `setProgress(int)` | Tracker position, same units as `Segment.getLength()`. Default 0. `getProgress()` is ≥ 0. |
| `setProgressIndeterminate(boolean)` | Init state with no known amount. When true, progress, segments, points, and the tracker icon are ignored. If there is exactly one segment, its color styles the indeterminate bar. |
| `setProgressSegments(List)` / `addProgressSegment(Segment)` | Replaces or appends segments. Lengths are relative, not percents. |
| `setProgressPoints(List)` / `addProgressPoint(Point)` | Milestones. Position is in the same units as progress, relative to `getProgressMax()`. Points at 0 and at max are not drawn. |
| `setProgressTrackerIcon(Icon)` | Overlay at the current progress. Aspect ratio 2:1 to 1:2; mirrored in RTL. |
| `setProgressStartIcon(Icon)` / `setProgressEndIcon(Icon)` | Optional square icons. Not mirrored in RTL. |
| `setStyledByProgress(boolean)` | Default **true**. Ahead-of-progress segments render unfilled. False means every segment looks filled and the app must show position with the tracker icon or colors. |
| `getProgressMax()` | Sum of segment lengths. **Defaults to 100** when segments are omitted. |

AOSP limits (not all called out on the public style page): `MAX_PROGRESS_SEGMENT_LIMIT = 10` (over the limit, System UI collapses to one segment), `MAX_PROGRESS_POINT_LIMIT = 4`, `DEFAULT_PROGRESS_MAX = 100`. Segment length must be ≥ 1. Point position must be ≥ 1 to be stored; positions `<= 0` or `>= totalLength` are dropped when drawing.

`Segment`: `Segment(int length)`, `setColor(int)`, `setId(int)` (default 0), `setSemanticStyle(int)` (API 37 / flagged semantic style). `getLength()` is unitless and relative.

`Point`: `Point(int position)`, `setColor`, `setId`, `setSemanticStyle`. If both a color and a semantic style are set, **the color overrides the semantic style** (Segment and Point reference, 2026-08-03).

AndroidX equivalent, added in **androidx.core 1.17.0**: `androidx.core.app.NotificationCompat.ProgressStyle`. Same method names. Tracker and end icons take `IconCompat`. Reference note: “ProgressStyle Notifications are supported on Android 36 and above. If the SDK version is below 36, the ProgressStyle will fall back to the default notification style.” Below API 36 the user sees a normal notification. The integer extras may still be present because the compat style writes `android.progress`, `android.progressMax`, and `android.progressIndeterminate` (and the segment/point lists) into the bundle. `Builder.setProgress(int max, int progress, boolean indeterminate)` is the pre-36 progress bar and is a different code path.

The Android 16 rideshare sample (progress 456, segment lengths 41+552+253+94 = 940) is about 49% along the bar. Do not treat a segment length as a percent.

### Promotion request

```text
NotificationCompat.Builder.setRequestPromotedOngoing(true)
```

writes the boolean extra `android.requestPromotedOngoing`. Platform method `Notification.Builder.setRequestPromotedOngoing` and the field `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` are API **36.1**. `NotificationCompat` has exposed the setter since 1.17.0, so app code should call the compat method and not the raw string.

Also used on Live Updates, not progress-specific:

- `setOngoing(true)` → `flags |= FLAG_ONGOING_EVENT` (`2`, `0x2`).
- `setContentTitle` → `android.title`. Required for promotion. `MetricStyle` can satisfy “has a title” via its metrics even when `android.title` is empty (AOSP `hasTitle()`).
- `setShortCriticalText(CharSequence)` → `android.shortCriticalText`. API 36 (`getShortCriticalText` added in the 35→36 diff). Chip text. Docs suggest keeping it short; the chip is at most 96dp and shows text only when it fits (under 7 characters, the whole string; otherwise icon-only or a prefix).
- `setWhen` / `setShowWhen` / `setUsesChronometer` / `setChronometerCountDown` drive the status-bar chip timer. When-time at least 2 minutes ahead shows a countdown (“5min”). A past when-time shows no chip text. Chronometer chip text shows while the countdown stays positive.
- `setDeleteIntent` — posting app learns about dismiss. Listeners see removal via `onNotificationRemoved`, not via that intent.

---

## 3. Extras a listener can read

All of these live on `StatusBarNotification.getNotification().extras`. Types are what AOSP writes.

### Identity and template

| Constant | String key | Type | Since | Notes |
| --- | --- | --- | --- | --- |
| `Notification.EXTRA_TEMPLATE` | `android.template` | String | long-standing | `style.getClass().getName()`. Platform ProgressStyle is `android.app.Notification$ProgressStyle`. BigText is `android.app.Notification$BigTextStyle`. Call is `android.app.Notification$CallStyle`. Metric is `android.app.Notification$MetricStyle`. **Absent or null means standard style** (still promotable). |
| `NotificationCompat.EXTRA_COMPAT_TEMPLATE` | `androidx.core.app.extra.COMPAT_TEMPLATE` | String | AndroidX | Compat class name, e.g. `androidx.core.app.NotificationCompat$ProgressStyle`, `androidx.core.app.NotificationCompat$MetricStyle`. Present even when the platform rewrites `android.template`. |

### Classic progress (`Builder.setProgress` and ProgressStyle)

| Constant | String key | Type | Notes |
| --- | --- | --- | --- |
| `Notification.EXTRA_PROGRESS` | `android.progress` | int | Current progress. Public SDK field. |
| `Notification.EXTRA_PROGRESS_MAX` | `android.progressMax` | int | Max. ProgressStyle writes `getProgressMax()` here (sum of lengths, or 100). |
| `Notification.EXTRA_PROGRESS_INDETERMINATE` | `android.progressIndeterminate` | boolean | True → ignore the ints. |

ProgressStyle also writes these. The platform fields are `@hide`; the strings are public on `NotificationCompat` since 1.17.0:

| Constant | String key | Type |
| --- | --- | --- |
| `EXTRA_PROGRESS_SEGMENTS` | `android.progressSegments` | `ArrayList<Bundle>` |
| `EXTRA_PROGRESS_POINTS` | `android.progressPoints` | `ArrayList<Bundle>` |
| `EXTRA_STYLED_BY_PROGRESS` | `android.styledByProgress` | boolean, default true if the key is missing |
| `EXTRA_PROGRESS_TRACKER_ICON` | `android.progressTrackerIcon` | `Icon` (or `Icon` parcelable) |
| `EXTRA_PROGRESS_START_ICON` | `android.progressStartIcon` | `Icon` |
| `EXTRA_PROGRESS_END_ICON` | `android.progressEndIcon` | `Icon` |

Segment bundle keys (AOSP `ProgressStyle`, private constants, stable strings):

| Key | Type | Meaning |
| --- | --- | --- |
| `length` | int | Relative length. Entries ≤ 0 are skipped. |
| `id` | int | Stable id across updates. Default 0. |
| `colorInt` | int | ARGB. `Notification.COLOR_DEFAULT` is 0. |
| `semanticStyle` | int | API 37. One of the `SEMANTIC_STYLE_*` values. Omitted on older system images. |

Point bundle keys:

| Key | Type | Meaning |
| --- | --- | --- |
| `position` | int | Same units as `android.progress`. |
| `id` | int | |
| `colorInt` | int | |
| `semanticStyle` | int | API 37. |

`android.progressMax` is already the sum. Re-sum `length` only to verify. If the sum overflows, AOSP substitutes 100 and drops the segments (`ArithmeticException` around `Math.addExact`).

### Promotion, titles, text, chronometer

| Constant | String key | Type |
| --- | --- | --- |
| `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` | `android.requestPromotedOngoing` | boolean. Public at API 36.1. Compat constant since 1.17.0. This is a **request**, not proof of promotion. |
| `Notification.EXTRA_TITLE` | `android.title` | CharSequence. `setContentTitle`. |
| `Notification.EXTRA_TITLE_BIG` | `android.title.big` | CharSequence. Expanded title (`BigTextStyle.setBigContentTitle`). |
| `Notification.EXTRA_TEXT` | `android.text` | CharSequence. `setContentText`. |
| `Notification.EXTRA_BIG_TEXT` | `android.bigText` | CharSequence. `BigTextStyle.bigText`. |
| `Notification.EXTRA_SUB_TEXT` | `android.subText` | CharSequence. Header subtext. |
| `Notification.EXTRA_SUMMARY_TEXT` | `android.summaryText` | CharSequence. |
| `Notification.EXTRA_INFO_TEXT` | `android.infoText` | CharSequence. |
| `Notification.EXTRA_TEXT_LINES` | `android.textLines` | CharSequence[]. Inbox lines. |
| `NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT` | `android.shortCriticalText` | CharSequence. `@hide` on the platform class; public on NotificationCompat. Chip text. |
| `Notification.EXTRA_SHOW_CHRONOMETER` | `android.showChronometer` | boolean. `setUsesChronometer`. |
| `Notification.EXTRA_CHRONOMETER_COUNT_DOWN` | `android.chronometerCountDown` | boolean. Default false. |
| `Notification.EXTRA_SHOW_WHEN` | `android.showWhen` | boolean. |
| `Notification.EXTRA_COLORIZED` | `android.colorized` | boolean. True disqualifies promotion. |

The chronometer base is **not** only an extra. `Notification.when` (milliseconds) is the field `setWhen` sets. `setShowWhen(false)` suppresses it on the card; the chip rules are separate.

### Android 17 metric extras

`@hide` / package-visible on the platform, same strings in AndroidX 1.19:

| String key | Type |
| --- | --- |
| `android.metrics` | `ArrayList<Bundle>` |
| `android.metrics.criticalIndex` | int. Index into that list. Compat `METRIC_INDEX_NONE = -1`. |

Each metric bundle:

| Key | Type |
| --- | --- |
| `value` | Bundle (a `MetricValue`: fixed int, time, and similar). Layout is an internal parcel. Do not depend on inner keys beyond what you have verified on a device. |
| `label` | String |
| `semanticStyle` | int, when the semantic-style flag is on |

`Notification.createSemanticStyleAnnotation(int)` (API 37) stores an `Annotation` span inside title/text. The span key in AOSP is `android.app.notification.semanticStyle`. The span value is the integer style as a string. Listeners that only read `CharSequence.toString()` drop the span. Use `Spanned.getSpans` if the color semantics matter.

Semantic style ints (API 37 diff):

| Constant | Value |
| --- | --- |
| `SEMANTIC_STYLE_UNSPECIFIED` | 0 |
| `SEMANTIC_STYLE_INFO` | 1 |
| `SEMANTIC_STYLE_SAFE` | 2 |
| `SEMANTIC_STYLE_CAUTION` | 3 |
| `SEMANTIC_STYLE_DANGER` | 4 |

### Other extras that matter for false positives

| Constant | String key | Why a listener cares |
| --- | --- | --- |
| `Notification.EXTRA_MEDIA_SESSION` | `android.mediaSession` | Media. Not a trip. |
| `Notification.EXTRA_COMPACT_ACTIONS` | `android.compactActions` | Media compact actions. |
| `Notification.EXTRA_MESSAGES` | `android.messages` | Messaging. |
| `Notification.EXTRA_CONVERSATION_TITLE` | `android.conversationTitle` | Messaging. |
| `Notification.EXTRA_CALL_TYPE` | `android.callType` | CallStyle. A promoted call is a real Live Update, and it is not a progress journey. |
| `Notification.EXTRA_IS_GROUP_CONVERSATION` | `android.isGroupConversation` | |

`lightened` copies can drop parcelable extras. See section 6.

---

## 4. Distinguishing a Live Update from ordinary progress

Check in this order.

### System promoted (the real Live Update)

```text
(notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
```

API 36+. Constant value `0x00040000`. The reference text: the system sets this bit when the notification `hasPromotableCharacteristics()` **and** the user has not disabled the feature for that app. Listeners are explicitly allowed to read it. Apps cannot set it. On `Build.VERSION.SDK_INT < 36` the bit is never set; do not read the field without a compile SDK of 36+, but the numeric mask is stable if you must branch.

`Notification.isRequestPromotedOngoing()` (API 36.1) is `extras.getBoolean("android.requestPromotedOngoing", false)`. A listener on an older compile SDK reads the string. **Request true + flag false** means the app asked and the system did not promote (user setting, channel `IMPORTANCE_MIN`, demotion, OEM rule, or the image is API 36.0 and does not honor the request).

There is **no** `Ranking` method for promotion. `Ranking.getRank()` is shade order (0-based), not “is live”. `Ranking.getImportance()` and `Ranking.getChannel().getImportance()` tell you whether the channel is `IMPORTANCE_MIN` (cannot be promoted). `Ranking.isSuspended()` means the posting app is suspended and the notification should be treated as hidden.

### Ongoing progress that is not promoted

Ordinary downloads have used this since API 14:

- `FLAG_ONGOING_EVENT` (`0x2`), and often `FLAG_FOREGROUND_SERVICE` (`0x40`)
- `android.progress` / `android.progressMax` / `android.progressIndeterminate`
- `android.template` **absent** or not `ProgressStyle`
- category often `progress` (`"progress"`), which the reference defines as “progress of a long-running background operation”

`ProgressStyle` plus ongoing, with or without the promoted flag, is the modern journey template. Promotion is the flag, not the style.

### Categories do not define a Live Update

The guide does not require a category. Categories are hints:

| Constant | String | Meaning in the reference |
| --- | --- | --- |
| `CATEGORY_PROGRESS` | `progress` | Long-running background operation (downloads). **Not** a Live Update signal. |
| `CATEGORY_NAVIGATION` | `navigation` | Turn-by-turn navigation. Useful context, not sufficient. |
| `CATEGORY_TRANSPORT` | `transport` | **Media** transport controls, not rideshare. |
| `CATEGORY_SERVICE` | `service` | A running background service. |
| `CATEGORY_STATUS` | `status` | Device or contextual status. |
| `CATEGORY_CALL` | `call` | Calls. Calls can be promoted Live Updates. |
| `CATEGORY_ALARM` | `alarm` | Alarm or timer. |
| `CATEGORY_STOPWATCH` | `stopwatch` | Running stopwatch. |
| `CATEGORY_WORKOUT` | `workout` | Workout tracking. Can be a legitimate Live Update. |
| `CATEGORY_LOCATION_SHARING` | `location_sharing` | Temporary location sharing. |

### Flags that are not promotion

| Flag | Value | Listener use |
| --- | --- | --- |
| `FLAG_ONGOING_EVENT` | `0x2` | Required for promotion. Also set by downloads, media, FG services. `StatusBarNotification.isOngoing()` is this bit. |
| `FLAG_NO_CLEAR` | `0x20` | User cannot swipe it away. Common on ongoing notifications. Not a Live Update bit. |
| `FLAG_FOREGROUND_SERVICE` | `0x40` | Posted as a foreground-service notification. Many downloads. Some ride apps also do this, so it is not a negative proof. |
| `FLAG_GROUP_SUMMARY` | `0x200` | Group summary. Disqualifies promotion. Skip for progress parsing. |
| `FLAG_PROMOTED_ONGOING` | `0x40000` | The Live Update bit. |

Custom views (`contentView` / `bigContentView` / `headsUpContentView` non-null) disqualify promotion. Legacy Uber-style `RemoteViews` notifications will not be Live Updates and usually will not carry `android.progress`.

---

## 5. `NotificationListenerService` lifecycle

Reference: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService). The class itself dates to API 18. Callbacks run on the main thread from API 24 (`N`) onward.

### Manifest

The permission is a **service attribute**, not a `uses-permission` the app is granted:

```xml
<service
    android:name=".LiveUpdateListener"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

`BIND_NOTIFICATION_LISTENER_SERVICE` is a signature-level permission the system holds in order to bind you. The user still has to enable the listener.

Do **not** copy the reference’s sample `meta-data` that sets `android.service.notification.disabled_filter_types` to `ongoing|silent`. Live Updates are ongoing. `FLAG_FILTER_TYPE_ONGOING` is defined as important ongoing notifications (importance above `IMPORTANCE_MIN`). Disabling that type hides the notifications you want. Omit filter meta-data unless you have a reason to drop conversations or silent notifications.

`android:exported` must be false. The intent action is `android.service.notification.NotificationListenerService` (`SERVICE_INTERFACE`).

### Grant UI

`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` = `"android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"`.

> Activity Action: Show Notification listener settings. Input: Nothing. In some cases, a matching Activity may not exist, so ensure you safeguard against this.

There is also `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (`"android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"`) with extra `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` to toggle one component. Prefer the list screen if the detail intent does not resolve.

Check access with `NotificationManager.isNotificationListenerAccessGranted(ComponentName)` (the Settings page points at this). Listeners in a **work profile are ignored**. A device-policy controller can block work-profile notifications. On low-RAM devices running Android 10 (Q) and below, listeners are not bound.

### Callbacks

| Method | When |
| --- | --- |
| `onListenerConnected()` | Listener enabled and bound. **Only now** is `getActiveNotifications()` safe. Call it here so a trip that was already on screen is not missed. |
| `onListenerDisconnected()` | No further events. The only legal call is `requestRebind(ComponentName)`. |
| `requestRebind(ComponentName)` | Static. Safe **before** `onListenerConnected` and **after** `onListenerDisconnected`. |
| `onNotificationPosted(StatusBarNotification)` | Posted or updated (same id/tag replaces in place and posts again). |
| `onNotificationPosted(StatusBarNotification, RankingMap)` | Same, plus a ranking snapshot. |
| `onNotificationRemoved(StatusBarNotification)` | Removed. API 18. |
| `onNotificationRemoved(StatusBarNotification, RankingMap)` | |
| `onNotificationRemoved(StatusBarNotification, RankingMap, int reason)` | `REASON_CANCEL` (user swipe), `REASON_APP_CANCEL`, `REASON_APP_CANCEL_ALL`, `REASON_CLICK`, `REASON_CHANNEL_BANNED`, and others. |
| `onNotificationRankingUpdate(RankingMap)` | Order or importance changed without a new post. |
| `getActiveNotifications()` | Active notifications visible to this listener for the current user. |
| `getActiveNotifications(String[] keys)` | Subset by key. |
| `getCurrentRanking()` | Latest `RankingMap` if you are outside a callback. |

The reference: “The service should wait for the `onListenerConnected()` event before performing any operations. The `requestRebind(ComponentName)` method is the only one that is safe to call before `onListenerConnected()` or after `onListenerDisconnected()`.”

`RankingMap.getRanking(key, outRanking)` fills a `Ranking` you allocate. `getOrderedKeys()` is shade order. Ranking objects are snapshots; they do not update in place.

Removed notifications are explicitly light:

> The `StatusBarNotification` object you receive will be "light"; that is, the result from `StatusBarNotification.getNotification` may be missing some heavyweight fields such as `Notification.contentView` and `Notification.largeIcon`.

Use the key (`StatusBarNotification.getKey()`), package, id, and tag to drop state. Do not re-parse progress off the removed object.

---

## 6. What listeners do not reliably get

### Android 15 (API 35) sensitive-content redaction

[Behavior changes: all apps](https://developer.android.com/about/versions/15/behavior-changes-all), still current as of the page’s 2026-09-16 update:

> Android will stop untrusted apps that implement a `NotificationListenerService` from reading unredacted content from notifications where an OTP has been detected. Trusted apps such as companion device manager associations are exempt from these restrictions.

That is OTP / assistant-marked sensitive content, not ride progress. AOSP `Adjustment.KEY_SENSITIVE_CONTENT` (`"key_sensitive_content"`) is the assistant signal: when true, “sensitive notification content is redacted from updates to most `NotificationListenerService`s” starting in `VANILLA_ICE_CREAM` (API 35). Title and text of those notifications can be blank or replaced. Progress ints are not OTP text. Do not expect message bodies, verification codes, or people extras from a sensitive notification.

Screen-share hiding is separate. If the posting app set `setPublicVersion()`, insecure contexts see that stand-in. Otherwise content is redacted for the remote viewer. The listener’s binder copy is the NLS path above, not the screen-share bitmap.

### Light payloads

`Notification.lightenPayload()` (AOSP, `@hide`) nulls `contentView`, `bigContentView`, `headsUpContentView`, ticker view, and large icon, then **removes parcelable extras** (`Parcelable`, arrays, `SparseArray`, `ArrayList`) except TV extender and `android.metrics`. `ArrayList<Bundle>` segment and point lists are parcelable lists, so a lightened notification can lose `android.progressSegments`, `android.progressPoints`, and the tracker `Icon` while keeping the int keys `android.progress` and `android.progressMax`. `onNotificationRemoved` is documented to be light. `onNotificationPosted` is not documented as light; treat posted extras as the source of truth and tolerate missing parcelable lists.

Bugreport redaction (`shouldRedactStringExtra`) keeps `android.template` and the compat template key and redacts other strings. That path is for dumps, not for NLS.

### Lock-screen visibility is not listener redaction

`Notification.visibility` (`VISIBILITY_PUBLIC`, `VISIBILITY_PRIVATE`, `VISIBILITY_SECRET`) and `Ranking.getLockscreenVisibilityOverride()` control the lock screen and similar untrusted surfaces. A granted listener still receives `extras` for a private notification, except for the API 35 sensitive/OTP case.

### Reliably available on `onNotificationPosted`

| Field | API |
| --- | --- |
| `StatusBarNotification.getPackageName()` | 18 |
| `getId()`, `getTag()`, `getKey()`, `getPostTime()`, `getUser()` | 18 (`getKey` is the stable id for updates) |
| `isOngoing()`, `isClearable()` | 18 |
| `Notification.flags`, including ongoing and, on API 36+, promoted | |
| `Notification.when` | the `setWhen` timestamp |
| `Notification.category` | may be null |
| `Notification.extras` string and int keys: title, text, big text, sub text, progress, progress max, indeterminate, chronometer booleans, template, request-promoted | ints survive lightening; CharSequences survive unless the notification is sensitive |
| `Notification.channelId` | 26, may be empty on older posts |
| Ranking importance, rank, suspended, channel | snapshot passed into the callback |

Not reliable:

- `RemoteViews` contents (and Live Updates are not allowed to use them)
- `Icon` parcels and segment/point lists on **removed** or otherwise lightened notifications
- Full message lists, OTP text, people, pictures
- Another app’s `canPostPromotedNotifications()` result
- Proof that a demoted notification was once promoted, unless you remembered the key
- `getPostTime()` as “last update” on every OEM (it is the original post time; updates may keep it)

---

## 7. Detection heuristic

Goal: “any app’s live update”, degrading when the publisher is old or the user demoted the card. This will include calls, workouts, and timers when Android itself treats them as Live Updates. It will also include downloads if you apply the loose tiers with no exclusions.

Run on `onNotificationPosted` and on each `getActiveNotifications()` entry. Drop the key on `onNotificationRemoved`.

**Skip always**

- `FLAG_GROUP_SUMMARY`
- `Ranking.isSuspended() == true`
- Own package if you only want other apps (the local sample in section 9 is the exception)
- Template or compat template contains `MediaStyle`, or `extras` contains `android.mediaSession`, or `category == "transport"` (media)

**Tier A — promoted ongoing.** If `(flags & FLAG_PROMOTED_ONGOING) != 0`:

- This is a Live Update, including after you have confirmed the bit on API 36+.
- Read progress from Tier B’s parser if those extras exist.
- If `android.progressIndeterminate` is true, or ProgressStyle is indeterminate: state is indeterminate, not 0%.
- If there is no progress extra and the template is `CallStyle` or `MetricStyle` or a chronometer: it is a Live Update with no trip percent. Do not invent a percent from the clock.
- False positives for a *ride/delivery* product: promoted calls, timers, workouts, navigation that is a Live Update but whose text is “200 m” rather than a trip percent. For “any live update” these are true positives.
- False negatives: user demoted the card; user turned off Live Updates for that app; OEM did not set the bit; device is below API 36; app never requested promotion.

**Tier B — ProgressStyle or progress extras, and ongoing.** Else if `isOngoing()` and any of:

- `android.template` or `androidx.core.app.extra.COMPAT_TEMPLATE` contains `ProgressStyle`
- `android.progressSegments` or `android.progressPoints` is present
- `extras` contains `android.progress` or `android.progressMax` (key present, not merely default 0)

Then:

- Indeterminate if `android.progressIndeterminate` is true.
- Else if segments exist, max = sum of positive `length` values (fall back to `android.progressMax`, then to 100 if the sum is missing or overflows).
- Else max = `android.progressMax` if &gt; 0, else 100.
- Percent = `progress * 100 / max`, clamped 0..100.
- False positives: Play Store, downloads, backup, podcast downloads, any ongoing `setProgress` notification. `category == "progress"` plus `FLAG_FOREGROUND_SERVICE` plus **no** `ProgressStyle` template is the usual download shape. Excluding every foreground-service notification will also drop ride apps that post the journey as their FGS notification. Prefer “exclude `progress` category unless the template is `ProgressStyle`” over a blanket FGS ban, and accept that some downloaders set no category.
- False negatives: `RemoteViews`-only notifications with no extras. Live Updates are not supposed to be custom, but older Grab/Uber builds predate the API.

**Tier C — percent in text.** Else if `isOngoing()` and title, text, big text, or short critical text contains a percentage:

- Match a number followed by `%` or the word `percent`. Example: `72%`, `72 %`, `72 percent`.
- Do **not** treat `8 min`, `5m`, `200 m`, `2 km`, or a bare integer as a percent.
- Clamp 0..100. Several percents: prefer the first in title, then text, then big text.
- False positives: battery, storage, “50% off” if that notification is ongoing, download text that Tier B should already have caught (“45%”). Ongoing is a weak filter; many status notifications are ongoing.
- False negatives: “5 min away”, “Arriving”, “Picking up your order”, with no number and no progress extras. Those are indeterminate if you keep them at all. Do not map ETA minutes onto a percent without a baseline you measured yourself.

**Suggested confidence**

1. Tier A with a parsed percent or indeterminate progress.
2. Tier A with no numeric progress (call, metric, timer).
3. Tier B with a `ProgressStyle` template.
4. Tier B with only classic `setProgress` extras (likely a download unless the package is a known journey app).
5. Tier C.

`android.requestPromotedOngoing == true` with the flag clear is a hint to rank Tier B above a random download, not proof. The system is the authority for the flag.

---

## 8. Minimal listener and parser (AndroidX)

Dependency and SDK (current stable as of 2026-09-28):

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        targetSdk = 37
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
}
```

`minSdk` 26 is for notification channels on the sample poster. The listener API exists at 18. `FLAG_PROMOTED_ONGOING` is only set on API 36+. `compileSdk` 37 is required to reference that field and the API 37 semantic constants without reflection. Progress **string keys** come from `NotificationCompat`, which is the right source because several platform keys are `@hide`.

```kotlin
class LiveUpdateListener : NotificationListenerService() {
    override fun onListenerConnected() {
        activeNotifications.orEmpty().forEach { consider(it) }
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, LiveUpdateListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        consider(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // sbn.notification is light here. Drop by sbn.key only.
    }

    private fun consider(sbn: StatusBarNotification) {
        val parsed = parse(sbn) ?: return
        // parsed.tier, parsed.percent, parsed.indeterminate, sbn.packageName
    }
}
```

```kotlin
data class ParsedUpdate(
    val tier: String,
    val percent: Int?,
    val indeterminate: Boolean,
)

fun parse(sbn: StatusBarNotification): ParsedUpdate? {
    val n = sbn.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    val extras = n.extras ?: return null
    if (isMedia(n, extras)) return null

    val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
    val promoted = Build.VERSION.SDK_INT >= 36 &&
        n.flags and Notification.FLAG_PROMOTED_ONGOING != 0
    if (!ongoing && !promoted) return null

    val progress = readProgress(extras)
    val tier = when {
        promoted -> "promoted"
        progress != null -> "progress"
        ongoing && percentInText(extras) != null -> "text"
        else -> return null
    }
    val percent = progress?.percent ?: percentInText(extras)
    val indeterminate = progress?.indeterminate == true
    return ParsedUpdate(tier, if (indeterminate) null else percent, indeterminate)
}

private data class ProgressReading(val percent: Int?, val indeterminate: Boolean)

private fun readProgress(extras: Bundle): ProgressReading? {
    val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
    val compat = extras.getString(NotificationCompat.EXTRA_COMPAT_TEMPLATE).orEmpty()
    val style = template.contains("ProgressStyle") || compat.contains("ProgressStyle")
    val hasClassic = extras.containsKey(Notification.EXTRA_PROGRESS) ||
        extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
    val segments = extras.getParcelableArrayList<Bundle>(NotificationCompat.EXTRA_PROGRESS_SEGMENTS)
    if (!style && !hasClassic && segments.isNullOrEmpty()) return null
    if (extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
        return ProgressReading(null, true)
    }
    val sum = segments.orEmpty().sumOf { it.getInt("length").coerceAtLeast(0) }
    val max = when {
        sum > 0 -> sum
        extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 ->
            extras.getInt(Notification.EXTRA_PROGRESS_MAX)
        else -> 100
    }
    val cur = extras.getInt(Notification.EXTRA_PROGRESS).coerceIn(0, max)
    return ProgressReading(cur * 100 / max, false)
}

private fun percentInText(extras: Bundle): Int? {
    val blob = listOf(
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
        NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT,
    ).joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }
    val match = Regex("""(\d{1,3})\s*(%|percent)""", RegexOption.IGNORE_CASE)
        .find(blob) ?: return null
    return match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
}

private fun isMedia(n: Notification, extras: Bundle): Boolean {
    val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
    return n.category == Notification.CATEGORY_TRANSPORT ||
        extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
        template.contains("MediaStyle")
}
```

`getParcelableArrayList` with a class token needs API 33. On older devices use the deprecated untyped overload. Segment lists are empty on a lightened notification; classic ints still work.

Grant intent:

```kotlin
startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
```

---

## 9. Local sample Live Update (same app, emulator)

Use this to verify the listener without Grab. The **posting** app needs:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

`POST_NOTIFICATIONS` is a runtime permission on API 33+. `POST_PROMOTED_NOTIFICATIONS` is install-time. Channel importance must not be `IMPORTANCE_MIN`.

```kotlin
fun postSample(context: Context) {
    val channelId = "sample-live"
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(channelId) == null) {
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Sample live", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Sample trip")
        .setContentText("40% complete")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setRequestPromotedOngoing(true)
        .setShortCriticalText("40%")
        .setStyle(
            NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                .setProgress(40)
                .setProgressSegments(
                    listOf(NotificationCompat.ProgressStyle.Segment(100)),
                ),
        )
        .build()
    NotificationManagerCompat.from(context).notify(42, notification)
}
```

On API 36+ the listener should see `android.template` = `android.app.Notification$ProgressStyle`, `android.progress` = 40, `android.progressMax` = 100, `android.requestPromotedOngoing` = true, and segment bundle `length` = 100.

`FLAG_PROMOTED_ONGOING` is set only when the system accepts it. `NotificationManager.canPostPromotedNotifications()` (API 36) is false until the user allows it. Send them to:

```kotlin
Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
```

That activity does not exist on every image; resolve it before `startActivity`. The guides’ name `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` is not in the SDK.

Emulator: use an **API 37** image (Android 17, current stable) or at least **API 36.1**. `ProgressStyle` exists at API 36. The promotion **request** API was added at 36.1. Reports from developers (not from the platform guide) say an API 36.0 image shows the notification and does not promote it. The listener can still assert Tier B on 36.0.

Cancel with `NotificationManagerCompat.from(context).cancel(42)` and confirm `onNotificationRemoved`. Update by calling `notify(42, …)` again with a new progress. The same key is posted again; do not treat that as a second journey.

---

## Android 17 delta (listeners)

Android 17 did **not** replace the API 36 promotion contract. `FLAG_PROMOTED_ONGOING`, the ongoing flag, and `android.progress*` still mean the same thing.

What API 37 adds:

- Qualifying style `MetricStyle` (`android.app.Notification$MetricStyle` / compat `androidx.core.app.NotificationCompat$MetricStyle`). Extras `android.metrics` and `android.metrics.criticalIndex`. A promoted metric notification may have **no** progress bar. Tier A still catches it. Tier B will not.
- Semantic styles on text (`createSemanticStyleAnnotation`), on `ProgressStyle.Segment`, on `ProgressStyle.Point`, and on `Notification.Metric`. New bundle int `semanticStyle`. If the app sets a semantic style and leaves color at default, `colorInt` is 0 and the system picks the color. Percent math must use `length` and `android.progress`, not color.
- If both color and semantic style are set, color wins (platform reference).
- `androidx.core:core-ktx:1.19.0` can post and name these types. Detection of pre-17 Live Updates does not require them.
- Custom notification view size is restricted further for apps **targeting** API 37 (Android 17 blog). Live Updates already forbid `RemoteViews`, so a listener should not expect custom views on a promoted notification.

No API 37 change removes `FLAG_PROMOTED_ONGOING` or the progress extras.
