# UI-024 Notification Count Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a real number badge (capped "99+") on an app's icon, on both the fisheye home
grid and the Apps tab, whenever that app reports a nonzero count via the de-facto
`BADGE_COUNT_UPDATE` broadcast convention — no `NotificationListenerService`, no special
permission, no Play Console Restricted Permissions review.

**Architecture:** A statically-registered `BadgeCountReceiver` validates and clamps an incoming
broadcast, writes the count through a new `AppPersistent.NOTIFICATION_COUNT` column (global
across lenses, exact same "no `LENS_ID` filter" shape `PALETTE_COLOR` already uses), and fires
the existing `AppEventManager.notifyAppsEdited()` event so `ActHome`/`FrmApps` refresh through
their already-wired observers. `LensView` and `AppAdapter` both read `App.notificationCount` at
render time. Opening an app clears its count to 0 through the one shared `UtilApp.launchComponent`
path every launch surface already uses.

**Tech Stack:** Kotlin + Java (existing mixed codebase), Room, `BroadcastReceiver`,
`AppEventManager` (LiveData), JUnit4 + Robolectric (unit), AndroidJUnit4 instrumented tests
(widget/integration, no Espresso — API 37 removed `InputManager.getInstance`, see existing test
docstrings).

## Global Constraints

- No `NotificationListenerService`, no new runtime/dangerous permission, no Play Console
  Restricted Permissions declaration — this is the explicit reason the badge-broadcast
  convention was chosen over the alternative.
- Notification count is global across all lenses (`FISH-008`) — same "no `LENS_ID` filter"
  scope `OPEN_COUNT`/`PALETTE_COLOR` already use, not a new per-lens concept.
- Coverage is deliberately partial: only apps that actively send the `BADGE_COUNT_UPDATE`
  broadcast get a badge. This is a known, accepted, documented limitation — never described as a
  bug or "fixed" later without an explicit owner decision to add `NotificationListenerService` or
  an OEM `ContentProvider` fallback (both out of scope here).
- Every implementation case needs unit, widget, and integration coverage per this repo's
  `doc/task/README.md` test-layer rule; a Tecno smoke pass on the session-locked device closes
  the loop.
- Every new/changed translatable string must exist, with matching placeholders and real
  (non-English) text, in all 16 non-English locales — `AllStringsTranslationTest` (existing,
  unmodified) already enforces this for every translatable string in `values/strings.xml`.
- Device policy (current owner decision): **TECNO only** for build/run/install/smoke — TECNO KJ7
  (`115333744A005844`) preferred, TECNO BG6 (`118743744X002560`) standing fallback if KJ7 drops.
  Never fall back to Pixel/S24U even if attached and idle. If no TECNO is attached, stop and ask.
- Spec: `docs/superpowers/specs/2026-09-30-ui-024-notification-count-badges-design.md`.

---

### Task 1: Data layer — `AppPersistent` column, DAO, `App`, `RAppsSingleton`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/model/AppPersistent.kt`
- Modify: `app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/model/App.kt`
- Modify: `app/src/main/java/com/mckimquyen/app/RAppsSingleton.kt`
- Test: `app/src/test/java/com/mckimquyen/model/AppPersistentRoomTest.kt` (existing file, extend)
- Test: `app/src/test/java/com/mckimquyen/app/RAppsSingletonTest.kt` (existing file, extend)

**Interfaces:**
- Produces: `AppPersistent.notificationCount: Int` (column `NOTIFICATION_COUNT`, default 0);
  `AppPersistentDao.setNotificationCount(defaults: AppPersistent, count: Int)` (transaction);
  `AppPersistent.setNotificationCount(packageName: String?, name: String?, count: Int)` (static,
  clamps to `[0, 9999]`); `App.notificationCount: Int = 0`;
  `App.copyWithNotificationCount(newCount: Int): App`; `RAppsSingleton.updateAppState(...,
  notificationCount: Int? = null)`.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/mckimquyen/model/AppPersistentRoomTest.kt` (inside the class,
reusing its existing `persistent()`/`db`/`dao` fields):

```kotlin
    @Test
    fun `setNotificationCount writes and reads back the exact value`() = runBlocking {
        val defaults = persistent("com.example.badge", "MainActivity", openCount = 0)

        dao.setNotificationCount(defaults, 5)

        assertEquals(5, dao.findByIdentifier(defaults.identifier)?.notificationCount)
    }

    @Test
    fun `setNotificationCount is global across lenses, not scoped to one`() = runBlocking {
        val onDefaultLens = persistent("com.example.crosslens", "MainActivity", openCount = 0)
        val onSecondLens = onDefaultLens.copy(lensId = "second")
        dao.insertIfAbsent(onDefaultLens)
        dao.insertIfAbsent(onSecondLens)

        dao.setNotificationCount(onDefaultLens, 7)

        assertEquals(7, dao.findByIdentifier("second", onDefaultLens.identifier)?.notificationCount)
    }

    @Test
    fun `setNotificationCount creates a row if none exists yet`() = runBlocking {
        val defaults = persistent("com.example.firstbadge", "MainActivity", openCount = 0)

        dao.setNotificationCount(defaults, 3)

        assertEquals(3, dao.findByIdentifier(defaults.identifier)?.notificationCount)
    }
```

Append to `app/src/test/java/com/mckimquyen/app/RAppsSingletonTest.kt` (using its existing
`createTestApp` helper):

```kotlin
    @Test
    fun `test updateAppState applies notification count`() {
        val app = createTestApp("com.example.badge")
        RAppsSingleton.getInstance().apps = arrayListOf(app)

        RAppsSingleton.getInstance().updateAppState(
            app.packageName.toString(),
            app.name.toString(),
            notificationCount = 4
        )

        assertEquals(4, RAppsSingleton.getInstance().findApp(app.packageName.toString(), app.name.toString())?.notificationCount)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.model.AppPersistentRoomTest"
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.app.RAppsSingletonTest"
```

Expected: FAIL to compile — `notificationCount`/`setNotificationCount` don't exist yet.

- [ ] **Step 3: Implement**

In `AppPersistent.kt`, add the column (placed directly after `pinnedZone`, before `lensId` closes
the constructor):

```kotlin
    @ColumnInfo(name = "NOTIFICATION_COUNT", defaultValue = "0")
    var notificationCount: Int = 0,
```

Add the static method directly below `incrementAppCount`:

```kotlin
        private const val MAX_STORED_NOTIFICATION_COUNT = 9999

        @JvmStatic
        fun setNotificationCount(packageName: String?, name: String?, count: Int) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val clamped = count.coerceIn(0, MAX_STORED_NOTIFICATION_COUNT)
            RAppsSingleton.instance.updateAppState(packageName, name, notificationCount = clamped)
            persist {
                setNotificationCount(defaults(packageName, name), clamped)
            }
        }
```

In `AppPersistentDao.kt`, add directly below `updatePaletteColor`/`setPaletteColor` (same shape):

```kotlin
    @Query("UPDATE APP_PERSISTENT SET NOTIFICATION_COUNT = :count WHERE IDENTIFIER = :identifier")
    suspend fun updateNotificationCount(identifier: String, count: Int)
```

and, next to `setPaletteColor` in the `@Transaction` block:

```kotlin
    @Transaction
    suspend fun setNotificationCount(defaults: AppPersistent, count: Int) {
        insertIfAbsent(defaults.copy(notificationCount = count))
        updateNotificationCount(defaults.identifier, count)
    }
```

In `App.kt`, add the field (end of constructor, after `lensId`) and the copy method (next to
`copyWithOrder`):

```kotlin
    val notificationCount: Int = 0
```

```kotlin
    fun copyWithNotificationCount(newCount: Int): App = copy(notificationCount = newCount)
```

In `RAppsSingleton.kt`, extend `updateAppState`:

```kotlin
    fun updateAppState(
        packageName: String,
        name: String,
        isOpened: Boolean? = null,
        isVisible: Boolean? = null,
        openCount: Long? = null,
        paletteColor: Int? = null,
        notificationCount: Int? = null
    ) {
        synchronized(this) {
            val list = mApps ?: return
            for (i in list.indices) {
                val app = list[i]
                if (app.packageName.toString() == packageName && app.name.toString() == name) {
                    list[i] = app.copyWithLockAndVisibility(
                        newOpened = isOpened ?: app.isOpened,
                        newVisible = isVisible ?: app.isVisible,
                        newOpenCount = openCount ?: app.openCount
                    ).copyWithPaletteColor(paletteColor ?: app.paletteColor)
                        .copyWithNotificationCount(notificationCount ?: app.notificationCount)
                    break
                }
            }
        }
    }
```

- [ ] **Step 4: Run the tests again to verify they pass**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.model.AppPersistentRoomTest"
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.app.RAppsSingletonTest"
```

Expected: `BUILD SUCCESSFUL`, all cases pass (existing + 4 new).

- [ ] **Step 5: Full unit regression** (this file/singleton is touched by many features)

```bash
./gradlew testDevDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/model/AppPersistent.kt app/src/main/java/com/mckimquyen/model/AppPersistentDao.kt app/src/main/java/com/mckimquyen/model/App.kt app/src/main/java/com/mckimquyen/app/RAppsSingleton.kt app/src/test/java/com/mckimquyen/model/AppPersistentRoomTest.kt app/src/test/java/com/mckimquyen/app/RAppsSingletonTest.kt
git commit -m "feat(ui-024): add global notification-count column, DAO op, and app-state wiring"
```

---

### Task 2: Pure helpers — `parseBadgeCount` and `NotificationBadgeFormatter`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/services/BadgeCountReceiver.kt` (companion object +
  parsing only in this task; `onReceive` body comes in Task 3 once the receiver can reach a real
  `RAppsSingleton` snapshot check — but the class and its pure parser exist now so Task 3 only
  adds the dispatch logic, not a new file)
- Create: `app/src/main/java/com/mckimquyen/util/NotificationBadgeFormatter.kt`
- Test: `app/src/test/java/com/mckimquyen/services/BadgeCountReceiverParserTest.kt`
- Test: `app/src/test/java/com/mckimquyen/util/NotificationBadgeFormatterTest.kt`

**Interfaces:**
- Produces: `BadgeCountReceiver.parseBadgeCount(extras: Bundle?): Int`;
  `NotificationBadgeFormatter.format(count: Int): String`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/mckimquyen/services/BadgeCountReceiverParserTest.kt`:

```kotlin
package com.mckimquyen.services

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BadgeCountReceiverParserTest {

    @Test
    fun `valid count passes through unchanged`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, 3) }
        assertEquals(3, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `negative count clamps to zero`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, -5) }
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `count above the spam guard clamps to the ceiling`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, 50000) }
        assertEquals(9999, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `missing extra defaults to zero`() {
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(Bundle()))
    }

    @Test
    fun `null extras defaults to zero`() {
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(null))
    }
}
```

Create `app/src/test/java/com/mckimquyen/util/NotificationBadgeFormatterTest.kt`:

```kotlin
package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBadgeFormatterTest {

    @Test
    fun `counts one through ninety-nine format as the exact number`() {
        assertEquals("1", NotificationBadgeFormatter.format(1))
        assertEquals("42", NotificationBadgeFormatter.format(42))
        assertEquals("99", NotificationBadgeFormatter.format(99))
    }

    @Test
    fun `counts above ninety-nine cap to the display ceiling`() {
        assertEquals("99+", NotificationBadgeFormatter.format(100))
        assertEquals("99+", NotificationBadgeFormatter.format(9999))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.services.BadgeCountReceiverParserTest"
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.NotificationBadgeFormatterTest"
```

Expected: FAIL to compile — neither class exists yet.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/mckimquyen/services/BadgeCountReceiver.kt`:

```kotlin
package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * UI-024: receives the de-facto "badge count" broadcast convention (popularized by
 * ShortcutBadger) that some notifying apps (Gmail and others) send, explicitly targeted at the
 * current default launcher's package, when their own badge count changes. This is intentionally
 * NOT a NotificationListenerService: no notification content is ever read, no special/dangerous
 * permission is required, and no Play Console Restricted Permissions review applies - the
 * tradeoff is partial coverage (only apps that actively send this broadcast get a badge).
 *
 * Registered statically in AndroidManifest.xml (not programmatically, unlike this app's other
 * receivers - see BroadcastReceivers.kt) because this broadcast is explicit (the sender targets
 * this app's package directly), so it is not subject to the Android 8+ implicit-broadcast
 * background-execution limits that made RApplication switch its own receivers to programmatic
 * registration; an explicit broadcast to a manifest-declared receiver still wakes this app's
 * process even when it isn't running.
 */
class BadgeCountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Dispatch logic added in Task 3, once RAppsSingleton snapshot validation is wired in.
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "badge_count_package_name"
        const val EXTRA_COUNT = "badge_count"

        // Accepted per the convention's payload shape but not read: matching by packageName
        // alone is sufficient for this feature (one badge per installed app, not per activity).
        const val EXTRA_CLASS_NAME = "badge_count_class_name"

        private const val MAX_STORED_NOTIFICATION_COUNT = 9999

        /** Pure, unit-testable: extras -> a validated, clamped count. Never throws. */
        @JvmStatic
        fun parseBadgeCount(extras: Bundle?): Int =
            (extras?.getInt(EXTRA_COUNT, 0) ?: 0).coerceIn(0, MAX_STORED_NOTIFICATION_COUNT)
    }
}
```

Create `app/src/main/java/com/mckimquyen/util/NotificationBadgeFormatter.kt`:

```kotlin
package com.mckimquyen.util

/**
 * UI-024: shared display-cap formatting so LensView (fisheye grid) and AppAdapter (Apps tab)
 * render the exact same text for the exact same count - one function, not two copies of the
 * same `if`. Never called for count == 0 - both render sites gate visibility on count > 0 first.
 */
object NotificationBadgeFormatter {
    private const val DISPLAY_CEILING = 99

    @JvmStatic
    fun format(count: Int): String = if (count > DISPLAY_CEILING) "99+" else count.toString()
}
```

- [ ] **Step 4: Run the tests again to verify they pass**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.services.BadgeCountReceiverParserTest"
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.NotificationBadgeFormatterTest"
```

Expected: `BUILD SUCCESSFUL`, 5 + 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/services/BadgeCountReceiver.kt app/src/main/java/com/mckimquyen/util/NotificationBadgeFormatter.kt app/src/test/java/com/mckimquyen/services/BadgeCountReceiverParserTest.kt app/src/test/java/com/mckimquyen/util/NotificationBadgeFormatterTest.kt
git commit -m "feat(ui-024): add badge-count extras parser and shared display formatter"
```

---

### Task 3: `BadgeCountReceiver` dispatch + Manifest registration

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/services/BadgeCountReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/com/mckimquyen/services/BadgeCountReceiverWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 1): `RAppsSingleton.instance.apps`, `AppPersistent.setNotificationCount(...)`.
- Consumes (existing): `AppEventManager.notifyAppsEdited()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/mckimquyen/services/BadgeCountReceiverWidgetTest.kt`:

```kotlin
package com.mckimquyen.services

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024: no Espresso needed - a BroadcastReceiver's onReceive() is called directly (the exact
 * same real production method a real broadcast dispatch would invoke), matching this project's
 * established convention of calling production handler methods directly where the test device
 * cannot build Espresso's event injector (API 37 removed InputManager.getInstance).
 */
@RunWith(AndroidJUnit4::class)
class BadgeCountReceiverWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val receiver = BadgeCountReceiver()

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        RAppsSingleton.instance.apps = ArrayList()
    }

    @After
    fun tearDown() = runBlocking {
        RAppsSingleton.instance.apps = ArrayList()
    }

    @Test
    fun broadcastForAnInstalledAppWritesTheClampedCountAndFiresAppsEdited() {
        val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        RAppsSingleton.instance.apps = arrayListOf(app)
        var eventFired = false
        val observer = androidx.lifecycle.Observer<Any?> { eventFired = true }
        AppEventManager.appsEdited.observeForever(observer)

        val intent = Intent(BadgeCountReceiver.ACTION_BADGE_COUNT_UPDATE).apply {
            putExtra(BadgeCountReceiver.EXTRA_PACKAGE_NAME, "pkg.camera")
            putExtra(BadgeCountReceiver.EXTRA_COUNT, 5)
        }
        receiver.onReceive(context, intent)
        // AppPersistent.setNotificationCount persists asynchronously on Dispatchers.IO via
        // ApplicationScope, not on the main thread - waitForIdleSync() alone only drains the
        // main thread's queue. This exact wait shape (idle-sync + a short fixed buffer) is this
        // codebase's own established pattern for the same async-Room-write timing gap
        // (see ActHomeLensManagementWidgetTest's idle() helper).
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)

        assertEquals(5, RAppsSingleton.instance.findApp("pkg.camera", "CameraActivity")?.notificationCount)
        val stored = AppDatabase.getInstance().appPersistentDao()
            .findByIdentifier(AppPersistent.generateIdentifier("pkg.camera", "CameraActivity"))
        assertEquals(5, stored?.notificationCount)
        assertEquals(true, eventFired)

        AppEventManager.appsEdited.removeObserver(observer)
    }

    @Test
    fun broadcastForAPackageNotInTheSnapshotIsDropped() = runBlocking {
        RAppsSingleton.instance.apps = ArrayList() // nothing installed per the snapshot

        val intent = Intent(BadgeCountReceiver.ACTION_BADGE_COUNT_UPDATE).apply {
            putExtra(BadgeCountReceiver.EXTRA_PACKAGE_NAME, "pkg.unknown")
            putExtra(BadgeCountReceiver.EXTRA_COUNT, 5)
        }
        receiver.onReceive(context, intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val stored = AppDatabase.getInstance().appPersistentDao()
            .findAnyByIdentifier(AppPersistent.generateIdentifier("pkg.unknown", ""))
        assertNull(stored)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew assembleDevDebugAndroidTest 2>&1 | tail -30
```

Expected: FAIL to compile — `ACTION_BADGE_COUNT_UPDATE` doesn't exist yet, `onReceive` does
nothing yet (second test would pass vacuously, but the module won't compile until the first
test's symbols exist).

- [ ] **Step 3: Implement**

In `BadgeCountReceiver.kt`, add the action constant and the real dispatch body:

```kotlin
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val app = RAppsSingleton.instance.apps.orEmpty()
            .firstOrNull { it.packageName.toString() == packageName } ?: return
        val count = parseBadgeCount(intent.extras)
        AppPersistent.setNotificationCount(packageName, app.name.toString(), count)
        AppEventManager.notifyAppsEdited()
    }
```

Add the imports (`com.mckimquyen.app.RAppsSingleton`, `com.mckimquyen.model.AppPersistent`) and,
in the companion object, the action string:

```kotlin
        const val ACTION_BADGE_COUNT_UPDATE = "android.intent.action.BADGE_COUNT_UPDATE"
```

In `AndroidManifest.xml`, inside `<application>`, directly after the last existing `<receiver>`
(`BackgroundChangedReceiver`/`NightModeReceiver` block):

```xml
        <!-- UI-024: unlike the receivers above (registered programmatically in RApplication
             because Android 8+ ignores IMPLICIT manifest broadcasts), this one is explicit - the
             sending app targets this package directly once it resolves the current default
             launcher - so a static manifest declaration correctly wakes this app even when its
             process isn't running. -->
        <receiver
            android:name=".services.BadgeCountReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BADGE_COUNT_UPDATE" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 4: Run the test again to verify it passes**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/*.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.services.BadgeCountReceiverWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (2 tests)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/services/BadgeCountReceiver.kt app/src/main/AndroidManifest.xml app/src/androidTest/java/com/mckimquyen/services/BadgeCountReceiverWidgetTest.kt
git commit -m "feat(ui-024): dispatch BADGE_COUNT_UPDATE broadcasts, register receiver statically"
```

---

### Task 4: `LensView` rendering (fisheye grid)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Modify: `app/src/main/res/values/dimen.xml`
- Test: `app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt` (existing file, extend
  with the new pure function - despite the filename, this file already hosts every
  `@VisibleForTesting internal fun` pure-logic test for `LensView`, e.g. `shouldTriggerLongPress`)
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewNotificationBadgeWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 2): `NotificationBadgeFormatter.format(count)`.
- Produces: `LensView.shouldDrawNotificationBadge(showBadgesSetting: Boolean, notificationCount:
  Int): Boolean` (`@VisibleForTesting internal`, companion); `LensView.badgeDrawCallCount: Int`
  (`@VisibleForTesting`, mirrors `LensGridCache.recomputeCount`'s "production field doubling as a
  test hook" pattern).

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt`:

```kotlin
    // ==================================================================== shouldDrawNotificationBadge

    @Test
    fun `badge draws when setting is on and count is positive`() {
        assertTrue(LensView.shouldDrawNotificationBadge(showBadgesSetting = true, notificationCount = 1))
    }

    @Test
    fun `badge does not draw when the setting is off`() {
        assertFalse(LensView.shouldDrawNotificationBadge(showBadgesSetting = false, notificationCount = 5))
    }

    @Test
    fun `badge does not draw when count is zero`() {
        assertFalse(LensView.shouldDrawNotificationBadge(showBadgesSetting = true, notificationCount = 0))
    }

    @Test
    fun `badge does not draw when count is negative`() {
        assertFalse(LensView.shouldDrawNotificationBadge(showBadgesSetting = true, notificationCount = -1))
    }
```

Create `app/src/androidTest/java/com/mckimquyen/views/LensViewNotificationBadgeWidgetTest.kt`:

```kotlin
package com.mckimquyen.views

import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024: state-based verification only (no real pixel assertion), mirroring
 * LensGridCacheTest's "production counter field doubling as a test hook" convention - not
 * LensViewDepthOfFieldWidgetTest's pixel-sampling convention, which this feature doesn't need.
 */
@RunWith(AndroidJUnit4::class)
class LensViewNotificationBadgeWidgetTest {

    @Test
    fun drawingAnAppWithAPositiveCountInvokesTheBadgeDrawPath() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(com.mckimquyen.R.id.lensViews)
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                lensView.setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity", notificationCount = 3)))
                val before = lensView.badgeDrawCallCount

                lensView.draw(Canvas())

                assertEquals(before + 1, lensView.badgeDrawCallCount)
            }
        }
    }

    @Test
    fun drawingAnAppWithZeroCountNeverInvokesTheBadgeDrawPath() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(com.mckimquyen.R.id.lensViews)
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                lensView.setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity", notificationCount = 0)))
                val before = lensView.badgeDrawCallCount

                lensView.draw(Canvas())

                assertEquals(before, lensView.badgeDrawCallCount)
            }
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.views.LensViewGestureStateTest"
```

Expected: FAIL to compile — `shouldDrawNotificationBadge` doesn't exist.

```bash
./gradlew assembleDevDebugAndroidTest 2>&1 | tail -30
```

Expected: FAIL to compile — `badgeDrawCallCount`/`KEY_SHOW_NOTIFICATION_BADGES` don't exist yet
(the setting key is added in Task 6, but this test references it now; if Task 6 hasn't landed yet
in your working order, stub the key in this task instead — do not reorder Tasks 4 and 6, add the
two-line `UtilSettings` key here as a small out-of-order dependency and let Task 6 find it already
present rather than re-adding it).

Add to `UtilSettings.kt` now (pulled forward from Task 6 to unblock this test — Task 6 will find
this already done and only add the Settings UI around it):

```kotlin
        const val KEY_SHOW_NOTIFICATION_BADGES = "show_notification_badges"
        const val DEFAULT_SHOW_NOTIFICATION_BADGES = true
```

and in `getBoolean`:

```kotlin
        KEY_SHOW_NOTIFICATION_BADGES -> prefs.getBoolean(name, DEFAULT_SHOW_NOTIFICATION_BADGES)
```

- [ ] **Step 3: Implement**

In `LensView.kt`, add the paint fields next to `mPaintNewAppTag`:

```kotlin
    private var mPaintBadgeBackground: Paint? = null
    private var mPaintBadgeText: Paint? = null

    /** Test hook only - mirrors LensGridCache.recomputeCount's convention of a production
     *  counter field standing in for a real pixel assertion. */
    @androidx.annotation.VisibleForTesting
    var badgeDrawCallCount: Int = 0
        private set
```

In `setupPaints()`, directly after the `mPaintNewAppTag` block:

```kotlin
        mPaintBadgeBackground = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.colorPrimary)
        }

        mPaintBadgeText = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = resources.getDimension(R.dimen.text_size_notification_badge)
            typeface = Typeface.DEFAULT_BOLD
        }
```

In `drawAppIcon`, directly after the existing `drawNewAppTag(canvas, rect)` block:

```kotlin
                if (shouldDrawNotificationBadge(
                        mUtilSettings?.getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES) == true,
                        app.notificationCount
                    )
                ) {
                    drawNotificationBadge(canvas, rect, app.notificationCount)
                }
```

New method, directly below `drawNewAppTag`:

```kotlin
    private fun drawNotificationBadge(canvas: Canvas, rect: RectF, count: Int) {
        badgeDrawCallCount++
        val radius = resources.getDimension(R.dimen.radius_notification_badge)
        val cx = rect.right - radius
        val cy = rect.top + radius
        mPaintBadgeBackground?.let { canvas.drawCircle(cx, cy, radius, it) }
        mPaintBadgeText?.let { p ->
            val label = com.mckimquyen.util.NotificationBadgeFormatter.format(count)
            canvas.drawText(label, cx, cy - (p.ascent() + p.descent()) / 2, p)
        }
    }
```

In the companion object, next to `shouldTriggerEmptySpaceLongPress`:

```kotlin
        /** UI-024: pure gating decision, unit-testable without a real Canvas/Paint. */
        @androidx.annotation.VisibleForTesting
        internal fun shouldDrawNotificationBadge(showBadgesSetting: Boolean, notificationCount: Int): Boolean =
            showBadgesSetting && notificationCount > 0
```

In `app/src/main/res/values/dimen.xml`, add next to `radius_new_app_tag`:

```xml
    <dimen name="radius_notification_badge">10dp</dimen>
    <dimen name="text_size_notification_badge">9sp</dimen>
```

- [ ] **Step 4: Run the tests again to verify they pass**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.views.LensViewGestureStateTest"
```

Expected: `BUILD SUCCESSFUL`.

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/*.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.views.LensViewNotificationBadgeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (2 tests)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/main/res/values/dimen.xml app/src/test/java/com/mckimquyen/views/LensViewGestureStateTest.kt app/src/androidTest/java/com/mckimquyen/views/LensViewNotificationBadgeWidgetTest.kt
git commit -m "feat(ui-024): draw notification-count badge on the fisheye grid icon"
```

---

### Task 5: `AppAdapter` rendering (Apps tab)

**Files:**
- Modify: `app/src/main/res/layout/view_item_app.xml`
- Modify: `app/src/main/java/com/mckimquyen/adt/AppAdapter.java`
- Create: `app/src/main/res/drawable/bg_notification_badge.xml`
- Test: `app/src/androidTest/java/com/mckimquyen/adt/AppAdapterNotificationBadgeWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 2): `NotificationBadgeFormatter.format(count)`.
- Consumes (Task 1/4): `App.notificationCount`, `UtilSettings.KEY_SHOW_NOTIFICATION_BADGES`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/mckimquyen/adt/AppAdapterNotificationBadgeWidgetTest.kt`,
following the exact real-`RecyclerView`-attach convention `AppAdapterWidgetTest` already uses
(construct with the app list, attach to a real measured/laid-out `RecyclerView` so the adapter's
own real `onBindViewHolder` runs — not a hand-built holder):

```kotlin
package com.mckimquyen.adt

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActSettings
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppAdapterNotificationBadgeWidgetTest {

    private fun app(pkg: String, count: Int) =
        App(packageName = pkg, name = pkg, label = pkg, notificationCount = count)

    private fun bindSingleApp(activity: android.app.Activity, count: Int): View {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = AppAdapter(activity, mutableListOf(app("pkg.camera", count)))
        recyclerView.adapter = adapter
        recyclerView.measure(0, 0)
        recyclerView.layout(0, 0, 1080, 400)
        return recyclerView.findViewHolderForAdapterPosition(0)!!.itemView
    }

    @Test
    fun bindingAnAppWithACountShowsTheBadgeWithTheFormattedText() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                val itemView = bindSingleApp(activity, 150)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.VISIBLE, badge.visibility)
                assertEquals("99+", badge.text.toString())
            }
        }
    }

    @Test
    fun bindingAnAppWithZeroCountHidesTheBadge() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
                val itemView = bindSingleApp(activity, 0)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.GONE, badge.visibility)
            }
        }
    }

    @Test
    fun settingOffHidesTheBadgeRegardlessOfCount() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilSettings(activity).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
                val itemView = bindSingleApp(activity, 3)

                val badge = itemView.findViewById<TextView>(R.id.tvAppNotificationBadge)
                assertEquals(View.GONE, badge.visibility)
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew assembleDevDebugAndroidTest 2>&1 | tail -30
```

Expected: FAIL to compile — `R.id.tvAppNotificationBadge` doesn't exist yet.

- [ ] **Step 3: Implement**

Create `app/src/main/res/drawable/bg_notification_badge.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/colorPrimary" />
</shape>
```

In `view_item_app.xml`, add directly after the `ivAppIcon` element:

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
            android:paddingHorizontal="4dp"
            android:gravity="center"
            android:background="@drawable/bg_notification_badge"
            android:textColor="@android:color/white"
            android:textSize="10sp"
            android:textStyle="bold"
            android:visibility="gone"
            android:importantForAccessibility="no"
            tools:text="3"
            tools:visibility="visible" />
```

In `AppAdapter.java`, add the field next to `ivAppIcon` (around line 433):

```java
        TextView tvAppNotificationBadge;
```

`findViewById` next to `ivAppIcon`'s (around line 468):

```java
        this.tvAppNotificationBadge = itemView.findViewById(R.id.tvAppNotificationBadge);
```

Bind logic in `setAppElement(App app)`, directly after the existing icon-bitmap set (around line
496):

```java
        boolean showBadges = new com.mckimquyen.util.UtilSettings(itemView.getContext())
                .getBoolean(com.mckimquyen.util.UtilSettings.KEY_SHOW_NOTIFICATION_BADGES);
        if (showBadges && app.getNotificationCount() > 0) {
            tvAppNotificationBadge.setText(com.mckimquyen.util.NotificationBadgeFormatter.format(app.getNotificationCount()));
            tvAppNotificationBadge.setVisibility(View.VISIBLE);
        } else {
            tvAppNotificationBadge.setVisibility(View.GONE);
        }
```

(`App` is a Kotlin `data class`, so Java sees `getNotificationCount()` for the `notificationCount`
property automatically - no extra Kotlin-side change needed for Java interop here.)

- [ ] **Step 4: Run the test again to verify it passes**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/*.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.adt.AppAdapterNotificationBadgeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (3 tests)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/view_item_app.xml app/src/main/java/com/mckimquyen/adt/AppAdapter.java app/src/main/res/drawable/bg_notification_badge.xml app/src/androidTest/java/com/mckimquyen/adt/AppAdapterNotificationBadgeWidgetTest.kt
git commit -m "feat(ui-024): render notification-count badge on the Apps tab row"
```

---

### Task 6: Settings toggle UI + accessibility label + strings

**Files:**
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Modify: `app/src/main/java/com/mckimquyen/views/LensAccessibilityHelper.kt`
- Modify: `app/src/main/res/values/strings.xml` (+ all 16 locales)
- Create: `app/src/main/res/drawable/ic_notifications_24dp.xml`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsNotificationBadgeWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 4, pulled forward): `UtilSettings.KEY_SHOW_NOTIFICATION_BADGES`,
  `DEFAULT_SHOW_NOTIFICATION_BADGES` (already added in Task 4's Step 2).

- [ ] **Step 1: Add strings**

`app/src/main/res/values/strings.xml`, next to `setting_show_new_app_tag`:

```xml
    <string name="setting_show_notification_badges">Show notification badges</string>
    <string name="notification_badge_count_description">%1$s, %2$d unread</string>
```

All 16 locales (same insertion point, next to each locale's `setting_show_new_app_tag`):

```
values-ar: <string name="setting_show_notification_badges">إظهار شارات الإشعارات</string>
           <string name="notification_badge_count_description">%1$s، %2$d غير مقروءة</string>
values-de: <string name="setting_show_notification_badges">Benachrichtigungs-Badges anzeigen</string>
           <string name="notification_badge_count_description">%1$s, %2$d ungelesen</string>
values-es: <string name="setting_show_notification_badges">Mostrar insignias de notificación</string>
           <string name="notification_badge_count_description">%1$s, %2$d sin leer</string>
values-fr: <string name="setting_show_notification_badges">Afficher les badges de notification</string>
           <string name="notification_badge_count_description">%1$s, %2$d non lus</string>
values-hi: <string name="setting_show_notification_badges">सूचना बैज दिखाएं</string>
           <string name="notification_badge_count_description">%1$s, %2$d अपठित</string>
values-in: <string name="setting_show_notification_badges">Tampilkan lencana notifikasi</string>
           <string name="notification_badge_count_description">%1$s, %2$d belum dibaca</string>
values-it: <string name="setting_show_notification_badges">Mostra badge di notifica</string>
           <string name="notification_badge_count_description">%1$s, %2$d non lette</string>
values-ja: <string name="setting_show_notification_badges">通知バッジを表示</string>
           <string name="notification_badge_count_description">%1$s、未読%2$d件</string>
values-km: <string name="setting_show_notification_badges">បង្ហាញផ្លាកសញ្ញាការជូនដំណឹង</string>
           <string name="notification_badge_count_description">%1$s មិនទាន់អាន %2$d</string>
values-ko: <string name="setting_show_notification_badges">알림 배지 표시</string>
           <string name="notification_badge_count_description">%1$s, 읽지 않음 %2$d개</string>
values-lo: <string name="setting_show_notification_badges">ສະແດງປ້າຍແຈ້ງເຕືອນ</string>
           <string name="notification_badge_count_description">%1$s, %2$d ຍັງບໍ່ໄດ້ອ່ານ</string>
values-pt: <string name="setting_show_notification_badges">Mostrar emblemas de notificação</string>
           <string name="notification_badge_count_description">%1$s, %2$d não lidas</string>
values-ru: <string name="setting_show_notification_badges">Показывать значки уведомлений</string>
           <string name="notification_badge_count_description">%1$s, непрочитано: %2$d</string>
values-th: <string name="setting_show_notification_badges">แสดงป้ายแจ้งเตือน</string>
           <string name="notification_badge_count_description">%1$s, ยังไม่อ่าน %2$d รายการ</string>
values-vi: <string name="setting_show_notification_badges">Hiện huy hiệu thông báo</string>
           <string name="notification_badge_count_description">%1$s, %2$d chưa đọc</string>
values-zh: <string name="setting_show_notification_badges">显示通知徽章</string>
           <string name="notification_badge_count_description">%1$s，%2$d 条未读</string>
```

- [ ] **Step 2: Write the failing test**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsNotificationBadgeWidgetTest.kt`
(exact same shape as `FrmSettingsDepthOfFieldWidgetTest`, default `true` not `false`):

```kotlin
package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsNotificationBadgeWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() { rawPrefs().edit().clear().commit() }

    @After
    fun tearDown() { rawPrefs().edit().clear().commit() }

    @Test
    fun switchIsOn_whenSettingHasNeverBeenSaved() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertTrue(sw.isChecked)
            }
        }
    }

    @Test
    fun switchIsOff_whenSettingWasPreviouslySavedFalse() {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertFalse(sw.isChecked)
            }
        }
    }

    @Test
    fun togglingTheSwitch_persistsTheNewValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                sw.isChecked = false
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES))
    }

    @Test
    fun resetToDefaults_turnsItBackOn() {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swShowNotificationBadges)
                assertTrue(sw.isChecked)
            }
        }
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew assembleDevDebugAndroidTest 2>&1 | tail -30
```

Expected: FAIL to compile — `R.id.swShowNotificationBadges` doesn't exist yet.

- [ ] **Step 4: Implement**

Create `app/src/main/res/drawable/ic_notifications_24dp.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorPrimary"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
```

In `frm_settings.xml`, directly after the `swShowNewAppTag` `<RelativeLayout>` block and its
divider:

```xml
                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="52dp"
                    app:dividerColor="?attr/colorOutlineVariant" />

                <!-- UI-024: Show Notification Count Badges -->
                <RelativeLayout
                    android:id="@+id/rlSwitchShowNotificationBadgesParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">

                    <ImageView
                        android:id="@+id/iconShowNotificationBadges"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_notifications_24dp" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swShowNotificationBadges"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconShowNotificationBadges"
                        android:paddingTop="12dp"
                        android:paddingBottom="12dp"
                        android:text="@string/setting_show_notification_badges"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />

                </RelativeLayout>
```

In `FrmSettings.kt`, seven touch points mirroring `swShowNewAppTag` exactly:

```kotlin
    private var swShowNotificationBadges: SwitchCompat? = null
```
```kotlin
        swShowNotificationBadges = view.findViewById(R.id.swShowNotificationBadges)
```
```kotlin
        view.findViewById<View>(R.id.rlSwitchShowNotificationBadgesParent).setOnClickListener(null)
```
```kotlin
        swShowNotificationBadges?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, isChecked)
        }
```
```kotlin
            swShowNotificationBadges?.isChecked = us.getBoolean(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES)
```
```kotlin
            us.save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, UtilSettings.DEFAULT_SHOW_NOTIFICATION_BADGES)
```
```kotlin
        swShowNotificationBadges = null
```

In `LensAccessibilityHelper.kt`, extend the `label` computation inside
`onPopulateNodeForVirtualView` (directly replacing `val label = app.label.toString()`):

```kotlin
        val label = if (app.notificationCount > 0) {
            host.context.getString(
                R.string.notification_badge_count_description,
                app.label.toString(),
                app.notificationCount
            )
        } else {
            app.label.toString()
        }
```

(Add `import com.mckimquyen.R` if not already present in that file.)

- [ ] **Step 5: Run the test again to verify it passes**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/*.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsNotificationBadgeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (4 tests)`.

- [ ] **Step 6: Run the localization unit test**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.a11y.AllStringsTranslationTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/frm_settings.xml app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/main/java/com/mckimquyen/views/LensAccessibilityHelper.kt app/src/main/res/values*/strings.xml app/src/main/res/drawable/ic_notifications_24dp.xml app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsNotificationBadgeWidgetTest.kt
git commit -m "feat(ui-024): add Settings toggle and accessibility label for notification badges"
```

---

### Task 7: Clear-on-launch wiring

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilApp.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/util/LaunchClearsNotificationBadgeIntegrationTest.kt` (new)

**Interfaces:**
- Consumes (Task 1): `AppPersistent.setNotificationCount(...)`.
- Consumes (existing): `AppEventManager.notifyAppsEdited()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/mckimquyen/util/LaunchClearsNotificationBadgeIntegrationTest.kt`:

```kotlin
package com.mckimquyen.util

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.ui.ActHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchClearsNotificationBadgeIntegrationTest {

    @Test
    fun launchingAnAppWithAnExistingBadgeClearsItToZero() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        // Use this app's own package/activity as the launch target - always installed, always
        // resolvable, real launchComponent path exercised end-to-end.
        val packageName = context.packageName
        val name = ActHome::class.java.name
        AppPersistent.setNotificationCount(packageName, name, 7)
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Test", packageName = packageName, name = name, notificationCount = 7)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilApp.launchComponent(activity, packageName, "Test", name, null, null)
            }
            // Same async-persist wait shape as BadgeCountReceiverWidgetTest - launchComponent's
            // AppPersistent.setNotificationCount(..., 0) call persists on a background
            // coroutine, not synchronously on this thread.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(250)

            assertEquals(0, RAppsSingleton.instance.findApp(packageName, name)?.notificationCount)
            val stored = AppDatabase.getInstance().appPersistentDao()
                .findByIdentifier(AppPersistent.generateIdentifier(packageName, name))
            assertEquals(0, stored?.notificationCount)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.util.LaunchClearsNotificationBadgeIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL — count still `7` (launch doesn't clear it yet).

- [ ] **Step 3: Implement**

In `UtilApp.kt`, add the import `com.mckimquyen.services.AppEventManager` and, inside
`launchComponent`'s `launch()` inner function, directly after the existing
`AppPersistent.incrementAppCount(packageName, name)` line:

```kotlin
            AppPersistent.setNotificationCount(packageName, name, 0)
```

and directly after the existing sort-type-conditional `context.sendBroadcast(...)` block (still
inside the same `try`):

```kotlin
            AppEventManager.notifyAppsEdited()
```

- [ ] **Step 4: Run the test again to verify it passes**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/*.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.util.LaunchClearsNotificationBadgeIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (1 test)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilApp.kt app/src/androidTest/java/com/mckimquyen/util/LaunchClearsNotificationBadgeIntegrationTest.kt
git commit -m "feat(ui-024): clear an app's notification badge the moment it's launched"
```

---

### Task 8: Cross-lens integration test, full regression, lint, device smoke, backlog

**Files:**
- Test: `app/src/androidTest/java/com/mckimquyen/model/NotificationCountCrossLensIntegrationTest.kt` (new)

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/mckimquyen/model/NotificationCountCrossLensIntegrationTest.kt`:

```kotlin
package com.mckimquyen.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCountCrossLensIntegrationTest {

    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAllForLens("second").filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
        dao.getAll().filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
    }

    @Test
    fun countSetWhileOneLensIsActiveReadsIdenticallyFromAnotherLens() = runBlocking {
        val onDefaultLens = AppPersistent(
            packageName = "pkg.camera",
            name = "CameraActivity",
            identifier = IDENTIFIER
        )
        val onSecondLens = onDefaultLens.copy(lensId = "second")
        dao.insertIfAbsent(onDefaultLens)
        dao.insertIfAbsent(onSecondLens)

        // Calls the suspend DAO method directly (awaited here) rather than the
        // AppPersistent.setNotificationCount static wrapper, which persists asynchronously on a
        // background coroutine this test's own runBlocking block cannot await - this proves the
        // same global-scope DB behavior without that unrelated timing race.
        dao.setNotificationCount(onDefaultLens, 6)

        assertEquals(6, dao.findByIdentifier("second", IDENTIFIER)?.notificationCount)
        assertEquals(6, dao.findByIdentifier(IDENTIFIER)?.notificationCount) // default lens
    }

    private companion object {
        const val IDENTIFIER = "pkg.camera-CameraActivity"
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.model.NotificationCountCrossLensIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected at this point: this should already PASS, since Task 1 already implements the global
update — if it fails, that's a real regression to fix before continuing (most likely cause:
`setNotificationCount`'s `insertIfAbsent` accidentally scoped by `LENS_ID` somewhere it
shouldn't be — re-check the DAO query has no `LENS_ID` filter).

- [ ] **Step 3: Full regression pass**

```bash
./gradlew testDevDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 0 failures.

```bash
adb -s <locked-device-serial> shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: full instrumented suite passes (aside from any already-disclosed, pre-existing,
unrelated flake documented in `doc/task/README.md`). If a new flake surfaces, apply this
codebase's established fix pattern (re-set any fake `RAppsSingleton`/DB state immediately before
the point that actually consumes it, not just at test setup) before concluding it's unrelated —
do not dismiss a new failure as "flaky" without that check first.

```bash
./gradlew lintDevDebug
```

Expected: 0 errors; warning count unchanged or lower.

- [ ] **Step 4: Device smoke test**

On the session-locked TECNO device, with the freshly-built debug APK installed:

1. Send a real test broadcast via `adb shell am broadcast` targeting the app's own package with
   the `BADGE_COUNT_UPDATE` action and a real installed app's package/count extras — confirm the
   badge appears with the correct number on both the fisheye grid icon and the Apps tab row.
2. Tap that app's icon — confirm the badge disappears from both surfaces immediately, before the
   app finishes opening.
3. Send another test broadcast with count `150` — confirm both surfaces show `"99+"`.
4. Open Settings, toggle "Show notification badges" off — confirm both surfaces stop showing the
   badge instantly; toggle back on — confirm it reappears without reopening the launcher.
5. Switch to a second lens (create one if needed) — confirm the same badge/count shows there too.
6. Send a broadcast for a package that is not installed — confirm no crash, no badge appears
   anywhere.
7. With TalkBack on, focus the badged icon — confirm the announced description includes the
   unread count (e.g. "Camera, 3 unread").

Record: device model, Android/HiOS version, build SHA, timestamp, and a short note per step,
matching this repo's existing smoke-test evidence format in `doc/task/README.md`.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/model/NotificationCountCrossLensIntegrationTest.kt
git commit -m "test(ui-024): add cross-lens notification-count integration coverage"
```

- [ ] **Step 6: Update the backlog**

Create `doc/task/done/p2-ui-ui-024-notification-count-badges.md` (following this repo's existing
done-story format — acceptance criteria, evidence, test counts, self-audit score, and an explicit
disclosure of the badge-broadcast coverage tradeoff) and update `doc/task/README.md`'s Picked
table (this was the last item of the owner-approved 4-story loop — mark it done, note the loop is
complete) and Done log. Commit separately:

```bash
git add doc/task/README.md doc/task/done/p2-ui-ui-024-notification-count-badges.md
git commit -m "docs(ui-024): mark notification count badges done"
```
