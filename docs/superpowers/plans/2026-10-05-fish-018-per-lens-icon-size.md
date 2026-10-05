# FISH-018 Per-Lens Icon Size Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each lens can have its own icon size; a lens without one inherits the shared value, and the Icon Size slider edits the active lens.

**Architecture:** Add `getIconSize(lensId)` / `saveIconSize(lensId, value)` to `UtilSettings`, copying the existing per-lens distortion helpers (`lensKey` suffix, default lens uses the unsuffixed key, missing key inherits the shared validated value). `LensView.drawGrid` and `FrmLens` switch from `KEY_ICON_SIZE` to those helpers. `LensGridCache` already keys on `iconSizeDp`, so grid geometry stays correct per lens with no change.

**Tech Stack:** Kotlin, Android Views, Robolectric + JUnit4 (unit), AndroidX Test (instrumentation).

Spec: `docs/superpowers/specs/2026-10-05-fish-018-per-lens-icon-size-design.md`

## Global Constraints

- Per-lens key is `"${KEY_ICON_SIZE}_$lensId"` = `min_icon_size_<lensId>`. The default lens (`LensWorkspace.DEFAULT_LENS_ID`) and null/empty lens ids use the unsuffixed `min_icon_size`, so single-lens installs are byte-for-byte unchanged.
- A lens with no override reads the shared value through the existing validated `getFloat(KEY_ICON_SIZE)`.
- Allowed range: `UtilSettings.MIN_ICON_SIZE` to `UtilSettings.MAX_ICON_SIZE.toFloat() + UtilSettings.MIN_ICON_SIZE` (same bounds the shared key uses).
- Reset-to-default in the Lens tab writes `us.autoDefaultIconSize` for the active lens only (owner decision 2026-10-05, same as Distortion).
- Icon pack stays global. No Room schema change. No new UI strings, so no locale files change.
- R5 (owner rules): no `late`/`!!` without a comment, no magic numbers, release every listener, tests prove behavior.
- Device: Samsung S24 Ultra `R5CX613VZBR` only (owner instruction 2026-10-05). NEVER run a Gradle `connected*` task. Install with `ANDROID_SERIAL=R5CX613VZBR ./gradlew installDevDebug installDevDebugAndroidTest`, run tests with `adb -s R5CX613VZBR shell am instrument -w [-e class <fqcn>] com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Green = `OK (N tests)`. Run Gradle and adb in the background and poll: a single Gradle run can take minutes.
- Do NOT stage `.idea/caches/deviceStreaming.xml`; add files by explicit path. Every commit ends with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

## File Structure

| File | Responsibility |
|---|---|
| Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` | Per-lens icon size get/save, duplicate, delete |
| Create `app/src/test/java/com/mckimquyen/util/UtilSettingsIconSizeTest.kt` | JVM tests for the settings contract |
| Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` | `drawGrid` reads the bound lens's size |
| Create `app/src/androidTest/java/com/mckimquyen/views/LensViewIconSizeIntegrationTest.kt` | Real `LensView`s lay out different cell sizes per lens |
| Modify `app/src/main/java/com/mckimquyen/ui/FrmLens.kt` | Slider, assign, reset use the active lens |
| Create `app/src/androidTest/java/com/mckimquyen/ui/FrmLensIconSizeWidgetTest.kt` | Widget tests for the slider |
| Create `doc/task/done/p2-fish-fish-018-per-lens-icon-size.md`; modify `doc/task/README.md`, `doc/feature.md` | Backlog record (owner rule R2) |

---

### Task 1: Per-lens icon size in `UtilSettings`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` (new functions next to `getDistortionFactor`, ~line 363; edits to `duplicateLensSettings` ~line 425 and `deleteLensSettings` ~line 440)
- Test: `app/src/test/java/com/mckimquyen/util/UtilSettingsIconSizeTest.kt`

**Interfaces:**
- Produces: `fun getIconSize(lensId: String?): Float`, `fun saveIconSize(lensId: String?, value: Float)`. `duplicateLensSettings(from, to)` also copies the effective icon size; `deleteLensSettings(id)` also removes `min_icon_size_<id>`.
- Consumes: existing `lensKey(baseKey, lensId)`, `getFloatWithValidation(name, default, min, max)`, `getFloat(KEY_ICON_SIZE)`, `autoDefaultIconSize`, `MIN_ICON_SIZE`, `MAX_ICON_SIZE`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FISH-018: per-lens icon size. Same contract as the per-lens distortion factor
 * ([UtilSettingsPerLensTest]): the default lens keeps the legacy unsuffixed key, a lens with no
 * override inherits the shared value, and once a lens has its own value no other lens moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsIconSizeTest {

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    private val maxAllowed = UtilSettings.MAX_ICON_SIZE.toFloat() + UtilSettings.MIN_ICON_SIZE

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `the key is stable and per lens keys carry the lens id`() {
        assertEquals("min_icon_size", UtilSettings.KEY_ICON_SIZE)
    }

    @Test
    fun `a lens with no override inherits the shared value`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        assertEquals(30f, settings.getIconSize(work), 0.001f)
        assertEquals(30f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `saving for one lens does not change another lens or the shared value`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(work, 50f)

        assertEquals(50f, settings.getIconSize(work), 0.001f)
        assertEquals(30f, settings.getIconSize(personal), 0.001f)
        assertEquals(30f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
    }

    @Test
    fun `the default lens reads and writes the legacy unsuffixed key`() {
        settings.saveIconSize(LensWorkspace.DEFAULT_LENS_ID, 45f)

        assertEquals(45f, rawPrefs().getFloat(UtilSettings.KEY_ICON_SIZE, -1f), 0.001f)
        assertEquals(45f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_${LensWorkspace.DEFAULT_LENS_ID}"))
    }

    @Test
    fun `null and empty lens ids use the shared key`() {
        settings.saveIconSize(null, 33f)
        assertEquals(33f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        settings.saveIconSize("", 34f)
        assertEquals(34f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(34f, settings.getIconSize(null), 0.001f)
        assertEquals(34f, settings.getIconSize(""), 0.001f)
    }

    @Test
    fun `a non default lens stores its value under the suffixed key`() {
        settings.saveIconSize(work, 51f)
        assertEquals(51f, rawPrefs().getFloat("${UtilSettings.KEY_ICON_SIZE}_$work", -1f), 0.001f)
    }

    @Test
    fun `a value below the minimum is clamped up to the minimum`() {
        settings.saveIconSize(work, UtilSettings.MIN_ICON_SIZE - 5f)
        assertEquals(UtilSettings.MIN_ICON_SIZE, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `a value above the maximum is clamped down to the maximum`() {
        settings.saveIconSize(work, maxAllowed + 50f)
        assertEquals(maxAllowed, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `a wrong typed stored value falls back instead of throwing`() {
        rawPrefs().edit().putString("${UtilSettings.KEY_ICON_SIZE}_$work", "huge").commit()
        val result = settings.getIconSize(work)
        assertTrue("fell back to a value inside the allowed range", result in UtilSettings.MIN_ICON_SIZE..maxAllowed)
    }

    @Test
    fun `duplicate copies the effective value of an inherited source`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.duplicateLensSettings(work, personal)

        assertEquals(44f, rawPrefs().getFloat("${UtilSettings.KEY_ICON_SIZE}_$personal", -1f), 0.001f)
        // Materialized: a later change of the shared value no longer moves the copy.
        settings.save(UtilSettings.KEY_ICON_SIZE, 20f)
        assertEquals(44f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `duplicate copies an explicit source override`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.saveIconSize(work, 52f)
        settings.duplicateLensSettings(work, personal)
        assertEquals(52f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `delete removes the override so the lens inherits again`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.saveIconSize(work, 52f)
        settings.deleteLensSettings(work)

        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_$work"))
        assertEquals(44f, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `deleting the default lens never removes the shared key`() {
        settings.saveIconSize(LensWorkspace.DEFAULT_LENS_ID, 46f)
        settings.deleteLensSettings(LensWorkspace.DEFAULT_LENS_ID)
        assertEquals(46f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run (background, poll the log): `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsIconSizeTest' > /tmp/f18-t1-red.log 2>&1; echo exit=$? >> /tmp/f18-t1-red.log`
Expected: FAIL, compilation error `Unresolved reference: getIconSize` (and `saveIconSize`).

- [ ] **Step 3: Implement**

In `UtilSettings.kt`, directly after `saveDistortionFactor` (~line 380):

```kotlin
    /**
     * FISH-018: per-lens icon size, same shape as [getDistortionFactor]. The default lens maps to
     * the legacy unsuffixed key (via [lensKey]); a lens with no override of its own inherits the
     * shared, already-validated value.
     */
    fun getIconSize(lensId: String?): Float {
        val key = lensKey(KEY_ICON_SIZE, lensId)
        return if (prefs.contains(key)) {
            // runCatching: a wrong-typed value under the key must not crash every caller.
            runCatching {
                getFloatWithValidation(
                    key,
                    autoDefaultIconSize,
                    MIN_ICON_SIZE,
                    MAX_ICON_SIZE.toFloat() + MIN_ICON_SIZE
                )
            }.getOrDefault(autoDefaultIconSize)
        } else {
            getFloat(KEY_ICON_SIZE)
        }
    }

    fun saveIconSize(lensId: String?, value: Float) {
        save(lensKey(KEY_ICON_SIZE, lensId), value)
    }
```

In `duplicateLensSettings`, after `saveSmartFocusBias(toLensId, smartFocus)`:

```kotlin
        saveIconSize(toLensId, getIconSize(fromLensId))
```

In `deleteLensSettings`, inside the `prefs.edit { ... }` block, add:

```kotlin
                remove("${KEY_ICON_SIZE}_$lensId")
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsIconSizeTest' --tests 'com.mckimquyen.util.UtilSettingsPerLensTest'`
Expected: PASS (13 new tests, existing per-lens tests still green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/UtilSettingsIconSizeTest.kt
git commit -m "feat(fish): per-lens icon size in UtilSettings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `LensView` draws with the bound lens's icon size

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt:1103`
- Test: `app/src/androidTest/java/com/mckimquyen/views/LensViewIconSizeIntegrationTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getIconSize(lensId: String?): Float`, `UtilSettings.saveIconSize`, `LensView.lensId`, `LensView.getAppBounds(index: Int, outRect: Rect): Boolean`.
- Produces: nothing new; behavior change only.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-018: two real LensViews bound to different lenses must lay the same app list out at
 * different icon sizes. Reading the cell bounds (not just a setting) proves the draw path
 * consumes the per-lens value, which a settings-only test cannot.
 */
@RunWith(AndroidJUnit4::class)
class LensViewIconSizeIntegrationTest {

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val APP_COUNT = 12
        const val SMALL_DP = 16f
        const val LARGE_DP = 50f
        const val FIRST_INDEX = 0
        const val OTHER_LENS = "other-lens"
        const val THIRD_LENS = "third-lens"
    }

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun apps() = ArrayList(
        (0 until APP_COUNT).map { App(id = it, label = "App $it", packageName = "com.t.a$it", name = "Act$it") }
    )

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme
        )
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @After
    fun tearDown() {
        settings.deleteLensSettings(OTHER_LENS)
        settings.deleteLensSettings(THIRD_LENS)
        rawPrefs().edit().clear().commit()
    }

    private fun laidOutView(lensId: String): LensView {
        var result: LensView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = LensView(context).apply {
                this.lensId = lensId
                setApps(apps())
                measure(
                    View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
                draw(Canvas())
            }
        }
        return requireNotNull(result) { "LensView was not created on the main thread" }
    }

    private fun cellWidth(view: LensView): Int {
        val bounds = Rect()
        assertTrue("grid must have been laid out", view.getAppBounds(FIRST_INDEX, bounds))
        return bounds.width()
    }

    @Test
    fun aLensWithItsOwnSize_laysOutDifferentlyFromAnotherLens() {
        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)

        val inheriting = laidOutView("some-lens-without-override")
        val own = laidOutView(OTHER_LENS)

        assertTrue(
            "a larger icon size must produce larger cells (own=${cellWidth(own)}, inherited=${cellWidth(inheriting)})",
            cellWidth(own) > cellWidth(inheriting)
        )
    }

    @Test
    fun aLensWithoutOverride_followsTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        val small = cellWidth(laidOutView(THIRD_LENS))

        settings.save(UtilSettings.KEY_ICON_SIZE, LARGE_DP)
        val large = cellWidth(laidOutView(THIRD_LENS))

        assertTrue("inheriting lens must follow the shared size ($small -> $large)", large > small)
    }

    @Test
    fun changingOneLensSize_doesNotChangeAnotherLensLayout() {
        settings.saveIconSize(OTHER_LENS, SMALL_DP)
        settings.saveIconSize(THIRD_LENS, SMALL_DP)
        val thirdBefore = cellWidth(laidOutView(THIRD_LENS))

        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        val other = cellWidth(laidOutView(OTHER_LENS))
        val thirdAfter = cellWidth(laidOutView(THIRD_LENS))

        assertEquals("the untouched lens keeps its geometry", thirdBefore, thirdAfter)
        assertNotEquals("the changed lens does not", thirdAfter, other)
    }

    @Test
    fun theSameViewRedrawnAfterASizeChange_picksUpTheNewSize() {
        settings.saveIconSize(OTHER_LENS, SMALL_DP)
        val view = laidOutView(OTHER_LENS)
        val before = cellWidth(view)

        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas()) }

        assertTrue("a redraw must recompute the cached grid for the new size", cellWidth(view) > before)
    }

    @Test
    fun theDefaultLens_stillUsesTheLegacySharedKey() {
        settings.save(UtilSettings.KEY_ICON_SIZE, LARGE_DP)
        val explicitDefault = cellWidth(laidOutView(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID))
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        val afterShrink = cellWidth(laidOutView(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID))
        assertTrue("default lens tracks the unsuffixed key", afterShrink < explicitDefault)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `ANDROID_SERIAL=R5CX613VZBR ./gradlew installDevDebug installDevDebugAndroidTest` then `adb -s R5CX613VZBR shell am instrument -w -e class com.mckimquyen.views.LensViewIconSizeIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL. `aLensWithItsOwnSize_laysOutDifferentlyFromAnotherLens`, `changingOneLensSize_...` and `theSameViewRedrawn...` fail because `drawGrid` still reads the shared key and ignores the per-lens value. The default-lens and inheritance tests pass already (they pin behavior that must not regress).

- [ ] **Step 3: Implement**

`LensView.kt:1103`, change:

```kotlin
        val iconSizeDp = us.getFloat(UtilSettings.KEY_ICON_SIZE)
```

to:

```kotlin
        val iconSizeDp = us.getIconSize(lensId)
```

- [ ] **Step 4: Run to verify it passes**

Same install + instrument command as Step 2, then also `-e class com.mckimquyen.views.LensViewPinchIntegrationTest` and `-e class com.mckimquyen.views.LensViewWidgetTest` to confirm no regression in the other `LensView` tests.
Expected: `OK (5 tests)` for the new class, and the two existing classes unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/views/LensViewIconSizeIntegrationTest.kt
git commit -m "feat(fish): LensView lays out with its own lens icon size

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Lens tab slider edits the active lens

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt` (slider listener ~line 113, `assignValues` ~line 229, `resetToDefault` ~line 261)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmLensIconSizeWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getIconSize(lensId)`, `saveIconSize(lensId, value)`, `FrmLens.activeLensId`, `SettingsInterface.onDefaultsReset()`.
- Produces: nothing new.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mckimquyen.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-018 widget proof: the Icon Size slider reads and writes the *active* lens. Same approach
 * as [FrmLensPerLensWidgetTest] for the distortion slider: point `KEY_ACTIVE_LENS_ID` at a
 * non-default lens, because on the default lens the per-lens key collapses onto the shared one
 * and cannot tell a correct implementation from one that ignores the lens.
 */
@RunWith(AndroidJUnit4::class)
class FrmLensIconSizeWidgetTest {

    private val workLens = "work-lens"
    private val otherLens = "other-lens"

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        clearLensState()
    }

    @After
    fun tearDown() {
        clearLensState()
    }

    private fun clearLensState() {
        settings.deleteLensSettings(workLens)
        settings.deleteLensSettings(otherLens)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            .remove(UtilSettings.KEY_ICON_SIZE)
            .apply()
    }

    private fun activate(lensId: String) {
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
    }

    /** A real touch sequence: only a drag reports `fromUser = true`, the branch that persists. */
    private fun dragSliderToEnd(slider: Slider) {
        val y = (slider.height / 2).toFloat()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            slider.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, slider.width / 2f)
        send(MotionEvent.ACTION_MOVE, slider.width.toFloat())
        send(MotionEvent.ACTION_UP, slider.width.toFloat())
    }

    private fun slider(fragment: FrmLens) =
        fragment.requireView().findViewById<Slider>(R.id.sbMinIconSize)

    @Test
    fun slider_showsTheActiveLensValue_notTheSharedOne() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 50f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(50f, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun aLensWithoutOverride_showsTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(30f, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun draggingTheSlider_writesToTheActiveLens_notTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        val perLens = settings.getIconSize(workLens)
        assertTrue("dragging must persist a new size for the active lens (got $perLens)", perLens > 30f)
        assertEquals(
            "the shared value must stay untouched while a non-default lens is active",
            30f,
            settings.getFloat(UtilSettings.KEY_ICON_SIZE),
            0.001f
        )
        scenario.close()
    }

    @Test
    fun draggingTheSlider_leavesOtherLensesAlone() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(otherLens, 48f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        assertEquals(48f, settings.getIconSize(otherLens), 0.001f)
        scenario.close()
    }

    @Test
    fun theLabel_followsTheActiveLensValue() {
        settings.saveIconSize(workLens, 50f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val label = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvValueMinIconSize)
            assertEquals(fragment.getString(R.string.unit_dp_format, 50), label.text.toString())
        }
        scenario.close()
    }

    @Test
    fun onResume_picksUpALensSwitchThatHappenedWhileTheTabWasAway() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 50f)
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(30f, slider(it).value, 0.001f) }

        activate(workLens)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        scenario.onFragment {
            assertEquals("resuming must re-read the active lens", 50f, slider(it).value, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun resetToDefaults_resetsTheActiveLensAndLeavesOthersAlone() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 52f)
        settings.saveIconSize(otherLens, 48f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { it.onDefaultsReset() }

        assertEquals(settings.autoDefaultIconSize, settings.getIconSize(workLens), 0.001f)
        assertEquals("another lens must not be reset", 48f, settings.getIconSize(otherLens), 0.001f)
        assertEquals("the shared value must not be rewritten", 30f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        scenario.onFragment { assertEquals(settings.autoDefaultIconSize, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun onTheDefaultLens_theSliderStillDrivesTheSharedKey() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        assertTrue(
            "default lens must write the legacy shared key",
            settings.getFloat(UtilSettings.KEY_ICON_SIZE) > 30f
        )
        scenario.close()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: install, then `adb -s R5CX613VZBR shell am instrument -w -e class com.mckimquyen.ui.FrmLensIconSizeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL. `slider_showsTheActiveLensValue_notTheSharedOne`, `draggingTheSlider_writesToTheActiveLens_notTheSharedValue`, `theLabel_...`, `onResume_...` and `resetToDefaults_...` fail because `FrmLens` still reads and writes the shared key.

- [ ] **Step 3: Implement**

`FrmLens.kt`, slider listener (~line 113): replace `utilSettings?.save(UtilSettings.KEY_ICON_SIZE, value)` with:

```kotlin
                utilSettings?.saveIconSize(activeLensId, value)
```

`assignValues` (~line 229): replace `val iconSize = us.getFloat(UtilSettings.KEY_ICON_SIZE)` with:

```kotlin
            val iconSize = us.getIconSize(activeLensId)
```

`resetToDefault` (~line 261): replace `us.save(UtilSettings.KEY_ICON_SIZE, us.autoDefaultIconSize)` with:

```kotlin
            us.saveIconSize(activeLensId, us.autoDefaultIconSize)
```

- [ ] **Step 4: Run to verify it passes**

Install, then run `FrmLensIconSizeWidgetTest` (expect `OK (8 tests)`), plus the existing `com.mckimquyen.ui.FrmLensPerLensWidgetTest`, `FrmLensWidgetTest`, `FrmLensPhysicsPresetsWidgetTest`, `FrmLensCustomPresetWidgetTest` (expect unchanged green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/FrmLens.kt app/src/androidTest/java/com/mckimquyen/ui/FrmLensIconSizeWidgetTest.kt
git commit -m "feat(fish): Lens tab Icon Size slider edits the active lens

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Lifecycle integration, full verification, backlog record

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/views/LensViewIconSizeIntegrationTest.kt` (add the cross-module lifecycle tests below)
- Create: `doc/task/done/p2-fish-fish-018-per-lens-icon-size.md`; modify `doc/task/README.md`, `doc/feature.md`

**Interfaces:**
- Consumes: everything from Tasks 1-3, plus `UtilSettings.duplicateLensSettings` / `deleteLensSettings`.

- [ ] **Step 1: Write the failing lifecycle tests**

Append to `LensViewIconSizeIntegrationTest`:

```kotlin
    @Test
    fun aDuplicatedLens_keepsTheSourceSizeEvenAfterTheSharedValueChanges() {
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        settings.duplicateLensSettings(OTHER_LENS, THIRD_LENS)

        val source = cellWidth(laidOutView(OTHER_LENS))
        val copy = cellWidth(laidOutView(THIRD_LENS))
        assertEquals("a duplicated lens starts at the source size", source, copy)

        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP + 5f)
        assertEquals(
            "the copy was materialized, so a shared change must not move it",
            copy,
            cellWidth(laidOutView(THIRD_LENS))
        )
    }

    @Test
    fun deletingALens_makesAReusedIdInheritTheSharedValueAgain() {
        settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
        settings.saveIconSize(OTHER_LENS, LARGE_DP)
        val withOverride = cellWidth(laidOutView(OTHER_LENS))

        settings.deleteLensSettings(OTHER_LENS)
        val afterDelete = cellWidth(laidOutView(OTHER_LENS))

        assertTrue("after delete the id follows the (smaller) shared size", afterDelete < withOverride)
    }

    @Test
    fun aValueSavedOutOfRange_stillLaysOutWithAClampedSize() {
        settings.saveIconSize(OTHER_LENS, 10_000f)
        val view = laidOutView(OTHER_LENS)
        val bounds = Rect()
        assertTrue(view.getAppBounds(FIRST_INDEX, bounds))
        assertTrue("an absurd stored value must not break the layout", bounds.width() in 1..VIEW_WIDTH)
    }
```

- [ ] **Step 2: Run to verify it fails or passes for the right reason**

Run the class on the S24 Ultra. These tests pin Task 1+2 behavior end to end, so they should already pass; that is intended. Before trusting them, prove each is non-vacuous once:
1. Temporarily delete the `saveIconSize(toLensId, getIconSize(fromLensId))` line in `duplicateLensSettings`, rebuild and run `aDuplicatedLens_keepsTheSourceSizeEvenAfterTheSharedValueChanges`: expected FAIL. Restore the line.
2. Temporarily delete the `remove("${KEY_ICON_SIZE}_$lensId")` line in `deleteLensSettings`, run `deletingALens_makesAReusedIdInheritTheSharedValueAgain`: expected FAIL. Restore the line.
Record both failing assertion messages in the report. `git diff` must show neither line removed before committing.

- [ ] **Step 3: Full verification on the S24 Ultra**

Run, in the background:
1. `./gradlew testDevDebugUnitTest lintDevDebug` then read totals from `app/build/test-results/testDevDebugUnitTest/*.xml` and `app/build/reports/lint-results-devDebug.txt`. Expected: 0 failures; lint still 0 errors / 8 warnings (the pre-existing icon-asset warnings).
2. `ANDROID_SERIAL=R5CX613VZBR ./gradlew installDevDebug installDevDebugAndroidTest`, then the three new classes (instrumented: `LensViewIconSizeIntegrationTest` 8, `FrmLensIconSizeWidgetTest` 8), then the full suite `adb -s R5CX613VZBR shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Expected: `OK (N tests)` with 0 failures; record the exact total printed by the runner instead of predicting it.
3. Manual smoke on the device: open the Lens tab, drag Icon Size on the default lens, add a second lens, give it a different size, swipe Home between the lenses and confirm each shows its own size; Reset in the Lens tab changes only the visible lens. If an ad covers the UI, stop and tell the owner.

- [ ] **Step 4: Backlog record (rule R2)**

Create `doc/task/done/p2-fish-fish-018-per-lens-icon-size.md` in the shape of `doc/task/done/p2-fish-fish-017-haptic-intensity.md` (field table, owner decision dated 2026-10-05 linking the spec and this plan, acceptance checklist, evidence with the exact counts and the device, disclosed gaps). Add a FISH-018 line to the Implemented section of `doc/task/README.md` and a line to `doc/feature.md`. Note explicitly that per-lens icon pack is split into a separate future story and why.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/views/LensViewIconSizeIntegrationTest.kt doc/task/done/p2-fish-fish-018-per-lens-icon-size.md doc/task/README.md doc/feature.md
git commit -m "docs(fish): close FISH-018 with evidence

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
