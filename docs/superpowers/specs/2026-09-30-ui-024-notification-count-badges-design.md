# UI-024 — Notification Count Badges on App Icons Design

- **Date:** 2026-09-30
- **Status:** Approved
- **Story:** `p2-ui-ui-024-notification-count-badges` (owner-approved 4-story delivery loop, final item — `doc/task/README.md`)
- **Target branch:** `dev`

## 1. Problem

Some apps queue up unread activity (unread mail, unread chats) that the user can't see at a
glance from home — they have to open each app to check. Real Android launchers surface this as a
small number badge on the app icon. This launcher has none.

The only cross-app mechanism to learn "how many pending items does app X have" without asking the
user to grant a highly sensitive, Play-Console-reviewed permission (`NotificationListenerService`
— requires a Restricted Permissions declaration and, for many categories, a demonstration video,
adding real review latency this project's release schedule cannot absorb) is the long-standing
de-facto **badge-broadcast convention** popularized by the `ShortcutBadger` library: a notifying
app that wants a badge sends an explicit broadcast, action `android.intent.action.BADGE_COUNT_UPDATE`,
targeted directly at whichever package is the user's current default launcher. Many messaging/mail
apps (Gmail and others) already send this. No special permission, no notification content is ever
read — only a package name and an integer the notifying app chooses to report.

## 2. Goals

- Show a small red number badge (capped display "99+") on an app's icon whenever that app has
  reported a nonzero count via the badge-broadcast convention.
- Show it in **both** places an app icon renders: the fisheye home grid (`LensView`) and the Apps
  tab list (`AppAdapter`).
- Tapping to open that app clears its badge to 0 immediately (optimistic), matching how real
  launchers behave, regardless of whether the notifying app ever sends its own "cleared" broadcast.
- Count is global across all lenses (`FISH-008`), matching `App.openCount`'s existing "by design"
  scope — not a new scoping rule.
- One Settings toggle (default on) hides/shows badges everywhere, same shape as the existing
  `KEY_SHOW_NEW_APP_TAG` toggle.
- No new dependency: the receiver is a few dozen lines against a well-known, stable Android
  broadcast action string — no `ShortcutBadger` library needed.
- **Explicit non-goal:** universal coverage. Only apps that actively send this broadcast get a
  badge (many will not: this is a known, accepted limitation of the badge-broadcast convention
  versus `NotificationListenerService`, traded deliberately for zero permission/review overhead).

## 3. Architecture

### Data — `AppPersistent.kt` / `App.kt` / `RAppsSingleton.kt`

New column, global across lens rows — exact same shape and update mechanism `OPEN_COUNT` already
uses:

```kotlin
@ColumnInfo(name = "NOTIFICATION_COUNT", defaultValue = "0")
var notificationCount: Int = 0
```

```kotlin
@JvmStatic
fun setNotificationCount(packageName: String?, name: String?, count: Int) {
    if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
    val clamped = count.coerceIn(0, MAX_STORED_NOTIFICATION_COUNT) // 9999 - spam/garbage guard
    RAppsSingleton.instance.updateAppState(packageName, name, notificationCount = clamped)
    persist {
        setNotificationCountForIdentifier(generateIdentifier(packageName, name), clamped) // all lens rows
    }
}
```

- `setNotificationCountForIdentifier` (new DAO method) is an `UPDATE ... WHERE IDENTIFIER = :id`
  with no `LENS_ID` filter — the exact same "global regardless of lens" shape
  `incrementAtomic`/`OPEN_COUNT` already uses.
- `App.kt`: `val notificationCount: Int = 0` + `fun copyWithNotificationCount(newCount: Int): App
  = copy(notificationCount = newCount)`.
- `RAppsSingleton.updateAppState(...)`: new `notificationCount: Int? = null` parameter, applied via
  the existing `copyWith*` chain in the same method.

### Receiver — new `services/BadgeCountReceiver.kt`

```xml
<receiver
    android:name=".services.BadgeCountReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BADGE_COUNT_UPDATE" />
    </intent-filter>
</receiver>
```

Static manifest registration (unlike `RApplication`'s other receivers, which are registered
programmatically because Android 8+ ignores *implicit* manifest broadcasts) — this broadcast is
**explicit**, targeted directly at this app's package by the sending app once it resolves the
current default launcher, so it is not subject to that restriction and must reach this app even
when its process isn't running.

```kotlin
class BadgeCountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val count = parseBadgeCount(intent.extras)
        if (RAppsSingleton.instance.apps.orEmpty().none { it.packageName == packageName }) return
        val app = RAppsSingleton.instance.apps.orEmpty().first { it.packageName == packageName }
        AppPersistent.setNotificationCount(packageName, app.name.toString(), count)
        AppEventManager.notifyAppsEdited() // same route AppsEditedReceiver already uses
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "badge_count_package_name"
        const val EXTRA_COUNT = "badge_count"
        // Accepted per the convention's payload shape but not read: matching by packageName
        // alone is sufficient for this feature (one badge per installed app, not per activity).
        const val EXTRA_CLASS_NAME = "badge_count_class_name"

        /** Pure, unit-testable: extras -> a validated, clamped count. Never throws. */
        @JvmStatic
        fun parseBadgeCount(extras: Bundle?): Int =
            (extras?.getInt(EXTRA_COUNT, 0) ?: 0).coerceIn(0, MAX_STORED_NOTIFICATION_COUNT)
    }
}
```

`parseBadgeCount` is a pure function taking a `Bundle` (or `null`) — fully unit-testable without a
real `BroadcastReceiver` dispatch.

### Rendering — `LensView.kt` (hot path) and `AppAdapter.java` (Apps tab)

`LensView`, mirroring the existing `drawNewAppTag` pattern exactly (paint objects allocated once
in the setup block, never per-frame — `PERF-001`):

```kotlin
private var mPaintBadgeBackground: Paint? = null
private var mPaintBadgeText: Paint? = null
```

```kotlin
private fun drawAppIcon(canvas: Canvas, rect: RectF, index: Int) {
    // ... existing icon-bitmap draw unchanged ...
    if (app.installDate >= ... ) { drawNewAppTag(canvas, rect) } // unchanged

    if (mUtilSettings?.getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES) == true
        && app.notificationCount > 0) {
        drawNotificationBadge(canvas, rect, app.notificationCount)
    }
}

private fun drawNotificationBadge(canvas: Canvas, rect: RectF, count: Int) {
    val label = if (count > 99) "99+" else count.toString()
    val cx = rect.right - resources.getDimension(R.dimen.radius_notification_badge)
    val cy = rect.top + resources.getDimension(R.dimen.radius_notification_badge)
    mPaintBadgeBackground?.let {
        canvas.drawCircle(cx, cy, resources.getDimension(R.dimen.radius_notification_badge), it)
    }
    mPaintBadgeText?.let { canvas.drawText(label, cx, cy - (it.ascent() + it.descent()) / 2, it) }
}
```

`AppAdapter`, new `view_item_app.xml` sibling of `ivAppIcon` (`RelativeLayout` already supports
corner-overlap alignment, no `FrameLayout` wrapper needed):

```xml
<androidx.appcompat.widget.AppCompatTextView
    android:id="@+id/tvAppNotificationBadge"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_alignTop="@id/ivAppIcon"
    android:layout_alignEnd="@id/ivAppIcon"
    android:layout_marginTop="-4dp"
    android:layout_marginEnd="-4dp"
    android:minWidth="18dp"
    android:minHeight="18dp"
    android:gravity="center"
    android:background="@drawable/bg_notification_badge"
    android:textColor="@android:color/white"
    android:textSize="10sp"
    android:textStyle="bold"
    android:visibility="gone"
    tools:text="3"
    tools:visibility="visible" />
```

Bind: `tvAppNotificationBadge.visibility = if (showBadges && app.notificationCount > 0) VISIBLE
else GONE`; text = same `count > 99 -> "99+"` formatting as `LensView`, factored into one shared
pure function (`NotificationBadgeFormatter.format(count): String`, `util/`) so both surfaces stay
in lockstep and both get one shared unit test instead of two copies of the same `if`.

### Settings — `UtilSettings.kt` + `FrmSettings`

```kotlin
const val KEY_SHOW_NOTIFICATION_BADGES = "show_notification_badges"
const val DEFAULT_SHOW_NOTIFICATION_BADGES = true
```

One new `MaterialSwitch` row in `frm_settings.xml`, wired exactly like
`KEY_SHOW_NEW_APP_TAG`'s existing row (find/click-blocker/check-changed/read-back/reset-default/
null-out — six call sites, same file, same pattern already used four times in that file).

### Clear-on-launch — `UtilApp.launchComponent(...)`

Directly beside the existing `AppPersistent.incrementAppCount(packageName, name)` call:

```kotlin
AppPersistent.setNotificationCount(packageName, name, 0)
val utilSettings = UtilSettings(context)
if (utilSettings.sortType in listOf(SortType.OPEN_COUNT_ASCENDING, SortType.OPEN_COUNT_DESCENDING)) {
    context.sendBroadcast(Intent(context, AppsEditedReceiver::class.java))
}
AppEventManager.notifyAppsEdited() // unconditional - unlike the sort-type broadcast above, the
                                    // badge must clear visually regardless of sort settings
```

One call site covers every launch surface (`LensView` tap, search, Apps tab) since all of them
already funnel through `UtilApp.launchComponent`.

## 4. Behavior

- **Broadcast for an uninstalled/unknown package:** `BadgeCountReceiver` checks the live
  `RAppsSingleton` snapshot first; no match → return immediately, nothing written, nothing
  notified. Prevents a malicious or stale broadcast from creating orphan rows.
- **Negative or absurd count:** `parseBadgeCount` clamps to `[0, 9999]` before it ever reaches
  persistence — a spam/garbage guard independent of the separate `"99+"` **display** cap (the
  real count is stored; only rendering truncates it).
- **Race (broadcast arrives mid-launch):** accepted, matches the pre-existing
  `incrementAppCount` optimistic-update race already present in this codebase for `openCount` —
  not a new risk class.
- **App uninstalled while a badge is showing:** no special handling — the app disappears from the
  snapshot entirely, so it stops being drawn, badge included.
- **Setting toggled off mid-session:** purely a render-time gate (`LensView`/`AppAdapter` both
  check the flag right before drawing/binding) — badge data keeps being written in the background
  and reappears instantly if the setting is turned back on, same as `KEY_SHOW_NEW_APP_TAG`.
- **Cross-lens:** a count set while lens A is active is visible immediately from lens B — global
  by design, proven at runtime (not just by column-scoping) by a dedicated integration test, the
  same way `FEAT-009`'s cross-lens recency proof works.

## 5. Tests

### Unit

1. `AppPersistentNotificationCountTest` — `setNotificationCount` round-trips; a value set while
   lens A is active reads back identically from a row created for lens B (global, not per-lens).
2. `BadgeCountReceiverParserTest` — `parseBadgeCount`: valid count passes through; negative → 0;
   above 9999 → clamped to 9999; missing extra → 0. Pure `Bundle` input, no real broadcast needed.
3. `NotificationBadgeFormatterTest` — `1`..`99` → exact string; `100`, `9999` → `"99+"`. (Callers
   never invoke this for `count == 0` — both surfaces gate visibility on `count > 0` before
   formatting — so a `0` case isn't part of this function's contract.)
4. `UtilSettingsNotificationBadgeToggleTest` — default `true`, round-trips `false`/`true`, key is
   stable (same four-case shape as `UtilSettingsSearchBarToggleTest`).

### Widget

1. `BadgeCountReceiverWidgetTest` — a broadcast Intent for a package **not** in the current
   `RAppsSingleton` snapshot is dropped (no Room row created, `AppEventManager` not fired); one
   for a real installed package writes the clamped count and fires the event.
2. `LensViewNotificationBadgeWidgetTest` — an app with `notificationCount > 0` and the setting on
   triggers the badge-paint draw path; `notificationCount == 0` or the setting off does not
   (mirrors `LensGridCacheTest`'s no-real-pixel-assert, state-based verification style).
3. `AppAdapterNotificationBadgeWidgetTest` — bind with count `0` → `tvAppNotificationBadge` GONE;
   count `3` → visible, text `"3"`; count `150` → visible, text `"99+"`; setting off → GONE
   regardless of count.
4. `FrmSettingsNotificationBadgeWidgetTest` — on by default, reflects a previously-saved `false`,
   toggling persists, reset-to-default turns it back on (four cases, same shape as
   `FrmSettingsDepthOfFieldWidgetTest`).

### Integration

1. `LaunchClearsNotificationBadgeIntegrationTest` — seed a real nonzero count via
   `AppPersistent.setNotificationCount`, launch the app through the real
   `UtilApp.launchComponent` path, confirm both `RAppsSingleton` and the real Room row read back
   `0`, and that `AppEventManager`'s `appsEdited` event actually fired (an observer records it).
2. Cross-lens integration case — extends/mirrors `ActHomeMultiLensRecentAppsIntegrationTest`'s
   shape: set a count while lens A is active, read it back from a fresh query scoped to lens B's
   row, confirm identical value (proves global-by-design at the DB layer, not just by convention).
3. `AllStringsTranslationTest` (existing, unchanged) — covers the new setting-label string and any
   new content-description string across all 16 locales; no new test needed, matching the
   correction already recorded for `FEAT-009`.

### Smoke (session-locked device)

1. Trigger a real badge-broadcast-sending app (e.g. Gmail with an unread message) — confirm the
   badge appears on both the fisheye grid icon and the Apps tab row with the correct count.
2. Tap that app's icon — confirm the badge clears immediately, before the app even finishes
   opening.
3. Toggle the new Settings switch off — confirm both surfaces stop showing the badge instantly;
   toggle back on — confirm it reappears without needing to reopen the launcher.
4. Switch to a second lens, confirm the same badge/count shows there too.

## 6. Non-Goals

- `NotificationListenerService` support (deliberately excluded — Play Console Restricted
  Permissions review/video-demo overhead was the explicit reason to choose the broadcast
  convention instead).
- Any OEM `ContentProvider`-based badge variant (some older Samsung/HTC devices use a provider
  write instead of a broadcast) — smaller, older device share; can be added later as a strict
  addition if ever requested, without touching this design.
- Per-lens badge scoping — stays global, matching `openCount`.
- User-configurable display cap or badge color/position — fixed "99+", fixed corner, fixed color,
  matching how the existing `KEY_SHOW_NEW_APP_TAG` dot is similarly non-configurable.
