# FISH-040 — Chromatic Aberration Rim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in red/blue colour fringe on the (at most) 8 icons farthest from a live touch, giving the lens rim an optical feel without touching geometry, hit-testing or the 120 Hz budget.

**Architecture:** A pure `ChromaticAberration` object (gate, offset in px, direction, and a fixed-capacity `TopFarthest` selector) beside `LensVignette` / `FocusPeaking`. `LensView.drawGrid` records each outside-zone icon in `TopFarthest` while it draws the icons, and after the icon loop a small second pass draws a red-only copy shifted outward and a blue-only copy shifted inward (colour-matrix filters, `PorterDuff.Mode.ADD`) for just the selected icons. A new global boolean `chromatic_aberration` (default off) follows the FISH-034/039 setting pattern end to end.

**Tech Stack:** Kotlin/Java, Android Canvas (`ColorMatrixColorFilter`, `PorterDuffXfermode`), SharedPreferences only, JUnit4/Robolectric and AndroidJUnit4. No new dependency.

## Global Constraints (spec: `docs/superpowers/specs/2026-11-07-fish-040-chromatic-aberration-rim-design.md`)

- Branch `dev` only; no feature branch, no worktree. No push unless the owner asks. Do not stage `.idea/`. Commit trailer: `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Mechanism: draw the icon in place (unchanged), then a red-only copy shifted **outward** and a blue-only copy shifted **inward** from the touch point, both `PorterDuff.Mode.ADD`. No `RenderEffect`, no `RuntimeShader`; one code path for every API (minSdk 25).
- Cap: at most **8** icons per frame, the 8 farthest from the touch among icons outside the sharp zone. Worst case 16 extra `drawBitmap` per frame.
- When: live touch only (`hasActiveTouch && dofTransition > 0`), exactly the vignette gate. Never while focus is pinned (FISH-029), on tilt (FISH-033) or idle.
- Offset: 0 inside the sharp zone, rising linearly from 1 dp to 3 dp with the normalized focus distance, scaled by the lens transition, along the unit vector from touch to icon centre.
- Alpha: the fringe uses the icon's own alpha (`min` of the DoF fallback alpha and the vignette alpha, never the product).
- Own setting `chromatic_aberration`, default **off**, in reset-to-defaults. FISH-024 halo, FISH-034 vignette, FISH-039 focus peaking and DoF are unchanged and independent.
- Never change geometry, hit-testing, launch, haptics or `LensGridCache`. Clean mode does not hide it; reduced motion only snaps the transition.
- No allocation in `onDraw`: view-owned paints, `TopFarthest(8)`, 8 `RectF` and one scratch `RectF`, all allocated once.
- DoF BLUR (API 31+): fringes apply to icons in DoF band 0 and 1 only; band 2 icons keep no fringe (recorded decision, revisited only if the audit shows a visual conflict).
- **Device (owner, 2026-11-07): Samsung S24 Ultra `R5CX613VZBR` for every session.** Never touch another device. The S24U has work-profile users (`UserInfo{95:DUAL_APP}`, `{150:Secure Folder}`): every `pm`/`am instrument`/`install`/`cmd package` command must pass `--user 0`, otherwise it fails with `Shell does not have permission to access user 150`. Before the first install the main app is **not** installed for user 0 (only `com.mckimquyen.lenslauncher.test`), so the baseline to restore is "main app absent, test APK hash unchanged": back up the test APK, its SHA-256 and the six system values, and at the end `pm uninstall --user 0 com.mckimquyen.lenslauncher` and reinstall the backed-up test APK. If the main app is installed when the run starts, back up its APK and a `databases shared_prefs` tar as well and restore them. Restore data by pushing the tar to `/data/local/tmp` and extracting with `run-as … tar xf`; **never** `exec-out … tar xf -` (it hangs).
- Ads: the owner allows closing an ad through adb (for example `input keyevent KEYCODE_BACK`, or `am force-stop` of an app that is not this project and raised a dialog). Never click ad content and never tap "Start (Ads may appear)". An unrelated app's dialog (seen before: `com.mckimquyen.bemytester`'s 16 KB warning) may cover Home; stop that app and rerun.
- Smoke input is real touch through `UiAutomation.injectInputEvent` plus `UiAutomation.takeScreenshot` (the FISH-039 pattern). Never `input tap` on icons. The injector keeps one virtual finger: always release it in `finally` and in `@Before`.
- R5: named constants, no `late`/`!!` without a justified comment, a test for every behavior. Every behavior change: write the test, run it, watch it fail for the expected reason, write the minimum code, rerun.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/views/ChromaticAberration.kt` — pure math + `TopFarthest`.
- Create `app/src/test/java/com/mckimquyen/views/ChromaticAberrationTest.kt`, `ChromaticAberrationNoAllocationTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`; create `app/src/test/java/com/mckimquyen/util/UtilSettingsChromaticAberrationTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/ui/settings/ResetEntries.kt`, `app/src/test/java/com/mckimquyen/ui/settings/ResetEntriesTest.kt`.
- Modify `app/src/main/res/layout/frm_settings.xml`, `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` — switch row.
- Modify `app/src/main/res/values/strings.xml` + 16 `values-<locale>/strings.xml` — two strings.
- Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsChromaticAberrationWidgetTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` — flag, paints, selection, second pass, probes.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewChromaticAberrationWidgetTest.kt` (touch path, software canvas) and `LensViewChromaticAberrationIntegrationTest.kt` (pinned/tilt/idle, real `ActHome` BLUR path).
- Create `app/src/androidTest/java/com/mckimquyen/ui/ChromaticAberrationScreenshotSmokeTest.kt`.
- Evidence: `doc/task/AUDIT_FISH-040_2026-11-07.md`, `doc/feature.md`, `doc/task/README.md`; story `todo/` → `inprogress/` (Task 1) → `done/`.

## Interfaces

```kotlin
package com.mckimquyen.views

object ChromaticAberration {
    const val MAX_ICONS = 8
    const val MIN_OFFSET_DP = 1f
    const val MAX_OFFSET_DP = 3f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA
    const val MAX_FRINGED_BAND = 1            // DoF bands 0 and 1 only
    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean
    fun offsetPx(focusDistance: Float, transition: Float, density: Float): Float
    fun direction(touchX: Float, touchY: Float, cellX: Float, cellY: Float, out: FloatArray)   // out[0], out[1]; zero when coincident
    fun isFringed(focusDistance: Float, dofBand: Int): Boolean   // outside the zone, finite, band <= MAX_FRINGED_BAND

    class TopFarthest(val capacity: Int) {
        val count: Int
        fun reset()
        fun offer(distance: Float, index: Int): Int   // slot now holding the entry (0 until capacity), or -1 when rejected
        fun indexAt(slot: Int): Int
        fun distanceAt(slot: Int): Float
    }
}
```

`UtilSettings`: `const val KEY_CHROMATIC_ABERRATION = "chromatic_aberration"`, `const val DEFAULT_CHROMATIC_ABERRATION = false`.

`LensView` (all `@VisibleForTesting internal`): `var chromaticAberrationEnabled: Boolean`, `val chromaIconsForTest: Int` (icons fringed in the last frame), `val chromaIndicesForTest: IntArray` (copy of the fringed cell indices, `chromaIconsForTest` long), `val chromaOffsetPxForTest: Float` (largest offset used in the last frame, 0 when none), `val chromaAlphaForTest: Int` (alpha of the fringes in the last frame, 0 when none).

Strings: `setting_chromatic_aberration`, `setting_chromatic_aberration_hint`. View ids: `rlSwitchChromaticAberrationParent`, `iconChromaticAberration`, `swChromaticAberration`, `tvChromaticAberrationHint`.

---

## Task 1 — Pure math and the farthest-N selector

**Files:** create `ChromaticAberration.kt`, `ChromaticAberrationTest.kt`; move the story to `inprogress/`.

- [ ] **Step 1: Move the story.** `git branch --show-current` must print `dev`. `git mv doc/task/todo/p3-fish-fish-040-chromatic-aberration-rim.md doc/task/inprogress/p3-fish-fish-040-chromatic-aberration-rim.md`, set `| Status | inprogress |`, and point the `FISH-040` row in `doc/task/README.md` at `inprogress/…`.
- [ ] **Step 2: RED.** Create `app/src/test/java/com/mckimquyen/views/ChromaticAberrationTest.kt`:

```kotlin
package com.mckimquyen.views

import com.mckimquyen.views.ChromaticAberration.TopFarthest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ChromaticAberrationTest {
    private val density = 3f
    private val zone = DepthOfField.FOCUS_ZONE

    @Test fun theCapIsEightAndTheOffsetRangeIsOneToThreeDp() {
        assertEquals(8, ChromaticAberration.MAX_ICONS)
        assertEquals(1f, ChromaticAberration.MIN_OFFSET_DP, 0f)
        assertEquals(3f, ChromaticAberration.MAX_OFFSET_DP, 0f)
    }

    @Test fun theEffectAppliesOnlyWhenEnabledAndTheLensIsVisible() {
        for (enabled in listOf(false, true)) for (visible in listOf(false, true)) {
            assertEquals("enabled=$enabled visible=$visible", enabled && visible,
                ChromaticAberration.shouldApply(enabled, visible))
        }
    }

    @Test fun thereIsNoOffsetInsideTheSharpZoneOrWithoutATransition() {
        assertEquals(0f, ChromaticAberration.offsetPx(0f, 1f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(zone, 1f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(1f, 0f, density), 0f)
    }

    @Test fun justOutsideTheZoneTheOffsetStartsAtOneDpAndTheFarthestIconGetsThreeDp() {
        assertEquals(1f * density, ChromaticAberration.offsetPx(zone + 0.0001f, 1f, density), 0.01f)
        assertEquals(3f * density, ChromaticAberration.offsetPx(1f, 1f, density), 0.001f)
    }

    @Test fun theOffsetNeverFallsAsTheDistanceGrows() {
        var previous = 0f
        var d = zone
        while (d <= 1.0001f) {
            val o = ChromaticAberration.offsetPx(d, 1f, density)
            assertTrue("offset $o at $d must not drop below $previous", o >= previous)
            previous = o
            d += 0.02f
        }
    }

    @Test fun theOffsetScalesWithDensityAndTheTransitionAndClampsOutOfRangeInput() {
        assertEquals(2f * ChromaticAberration.offsetPx(0.6f, 1f, 2f), ChromaticAberration.offsetPx(0.6f, 1f, 4f), 0.001f)
        assertEquals(0.5f * ChromaticAberration.offsetPx(0.6f, 1f, density), ChromaticAberration.offsetPx(0.6f, 0.5f, density), 0.001f)
        assertEquals(ChromaticAberration.offsetPx(1f, 1f, density), ChromaticAberration.offsetPx(9f, 1f, density), 0f)
        assertEquals(ChromaticAberration.offsetPx(0.6f, 1f, density), ChromaticAberration.offsetPx(0.6f, 7f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(-3f, 1f, density), 0f)
    }

    @Test fun nonFiniteInputProducesNoOffset() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, ChromaticAberration.offsetPx(bad, 1f, density), 0f)
            assertEquals(0f, ChromaticAberration.offsetPx(0.8f, bad, density), 0f)
            assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, bad), 0f)
        }
        assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, 0f), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, -2f), 0f)
    }

    @Test fun theDirectionIsAUnitVectorFromTheTouchToTheCell() {
        val out = FloatArray(2)
        ChromaticAberration.direction(100f, 100f, 400f, 500f, out)
        assertEquals(1f, sqrt(out[0] * out[0] + out[1] * out[1]), 1e-5f)
        assertEquals(0.6f, out[0], 1e-5f)
        assertEquals(0.8f, out[1], 1e-5f)
        ChromaticAberration.direction(100f, 100f, 0f, 100f, out)
        assertEquals(-1f, out[0], 1e-5f)
        assertEquals(0f, out[1], 1e-5f)
    }

    @Test fun theDirectionIsZeroWhenTheCellIsOnTheTouchOrAnInputIsNotFinite() {
        val out = floatArrayOf(7f, 7f)
        ChromaticAberration.direction(50f, 60f, 50f, 60f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
        out[0] = 7f; out[1] = 7f
        ChromaticAberration.direction(Float.NaN, 60f, 5f, 6f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
    }

    @Test fun anIconIsFringedOnlyOutsideTheZoneAndInDofBandsZeroAndOne() {
        assertFalse(ChromaticAberration.isFringed(zone, 0))
        assertTrue(ChromaticAberration.isFringed(zone + 0.01f, 0))
        assertTrue(ChromaticAberration.isFringed(0.9f, 1))
        assertFalse("band 2 is blurred, a fringe would fight the blur", ChromaticAberration.isFringed(0.9f, 2))
        assertFalse(ChromaticAberration.isFringed(Float.NaN, 0))
        assertFalse(ChromaticAberration.isFringed(0.9f, -1))
    }

    private fun selected(top: TopFarthest) = (0 until top.count).map { top.indexAt(it) }.sorted()

    @Test fun topFarthestKeepsTheLargestDistancesInAnyInsertionOrder() {
        for (order in listOf(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0), listOf(4, 9, 0, 5, 2, 7, 1, 8, 3, 6))) {
            val top = TopFarthest(3)
            order.forEach { top.offer(it / 10f, it) }
            assertEquals(order.toString(), listOf(7, 8, 9), selected(top))
            assertEquals(3, top.count)
        }
    }

    @Test fun topFarthestWithFewerEntriesThanCapacityKeepsAll() {
        val top = TopFarthest(8)
        top.offer(0.5f, 11); top.offer(0.3f, 12)
        assertEquals(2, top.count)
        assertEquals(listOf(11, 12), selected(top))
    }

    @Test fun topFarthestIgnoresNonFiniteAndNeverExceedsCapacity() {
        val top = TopFarthest(2)
        assertEquals(-1, top.offer(Float.NaN, 1))
        assertEquals(-1, top.offer(Float.POSITIVE_INFINITY, 2))
        assertEquals(0, top.count)
        repeat(50) { top.offer(it / 50f, it) }
        assertEquals(2, top.count)
        assertEquals(listOf(48, 49), selected(top))
    }

    @Test fun offerReturnsAStableSlotForKeptEntriesAndMinusOneForRejectedOnes() {
        val top = TopFarthest(2)
        val a = top.offer(0.5f, 100)
        val b = top.offer(0.6f, 101)
        assertTrue(a in 0..1 && b in 0..1 && a != b)
        assertEquals(-1, top.offer(0.1f, 102))
        assertEquals(100, top.indexAt(a)); assertEquals(0.5f, top.distanceAt(a), 0f)
        assertEquals(101, top.indexAt(b)); assertEquals(0.6f, top.distanceAt(b), 0f)
    }

    @Test fun aDisplacedSlotIsReusedByTheFartherEntry() {
        val top = TopFarthest(2)
        val low = top.offer(0.2f, 1)
        top.offer(0.9f, 2)
        val reused = top.offer(0.5f, 3)
        assertEquals("the slot of the nearest displaced entry is reused", low, reused)
        assertEquals(3, top.indexAt(reused))
        assertEquals(listOf(2, 3), selected(top))
    }

    @Test fun equalDistancesDoNotDisplaceAnEarlierEqualEntry() {
        val top = TopFarthest(2)
        top.offer(0.5f, 1); top.offer(0.5f, 2)
        assertEquals(-1, top.offer(0.5f, 3))
        assertEquals(listOf(1, 2), selected(top))
    }

    @Test fun resetEmptiesTheSelectorAndAllowsReuse() {
        val top = TopFarthest(2)
        top.offer(0.5f, 1); top.offer(0.6f, 2)
        top.reset()
        assertEquals(0, top.count)
        top.offer(0.1f, 9)
        assertEquals(listOf(9), selected(top))
    }

    @Test fun aCapacityBelowOneIsRejected() {
        assertTrue(runCatching { TopFarthest(0) }.isFailure)
        assertTrue(runCatching { TopFarthest(-1) }.isFailure)
    }
}
```

- [ ] **Step 3: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.ChromaticAberrationTest' > /tmp/f040-red1.txt 2>&1; grep -E "^e: " /tmp/f040-red1.txt | sed 's#.*/##' | cut -c1-120 | sort -u | head`. Expected: compile errors only, `Unresolved reference 'ChromaticAberration'` (and `TopFarthest`).
- [ ] **Step 4: GREEN.** Create `app/src/main/java/com/mckimquyen/views/ChromaticAberration.kt`:

```kotlin
package com.mckimquyen.views

import kotlin.math.hypot

/**
 * FISH-040: chromatic aberration at the lens rim. Pure math only. The fringe is two extra copies of an icon (red-only,
 * shifted outward; blue-only, shifted inward) drawn after the icons, so it never touches geometry or hit-testing.
 * Distance, focus zone and transition reuse [DepthOfField] so every optical effect agrees on what "far from the finger"
 * means and on how reduced motion snaps the transition.
 */
object ChromaticAberration {
    /** At most this many icons per frame get fringes: the farthest ones. Worst case 2 * MAX_ICONS extra bitmap draws. */
    const val MAX_ICONS = 8
    const val MIN_OFFSET_DP = 1f
    const val MAX_OFFSET_DP = 3f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    /** Blurred DoF layers (band 2) are downsampled, so a 1 to 3 dp fringe there is invisible and wasted. */
    const val MAX_FRINGED_BAND = 1

    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean {
        return enabled && lensVisible
    }

    /**
     * Offset in px: 0 inside the sharp zone, [MIN_OFFSET_DP] just outside it, rising linearly to [MAX_OFFSET_DP] at the
     * farthest point, scaled by the lens [transition]. Any non-finite or non-positive input yields 0 (no fringe).
     */
    fun offsetPx(focusDistance: Float, transition: Float, density: Float): Float {
        if (!focusDistance.isFinite() || !transition.isFinite() || !density.isFinite() || density <= 0f) return 0f
        if (focusDistance <= DepthOfField.FOCUS_ZONE) return 0f
        val ramp = ((focusDistance - DepthOfField.FOCUS_ZONE) / (1f - DepthOfField.FOCUS_ZONE)).coerceIn(0f, 1f)
        val dp = MIN_OFFSET_DP + (MAX_OFFSET_DP - MIN_OFFSET_DP) * ramp
        return density * dp * transition.coerceIn(0f, 1f)
    }

    /** Unit vector from the touch to the cell centre, written into [out]; zero when they coincide or input is not finite. */
    fun direction(touchX: Float, touchY: Float, cellX: Float, cellY: Float, out: FloatArray) {
        val dx = cellX - touchX
        val dy = cellY - touchY
        val length = hypot(dx, dy)
        if (!length.isFinite() || length <= 0f) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        out[0] = dx / length
        out[1] = dy / length
    }

    /** True for a finite distance outside the sharp zone, in a DoF band that still shows detail. */
    fun isFringed(focusDistance: Float, dofBand: Int): Boolean {
        return focusDistance.isFinite() && focusDistance > DepthOfField.FOCUS_ZONE && dofBand in 0..MAX_FRINGED_BAND
    }

    /**
     * Keeps the [capacity] largest distances seen since [reset], with the cell index of each. Fixed arrays, so offering
     * during the draw loop allocates nothing. Equal distances never displace an earlier entry.
     */
    class TopFarthest(val capacity: Int) {
        init {
            require(capacity >= 1) { "capacity must be at least 1" }
        }

        private val distances = FloatArray(capacity)
        private val indices = IntArray(capacity)

        var count = 0
            private set

        fun reset() {
            count = 0
        }

        fun indexAt(slot: Int): Int = indices[slot]

        fun distanceAt(slot: Int): Float = distances[slot]

        /** Returns the slot now holding the entry (0 until [capacity]), or -1 when it did not make the cut. */
        fun offer(distance: Float, index: Int): Int {
            if (!distance.isFinite()) return -1
            if (count < capacity) {
                distances[count] = distance
                indices[count] = index
                return count++
            }
            var nearest = 0
            for (slot in 1 until capacity) if (distances[slot] < distances[nearest]) nearest = slot
            if (distance <= distances[nearest]) return -1
            distances[nearest] = distance
            indices[nearest] = index
            return nearest
        }
    }
}
```

- [ ] **Step 5: Verify GREEN.** Rerun the class; read `app/build/test-results/testDevDebugUnitTest/TEST-com.mckimquyen.views.ChromaticAberrationTest.xml` for `tests="18" failures="0"`.
- [ ] **Step 6: Commit.** `git branch --show-current` (must be `dev`), `git add app/src/main/java/com/mckimquyen/views/ChromaticAberration.kt app/src/test/java/com/mckimquyen/views/ChromaticAberrationTest.kt doc/task/inprogress doc/task/todo doc/task/README.md && git commit -m "feat(fish-040): add chromatic aberration math and farthest selector"` with the trailer.

---

## Task 2 — Own setting, reset entry and Settings switch

**Files:** modify `UtilSettings.kt`, create `UtilSettingsChromaticAberrationTest.kt`, modify `ResetEntries.kt` and `ResetEntriesTest.kt`, `frm_settings.xml`, `FrmSettings.kt`; create `FrmSettingsChromaticAberrationWidgetTest.kt`; add two strings in 17 locale files.

**Interfaces:** consumes no code from Task 1; produces `UtilSettings.KEY_CHROMATIC_ABERRATION`, `DEFAULT_CHROMATIC_ABERRATION`, resource ids `rlSwitchChromaticAberrationParent`, `iconChromaticAberration`, `swChromaticAberration`, `tvChromaticAberrationHint`, strings `setting_chromatic_aberration` and `_hint`.

- [ ] **Step 1: RED (settings).** Create `app/src/test/java/com/mckimquyen/util/UtilSettingsChromaticAberrationTest.kt`:

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsChromaticAberrationTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun chromaticAberrationIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
        assertFalse(UtilSettings.DEFAULT_CHROMATIC_ABERRATION)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
        settings.save(UtilSettings.KEY_CHROMATIC_ABERRATION, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
    }

    @Test fun theKeyIsStable() = assertEquals("chromatic_aberration", UtilSettings.KEY_CHROMATIC_ABERRATION)
}
```

- [ ] **Step 2: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsChromaticAberrationTest'`. Expected: compile failure only: `Unresolved reference KEY_CHROMATIC_ABERRATION / DEFAULT_CHROMATIC_ABERRATION`.
- [ ] **Step 3: GREEN (settings + reset).** In `UtilSettings.kt` beside `KEY_LENS_VIGNETTE`: add `const val DEFAULT_CHROMATIC_ABERRATION = false`, `const val KEY_CHROMATIC_ABERRATION = "chromatic_aberration"`, and add `KEY_CHROMATIC_ABERRATION -> prefs.getBoolean(name, DEFAULT_CHROMATIC_ABERRATION)` beside the other boolean keys. In `ResetEntries.kt` add `flag(UtilSettings.KEY_CHROMATIC_ABERRATION, R.string.setting_chromatic_aberration, UtilSettings.DEFAULT_CHROMATIC_ABERRATION)` beside the vignette entry. In `ResetEntriesTest.kt` add `"chromatic_aberration"` to the expected settings-key set.
- [ ] **Step 4: Verify GREEN.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsChromaticAberrationTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest'` — new setting tests 3/3, ResetEntries test class all pass.
- [ ] **Step 5: RED (Settings widget).** Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsChromaticAberrationWidgetTest.kt`, mirror `FrmSettingsLensVignetteWidgetTest` exactly, replacing `KEY_LENS_VIGNETTE`/`swLensVignette` with `KEY_CHROMATIC_ABERRATION`/`swChromaticAberration`. Four test bodies:
  - `switchIsOff_whenSettingHasNeverBeenSaved`: launch `FrmSettings`, assert the switch is unchecked.
  - `switchIsOn_whenSettingWasPreviouslySavedTrue`: set key true, launch, assert checked.
  - `togglingTheSwitch_persistsTheNewValue`: toggle on in the fragment, assert stored true.
  - `resetToDefaults_clearsTheSettingBackToOff`: set true, invoke `SettingsInterface.onDefaultsReset()`, assert switch and stored value are false.
  Use `DeviceStateGuard`, clear default preferences in `@Before/@After`, and `launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar)` as the existing vignette test does.
- [ ] **Step 6: Verify RED.** `./gradlew compileDevDebugAndroidTestKotlin`. Expected resource symbol errors for `swChromaticAberration` and `setting_chromatic_aberration` only.
- [ ] **Step 7: GREEN (layout and fragment).** Insert this row in `frm_settings.xml` after the vignette row/divider and before the Smart Focus row; add a matching divider after the row:

```xml
                <!-- FISH-040: chromatic aberration, opt-in -->
                <RelativeLayout
                    android:id="@+id/rlSwitchChromaticAberrationParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">
                    <ImageView
                        android:id="@+id/iconChromaticAberration"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_color_lens_24dp" />
                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swChromaticAberration"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconChromaticAberration"
                        android:paddingTop="12dp"
                        android:text="@string/setting_chromatic_aberration"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />
                    <TextView
                        android:id="@+id/tvChromaticAberrationHint"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_below="@id/swChromaticAberration"
                        android:layout_marginStart="12dp"
                        android:layout_marginEnd="16dp"
                        android:layout_toEndOf="@id/iconChromaticAberration"
                        android:paddingBottom="8dp"
                        android:text="@string/setting_chromatic_aberration_hint"
                        android:textColor="?android:attr/textColorTertiary"
                        android:textSize="12sp" />
                </RelativeLayout>
```

  `FrmSettings.kt`: add nullable `swChromaticAberration: SwitchCompat?`; bind `R.id.swChromaticAberration`; null the parent row listener in `onDestroyView`; `setOnCheckedChangeListener` saves `KEY_CHROMATIC_ABERRATION`; `assignValues()` reads it; null the field in `onDestroyView`. Keep it independent from `KEY_LENS_VIGNETTE`, `KEY_DEPTH_OF_FIELD`, `KEY_SHOW_FOCUS_HALO` and Clean mode.

- [ ] **Step 8: Add strings in 17 locales.** In `values/strings.xml` and `values-ar`, `-de`, `-es`, `-fr`, `-hi`, `-in`, `-it`, `-ja`, `-km`, `-ko`, `-lo`, `-pt`, `-ru`, `-th`, `-vi`, `-zh` add both keys (AI translations; audit says not native-reviewed):

| locale | `setting_chromatic_aberration` | `setting_chromatic_aberration_hint` |
|---|---|---|
| base | Chromatic aberration | Adds subtle red/blue colour fringes to a few distant icons while dragging the lens. Off by default. |
| ar | الانحراف اللوني | يضيف حواف لونية حمراء وزرقاء خفيفة إلى بعض الأيقونات البعيدة أثناء سحب العدسة. معطل افتراضياً. |
| de | Chromatische Aberration | Fügt beim Ziehen der Linse einigen entfernten Symbolen dezente rote und blaue Farbsäume hinzu. Standardmäßig aus. |
| es | Aberración cromática | Añade sutiles franjas rojas y azules a algunos iconos lejanos al arrastrar la lente. Desactivado de forma predeterminada. |
| fr | Aberration chromatique | Ajoute de légères franges rouges et bleues à quelques icônes éloignées pendant le déplacement de la loupe. Désactivé par défaut. |
| hi | रंगीन विपथन | लेंस खींचते समय कुछ दूर के आइकन में हल्की लाल/नीली रंगीन किनारियाँ जोड़ता है। डिफ़ॉल्ट रूप से बंद। |
| in | Aberasi kromatik | Menambahkan pinggiran merah/biru halus pada beberapa ikon yang jauh saat lensa digeser. Nonaktif secara default. |
| it | Aberrazione cromatica | Aggiunge sottili frange rosse e blu ad alcune icone lontane mentre trascini la lente. Disattivata per impostazione predefinita. |
| ja | 色収差 | レンズをドラッグ中、遠くの一部のアイコンに赤と青の薄い色縁を追加します。初期設定ではオフです。 |
| km | ភាពខុសពណ៌ | បន្ថែមគែមពណ៌ក្រហម/ខៀវស្រាលៗលើរូបតំណាងឆ្ងាយមួយចំនួនពេលអូសកែវ។ បិទតាមលំនាំដើម។ |
| ko | 색수차 | 렌즈를 드래그할 때 멀리 있는 일부 아이콘에 은은한 빨강/파랑 색 테두리를 추가합니다. 기본값은 꺼짐입니다. |
| lo | ຄວາມຄາດເຄື່ອນຂອງສີ | ເພີ່ມຂອບສີແດງ/ຟ້າບາງໆໃຫ້ບາງໄອຄອນທີ່ໄກ ເມື່ອລາກເລນ. ປິດໄວ້ໂດຍຄ່າເລີ່ມຕົ້ນ. |
| pt | Aberração cromática | Adiciona franjas vermelhas e azuis sutis a alguns ícones distantes ao arrastar a lente. Desativada por padrão. |
| ru | Хроматическая аберрация | Добавляет лёгкие красно-синие цветные каймы нескольким дальним значкам при перетаскивании линзы. По умолчанию выключена. |
| th | ความคลาดสี | เพิ่มขอบสีแดง/น้ำเงินบางๆ ให้ไอคอนที่อยู่ไกลบางรายการขณะลากเลนส์ ปิดไว้โดยค่าเริ่มต้น |
| vi | Sai sắc | Thêm viền đỏ/xanh nhẹ cho một số biểu tượng xa khi kéo lens. Mặc định tắt. |
| zh | 色差 | 拖动镜头时，为少数较远的图标添加淡淡的红蓝色边缘。默认关闭。 |

- [ ] **Step 9: Verify GREEN (host).** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsChromaticAberrationTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest' --tests 'com.mckimquyen.a11y.AllStringsTranslationTest' --tests 'com.mckimquyen.a11y.LensStringTranslationTest'`; `assembleDevDebugAndroidTest` compiles; then after Task 6 backup run `FrmSettingsChromaticAberrationWidgetTest`, expect `OK (4 tests)`.
- [ ] **Step 10: Commit.** `git branch --show-current` must print `dev`; stage only setting, reset, layout, fragment, strings and those tests; commit `feat(fish-040): add chromatic aberration setting` with trailer.

---

## Task 3 — Tests first: no-allocation guard, widget and integration tests

**Files:** create `ChromaticAberrationNoAllocationTest.kt`, `LensViewChromaticAberrationWidgetTest.kt`, `LensViewChromaticAberrationIntegrationTest.kt`. No production code in this task: every test must fail for the expected reason (missing symbols or missing functions) before Task 4.

**Interfaces:** consumes Task 1 (`ChromaticAberration`) and Task 2 (`UtilSettings.KEY_CHROMATIC_ABERRATION`); the tests define the `LensView` probes that Task 4 must implement exactly: `chromaticAberrationEnabled`, `chromaIconsForTest`, `chromaIndicesForTest`, `chromaOffsetPxForTest`, `chromaAlphaForTest`.

**Design (read before coding Task 4).** `drawGrid` computes one decision per frame: `chromaOn = ChromaticAberration.shouldApply(chromaticAberrationEnabled, mDrawType == APPS && hasActiveTouch && dofTransition > 0f)`. Inside the cell loop (live touch only) the existing `focusDistance` is reused: it is already computed when `dofMode != OFF || vignetteOn`, and the gate becomes `dofMode != OFF || vignetteOn || chromaOn`. A cell is offered to `TopFarthest(8)` when `ChromaticAberration.isFringed(focusDistance, dofBand)`; the slot that `offer` returns decides where its final rect is copied (`mChromaRects[slot].set(rect)`). The fringe alpha is the icon's own `LensVignette.combine(dofAlpha, vignetteAlpha)` for that cell, stored per slot in `mChromaAlpha[slot]`. After the icon loop, and after `dofLayers?.endAndDraw(canvas)`, a second pass draws red then blue for each selected slot on the main canvas; the fringe never goes through the DoF layers.

- [ ] **Step 1: JVM allocation guard.** Create `app/src/test/java/com/mckimquyen/views/ChromaticAberrationNoAllocationTest.kt`:

```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** FISH-040: the chromatic-aberration hot path must not allocate; checked on the source of the new LensView functions. */
class ChromaticAberrationNoAllocationTest {
    private val lensView: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }.readText()
    }

    private val math: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/ChromaticAberration.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/ChromaticAberration.kt")
        ).first { it.isFile }.readText()
    }

    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature not found", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') { depth--; if (depth == 0) return source.substring(open, i + 1) }
            i++
        }
        error("unbalanced braces after $signature")
    }

    private val forbidden = listOf("Paint(", "RectF(", "Rect(", "Path(", "ArrayList", "listOf(", "mutableListOf", "String.format", "Array(", "FloatArray(", "IntArray(", "ColorMatrix(", "ColorMatrixColorFilter(", "PorterDuffXfermode(")

    @Test fun theSecondPassDoesNotAllocate() {
        val body = bodyOf(lensView, "private fun drawChromaticFringes(")
        forbidden.forEach { assertTrue("drawChromaticFringes must not contain '$it'", !body.contains(it)) }
        assertEquals("two bitmap draws per fringed icon (red and blue)", 3, body.split("drawBitmap(").size)
    }

    @Test fun thePerFramePreparationDoesNotAllocate() {
        val body = bodyOf(lensView, "private fun prepareChromaticAberration(")
        forbidden.forEach { assertTrue("prepareChromaticAberration must not contain '$it'", !body.contains(it)) }
    }

    @Test fun theMathFunctionsDoNotAllocate() {
        listOf("fun offsetPx(", "fun direction(", "fun isFringed(", "fun shouldApply(").forEach { sig ->
            val body = bodyOf(math, sig)
            forbidden.forEach { assertTrue("$sig must not contain '$it'", !body.contains(it)) }
        }
    }

    @Test fun offerDoesNotAllocateAfterConstruction() {
        val body = bodyOf(math, "fun offer(")
        forbidden.forEach { assertTrue("offer must not contain '$it'", !body.contains(it)) }
    }
}
```

- [ ] **Step 2: Widget test, touch path on a bare view.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewChromaticAberrationWidgetTest.kt`. It reuses the FISH-034/039 harness (software `Bitmap` canvas, `setLensStateForTest`) with **pure red** and **pure blue** icon bitmaps so each fringe channel can be probed from pixels:

```kotlin
package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-040 touch-path proof on a bare LensView drawn into a software canvas (the DoF ALPHA fallback path). */
@RunWith(AndroidJUnit4::class)
class LensViewChromaticAberrationWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 40
        const val ICON_PX = 48
        const val PARTIAL_ANIMATION = 0.3f
        const val FULL_ANIMATION = 1f
        const val IDLE = -Float.MAX_VALUE
        const val OPAQUE = 255
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    /** Pure green: ADD of the red-only copy then shows as a red component on top of green, blue likewise. */
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish040.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish040#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        com.mckimquyen.util.BitmapCache.clear()
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun touchAt(x: Float, y: Float, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, animation, reduceMotion)

    /** Centre of a real cell; the first render computes the grid cache. */
    private fun cellCentre(index: Int): Pair<Float, Float> {
        render().recycle()
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun touchAtCell(index: Int, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) {
        val (x, y) = cellCentre(index)
        touchAt(x, y, animation, reduceMotion)
    }

    private fun countReddish(bitmap: Bitmap): Int {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.count { Color.red(it) > 40 && Color.green(it) > 40 }       // green icon + added red
    }

    private fun countBluish(bitmap: Bitmap): Int {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.count { Color.blue(it) > 40 && Color.green(it) > 40 }
    }

    @Test fun defaultIsOffSoNoIconIsFringed() {
        touchAtCell(0)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
        assertFalse(lensView.chromaticAberrationEnabled)
    }

    @Test fun withTheSettingOnAtMostEightFarthestOutsideZoneIconsAreFringed() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        val n = lensView.chromaIconsForTest
        assertTrue("some far icons are fringed, was $n", n >= 1)
        assertTrue("never more than the cap, was $n", n <= ChromaticAberration.MAX_ICONS)
        assertEquals(n, lensView.chromaIndicesForTest.size)
    }

    @Test fun theFringedIconsAreTheFarthestOnesFromTheTouch() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)                                   // finger on the first cell: far cells are the last ones
        render().recycle()
        val fringed = lensView.chromaIndicesForTest.toList()
        assertTrue(fringed.isNotEmpty())
        assertFalse("the icon under the finger is never fringed", 0 in fringed)
        assertTrue("fringed icons come from the far half of the grid, was $fringed", fringed.all { it >= APP_COUNT / 2 })
    }

    @Test fun redAndBlueFringePixelsAppearWhenEnabledAndNotWhenDisabled() {
        touchAtCell(0)
        val offRed = countReddish(render())
        val offBlue = countBluish(render())
        lensView.chromaticAberrationEnabled = true
        val onRed = countReddish(render())
        val onBlue = countBluish(render())
        assertTrue("red fringe adds pixels ($onRed vs $offRed)", onRed > offRed)
        assertTrue("blue fringe adds pixels ($onBlue vs $offBlue)", onBlue > offBlue)
    }

    @Test fun theOffsetStaysWithinOneToThreeDpOfTheDensity() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        val density = context.resources.displayMetrics.density
        val px = lensView.chromaOffsetPxForTest
        assertTrue("offset $px >= 1dp", px >= ChromaticAberration.MIN_OFFSET_DP * density - 0.01f)
        assertTrue("offset $px <= 3dp", px <= ChromaticAberration.MAX_OFFSET_DP * density + 0.01f)
    }

    /**
     * Direction proof on one far icon: the red copy lies on the side AWAY from the touch and the blue copy on the side
     * TOWARD it. The icon is pure green, so a pixel that has gained red (or blue) is a fringe pixel; it is classified by
     * which side of the icon's drawn centre it lies on, along the touch-to-icon axis.
     */
    @Test fun theRedFringeLiesOutwardAndTheBlueFringeInwardAlongTheTouchAxis() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        val (touchX, touchY) = cellCentre(0)
        render().recycle()
        val farCell = lensView.chromaIndicesForTest.maxOrNull() ?: error("no icon was fringed")
        val rect = lensView.drawnRectsForTest()[farCell]
        val dx = rect.centerX() - touchX
        val dy = rect.centerY() - touchY
        val length = kotlin.math.hypot(dx, dy)
        assertTrue("far icon is away from the touch", length > 1f)
        val ux = dx / length
        val uy = dy / length
        val bitmap = render()
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        var redOutward = 0; var redInward = 0; var blueOutward = 0; var blueInward = 0
        val x0 = (rect.left - 12f).toInt().coerceAtLeast(0); val x1 = (rect.right + 12f).toInt().coerceAtMost(SIZE_PX - 1)
        val y0 = (rect.top - 12f).toInt().coerceAtLeast(0); val y1 = (rect.bottom + 12f).toInt().coerceAtMost(SIZE_PX - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val c = pixels[y * SIZE_PX + x]
            val side = (x - rect.centerX()) * ux + (y - rect.centerY()) * uy      // > 0 means away from the touch
            if (Color.red(c) > 40 && Color.green(c) > 40) { if (side > 0) redOutward++ else redInward++ }
            if (Color.blue(c) > 40 && Color.green(c) > 40) { if (side > 0) blueOutward++ else blueInward++ }
        }
        assertTrue("red lies outward ($redOutward out vs $redInward in)", redOutward > redInward)
        assertTrue("blue lies inward ($blueInward in vs $blueOutward out)", blueInward > blueOutward)
    }

    @Test fun anIdleLensDrawsNoFringeEvenWhenEnabled() {
        lensView.chromaticAberrationEnabled = true
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
    }

    @Test fun aTouchWhoseTransitionIsStillZeroDrawsNothingYet() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
        assertEquals(0f, lensView.chromaOffsetPxForTest, 0f)
    }

    @Test fun theOffsetFadesWithTheLensAndSnapsUnderReducedMotion() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0, animation = PARTIAL_ANIMATION)
        render().recycle()
        val eased = lensView.chromaOffsetPxForTest
        touchAtCell(0, animation = PARTIAL_ANIMATION, reduceMotion = true)
        render().recycle()
        val snapped = lensView.chromaOffsetPxForTest
        assertTrue("snapped offset $snapped is larger than the eased one $eased", snapped > eased)
    }

    @Test fun cleanLensModeDoesNotHideTheEffect() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        assertTrue(lensView.chromaIconsForTest >= 1)
    }

    @Test fun theFringeAlphaIsTheIconAlphaNeverTheProductWithTheVignette() {
        lensView.chromaticAberrationEnabled = true
        lensView.lensVignetteEnabled = false
        touchAtCell(0)
        render().recycle()
        assertEquals("without the vignette the fringe is opaque", OPAQUE, lensView.chromaAlphaForTest)
        lensView.lensVignetteEnabled = true
        render().recycle()
        val withVignette = lensView.chromaAlphaForTest
        assertTrue("the vignette lowers the fringe alpha ($withVignette)", withVignette < OPAQUE)
        assertTrue("never below the vignette floor", withVignette >= LensVignette.alpha(1f))
    }

    @Test fun geometryHitTestAndGridCacheAreIdenticalWithAndWithoutTheEffect() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            touchAt(x.toFloat(), y.toFloat())
            lensView.chromaticAberrationEnabled = false
            render().recycle()
            val rectsOff = lensView.drawnRectsForTest()
            val indexOff = lensView.selectedIndexForTest
            val touchOff = lensView.touchCoordinatesForTest()
            val recomputes = cache.recomputeCount
            lensView.chromaticAberrationEnabled = true
            render().recycle()
            assertEquals(rectsOff, lensView.drawnRectsForTest())
            assertEquals(indexOff, lensView.selectedIndexForTest)
            assertEquals(touchOff, lensView.touchCoordinatesForTest())
            assertEquals(recomputes, cache.recomputeCount)
        }
    }

    @Test fun afterATouchedFrameAnIdleFrameDrawsNoFringe() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        assertTrue(lensView.chromaIconsForTest >= 1)
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
    }

    @Test fun theIconPaintAlphaIsRestoredAfterAFringedFrame() {
        touchAtCell(0)
        lensView.chromaticAberrationEnabled = false
        val before = countGreen(render())
        lensView.chromaticAberrationEnabled = true
        render().recycle()
        lensView.chromaticAberrationEnabled = false
        assertEquals(before, countGreen(render()))
    }

    private fun countGreen(bitmap: Bitmap): Int {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.count { Color.green(it) > 200 && Color.red(it) < 40 }
    }
}
```

- [ ] **Step 3: Integration test, pinned focus, tilt and the real BLUR path.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewChromaticAberrationIntegrationTest.kt`. Use the harness of `LensViewFocusPeakingIntegrationTest` (an attached view in `OrganizationHarnessActivity`, 1000×1000, 40 apps so far icons exist, `cellCentre(view, index)` taken from real cell bounds after the first draw):

```kotlin
package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewChromaticAberrationIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 40
        const val DRAG_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 50L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withView(block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList((0 until APP_COUNT).map { i ->
                        App(id = i, packageName = "com.fish040.int$i", name = "Int$i", label = "Int $i")
                    }))
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "assigned in onActivity above" })
        }
    }

    private fun draw(view: LensView) = instrumentation.runOnMainSync { view.draw(Canvas()) }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun cellCentre(view: LensView, index: Int): Pair<Float, Float> {
        val bounds = Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    @Test fun aPinnedFocusWithoutATouchDrawsNoFringe() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true }
        val (x, y) = cellCentre(view, APP_COUNT / 2)
        onMain { assertTrue(view.toggleFocusLockAtForTest(x, y)) }
        draw(view)
        assertEquals("the effect is touch-only, a pin has no transition", 0, view.chromaIconsForTest)
    }

    @Test fun tiltNeverFringesAnything() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true; view.setTiltOffset(120f, 80f) }
        draw(view)
        assertEquals(0, view.chromaIconsForTest)
    }

    @Test fun aLiveTouchOnTheAttachedViewFringesFarIconsAndReleaseClearsThem() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true }
        val (x, y) = cellCentre(view, 0)
        onMain { view.setLensStateForTest(x, y, 1f, reduceMotion = false) }
        draw(view)
        assertTrue(view.chromaIconsForTest in 1..ChromaticAberration.MAX_ICONS)
        onMain { view.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false) }
        draw(view)
        assertEquals(0, view.chromaIconsForTest)
    }

    @Test fun theSettingIsReadWhenTheLensAnimationStartsNotEveryFrame() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        assertTrue("the view has not seen the stored setting yet", !view.chromaticAberrationEnabled)
        val down = SystemClock.uptimeMillis()
        val (x, y) = cellCentre(view, 0)
        onMain {
            val e = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            view.dispatchTouchEvent(e); e.recycle()
            val m = MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_MOVE, x + 40f, y + 40f, 0)
            view.dispatchTouchEvent(m); m.recycle()
        }
        assertTrue("starting the lens animation samples the stored setting", view.chromaticAberrationEnabled)
        onMain {
            val c = MotionEvent.obtain(down, down + 16, MotionEvent.ACTION_CANCEL, x, y, 0)
            view.dispatchTouchEvent(c); c.recycle()
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test fun onTheRealHomeWithTheHardwareBlurPathFringesStillDrawForTheFarIcons() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        RAppsSingleton.instance.apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish040.home$i", name = "Home$i", label = "Home $i")
        })
        UtilSettings(context).save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        UtilSettings(context).save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var found: LensView? = null
                scenario.onActivity { found = it.findViewById(R.id.lensViews) }
                val view = requireNotNull(found) { "assigned in onActivity above" }
                instrumentation.waitForIdleSync()
                var w = 0f
                var h = 0f
                onMain { w = view.width.toFloat(); h = view.height.toFloat() }
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float, y: Float) = onMain {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    view.dispatchTouchEvent(e); e.recycle()
                }
                send(MotionEvent.ACTION_DOWN, w / 4, h / 4)
                for (i in 1..DRAG_STEPS) {
                    send(MotionEvent.ACTION_MOVE, w / 4 + w / 2 * i / DRAG_STEPS, h / 4 + h / 2 * i / DRAG_STEPS)
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
                var ok = false
                while (SystemClock.uptimeMillis() < deadline && !ok) {
                    onMain { ok = view.dofLastMode == DepthOfField.Mode.BLUR && view.chromaIconsForTest >= 1 }
                    if (!ok) SystemClock.sleep(POLL_MS)
                }
                assertTrue("BLUR path active and far icons are still fringed", ok)
                send(MotionEvent.ACTION_CANCEL, w * 3 / 4, h * 3 / 4)
            }
        } finally {
            prefs.edit().clear().commit()
            RAppsSingleton.instance.clearAllData()
        }
    }
}
```

- [ ] **Step 4: Verify RED.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.ChromaticAberrationNoAllocationTest' > /tmp/f040-red3.txt 2>&1` fails on `drawChromaticFringes( not found` and `prepareChromaticAberration( not found` (the math-function guards pass because Task 1 exists). `./gradlew compileDevDebugAndroidTestKotlin > /tmp/f040-red3b.txt 2>&1` fails with only these unresolved references: `chromaticAberrationEnabled`, `chromaIconsForTest`, `chromaIndicesForTest`, `chromaOffsetPxForTest`, `chromaAlphaForTest`. Fix typos in the tests, never by weakening an assertion.
- [ ] **Step 5: Commit the RED tests only if the repo's convention allows a red commit; otherwise keep them uncommitted and commit with Task 4.** The repo's own history (`feat(fish-0xx): …` with tests and code together) commits tests with their code, so **do not commit here**; Task 4 commits all three files with the production code.

---

## Task 4 — `LensView`: paints, selection, second pass, sampling

**Files:** modify `LensView.kt`. This task turns every Task 3 test green; it commits the three Task 3 test files, `ChromaticAberrationNoAllocationTest.kt` and the production change together.

**Interfaces:** consumes `ChromaticAberration.{shouldApply, offsetPx, direction, isFringed, MAX_ICONS, TopFarthest}` (Task 1), `UtilSettings.KEY_CHROMATIC_ABERRATION` / `DEFAULT_CHROMATIC_ABERRATION` (Task 2); produces the probes the Task 3 tests read.

- [ ] **Step 1: State and probes.** In `LensView.kt` next to `internal var lensVignetteEnabled` (line ~830) add:

```kotlin
    /** FISH-040: sampled once per lens show in LensAnimation.init (live touch only), never per frame. */
    @androidx.annotation.VisibleForTesting
    internal var chromaticAberrationEnabled = UtilSettings.DEFAULT_CHROMATIC_ABERRATION
    private var mPaintChromaRed: Paint? = null
    private var mPaintChromaBlue: Paint? = null
    private val mChromaTop = ChromaticAberration.TopFarthest(ChromaticAberration.MAX_ICONS)
    private val mChromaRects = Array(ChromaticAberration.MAX_ICONS) { RectF() }
    private val mChromaDistances = FloatArray(ChromaticAberration.MAX_ICONS)
    private val mChromaAlphas = IntArray(ChromaticAberration.MAX_ICONS)
    private val mChromaDirection = FloatArray(2)
    private val mScratchChromaRect = RectF()
    private var mChromaOffsetPx = 0f
    private var mChromaDrawn = 0
    private var mChromaLastAlpha = 0
    private var mChromaDrawnIndices = IntArray(ChromaticAberration.MAX_ICONS)

    /** Icons fringed in the last frame. */
    @androidx.annotation.VisibleForTesting
    internal val chromaIconsForTest: Int get() = mChromaDrawn

    /** Cell indices fringed in the last frame (a copy, [chromaIconsForTest] long). */
    @androidx.annotation.VisibleForTesting
    internal val chromaIndicesForTest: IntArray get() = mChromaDrawnIndices.copyOf(mChromaDrawn)

    /** Largest offset (px) used in the last frame; 0 when no icon was fringed. */
    @androidx.annotation.VisibleForTesting
    internal val chromaOffsetPxForTest: Float get() = if (mChromaDrawn > 0) mChromaOffsetPx else 0f

    /** Alpha of the fringes in the last frame; 0 when none. */
    @androidx.annotation.VisibleForTesting
    internal val chromaAlphaForTest: Int get() = if (mChromaDrawn > 0) mChromaLastAlpha else 0
```

  (`mChromaDrawnIndices` is a plain array, never reallocated: its size equals the cap.)

- [ ] **Step 2: Paints, built once.** In `setupPaints()` directly after the `mPaintFocusPeaking` block, add the two channel paints. Each keeps one colour channel through a `ColorMatrixColorFilter` and adds it onto the canvas with `ADD`:

```kotlin
        mPaintChromaRed = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        }
        mPaintChromaBlue = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        }
```

  The two matrices keep only the red (row 1) or blue (row 3) component and the source alpha; their allocation is why the no-allocation guard inspects only the two functions below, not `setupPaints`.

- [ ] **Step 3: Two allocation-free functions.** Beside `drawFocusPeakingRing` add exactly these two functions (names and shape are what `ChromaticAberrationNoAllocationTest` inspects: no allocation tokens, and exactly two `drawBitmap(` calls in the second pass):

```kotlin
    /** FISH-040: per-frame reset of the selector and the probes; returns nothing, allocates nothing. */
    private fun prepareChromaticAberration() {
        mChromaTop.reset()
        mChromaDrawn = 0
        mChromaOffsetPx = 0f
        mChromaLastAlpha = 0
    }

    /**
     * FISH-040: second pass after the icon loop. For each selected (farthest) icon draw a red-only copy shifted outward
     * from the touch and a blue-only copy shifted inward, both added onto the canvas. Uses only view-owned objects.
     */
    private fun drawChromaticFringes(canvas: Canvas, density: Float, transition: Float) {
        val red = mPaintChromaRed ?: return
        val blue = mPaintChromaBlue ?: return
        val list = mApps ?: return
        for (slot in 0 until mChromaTop.count) {
            val cell = mChromaTop.indexAt(slot)
            val app = list.getOrNull(cell) ?: continue
            val bitmap = com.mckimquyen.app.RAppsSingleton.instance.getAppIcon(app.iconCacheKey)
            if (bitmap == null || bitmap.isRecycled) continue
            val offset = ChromaticAberration.offsetPx(mChromaDistances[slot], transition, density)
            if (offset <= 0f) continue
            val rect = mChromaRects[slot]
            ChromaticAberration.direction(mTouchX, mTouchY, rect.centerX(), rect.centerY(), mChromaDirection)
            val dx = mChromaDirection[0] * offset
            val dy = mChromaDirection[1] * offset
            mScratchIconSourceRect.set(0, 0, bitmap.width, bitmap.height)
            red.alpha = mChromaAlphas[slot]
            mScratchChromaRect.set(rect)
            mScratchChromaRect.offset(dx, dy)
            canvas.drawBitmap(bitmap, mScratchIconSourceRect, mScratchChromaRect, red)
            blue.alpha = mChromaAlphas[slot]
            mScratchChromaRect.set(rect)
            mScratchChromaRect.offset(-dx, -dy)
            canvas.drawBitmap(bitmap, mScratchIconSourceRect, mScratchChromaRect, blue)
            if (offset > mChromaOffsetPx) mChromaOffsetPx = offset
            mChromaLastAlpha = mChromaAlphas[slot]
            if (mChromaDrawn < mChromaDrawnIndices.size) mChromaDrawnIndices[mChromaDrawn] = cell
            mChromaDrawn++
        }
    }
```

- [ ] **Step 4: `drawGrid` — one decision per frame.** Directly after the FISH-039 block (the line `mFocusPeakingRinged.fill(false)`) add:

```kotlin
        // FISH-040: the vignette's live-touch gate exactly (no pin, tilt or idle); it reuses the per-cell focus distance.
        val chromaOn = ChromaticAberration.shouldApply(
            chromaticAberrationEnabled, mDrawType == DrawType.APPS && hasActiveTouch && dofTransition > 0f
        )
        prepareChromaticAberration()
```

- [ ] **Step 5: `drawGrid` — per cell.** (a) The existing `focusDistance` line currently reads `if (dofMode != DepthOfField.Mode.OFF || vignetteOn) DepthOfField.focusDistance(…) else 0f`. Change only the condition to `dofMode != DepthOfField.Mode.OFF || vignetteOn || chromaOn`. (b) The existing `mPaintIcons?.alpha = LensVignette.combine(dofAlpha, vignetteAlpha)` assignment stays as is; capture the icon alpha it uses by reading it back after the assignment: declare `var iconAlpha = DepthOfField.OPAQUE_ALPHA` before the `if (dofMode == ALPHA || vignetteOn)` block and set `iconAlpha = LensVignette.combine(dofAlpha, vignetteAlpha)` inside it. (c) Directly after `drawAppIcon(iconCanvas, rect, currentIndex)` and **before** the FISH-039 `if (peakingDraw …)` block add:

```kotlin
                if (chromaOn && ChromaticAberration.isFringed(focusDistance, dofBand)) {
                    val slot = mChromaTop.offer(focusDistance, currentIndex)
                    if (slot >= 0) {
                        mChromaRects[slot].set(rect)
                        mChromaDistances[slot] = focusDistance
                        mChromaAlphas[slot] = iconAlpha
                    }
                }
```

  `offer` returns the slot (0..7) or -1, so the rect and alpha are copied only for kept entries; a displaced entry's slot is overwritten by the new one.

- [ ] **Step 6: `drawGrid` — the second pass.** After `dofLayers?.endAndDraw(canvas)` (inside the existing `if (SDK_INT >= S)` the call stays; put the new call **after** that block, before `mPaintIcons?.alpha = DepthOfField.OPAQUE_ALPHA`):

```kotlin
        if (chromaOn) drawChromaticFringes(canvas, resources.displayMetrics.density, dofTransition)
```

  Fringes are therefore drawn on the main canvas after the DoF layers, and before the focus halo, name label and focus ring, so they sit under the UI chrome.

- [ ] **Step 7: Sampling and detach.** In `LensAnimation.init`, after the FISH-039 read, add:

```kotlin
            chromaticAberrationEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION)
                ?: UtilSettings.DEFAULT_CHROMATIC_ABERRATION
```

  In `onDetachedFromWindow`, beside the FISH-039 resets, add `mChromaDrawn = 0` and `mChromaTop.reset()`. Do not null the paints: they are built once in `init`, and a re-attached ViewPager page must still draw. (There is no pin-time sampling: the effect is touch-only, and the first touch starts a `LensAnimation`.)

- [ ] **Step 8: Verify GREEN (JVM and build).** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.ChromaticAberrationNoAllocationTest' --tests 'com.mckimquyen.views.ChromaticAberrationTest' assembleDevDebug assembleDevDebugAndroidTest` — guard 4/4, math 18/18, build passes. If the guard fails on `'Array('`, `'Paint('` or similar, the forbidden token is in one of the two new functions: fix the function, never the guard.
- [ ] **Step 9: Verify GREEN (device, after the Task 6 backup).** Run `LensViewChromaticAberrationWidgetTest` (14 tests), `LensViewChromaticAberrationIntegrationTest` (5 tests; the `@SdkSuppress` BLUR test runs on API 31+), then the regression set: `LensViewVignetteWidgetTest`, `LensViewDepthOfFieldWidgetTest`, `LensViewDepthOfFieldIntegrationTest`, `LensViewFocusPeakingWidgetTest`, `LensViewFocusPeakingIntegrationTest`, `LensViewFocusLockIntegrationTest`, `LensViewTiltIntegrationTest`, `LensViewReattachIntegrationTest`, `LensViewWidgetTest`, `LensViewFocusRingIntegrationTest`, `LensViewPinchIntegrationTest`, `LensViewPagerGestureIntegrationTest`, `LensViewRingRotateIntegrationTest`, `ActHomeRingRotateGestureSmokeTest`. Every class must print `OK (N)` with N > 0.
- [ ] **Step 10: Mutation checks (each reverted, then rebuilt).** (1) `ChromaticAberration.isFringed` returns `true` always: `theFringedIconsAreTheFarthestOnesFromTheTouch` (the finger-cell must not be fringed) and the cap test must fail. (2) Remove `hasActiveTouch &&` from the `chromaOn` gate: `anIdleLensDrawsNoFringeEvenWhenEnabled`, `afterATouchedFrameAnIdleFrameDrawsNoFringe` and `aPinnedFocusWithoutATouchDrawsNoFringe` must fail. (3) Swap the sign of `dx, dy` for the red copy (draw it inward) and for the blue copy (outward): `theRedFringeLiesOutwardAndTheBlueFringeInwardAlongTheTouchAxis` must fail. (4) Replace `LensVignette.combine(...)` by the product of the two alphas in the fringe alpha: `theFringeAlphaIsTheIconAlphaNeverTheProductWithTheVignette` must fail. Record which tests failed in the audit.
- [ ] **Step 11: Commit.** `git branch --show-current` must print `dev`. `git add app/src/main/java/com/mckimquyen/views/LensView.kt app/src/test/java/com/mckimquyen/views/ChromaticAberrationNoAllocationTest.kt app/src/androidTest/java/com/mckimquyen/views/LensViewChromaticAberrationWidgetTest.kt app/src/androidTest/java/com/mckimquyen/views/LensViewChromaticAberrationIntegrationTest.kt && git commit -m "feat(fish-040): add chromatic aberration fringes at the lens rim"` with the trailer.

---

## Task 5 — Real-touch screenshot smoke (evidence, not behavior)

**Files:** create `app/src/androidTest/java/com/mckimquyen/ui/ChromaticAberrationScreenshotSmokeTest.kt`. No production code. It reuses the FISH-039 `FocusPeakingScreenshotSmokeTest` helpers exactly (open it and copy `shot`, `inject`, `tapAt`, `centreOnScreen`, `waitUntil`, `seedRealApps`); only the scenario differs.

**Interfaces:** consumes the Task 4 probes `chromaIconsForTest` and `chromaOffsetPxForTest`, and `UtilSettings.KEY_CHROMATIC_ABERRATION`.

- [ ] **Step 1: Write the test.** Two tests, screenshots saved under `filesDir/fish040` (`settings-1-off.png`, `settings-2-on.png`, `home-1-idle.png`, `home-2-drag.png`, `home-3-released.png`):

```kotlin
    @Test fun theSettingsSwitchIsTurnedOnByARealTouchAndPersists() {
        // same as FocusPeakingScreenshotSmokeTest, with swChromaticAberration / KEY_CHROMATIC_ABERRATION and SHOT_DIR = "fish040":
        // scroll the switch to the middle of the screen, assert it starts off, shot, one held real touch,
        // waitUntil it is checked, assert the stored value is true, shot, touch again, waitUntil it is off.
    }

    @Test fun aRealSlowDragFringesFarIconsAndReleaseReturnsToIdle() {
        UtilSettings(context).save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val original = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = seedRealApps()
        var downTime = 0L
        var lastX = 0f
        var lastY = 0f
        var down = false
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                val view = awaitGrid(scenario)
                SystemClock.sleep(SETTLE_MS)
                shot("home-1-idle")
                val loc = IntArray(2)
                instrumentation.runOnMainSync { view.getLocationOnScreen(loc) }
                // a continuous single-pointer diagonal drag from the top-left quarter to the middle, 16 ms per MOVE
                val startX = loc[0] + view.width * 0.2f
                val startY = loc[1] + view.height * 0.25f
                val endX = loc[0] + view.width * 0.5f
                val endY = loc[1] + view.height * 0.5f
                downTime = SystemClock.uptimeMillis()
                inject(MotionEvent.ACTION_DOWN, downTime, downTime, startX, startY)
                down = true
                for (i in 1..DRAG_STEPS) {
                    val t = i / DRAG_STEPS.toFloat()
                    lastX = startX + (endX - startX) * t
                    lastY = startY + (endY - startY) * t
                    inject(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                waitUntil("far icons are fringed while the finger is down") { view.chromaIconsForTest >= 1 }
                assertTrue("never more than the cap", view.chromaIconsForTest <= ChromaticAberration.MAX_ICONS)
                assertTrue("a real offset is in use", view.chromaOffsetPxForTest > 0f)
                SystemClock.sleep(SETTLE_MS)
                shot("home-2-drag")
                inject(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                down = false
                waitUntil("fringes are gone after release") { view.chromaIconsForTest == 0 }
                SystemClock.sleep(SETTLE_MS)
                shot("home-3-released")
            }
        } finally {
            if (down) releasePointer(lastX, lastY)
            RAppsSingleton.instance.apps = original
        }
    }
```

  Add the helpers the FISH-039 file does not have: `awaitGrid(scenario)` (poll for `MIN_APPS` displayed apps like the FISH-039 `pinAndReleaseOnHome` does), `releasePointer(x, y)` (inject one `ACTION_UP`, as `ActHomeRingRotateGestureSmokeTest` does) and an `@Before fun releaseAnyStaleFinger() { runCatching { releasePointer(1f, 1f) } }`. Constants: `DRAG_STEPS = 28`, `STEP_DELAY_MS = 16L`, `SETTLE_MS = 700L`, `WAIT_MS = 10_000L`, `POLL_MS = 100L`, `MIN_APPS = 12`, `SEED_APPS = 60`, `SEED_ICON_PX = 96`, `SHOT_DIR = "fish040"`.
- [ ] **Step 2: Verify the test compiles and is RED for the right reason.** Before Task 4 it fails to compile (unresolved `chromaIconsForTest`); after Task 4 it runs. Record both facts.
- [ ] **Step 3: Run on the S24U (after the Task 6 backup and install).** `adb -s R5CX613VZBR shell am instrument --user 0 -w -e class com.mckimquyen.ui.ChromaticAberrationScreenshotSmokeTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner > "$D/smoke.txt"`; expect `OK (2 tests)`. Pull the five screenshots with `adb -s R5CX613VZBR exec-out run-as com.mckimquyen.lenslauncher cat files/fish040/<name>.png > "$D/shots/<name>.png"` and compare with Pillow over the grid area: idle vs drag differs, idle vs released is **0 px**. Look at `home-2-drag.png` zoomed on a far icon: a red edge on the side away from the finger and a blue edge toward it; none on icons near the finger. If an ad covers the UI, close it through adb (`input keyevent KEYCODE_BACK`) and rerun; never click ad content.
- [ ] **Step 4: Mutation check for the smoke.** Remove the `hasActiveTouch &&` term in the `chromaOn` gate (Task 4 mutation 2) and confirm `aRealSlowDragFringesFarIconsAndReleaseReturnsToIdle` still passes while an idle test fails; then remove the sampling line in `LensAnimation.init` and confirm the smoke fails at `far icons are fringed while the finger is down`. Revert both.
- [ ] **Step 5: Commit.** `git add app/src/androidTest/java/com/mckimquyen/ui/ChromaticAberrationScreenshotSmokeTest.kt && git commit -m "test(fish-040): capture chromatic aberration screenshots"` with the trailer.

---

## Task 6 — Device procedure on the Samsung S24 Ultra: backup, regression, frame budget, restore

Everything uses `S=R5CX613VZBR` and **`--user 0`** (the S24U also runs `UserInfo{95:DUAL_APP}` and `{150:Secure Folder}`; without `--user 0`, `pm`, `am instrument` and `install` fail with `Shell does not have permission to access user 150`). Never use bare `adb`, a Gradle `connected*` task or `installDevDebug`. Run each command on its own and read its output file afterwards (a truncated display does not mean the command stopped). Owner permission: if an ad covers the UI, close it through adb (`input keyevent KEYCODE_BACK`); never click ad content, never tap "Start (Ads may appear)".

- [ ] **Step 1: Lock the device and create the run directory.**

```bash
S=R5CX613VZBR
adb devices -l | grep "$S"                         # must print one line ending in model:SM_S928B
D=/tmp/f040-run-$(date +%Y%m%d-%H%M%S); mkdir -p "$D"; echo "$D" > /tmp/f040-dir; echo "$S" > /tmp/f040-serial
echo "Đang dùng device Samsung S24 Ultra $S"
adb -s $S shell getprop ro.product.model; adb -s $S shell getprop ro.build.version.release
adb -s $S shell pm list packages --user 0 | grep mckimquyen | sort > "$D/packages-before.txt"; cat "$D/packages-before.txt"
```

  If the S24U is missing from `adb devices`, stop and ask the owner; do not touch any other device.

- [ ] **Step 2: Backup before the first install.** The expected state is "only `com.mckimquyen.lenslauncher.test` installed for user 0". If the main app **is** installed, also pull its APK and a data tar and restore both in Step 8.

```bash
D=$(cat /tmp/f040-dir); S=$(cat /tmp/f040-serial)
TEST=$(adb -s $S shell pm path --user 0 com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')
adb -s $S pull "$TEST" "$D/before-test.apk" > "$D/pull-test.txt" 2>&1
shasum -a 256 "$D/before-test.apk" > "$D/apk-hashes.txt"
if adb -s $S shell pm path --user 0 com.mckimquyen.lenslauncher | grep -q package; then
  MAIN=$(adb -s $S shell pm path --user 0 com.mckimquyen.lenslauncher | sed 's/package://' | tr -d '\r')
  adb -s $S pull "$MAIN" "$D/before.apk" > "$D/pull-main.txt" 2>&1
  adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/before-data.tar"
  mkdir -p "$D/x" && tar -xf "$D/before-data.tar" -C "$D/x"
  ( cd "$D/x" && find . -type f | sort | xargs shasum -a 256 ) > "$D/before-hashes.txt"
  shasum -a 256 "$D/before.apk" >> "$D/apk-hashes.txt"; echo MAIN_APP_PRESENT > "$D/main-state.txt"
else
  echo MAIN_APP_ABSENT > "$D/main-state.txt"
fi
{ adb -s $S shell settings get system font_scale; adb -s $S shell settings get global animator_duration_scale; \
  adb -s $S shell settings get global transition_animation_scale; adb -s $S shell settings get global window_animation_scale; \
  adb -s $S shell cmd locale get-app-locales com.mckimquyen.lenslauncher; \
  adb -s $S shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME; } > "$D/device-state.txt" 2>&1
cat "$D/main-state.txt" "$D/apk-hashes.txt" "$D/device-state.txt"
```

  Record the main-app state, the APK hash(es), file count (when present) and the six system values in the audit. (`exec-out … tar cf -` for reading is safe; only writing with `exec-out … tar xf -` hangs.)

- [ ] **Step 3: Host gate (no device).** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `bash scripts/check-secrets.sh`, `git diff --check`. Record the unit count (baseline 1589 plus 18 + 4 + 3 new JVM tests = 1614), lint errors/warnings (baseline 0/15), exit codes and `git rev-parse --short HEAD`.

- [ ] **Step 4: Install and run every class.**

```bash
D=$(cat /tmp/f040-dir); S=$(cat /tmp/f040-serial)
APK=app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk
TAPK=app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s $S install --user 0 -r -d "$APK" > "$D/inst1.txt" 2>&1
adb -s $S install --user 0 -r -d "$TAPK" > "$D/inst2.txt" 2>&1
cat "$D/inst1.txt" "$D/inst2.txt" | tr '\n' ' '; echo
: > "$D/summary.txt"
for c in views.LensViewChromaticAberrationWidgetTest views.LensViewChromaticAberrationIntegrationTest ui.FrmSettingsChromaticAberrationWidgetTest \
         views.LensViewVignetteWidgetTest ui.FrmSettingsLensVignetteWidgetTest views.LensViewDepthOfFieldWidgetTest views.LensViewDepthOfFieldIntegrationTest \
         views.LensViewFocusPeakingWidgetTest views.LensViewFocusPeakingIntegrationTest views.LensViewFocusLockIntegrationTest views.LensViewTiltIntegrationTest \
         views.LensViewReattachIntegrationTest views.LensViewWidgetTest views.LensViewFocusRingIntegrationTest views.LensViewPinchIntegrationTest \
         views.LensViewPagerGestureIntegrationTest views.LensViewRingRotateIntegrationTest ui.ActHomeRingRotateGestureSmokeTest ui.FocusPeakingScreenshotSmokeTest; do
  adb -s $S shell input keyevent KEYCODE_HOME
  adb -s $S shell am instrument --user 0 -w -e class com.mckimquyen.$c com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner > "$D/run-$c.txt" 2>&1
  echo "$c: $(grep -E '^OK|FAILURES|Tests run' "$D/run-$c.txt" | tr '\n' ' ')" >> "$D/summary.txt"
done; cat "$D/summary.txt"
```

  `OK (0 tests)` means a stale test APK and is a failure: rebuild `assembleDevDebugAndroidTest` and reinstall. If a class fails with `the device rejected MotionEvent` or `pointers already down`, a crashed earlier run left the injector's finger down: rerun once (the smoke classes release it in `@Before`). If an unrelated app's dialog covers Home, `am force-stop` that app only (never this project) and rerun. If `mCurrentFocus` is a `AdActivity`, close it with `KEYCODE_BACK` and rerun.

- [ ] **Step 5: Frame budget (acceptance criterion).** Same method as FISH-034/039. The switch is turned on by the real-touch smoke test of Task 5 (or, for the measurement, by writing `chromatic_aberration=true` into the app's SharedPreferences the same way FISH-039 did: copy the file out with `run-as … cat`, add the boolean, push it to `/data/local/tmp`, copy it in with `run-as … cp`).

```bash
D=$(cat /tmp/f040-dir); S=$(cat /tmp/f040-serial)
adb -s $S shell am start --user 0 -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome; sleep 6
adb -s $S shell dumpsys gfxinfo com.mckimquyen.lenslauncher reset > /dev/null
for i in 1 2 3 4 5; do adb -s $S shell "input swipe 200 700 900 1500 1200; input swipe 900 1500 200 700 1200"; done
adb -s $S shell dumpsys gfxinfo com.mckimquyen.lenslauncher > "$D/gfx-off.txt"
```

  Enable the setting, return to Home and repeat the identical loop into `gfx-on.txt`. Extract `Total frames rendered`, `Janky frames`, and the 50th/90th/95th/99th percentile lines of both. A run is valid only with more than 1600 frames each; otherwise repeat it and say so. **Pass criterion:** the "on" p50 and p90 stay at or under the 120 Hz budget of 8.3 ms and jank does not rise by more than 1 percentage point over "off". The S24U is a 120 Hz device, so its numbers are the primary evidence here; run each state **three times** (reset between runs) and report all three, not just the best. Report honestly: device on USB power, no claim for other devices or API levels.

- [ ] **Step 6: Cap-reduction fallback (only if Step 5 fails the pass criterion).** Do not claim a pass. Change only the named constant `ChromaticAberration.MAX_ICONS` (8 → 4), update the three tests that assert the cap (`theCapIsEightAndTheOffsetRangeIsOneToThreeDp`, `withTheSettingOnAtMostEightFarthestOutsideZoneIconsAreFringed`, the integration range) and the spec/plan text, rerun Task 4 Step 9 and this Step 5, and record both measurements. If 4 still fails, try 2; if 2 still fails, stop and ask the owner whether to drop the story; the owner decides, I do not silently ship a failing budget.
- [ ] **Step 7: Run the real-touch smoke (Task 5 Step 3) and pull the screenshots.**
- [ ] **Step 8: Restore and prove it exact.**

```bash
D=$(cat /tmp/f040-dir); S=$(cat /tmp/f040-serial)
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
if [ "$(cat "$D/main-state.txt")" = "MAIN_APP_PRESENT" ]; then
  adb -s $S push "$D/before-data.tar" /data/local/tmp/f040-before-data.tar > /dev/null
  adb -s $S shell chmod 644 /data/local/tmp/f040-before-data.tar
  adb -s $S install --user 0 -r -d "$D/before.apk"
  adb -s $S shell "run-as com.mckimquyen.lenslauncher sh -c 'rm -rf databases shared_prefs && tar xf /data/local/tmp/f040-before-data.tar && echo EXTRACT_OK'"
  adb -s $S shell rm -f /data/local/tmp/f040-before-data.tar
  adb -s $S shell am force-stop com.mckimquyen.lenslauncher
  adb -s $S exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > "$D/restored-data.tar"
  mkdir -p "$D/y" && tar -xf "$D/restored-data.tar" -C "$D/y"
  ( cd "$D/y" && find . -type f | sort | xargs shasum -a 256 ) > "$D/restored-hashes.txt"
  diff "$D/before-hashes.txt" "$D/restored-hashes.txt" && echo DATA_HASHES_IDENTICAL
else
  adb -s $S shell pm uninstall --user 0 com.mckimquyen.lenslauncher          # the app was not installed before this run
fi
adb -s $S install --user 0 -r -d "$D/before-test.apk"
adb -s $S shell am force-stop com.mckimquyen.lenslauncher.test
adb -s $S pull "$(adb -s $S shell pm path --user 0 com.mckimquyen.lenslauncher.test | sed 's/package://' | tr -d '\r')" "$D/after-test.apk" > /dev/null
shasum -a 256 "$D/after-test.apk" | awk '{print $1}' | diff - <(head -1 "$D/apk-hashes.txt" | awk '{print $1}') && echo TEST_APK_HASH_IDENTICAL
adb -s $S shell pm list packages --user 0 | grep mckimquyen | sort > "$D/packages-after.txt"; diff "$D/packages-before.txt" "$D/packages-after.txt" && echo PACKAGES_IDENTICAL
{ adb -s $S shell settings get system font_scale; adb -s $S shell settings get global animator_duration_scale; \
  adb -s $S shell settings get global transition_animation_scale; adb -s $S shell settings get global window_animation_scale; \
  adb -s $S shell cmd locale get-app-locales com.mckimquyen.lenslauncher; \
  adb -s $S shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME; } > "$D/restored-state.txt" 2>&1
diff "$D/device-state.txt" "$D/restored-state.txt" && echo STATE_IDENTICAL
```

  Stop and disclose if any hash, package list, setting or the HOME resolver differs. Do not open the app again after the final restore.

---

## Task 7 — Audit, trackers, closure

**Files:** create `doc/task/AUDIT_FISH-040_2026-11-07.md`; modify `doc/feature.md`, `doc/task/README.md`, the story file.

- [ ] **Step 1: Audit document.** Sections: scope; evidence table (JVM total and new tests per class; lint/build/secrets/diff; every device class with `OK (N)`; Task 4 mutation results; the three frame-budget runs off vs on with the pass criterion verdict; restore results); defects found by RED tests; smoke result with the five screenshots described honestly (red away from the finger, blue toward it, none near the finger, idle = 0 px); limitations. State unless disproved: the cap (8) and offset range (1–3 dp) are provisional and tuned on synthetic and screenshot evidence, not on real eyes; fringes are skipped for DoF band 2 icons; the second pass uses the icon bitmap cache and skips an icon whose bitmap is missing; frame numbers are from one device and USB power; AI-written translations; the owner has not used it by hand.
- [ ] **Step 2: Score honestly** out of 10. The story moves `inprogress/` → `done/` only if the score is above 9.0, every device class showed `OK (N>0)`, the frame-budget pass criterion held (or the cap fallback was applied and re-measured) and the restore was proven exact; otherwise it stays in `inprogress/` with the gap listed. The owner decides closure: ask with `AskUserQuestion`.
- [ ] **Step 3: Trackers (R2).** `doc/feature.md`: replace the "📋 Picked" FISH-040 bullet with the final status, counts, audit link and limitations. `doc/task/README.md`: update the `FISH-040` row. Tick the story's acceptance criteria with evidence (opt-in and reset, no change to geometry/hit-test/cache, frame measurements, API < 31 path = the same code path, no `RenderEffect`).
- [ ] **Step 4: Commit** the audit and tracker changes as their own commit on `dev`. Never stage `.idea/`. Do not push; pushing and a PR to `master` happen only if the owner asks.

---

## Self-review

**Spec coverage** (`docs/superpowers/specs/2026-11-07-fish-040-chromatic-aberration-rim-design.md`):
- Three draws (original + red outward + blue inward), ADD, every API, no `RenderEffect` → Task 4 Steps 2–3, 5–6; tests `redAndBlueFringePixelsAppear…`, `theRedFringeLiesOutwardAndTheBlueFringeInwardAlongTheTouchAxis`.
- At most 8 farthest icons, outside the sharp zone → Task 1 `TopFarthest`, `isFringed`; Task 4 Step 5; tests `withTheSettingOnAtMostEightFarthest…`, `theFringedIconsAreTheFarthestOnesFromTheTouch`.
- Live touch only (never pin, tilt, idle) → Task 4 Step 4; tests `anIdleLens…`, `aPinnedFocusWithoutATouch…`, `tiltNeverFringesAnything`, `afterATouchedFrame…`.
- Offset 1–3 dp by distance, transition scaling, reduced-motion snap, Clean mode → Task 1 `offsetPx`; tests `theOffsetStaysWithinOneToThreeDp…`, `theOffsetFadesWithTheLensAndSnaps…`, `cleanLensModeDoesNotHideTheEffect`.
- Alpha is the icon's own `min` alpha, never the product → Task 4 Step 5(b); test `theFringeAlphaIsTheIconAlphaNeverTheProduct…`, mutation 4.
- DoF BLUR keeps band 0/1 fringes only → Task 1 `isFringed`; test `onTheRealHomeWithTheHardwareBlurPath…` (API 31+).
- Own switch, default off, in reset → Task 2 (settings, reset entry, layout, fragment, 17 locales, widget test).
- Flag sampled once per lens show → Task 4 Step 7; test `theSettingIsReadWhenTheLensAnimationStartsNotEveryFrame`.
- No change to geometry, hit-test, cache → test `geometryHitTestAndGridCacheAreIdentical…`.
- No allocation in `onDraw` → Task 4 Steps 1–3 and `ChromaticAberrationNoAllocationTest` (static; `setupPaints` allocation is allowed because it runs once).
- Frame budget with a cap-reduction fallback → Task 6 Steps 5–6. Smoke, backup, restore on the S24U with `--user 0` → Tasks 5–6. Audit and trackers → Task 7.

**Placeholder scan:** no `TBD`/`TODO`/comment-only test bodies except the Settings-switch test in Task 5 Step 1, which is specified as "same as `FocusPeakingScreenshotSmokeTest` with the identifiers switched", an existing file the executor copies line by line, with its assertions listed.

**Type consistency:** `ChromaticAberration.{MAX_ICONS, MIN_OFFSET_DP, MAX_OFFSET_DP, OPAQUE_ALPHA, MAX_FRINGED_BAND, shouldApply, offsetPx, direction, isFringed, TopFarthest.{capacity, count, reset, offer, indexAt, distanceAt}}`; `UtilSettings.{KEY_CHROMATIC_ABERRATION, DEFAULT_CHROMATIC_ABERRATION}`; `LensView.{chromaticAberrationEnabled, chromaIconsForTest, chromaIndicesForTest, chromaOffsetPxForTest, chromaAlphaForTest}` and private `prepareChromaticAberration`, `drawChromaticFringes`, `mChromaTop`, `mChromaRects`, `mChromaDistances`, `mChromaAlphas`; ids `rlSwitchChromaticAberrationParent`, `iconChromaticAberration`, `swChromaticAberration`, `tvChromaticAberrationHint`; strings `setting_chromatic_aberration`, `setting_chromatic_aberration_hint`. Counts: `ChromaticAberrationTest` 18, `ChromaticAberrationNoAllocationTest` 4, `UtilSettingsChromaticAberrationTest` 3, widget 14, integration 5, Settings widget 4.

**Known risks the executor must watch:** (1) the frame budget is the real risk: up to 16 extra `drawBitmap` per frame with a colour filter and `ADD` blend; if the S24U shows p50 or p90 above 8.3 ms the cap fallback in Task 6 Step 6 applies and the owner decides, no silent pass. (2) `ColorMatrixColorFilter` + `PorterDuff.Mode.ADD` behaviour with software-canvas bitmaps differs from the hardware canvas, so the pixel tests prove the software path and the real-`ActHome` test plus the screenshots prove the hardware path. (3) The S24U needs `--user 0` on every command and has no main app at the start. (4) `LensView` is already large: do not extract the ring/fringe code in this story. (5) No push without the owner's request.

**Execution handoff** (after the plan commit): ask the owner with `AskUserQuestion` — subagent-driven vs inline (FISH-029/034/035/039 were inline) — and do not start Task 1 before they answer.


---

## Execution note (2026-11-07): cap lowered 8 -> 4

Task 6 Step 5 on the Samsung S24 Ultra (120 Hz, charging on AC, 2400+ frames per run, interleaved off/on): cap 8 raised the mean frame bin from 8.06 ms (off, 4 runs) to 8.60 ms (on, 3 runs) and p50 from 8 to 9 ms, so the 8.3 ms p50/p90 criterion was not met (p90 was 10 ms in every state, off included, so the absolute criterion was already unreachable by the baseline). Step 6 applied: `ChromaticAberration.MAX_ICONS` 8 -> 4; cap 4 measured mean 8.20 ms, p50 9/8/8 ms, p90 10 ms. The owner chose the cap-4 fallback. The cap tests (`theCapIsFourAndTheOffsetRangeIsOneToThreeDp`, `withTheSettingOnAtMostFourFarthestOutsideZoneIconsAreFringed`) were changed first and watched fail (RED) before the constant changed. Text above that says 8 describes the first design; the constant is 4.
