# FISH-017 Haptic Intensity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One shared Light / Medium / Strong setting that picks the haptic effect for both hover and launch haptics in `LensView`.

**Architecture:** A pure-Kotlin enum `HapticIntensity` maps a level to a `HapticFeedbackConstants` value per API level. `UtilSettings` stores the level as an Int ordinal under one global key. `LensView` reads it where it currently hardcodes `VIRTUAL_KEY`. `FrmSettings` shows a `MaterialButtonToggleGroup` that writes it and plays a preview.

**Tech Stack:** Kotlin 2.0.20, Android Views, Material 1.13.0, Robolectric + JUnit4 (unit), AndroidX Test (instrumentation).

Spec: `docs/superpowers/specs/2026-10-04-fish-017-haptic-intensity-design.md`

## Global Constraints

- minSdk 25, target/compileSdk 37. `CONFIRM` needs API 30; below that `STRONG` falls back to `LONG_PRESS`.
- `MEDIUM` = `HapticFeedbackConstants.VIRTUAL_KEY` = today's behavior. Default is `MEDIUM`, so an install with no saved key behaves exactly as before.
- Key is global: no `_<lensId>` suffix.
- Existing gates stay untouched: `KEY_VIBRATE_APP_HOVER` / `KEY_VIBRATE_APP_LAUNCH` switches, `LensPhysicsPolicy.shouldReduceLensMotion`, `mAnimationHiding`.
- No magic numbers: levels via enum, default via named constant.
- Every resource added in a hook/listener must be released (`LensView` hook nulled on detach).
- Tests are part of each task: unit + widget + integration. No task is done without its tests.
- Device (locked this session): TECNO KJ7 `115333744A005844` only. NEVER run a Gradle `connected*` task: it fans out to every attached device (Pixel 7 Pro is attached and banned). Install with `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest`, then run tests with `adb -s 115333744A005844 shell am instrument -w [-e class <fqcn> | -e package <pkg>] com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. A green run prints `OK (N tests)`; any `FAILURES!!!` or `INSTRUMENTATION_FAILED` is a failure.
- Commit trailer on every commit: `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## File Structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/mckimquyen/util/HapticIntensity.kt` | Enum + level to constant mapping + safe decode |
| Create `app/src/test/java/com/mckimquyen/util/HapticIntensityTest.kt` | JVM unit tests for the enum |
| Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` | Key, default, get/save |
| Create `app/src/test/java/com/mckimquyen/util/UtilSettingsHapticIntensityTest.kt` | Robolectric tests for persistence |
| Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` | Use the setting; test hook |
| Create `app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt` | Real `LensView` emits the right constant |
| Modify `app/src/main/res/layout/frm_settings.xml` | Toggle group row |
| Modify `app/src/main/res/values/strings.xml`, `values-vi/strings.xml` | 5 strings each |
| Modify `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` | Bind, save, preview, enable state, reset |
| Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsHapticIntensityWidgetTest.kt` | Widget tests |
| Create `doc/task/done/p2-fish-fish-017-haptic-intensity.md`; modify `doc/task/README.md` | Backlog record |

---

### Task 1: `HapticIntensity` enum

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/HapticIntensity.kt`
- Test: `app/src/test/java/com/mckimquyen/util/HapticIntensityTest.kt`

**Interfaces:**
- Produces: `enum class HapticIntensity { LIGHT, MEDIUM, STRONG }` with `fun feedbackConstant(sdkInt: Int): Int`, and `companion object { val DEFAULT: HapticIntensity; const val CONFIRM_MIN_SDK: Int; fun from(ordinal: Int): HapticIntensity }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import android.os.Build
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HapticIntensityTest {

    @Test
    fun `light maps to clock tick on every api level`() {
        for (sdk in listOf(Build.VERSION_CODES.N_MR1, Build.VERSION_CODES.R, 37)) {
            assertEquals(HapticFeedbackConstants.CLOCK_TICK, HapticIntensity.LIGHT.feedbackConstant(sdk))
        }
    }

    @Test
    fun `medium maps to virtual key which is the pre-existing behavior`() {
        for (sdk in listOf(Build.VERSION_CODES.N_MR1, Build.VERSION_CODES.R, 37)) {
            assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, HapticIntensity.MEDIUM.feedbackConstant(sdk))
        }
    }

    @Test
    fun `strong uses long press below api 30`() {
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            HapticIntensity.STRONG.feedbackConstant(Build.VERSION_CODES.Q)
        )
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            HapticIntensity.STRONG.feedbackConstant(HapticIntensity.CONFIRM_MIN_SDK - 1)
        )
    }

    @Test
    fun `strong uses confirm from api 30`() {
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            HapticIntensity.STRONG.feedbackConstant(HapticIntensity.CONFIRM_MIN_SDK)
        )
        assertEquals(HapticFeedbackConstants.CONFIRM, HapticIntensity.STRONG.feedbackConstant(37))
    }

    @Test
    fun `the three levels produce three distinct constants on api 30 plus`() {
        val constants = HapticIntensity.entries.map { it.feedbackConstant(37) }.toSet()
        assertEquals(HapticIntensity.entries.size, constants.size)
    }

    @Test
    fun `default is medium`() {
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.DEFAULT)
    }

    @Test
    fun `from decodes every valid ordinal`() {
        for (level in HapticIntensity.entries) {
            assertEquals(level, HapticIntensity.from(level.ordinal))
        }
    }

    @Test
    fun `from falls back to default for negative and out of range ordinals`() {
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(-1))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(HapticIntensity.entries.size))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(Int.MAX_VALUE))
        assertEquals(HapticIntensity.DEFAULT, HapticIntensity.from(Int.MIN_VALUE))
    }

    @Test
    fun `confirm threshold is api 30`() {
        assertEquals(Build.VERSION_CODES.R, HapticIntensity.CONFIRM_MIN_SDK)
        assertNotEquals(0, HapticIntensity.CONFIRM_MIN_SDK)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.HapticIntensityTest'`
Expected: FAIL, compilation error `Unresolved reference: HapticIntensity`.

- [ ] **Step 3: Implement**

```kotlin
package com.mckimquyen.util

import android.os.Build
import android.view.HapticFeedbackConstants

/**
 * FISH-017: one shared strength for the hover and launch haptics. [MEDIUM] is
 * [HapticFeedbackConstants.VIRTUAL_KEY], what `LensView` always used, so it is the default and an
 * install that never touches the setting behaves exactly as before.
 */
enum class HapticIntensity {
    LIGHT,
    MEDIUM,
    STRONG;

    fun feedbackConstant(sdkInt: Int): Int = when (this) {
        LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
        STRONG ->
            if (sdkInt >= CONFIRM_MIN_SDK) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
    }

    companion object {
        val DEFAULT = MEDIUM

        /** [HapticFeedbackConstants.CONFIRM] was added in API 30. */
        const val CONFIRM_MIN_SDK = Build.VERSION_CODES.R

        /** Decodes a stored ordinal; anything unknown (corrupt or from a newer build) is [DEFAULT]. */
        fun from(ordinal: Int): HapticIntensity = entries.getOrNull(ordinal) ?: DEFAULT
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.HapticIntensityTest'`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/HapticIntensity.kt app/src/test/java/com/mckimquyen/util/HapticIntensityTest.kt
git commit -m "feat(fish): add HapticIntensity level to constant mapping

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Persist the level in `UtilSettings`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` (constants next to `KEY_VIBRATE_APP_LAUNCH` ~line 108 and `DEFAULT_VIBRATE_APP_LAUNCH` ~line 49; functions after `getBoolean`)
- Test: `app/src/test/java/com/mckimquyen/util/UtilSettingsHapticIntensityTest.kt`

**Interfaces:**
- Consumes: `HapticIntensity` from Task 1.
- Produces: `UtilSettings.KEY_HAPTIC_INTENSITY: String`, `UtilSettings.DEFAULT_HAPTIC_INTENSITY: Int`, `fun getHapticIntensity(): HapticIntensity`, `fun saveHapticIntensity(value: HapticIntensity)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FISH-017: the haptic level defaults to MEDIUM, round-trips, and survives corrupt storage. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsHapticIntensityTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    private fun rawPrefs() =
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())

    @Test
    fun `never saved reads medium`() {
        assertEquals(HapticIntensity.MEDIUM, freshSettings().getHapticIntensity())
        assertEquals(HapticIntensity.DEFAULT.ordinal, UtilSettings.DEFAULT_HAPTIC_INTENSITY)
    }

    @Test
    fun `every level round trips`() {
        val settings = freshSettings()
        for (level in HapticIntensity.entries) {
            settings.saveHapticIntensity(level)
            assertEquals(level, settings.getHapticIntensity())
        }
    }

    @Test
    fun `corrupt stored ordinal reads the default`() {
        val settings = freshSettings()
        rawPrefs().edit().putInt(UtilSettings.KEY_HAPTIC_INTENSITY, 99).commit()
        assertEquals(HapticIntensity.DEFAULT, settings.getHapticIntensity())
        rawPrefs().edit().putInt(UtilSettings.KEY_HAPTIC_INTENSITY, -3).commit()
        assertEquals(HapticIntensity.DEFAULT, settings.getHapticIntensity())
    }

    @Test
    fun `the key is stable and carries no lens suffix`() {
        assertEquals("haptic_intensity", UtilSettings.KEY_HAPTIC_INTENSITY)
    }

    @Test
    fun `saving does not touch the hover and launch switches`() {
        val settings = freshSettings()
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        assertEquals(UtilSettings.DEFAULT_VIBRATE_APP_HOVER, settings.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER))
        assertEquals(UtilSettings.DEFAULT_VIBRATE_APP_LAUNCH, settings.getBoolean(UtilSettings.KEY_VIBRATE_APP_LAUNCH))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsHapticIntensityTest'`
Expected: FAIL, `Unresolved reference: getHapticIntensity`.

- [ ] **Step 3: Implement**

In the companion object, after `const val DEFAULT_VIBRATE_APP_LAUNCH = true`:

```kotlin
        // FISH-017: shared by the hover and launch haptics. Stored as an enum ordinal.
        val DEFAULT_HAPTIC_INTENSITY = HapticIntensity.DEFAULT.ordinal
```

Note `val` (not `const`): `ordinal` is not a compile-time constant. The test above reads it as a normal property, which works for `val` in a companion.

After `const val KEY_VIBRATE_APP_LAUNCH = "vibrate_app_launch"`:

```kotlin
        const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
```

Right after the `getBoolean` function:

```kotlin
    fun getHapticIntensity(): HapticIntensity =
        HapticIntensity.from(prefs.getInt(KEY_HAPTIC_INTENSITY, DEFAULT_HAPTIC_INTENSITY))

    fun saveHapticIntensity(value: HapticIntensity) {
        save(KEY_HAPTIC_INTENSITY, value.ordinal)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsHapticIntensityTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/UtilSettingsHapticIntensityTest.kt
git commit -m "feat(fish): persist haptic intensity in UtilSettings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Use the level in `LensView`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt` (`performHoverVibration` ~line 1375, `performLaunchVibration` ~line 1393, detach cleanup ~line 1505, imports ~line 38)
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getHapticIntensity()`, `HapticIntensity.feedbackConstant(Int)`.
- Produces: `@VisibleForTesting internal var onHapticPerformed: ((Int) -> Unit)?` on `LensView`, invoked with the exact constant just before `performHapticFeedback`.

- [ ] **Step 1: Write the failing integration test**

```kotlin
package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-017: a real LensView reads the saved level for BOTH the hover and the launch haptic,
 * keeps the old VIRTUAL_KEY behavior when nothing was saved, and emits nothing when the
 * existing gates (switch off, reduced motion) say no.
 */
@RunWith(AndroidJUnit4::class)
class LensViewHapticIntensityIntegrationTest {

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val FIRST_INDEX = 0
    }

    private lateinit var context: Context
    private lateinit var lensView: LensView
    private lateinit var settings: UtilSettings
    private val performed = mutableListOf<Int>()

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme
        )
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
            lensView.setApps(
                arrayListOf(
                    App(id = 1, label = "App 1", packageName = "com.test1", name = "Act1"),
                    App(id = 2, label = "App 2", packageName = "com.test2", name = "Act2")
                )
            )
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            lensView.draw(Canvas())
            lensView.onHapticPerformed = { performed += it }
        }
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    /** Presses on the first icon and redraws so the hover is detected; returns after the draw. */
    private fun hoverFirstIcon() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bounds = Rect()
            assertTrue("grid must have drawn", lensView.getAppBounds(FIRST_INDEX, bounds))
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            lensView.dispatchTouchEvent(down)
            down.recycle()
            lensView.draw(Canvas())
        }
    }

    private fun releaseOnFirstIcon() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bounds = Rect()
            lensView.getAppBounds(FIRST_INDEX, bounds)
            val now = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(
                now, now + 10, MotionEvent.ACTION_UP,
                bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            lensView.dispatchTouchEvent(up)
            up.recycle()
        }
    }

    private fun expected(level: HapticIntensity) = level.feedbackConstant(Build.VERSION.SDK_INT)

    /** One fresh LensView per test (from @Before): a second hover on the same icon never re-fires. */
    private fun assertHoverEmits(level: HapticIntensity) {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(level)
        hoverFirstIcon()
        assertEquals("level $level", listOf(expected(level)), performed.take(1))
    }

    @Test
    fun hover_light() = assertHoverEmits(HapticIntensity.LIGHT)

    @Test
    fun hover_medium() = assertHoverEmits(HapticIntensity.MEDIUM)

    @Test
    fun hover_strong() = assertHoverEmits(HapticIntensity.STRONG)

    @Test
    fun launch_usesTheSavedLevel() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        hoverFirstIcon()
        performed.clear()
        releaseOnFirstIcon()
        assertEquals(listOf(expected(HapticIntensity.STRONG)), performed.take(1))
    }

    @Test
    fun noSavedLevel_keepsTheOldVirtualKeyBehavior() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        hoverFirstIcon()
        assertEquals(
            listOf(android.view.HapticFeedbackConstants.VIRTUAL_KEY),
            performed.take(1)
        )
    }

    @Test
    fun switchesOff_emitNothingRegardlessOfLevel() {
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        hoverFirstIcon()
        releaseOnFirstIcon()
        assertTrue("expected no haptic, got $performed", performed.isEmpty())
    }

    @Test
    fun hookIsReleasedWhenTheHostingActivityIsDestroyed() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.mckimquyen.ui.ActHome::class.java)
        var homeLens: LensView? = null
        scenario.onActivity {
            homeLens = it.findViewById(R.id.lensViews)
            homeLens?.onHapticPerformed = { }
        }
        scenario.close() // destroys the Activity, which detaches the view
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(null, homeLens?.onHapticPerformed)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest && adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewHapticIntensityIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL, compilation error `Unresolved reference: onHapticPerformed`.

- [ ] **Step 3: Implement**

Imports in `LensView.kt` (alphabetical with the existing block):

```kotlin
import android.os.Build
import androidx.annotation.VisibleForTesting
```

(`android.os.Build` goes next to `import android.os.Handler`.)

Add near the other private state (next to `mMustVibrate`, ~line 266):

```kotlin
    // FISH-017: test seam only. Called with the exact constant right before the haptic is
    // performed. Nulled on detach so the view never keeps a test lambda (and its captures) alive.
    @VisibleForTesting
    internal var onHapticPerformed: ((Int) -> Unit)? = null
```

Replace the two vibration helpers' body calls. Add one shared function and use it:

```kotlin
    private fun performIntensityHaptic() {
        val level = mUtilSettings?.getHapticIntensity() ?: HapticIntensity.DEFAULT
        val constant = level.feedbackConstant(Build.VERSION.SDK_INT)
        onHapticPerformed?.invoke(constant)
        performHapticFeedback(constant)
    }
```

In `performHoverVibration` replace `performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)` with `performIntensityHaptic()`. Do the same in `performLaunchVibration`. Leave every `if` gate as it is.

Add `import com.mckimquyen.util.HapticIntensity` to the `com.mckimquyen.util` import group.

In the detach cleanup block (the one that sets `mUtilSettings = null`), add `onHapticPerformed = null` on the line before `mApps = null`.

If `HapticFeedbackConstants` is no longer referenced in the file after this change, remove its import. Check: `grep -n HapticFeedbackConstants app/src/main/java/com/mckimquyen/views/LensView.kt`.

- [ ] **Step 4: Run to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest && adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.views.LensViewHapticIntensityIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS. Tests with `assumeFalse(reduced motion)` report as skipped, not failed, if the device is under battery saver or animator scale 0. Skipped is not a pass: re-run with battery saver off so `hover_light`, `hover_medium`, `hover_strong`, `launch_usesTheSavedLevel` and `noSavedLevel_keepsTheOldVirtualKeyBehavior` actually execute.

Also run the JVM suite for regressions: `./gradlew testDevDebugUnitTest`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt
git commit -m "feat(fish): LensView hover and launch haptics follow the saved intensity

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Settings UI

**Files:**
- Modify: `app/src/main/res/layout/frm_settings.xml` (insert after the divider that follows the `rlSwitchVibrateAppLaunchParent` block, before the `<!-- Show Name App Hover -->` block)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` (fields ~line 37, binding ~line 102, listeners ~line 180, `assignValues` ~line 308, `resetToDefault` ~line 403)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsHapticIntensityWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getHapticIntensity()` / `saveHapticIntensity()` / `DEFAULT_HAPTIC_INTENSITY`, `HapticIntensity.feedbackConstant`.
- Produces: view IDs `R.id.groupHapticIntensity`, `R.id.btnHapticLight`, `R.id.btnHapticMedium`, `R.id.btnHapticStrong`.

- [ ] **Step 1: Write the failing widget test**

```kotlin
package com.mckimquyen.ui

import android.widget.CompoundButton
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButtonToggleGroup
import com.mckimquyen.R
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-017 widget proof for the haptic intensity row in the Settings tab. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsHapticIntensityWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() { rawPrefs().edit().clear().commit() }

    @After
    fun tearDown() { rawPrefs().edit().clear().commit() }

    private fun idFor(level: HapticIntensity) = when (level) {
        HapticIntensity.LIGHT -> R.id.btnHapticLight
        HapticIntensity.MEDIUM -> R.id.btnHapticMedium
        HapticIntensity.STRONG -> R.id.btnHapticStrong
    }

    private fun launch(block: (FrmSettings, MaterialButtonToggleGroup) -> Unit) {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                block(fragment, fragment.requireView().findViewById(R.id.groupHapticIntensity))
            }
        }
    }

    @Test
    fun neverSaved_selectsMedium() {
        launch { _, group -> assertEquals(R.id.btnHapticMedium, group.checkedButtonId) }
    }

    @Test
    fun everySavedLevel_isReflected() {
        for (level in HapticIntensity.entries) {
            UtilSettings(context).saveHapticIntensity(level)
            launch { _, group -> assertEquals("level $level", idFor(level), group.checkedButtonId) }
        }
    }

    @Test
    fun pickingALevel_persistsIt() {
        for (level in HapticIntensity.entries) {
            launch { _, group -> group.check(idFor(level)) }
            assertEquals(level, UtilSettings(context).getHapticIntensity())
        }
    }

    @Test
    fun alwaysExactlyOneLevelSelected_evenWhenTheSelectedOneIsTappedAgain() {
        launch { _, group ->
            group.findViewById<android.view.View>(R.id.btnHapticMedium).performClick()
            assertEquals(R.id.btnHapticMedium, group.checkedButtonId)
        }
    }

    @Test
    fun groupIsEnabledWhenAtLeastOneHapticSwitchIsOn() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)
        launch { _, group -> assertTrue(group.children().all { it.isEnabled }) }
    }

    @Test
    fun groupIsDisabledWhenBothHapticSwitchesAreOff() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { _, group -> assertTrue(group.children().none { it.isEnabled }) }
    }

    @Test
    fun turningASwitchOn_reenablesTheGroupLive() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        launch { fragment, group ->
            assertTrue(group.children().none { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = true
            assertTrue(group.children().all { it.isEnabled })
            fragment.requireView().findViewById<CompoundButton>(R.id.swVibrateAppHover).isChecked = false
            assertFalse(group.children().any { it.isEnabled })
        }
    }

    @Test
    fun resetToDefaults_returnsToMedium() {
        UtilSettings(context).saveHapticIntensity(HapticIntensity.STRONG)
        launch { fragment, group ->
            assertEquals(R.id.btnHapticStrong, group.checkedButtonId)
            (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
            assertEquals(R.id.btnHapticMedium, group.checkedButtonId)
        }
        assertEquals(HapticIntensity.DEFAULT, UtilSettings(context).getHapticIntensity())
    }

    @Test
    fun rowLabelsAreLocalizedStrings_notBlank() {
        launch { _, group ->
            for (level in HapticIntensity.entries) {
                val text = (group.findViewById<com.google.android.material.button.MaterialButton>(idFor(level))).text
                assertTrue("label for $level must not be blank", text.isNotBlank())
            }
        }
    }

    private fun MaterialButtonToggleGroup.children() = (0 until childCount).map { getChildAt(it) }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest && adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsHapticIntensityWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL, compilation error `Unresolved reference: groupHapticIntensity`.

- [ ] **Step 3: Implement**

`values/strings.xml`, after `setting_vibrate_app_launch`:

```xml
    <string name="setting_haptic_intensity">Haptic strength</string>
    <string name="haptic_intensity_light">Light</string>
    <string name="haptic_intensity_medium">Medium</string>
    <string name="haptic_intensity_strong">Strong</string>
```

`values-vi/strings.xml`, after `setting_vibrate_app_launch`:

```xml
    <string name="setting_haptic_intensity">Cường độ rung</string>
    <string name="haptic_intensity_light">Nhẹ</string>
    <string name="haptic_intensity_medium">Vừa</string>
    <string name="haptic_intensity_strong">Mạnh</string>
```

Other locales already lag the default file (182 vs 193 strings) and fall back to English; do not hand-translate 15 more locales in this story.

`frm_settings.xml`, inserted between the divider after the launch row and `<!-- Show Name App Hover -->`:

```xml
                <!-- Haptic intensity (shared by hover + launch) -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:paddingVertical="8dp"
                    android:paddingStart="52dp"
                    android:paddingEnd="0dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:paddingBottom="8dp"
                        android:text="@string/setting_haptic_intensity"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold" />

                    <com.google.android.material.button.MaterialButtonToggleGroup
                        android:id="@+id/groupHapticIntensity"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:selectionRequired="true"
                        app:singleSelection="true">

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnHapticLight"
                            style="?attr/materialButtonOutlinedStyle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/haptic_intensity_light" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnHapticMedium"
                            style="?attr/materialButtonOutlinedStyle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/haptic_intensity_medium" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnHapticStrong"
                            style="?attr/materialButtonOutlinedStyle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/haptic_intensity_strong" />
                    </com.google.android.material.button.MaterialButtonToggleGroup>
                </LinearLayout>

                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="52dp"
                    app:dividerColor="?attr/colorOutlineVariant" />
```

`FrmSettings.kt`:

1. Imports: `com.google.android.material.button.MaterialButtonToggleGroup`, `com.mckimquyen.util.HapticIntensity`, `com.mckimquyen.util.LensPhysicsPolicy`, `android.os.Build`.
2. Field next to `swVibrateAppLaunch` (line ~38): `private var groupHapticIntensity: MaterialButtonToggleGroup? = null`.
3. Binding next to `swVibrateAppLaunch = view.findViewById(...)`: `groupHapticIntensity = view.findViewById(R.id.groupHapticIntensity)`.
4. Replace the two existing vibrate switch listeners with versions that also refresh the enabled state, and add the group listener right after them:

```kotlin
        swVibrateAppHover?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_VIBRATE_APP_HOVER, isChecked)
            updateHapticIntensityEnabled()
        }
        swVibrateAppLaunch?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, isChecked)
            updateHapticIntensityEnabled()
        }
        groupHapticIntensity?.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val level = hapticLevelForButton(checkedId)
            utilSettings?.saveHapticIntensity(level)
            previewHaptic(group, level)
        }
```

5. Helpers (private, near `resetToDefault`):

```kotlin
    private fun hapticLevelForButton(buttonId: Int): HapticIntensity = when (buttonId) {
        R.id.btnHapticLight -> HapticIntensity.LIGHT
        R.id.btnHapticStrong -> HapticIntensity.STRONG
        else -> HapticIntensity.MEDIUM
    }

    private fun buttonForHapticLevel(level: HapticIntensity): Int = when (level) {
        HapticIntensity.LIGHT -> R.id.btnHapticLight
        HapticIntensity.MEDIUM -> R.id.btnHapticMedium
        HapticIntensity.STRONG -> R.id.btnHapticStrong
    }

    /** The row only matters while at least one haptic is on. */
    private fun updateHapticIntensityEnabled() {
        val group = groupHapticIntensity ?: return
        val anyOn = swVibrateAppHover?.isChecked == true || swVibrateAppLaunch?.isChecked == true
        for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = anyOn
    }

    private fun previewHaptic(anchor: View, level: HapticIntensity) {
        val ctx = context ?: return
        if (LensPhysicsPolicy.shouldReduceLensMotion(ctx)) return
        anchor.performHapticFeedback(level.feedbackConstant(Build.VERSION.SDK_INT))
    }
```

6. In `assignValues`, directly after the two `swVibrate...isChecked = ...` lines:

```kotlin
            groupHapticIntensity?.check(buttonForHapticLevel(us.getHapticIntensity()))
            updateHapticIntensityEnabled()
```

Setting `check()` here fires the listener and would play a preview on every screen open. Guard it: add a field `private var bindingHapticGroup = false`, set it `true` before the `check(...)` call and `false` after, and make the group listener return early when it is `true`. Same guard covers `onDefaultsReset`, which calls `assignValues`.

7. In `resetToDefault`, after `us.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)`: `us.saveHapticIntensity(HapticIntensity.DEFAULT)`.

8. Release: in `onDestroyView` (or wherever the other view fields are nulled), add `groupHapticIntensity?.clearOnButtonCheckedListeners()` then `groupHapticIntensity = null`. `FrmSettings.onDestroyView()` already exists (~line 440): add the two lines there, before `super.onDestroyView()`.

- [ ] **Step 4: Run to verify it passes**

Run: `ANDROID_SERIAL=115333744A005844 ./gradlew installDevDebug installDevDebugAndroidTest && adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsHapticIntensityWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS, 9 tests.

Then confirm no neighbour broke: `adb -s 115333744A005844 shell am instrument -w -e package com.mckimquyen.ui com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` and `./gradlew lintDevDebug`.
Expected: no new failures; lint warning count not above the count recorded in `doc/task/README.md`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/frm_settings.xml app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsHapticIntensityWidgetTest.kt
git commit -m "feat(fish): haptic intensity toggle group in Settings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Full verification, device smoke, backlog record

**Files:**
- Create: `doc/task/done/p2-fish-fish-017-haptic-intensity.md`
- Modify: `doc/task/README.md` (Implemented list), `doc/feature.md` (R2)

- [ ] **Step 1: Full JVM suite**

Run: `./gradlew testDevDebugUnitTest`
Expected: 0 failures. Record the new total.

- [ ] **Step 2: Confirm the device**

Run `adb devices`. TECNO KJ7 `115333744A005844` must be listed. If it is not, stop and ask the user (BG6 `118743744X002560` is the only pre-approved substitute); do not fall back to Pixel.

- [ ] **Step 3: Full instrumentation suite on the locked device**

Run: `ANDROID_SERIAL=<locked serial> ./gradlew connectedDevDebugAndroidTest`
Expected: 0 failures. Skipped `assumeFalse` tests must be zero in the final run: turn battery saver off and confirm `adb -s <serial> shell settings get global animator_duration_scale` is `1.0`.

- [ ] **Step 4: Manual smoke on the locked device**

Install `devDebug`, open Settings tab. Check, and write down the result of each:
1. Row shows Light / Medium / Strong with Medium selected on a fresh install.
2. Tapping each level vibrates differently (Light is a tick, Strong is firmer).
3. Turn both haptic switches off: the three buttons grey out and do not respond. Turn one on: they re-enable.
4. Set Strong, make Home the default launcher, drag over icons with hover haptic on, and launch an app: both feel Strong.
5. Font scale at maximum (Settings > Display): the three labels stay readable and the row does not clip.
6. Reset to defaults: Medium selected again.
If any ad appears over the UI during screenshots, stop and report it (R4).

- [ ] **Step 5: Independent review and audit**

Run the code-review skill on the diff. Score the round out of 10 from evidence (tests run, device smoke, review findings). Per the step-done gate memory, push only if the score is above 9.

- [ ] **Step 6: Backlog and feature doc (R2)**

Create `doc/task/done/p2-fish-fish-017-haptic-intensity.md` in the same shape as `doc/task/done/p2-ui-ui-025-unified-popup-menus.md` (field table, owner decision dated 2026-10-04 linking the spec and this plan, acceptance checklist, evidence with test counts and the device used). Add a FISH-017 line to the Implemented section of `doc/task/README.md` and update `doc/feature.md`.

- [ ] **Step 7: Commit**

```bash
git add doc/task/done/p2-fish-fish-017-haptic-intensity.md doc/task/README.md doc/feature.md
git commit -m "docs(fish): close FISH-017 with evidence

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
