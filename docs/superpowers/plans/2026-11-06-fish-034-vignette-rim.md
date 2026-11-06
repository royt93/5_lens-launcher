# FISH-034 — Lens Rim Vignette Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add an opt-in vignette that dims icons far from the live touch centre while the lens is magnified, without touching geometry, hit-testing or the grid cache.

**Architecture:** A pure `LensVignette` object (next to `DepthOfField`, reusing its distance/transition math) turns focus distance into a per-icon paint alpha. `LensView.drawGrid` sets `mPaintIcons.alpha` to the lower of the DoF-fallback alpha and the vignette alpha. One boolean setting, read once per lens show/hide, mirrors the existing `KEY_DEPTH_OF_FIELD` wiring end to end.

**Tech Stack:** Kotlin, Android `Paint.alpha`, existing JUnit4/Robolectric and AndroidJUnit4 instrumentation. No new dependency, no `RenderEffect`.

## Global Constraints

- TECNO KJ7 `115333744A005844` only; never use Pixel `2B051FDH3006MU`, Samsung or OPPO. No Gradle `connected*` tasks; use `adb -s 115333744A005844 shell am instrument`.
- Before any device install: back up main APK, test APK and `databases shared_prefs` tar, SHA-256 every data member, record font/animator/transition/window scales, app locale and HOME resolver. After device work: restore, then compare every hash and setting. Stop if exact restore fails.
- adb smoke must use `input swipe x y x y 80` (hold ≥80 ms) or real drags. **Never `input tap`** on icons: `mSelectIndex` is only refreshed by `drawGrid`, so an instant tap races (root cause recorded in `doc/task/AUDIT_FISH-029_2026-11-06.md`).
- If an ad/consent (`AdActivity`) covers the UI: stop and wait for the owner's `done` (R4). Never tap “Start (Ads may appear)”.
- Scope: **vignette only**. Chromatic aberration is split out into a separate story. Spec: "No chromatic aberration (separate story), no per-lens override, no slider, no new dependency, no `RenderEffect`, no overlay view".
- The vignette applies only with a live touch (`hasActiveTouch`), faded by the lens animation transition. Idle lens, pinned focus (FISH-029) and tilt (FISH-033) are unchanged.
- Setting is opt-in: default `false`, included in reset-to-defaults.
- Never change geometry, hit-test, launch, haptics, depth-of-field blur path or `LensGridCache`. No allocation inside the per-cell loop; restore `mPaintIcons.alpha = OPAQUE_ALPHA` after the loop.
- Every behavior change: write the test, run it and watch it fail for the expected reason, write the minimum code, rerun. Do not stage `.idea/`. No push unless the owner asks.

---

## File Map

- Create `app/src/main/java/com/mckimquyen/views/LensVignette.kt` — pure math. (Deliberate deviation from the spec's `util/` path: it lives in `views` beside `DepthOfField` so it reuses that object's `intensity`/`focusDistance`/`transition` without a util→views dependency.)
- Create `app/src/test/java/com/mckimquyen/views/LensVignetteTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` — key, default, boolean getter.
- Create `app/src/test/java/com/mckimquyen/util/UtilSettingsLensVignetteTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/ui/settings/ResetEntries.kt` and `app/src/test/java/com/mckimquyen/ui/settings/ResetEntriesTest.kt`.
- Modify `app/src/main/res/layout/frm_settings.xml`, `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` — switch row.
- Modify `app/src/main/res/values/strings.xml` plus `values-ar/de/es/fr/hi/in/it/ja/km/ko/lo/pt/ru/th/vi/zh/strings.xml` — two strings each (covered by existing `AllStringsTranslationTest`).
- Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsLensVignetteWidgetTest.kt`.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt` — flag, per-icon alpha, animation read.
- Create `app/src/androidTest/java/com/mckimquyen/views/LensViewVignetteWidgetTest.kt`.
- Evidence: `doc/task/AUDIT_FISH-034_2026-11-06.md`, `doc/feature.md`, `doc/task/README.md`, story file moves `todo/` → `inprogress/` → `done/`.

## Interfaces

```kotlin
package com.mckimquyen.views

object LensVignette {
    const val FLOOR_ALPHA_FRACTION = 0.5f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA          // 255
    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean
    fun intensity(focusDistance: Float, transition: Float): Float   // 0f for non-finite input
    fun alpha(intensity: Float): Int                                // 255 for non-finite, else 255..128
    fun combine(dofAlpha: Int, vignetteAlpha: Int): Int             // min of the two
}
```

`UtilSettings`: `const val KEY_LENS_VIGNETTE = "lens_vignette"`, `const val DEFAULT_LENS_VIGNETTE = false`, read with `getBoolean(KEY_LENS_VIGNETTE)`.

`LensView`: `@VisibleForTesting internal var lensVignetteEnabled = UtilSettings.DEFAULT_LENS_VIGNETTE`.

Layout ids: `rlSwitchLensVignetteParent`, `iconLensVignette`, `swLensVignette`, `tvLensVignetteHint`. Strings: `setting_lens_vignette`, `setting_lens_vignette_hint`.

---

## Task 1 — Pure vignette math

**Files:** create `LensVignette.kt`, `LensVignetteTest.kt`.

- [ ] **Step 1: RED.** Create `app/src/test/java/com/mckimquyen/views/LensVignetteTest.kt`:

```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensVignetteTest {
    @Test fun opaqueInsideTheFocusZone() {
        val inside = LensVignette.intensity(DepthOfField.FOCUS_ZONE, 1f)
        assertEquals(0f, inside, 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(inside))
    }

    @Test fun opaqueWheneverTheTransitionIsZero() {
        assertEquals(0f, LensVignette.intensity(1f, 0f), 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(LensVignette.intensity(1f, 0f)))
    }

    @Test fun floorAtTheFarthestPointIsHalfOpacity() {
        val farthest = LensVignette.intensity(1f, 1f)
        assertEquals(1f, farthest, 1e-6f)
        assertEquals(128, LensVignette.alpha(farthest))
    }

    @Test fun alphaNeverFallsBelowTheFloorAndNeverRisesWithDistance() {
        var previous = LensVignette.OPAQUE_ALPHA
        var distance = 0f
        while (distance <= 1.0001f) {
            val a = LensVignette.alpha(LensVignette.intensity(distance, 1f))
            assertTrue("alpha $a at $distance must not exceed previous $previous", a <= previous)
            assertTrue("alpha $a below floor", a >= 128)
            previous = a
            distance += 0.05f
        }
    }

    @Test fun alphaClampsOutOfRangeIntensity() {
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(-3f))
        assertEquals(128, LensVignette.alpha(7f))
    }

    @Test fun nonFiniteInputsFailVisible() {
        assertEquals(0f, LensVignette.intensity(Float.NaN, 1f), 0f)
        assertEquals(0f, LensVignette.intensity(0.9f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(Float.NaN))
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(Float.NEGATIVE_INFINITY))
    }

    @Test fun combineTakesTheLowerAlphaAndIsSymmetric() {
        assertEquals(100, LensVignette.combine(100, 200))
        assertEquals(100, LensVignette.combine(200, 100))
        assertEquals(180, LensVignette.combine(255, 180))
        assertEquals(255, LensVignette.combine(255, 255))
    }

    @Test fun combineNeverMultipliesTheTwoEffects() {
        // 128 x 128 / 255 would be 64; the owner decision is "lower of the two", so it must stay 128.
        assertEquals(128, LensVignette.combine(128, 128))
    }

    @Test fun gateRequiresBothTheSettingAndALiveLens() {
        assertTrue(LensVignette.shouldApply(enabled = true, lensVisible = true))
        assertFalse(LensVignette.shouldApply(enabled = false, lensVisible = true))
        assertFalse(LensVignette.shouldApply(enabled = true, lensVisible = false))
        assertFalse(LensVignette.shouldApply(enabled = false, lensVisible = false))
    }
}
```

- [ ] **Step 2: Verify RED.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.LensVignetteTest'`. Expected: compilation fails only with `Unresolved reference 'LensVignette'`.

- [ ] **Step 3: GREEN.** Create `app/src/main/java/com/mckimquyen/views/LensVignette.kt`:

```kotlin
package com.mckimquyen.views

import kotlin.math.roundToInt

/**
 * FISH-034: edge vignette for the lens grid. Pure math only. The vignette is a per-icon paint alpha, so it
 * never touches geometry or hit-testing: LensView computes each cell's rect first and only picks the alpha
 * the icon is drawn with. Distance, focus zone and transition reuse [DepthOfField] so both effects agree on
 * what "far from the finger" means and on how reduced motion snaps the transition.
 */
object LensVignette {
    /** Far icons keep at least this share of opacity so they stay legible and tappable. */
    const val FLOOR_ALPHA_FRACTION = 0.5f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean = enabled && lensVisible

    /** 0 inside the focus zone or when the lens is not magnified; 1 at the farthest point. */
    fun intensity(focusDistance: Float, transition: Float): Float =
        if (!focusDistance.isFinite() || !transition.isFinite()) 0f
        else DepthOfField.intensity(focusDistance, transition)

    /** [OPAQUE_ALPHA] at intensity 0 down to `OPAQUE_ALPHA * FLOOR_ALPHA_FRACTION` at 1. Non-finite is opaque. */
    fun alpha(intensity: Float): Int {
        if (!intensity.isFinite()) return OPAQUE_ALPHA
        val t = intensity.coerceIn(0f, 1f)
        return (OPAQUE_ALPHA * (1f - t * (1f - FLOOR_ALPHA_FRACTION))).roundToInt()
    }

    /** Both effects can lower alpha (DoF fallback below API 31); the icon takes the lower, never the product. */
    fun combine(dofAlpha: Int, vignetteAlpha: Int): Int = minOf(dofAlpha, vignetteAlpha)
}
```

- [ ] **Step 4: Verify GREEN.** Rerun the focused class; every test passes. Also run `--tests 'com.mckimquyen.views.DepthOfFieldTest'` to confirm nothing regressed.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/views/LensVignette.kt app/src/test/java/com/mckimquyen/views/LensVignetteTest.kt && git commit -m "feat(fish-034): add vignette alpha math"` (end the message with the attribution line the harness requires).

## Task 2 — Setting, reset entry, switch row, strings

**Files:** modify `UtilSettings.kt`, `ResetEntries.kt`, `ResetEntriesTest.kt`, `frm_settings.xml`, `FrmSettings.kt`, `strings.xml` ×17; create `UtilSettingsLensVignetteTest.kt`, `FrmSettingsLensVignetteWidgetTest.kt`.

- [ ] **Step 1: RED (JVM).** Create `app/src/test/java/com/mckimquyen/util/UtilSettingsLensVignetteTest.kt` (copy of the depth-of-field test structure):

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
class UtilSettingsLensVignetteTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun `lens vignette is off by default`() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
        assertFalse(UtilSettings.DEFAULT_LENS_VIGNETTE)
    }

    @Test fun `enabling and disabling persists`() {
        val s = freshSettings()
        s.save(UtilSettings.KEY_LENS_VIGNETTE, true)
        assertTrue(s.getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
        s.save(UtilSettings.KEY_LENS_VIGNETTE, false)
        assertFalse(s.getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
    }

    @Test fun `the key is stable`() {
        assertEquals("lens_vignette", UtilSettings.KEY_LENS_VIGNETTE)
    }
}
```

In `ResetEntriesTest.kt`, edit the expected key set of ``settings tab writes exactly the keys the old reset wrote`` by inserting `"lens_vignette"` right after `"depth_of_field_blur"`.

- [ ] **Step 2: Verify RED.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsLensVignetteTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest'`. Expected: compile failure `Unresolved reference 'KEY_LENS_VIGNETTE'`; after Step 3's constants only, `ResetEntriesTest` fails on the missing key.

- [ ] **Step 3: GREEN (settings).**
  - `UtilSettings.kt`: after `const val DEFAULT_DEPTH_OF_FIELD = false` add `// FISH-034: rim vignette is opt-in, off by default.` and `const val DEFAULT_LENS_VIGNETTE = false`; after `KEY_DEPTH_OF_FIELD` add `const val KEY_LENS_VIGNETTE = "lens_vignette"`; in the boolean `when` after `KEY_DEPTH_OF_FIELD -> ...` add `KEY_LENS_VIGNETTE -> prefs.getBoolean(name, DEFAULT_LENS_VIGNETTE)`.
  - `ResetEntries.kt`: after the `KEY_DEPTH_OF_FIELD` flag line add `flag(UtilSettings.KEY_LENS_VIGNETTE, R.string.setting_lens_vignette, UtilSettings.DEFAULT_LENS_VIGNETTE),`.
  - Base `values/strings.xml`: add
    `<string name="setting_lens_vignette">Rim vignette</string>` and
    `<string name="setting_lens_vignette_hint">Softly dims icons far from your finger while dragging the lens. Off by default.</string>`.
  - Add the same two keys to all 16 locale files with these exact values (`ar`: `تعتيم الحواف` / `يخفّت الرموز البعيدة عن إصبعك أثناء سحب العدسة. متوقف افتراضيًا.`; `de`: `Randvignette` / `Dunkelt Symbole weit vom Finger beim Ziehen der Linse leicht ab. Standardmäßig aus.`; `es`: `Viñeteado de bordes` / `Atenúa suavemente los iconos lejos de tu dedo al arrastrar la lente. Desactivado por defecto.`; `fr`: `Vignettage des bords` / `Atténue légèrement les icônes éloignées du doigt pendant le glissement de la lentille. Désactivé par défaut.`; `hi`: `किनारा धुंधलापन` / `लेंस खींचते समय उंगली से दूर के आइकन हल्के धुंधले करता है। डिफ़ॉल्ट रूप से बंद।`; `in`: `Vinyet tepi` / `Meredupkan ikon yang jauh dari jari saat menggeser lensa. Nonaktif secara default.`; `it`: `Vignettatura dei bordi` / `Attenua leggermente le icone lontane dal dito mentre trascini la lente. Disattivato per impostazione predefinita.`; `ja`: `周辺減光` / `レンズをドラッグ中、指から遠いアイコンをわずかに暗くします。初期設定はオフです。`; `km`: `ការបន្ថយពន្លឺគែម` / `បន្ថយពន្លឺរូបតំណាងដែលនៅឆ្ងាយពីម្រាមដៃពេលអូសកញ្ចក់។ បិទតាមលំនាំដើម។`; `ko`: `가장자리 비네팅` / `렌즈를 드래그하는 동안 손가락에서 먼 아이콘을 살짝 어둡게 합니다. 기본값은 꺼짐입니다.`; `lo`: `ການມືດຂອບ` / `ເຮັດໃຫ້ໄອຄອນທີ່ຢູ່ໄກນິ້ວມືມືດລົງເລັກນ້ອຍເມື່ອລາກເລນ. ປິດຕາມຄ່າເລີ່ມຕົ້ນ.`; `pt`: `Vinheta nas bordas` / `Escurece levemente os ícones longe do dedo ao arrastar a lente. Desativado por padrão.`; `ru`: `Виньетирование краёв` / `Слегка затемняет значки вдали от пальца при перетаскивании линзы. По умолчанию выключено.`; `th`: `ขอบมืด` / `ทำให้ไอคอนที่อยู่ไกลจากนิ้วมืดลงเล็กน้อยขณะลากเลนส์ ปิดไว้เป็นค่าเริ่มต้น`; `vi`: `Tối viền ống kính` / `Làm tối nhẹ các biểu tượng xa ngón tay khi kéo lens. Mặc định tắt.`; `zh`: `边缘暗角` / `拖动镜头时轻微压暗远离手指的图标。默认关闭。`). Append each pair before the closing `</resources>`.
  - `frm_settings.xml`: after the closing `</RelativeLayout>` of the depth-of-field row (the block starting at the comment `<!-- FISH-007: depth-of-field blur around the lens, opt-in -->`) and **before** the next `MaterialDivider`, insert a `MaterialDivider` (`layout_marginStart="52dp"`, `app:dividerColor="?attr/colorOutlineVariant"`) followed by a copy of that row with `<!-- FISH-034: rim vignette, opt-in -->`, ids `rlSwitchLensVignetteParent`, `iconLensVignette`, `swLensVignette`, `tvLensVignetteHint`, `@string/setting_lens_vignette` and `@string/setting_lens_vignette_hint`, same `ic_lens_distortion_24dp` icon.
  - `FrmSettings.kt`: add `private var swLensVignette: SwitchCompat? = null`; in the view-binding block add `swLensVignette = view.findViewById(R.id.swLensVignette)`; next to `rlSwitchDepthOfFieldParent` add `view.findViewById<View>(R.id.rlSwitchLensVignetteParent).setOnClickListener(null)`; next to the depth-of-field listener add `swLensVignette?.setOnCheckedChangeListener { _, isChecked -> utilSettings?.save(UtilSettings.KEY_LENS_VIGNETTE, isChecked) }`; in `assignValues` add `swLensVignette?.isChecked = us.getBoolean(UtilSettings.KEY_LENS_VIGNETTE)`; in `onDestroyView` add `swLensVignette = null`.

- [ ] **Step 4: Verify GREEN (JVM).** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.UtilSettingsLensVignetteTest' --tests 'com.mckimquyen.ui.settings.ResetEntriesTest' --tests 'com.mckimquyen.a11y.AllStringsTranslationTest' --tests 'com.mckimquyen.a11y.LensStringTranslationTest'`. Expected: all pass; `AllStringsTranslationTest` proves the strings exist in every locale.

- [ ] **Step 5: RED (device).** Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsLensVignetteWidgetTest.kt` by copying `FrmSettingsDepthOfFieldWidgetTest.kt`, renaming the class, replacing `R.id.swDepthOfField` with `R.id.swLensVignette` and `KEY_DEPTH_OF_FIELD` with `KEY_LENS_VIGNETTE`, keeping its four cases (off by default, reflects saved true, toggle persists, reset turns it off). Run `./gradlew compileDevDebugAndroidTestKotlin`; expected before Step 3 is done: unresolved `swLensVignette`.

- [ ] **Step 6: Device run after backup (Task 5, Step 2 procedure).** Install candidate APKs and run only: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsLensVignetteWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`. Expected `OK (4 tests)`; `OK (0 tests)` is not a pass. Also rerun `FrmSettingsDepthOfFieldWidgetTest` and the existing Settings layout/backup widget tests.

- [ ] **Step 7: Commit.** Stage only these files; `git commit -m "feat(fish-034): add rim vignette setting"`.

## Task 3 — Per-icon alpha in `LensView`

**Files:** modify `LensView.kt`; create `LensViewVignetteWidgetTest.kt`.

- [ ] **Step 1: RED.** Create `app/src/androidTest/java/com/mckimquyen/views/LensViewVignetteWidgetTest.kt` using the same fixture as `LensViewDepthOfFieldWidgetTest` (software `Bitmap` canvas, 25 red icons registered in `RAppsSingleton`, `BitmapCache.clear()` in `@After`, `DeviceStateGuard` rule). Add these tests, each calling `lensView.setLensStateForTest(x, y, animationMultiplier, reduceMotion)` then `render()` / `alphaSum()` exactly as the DoF test does:

```kotlin
@Test fun defaultIsOff_everyIconStaysOpaque() {
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    val a = alphaSum(render())
    lensView.lensVignetteEnabled = false
    assertEquals(a, alphaSum(render()))
}

@Test fun enabled_dimsFarIconsButNeverBelowTheFloor() {
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    lensView.lensVignetteEnabled = false
    val off = alphaSum(render())
    lensView.lensVignetteEnabled = true
    val on = alphaSum(render())
    assertTrue("far icons must dim ($on vs $off)", on < off)
    assertTrue("floor keeps icons visible", on * 2 > off)   // every icon keeps >= 50% alpha
}

@Test fun idleLens_staysOpaqueEvenWhenEnabled() {
    lensView.lensVignetteEnabled = false
    lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
    val off = alphaSum(render())
    lensView.lensVignetteEnabled = true
    assertEquals(off, alphaSum(render()))
}

@Test fun geometryAndHitTestAreIdenticalWithAndWithoutTheVignette() {
    val step = SIZE_PX / 8
    for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
        lensView.setLensStateForTest(x.toFloat(), y.toFloat(), 1f, reduceMotion = false)
        lensView.lensVignetteEnabled = false
        render().recycle()
        val rectsOff = lensView.drawnRectsForTest()
        val indexOff = lensView.selectedIndexForTest
        lensView.lensVignetteEnabled = true
        render().recycle()
        assertEquals(rectsOff, lensView.drawnRectsForTest())
        assertEquals(indexOff, lensView.selectedIndexForTest)
    }
}

@Test fun paintAlphaIsRestoredSoTheNextFrameWithoutTheVignetteIsOpaque() {
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    lensView.lensVignetteEnabled = false
    val before = alphaSum(render())
    lensView.lensVignetteEnabled = true
    render().recycle()
    lensView.lensVignetteEnabled = false
    assertEquals(before, alphaSum(render()))
}

@Test fun withTheDofAlphaFallbackTheLowerAlphaWinsAndNothingIsMultiplied() {
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    lensView.depthOfFieldEnabled = true
    lensView.lensVignetteEnabled = false
    val dofOnly = alphaSum(render())          // software canvas => DoF ALPHA fallback
    lensView.depthOfFieldEnabled = false
    lensView.lensVignetteEnabled = true
    val vignetteOnly = alphaSum(render())
    lensView.depthOfFieldEnabled = true
    val both = alphaSum(render())
    assertEquals("min of equal curves equals either one, not their product", dofOnly, both)
    assertEquals(vignetteOnly, both)
}

@Test fun reducedMotionSnapsTheTransitionInsteadOfEasing() {
    lensView.lensVignetteEnabled = true
    lensView.setLensStateForTest(0f, 0f, 0.3f, reduceMotion = false)
    val eased = alphaSum(render())
    lensView.setLensStateForTest(0f, 0f, 0.3f, reduceMotion = true)
    val snapped = alphaSum(render())
    assertTrue("snapped (full) vignette is darker than the eased 30% one", snapped < eased)
}

@Test fun theGridCacheIsNotRecomputedByVignetteFrames() {
    val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
    val cache = field.get(lensView) as LensGridCache
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    lensView.lensVignetteEnabled = false
    render().recycle()
    val recomputes = cache.recomputeCount
    lensView.lensVignetteEnabled = true
    repeat(5) { render().recycle() }
    assertEquals(recomputes, cache.recomputeCount)
}
```

- [ ] **Step 2: Verify RED.** `./gradlew compileDevDebugAndroidTestKotlin` fails only with `Unresolved reference 'lensVignetteEnabled'`.

- [ ] **Step 3: GREEN.** In `LensView.kt`:
  - After the `depthOfFieldEnabled` declaration add:
    ```kotlin
    /** FISH-034: sampled once per lens show/hide in LensAnimation, never per frame. */
    @androidx.annotation.VisibleForTesting
    internal var lensVignetteEnabled = UtilSettings.DEFAULT_LENS_VIGNETTE
    ```
  - In `LensAnimation.init`, directly after the `depthOfFieldEnabled = ...` statement add:
    ```kotlin
    lensVignetteEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_LENS_VIGNETTE)
        ?: UtilSettings.DEFAULT_LENS_VIGNETTE
    ```
  - In `drawGrid`, after the `dofMode` is computed and before the cell loop add:
    ```kotlin
    // FISH-034: same gate shape as depth-of-field; live touch only, so pinned focus/tilt never darken the rim.
    val vignetteOn = LensVignette.shouldApply(
        lensVignetteEnabled, mDrawType == DrawType.APPS && hasActiveTouch && dofTransition > 0f
    )
    ```
  - Replace the per-icon block (from `val dofIntensity = ...` through the `if (dofMode == DepthOfField.Mode.ALPHA) { ... }`) with:
    ```kotlin
    val focusDistance = if (dofMode != DepthOfField.Mode.OFF || vignetteOn) DepthOfField.focusDistance(
        mTouchX, mTouchY, baseRect.centerX(), baseRect.centerY(), width.toFloat(), height.toFloat()
    ) else 0f
    val dofIntensity = if (dofMode == DepthOfField.Mode.OFF) 0f else DepthOfField.intensity(focusDistance, dofTransition)
    val dofBand = DepthOfField.band(dofIntensity)
    dofBandCounts[dofBand]++
    if (dofMode == DepthOfField.Mode.ALPHA || vignetteOn) {
        val dofAlpha = if (dofMode == DepthOfField.Mode.ALPHA) DepthOfField.fallbackAlpha(dofIntensity) else DepthOfField.OPAQUE_ALPHA
        val vignetteAlpha = if (vignetteOn) LensVignette.alpha(LensVignette.intensity(focusDistance, dofTransition)) else LensVignette.OPAQUE_ALPHA
        mPaintIcons?.alpha = LensVignette.combine(dofAlpha, vignetteAlpha)
    }
    ```
    Keep the existing `mPaintIcons?.alpha = DepthOfField.OPAQUE_ALPHA` after the loop untouched. Keep the existing "blur only picks the target canvas/alpha" comment and extend it with `FISH-034: the vignette only lowers paint alpha`.

- [ ] **Step 4: Verify GREEN.** Run `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.views.*'` (host), then, after the Task 5 backup procedure, `LensViewVignetteWidgetTest` and the regression set: `LensViewDepthOfFieldWidgetTest`, `LensViewDepthOfFieldIntegrationTest`, `LensViewPinchIntegrationTest`, `LensViewFocusLockIntegrationTest`, `LensViewTiltIntegrationTest`, `LensViewReattachIntegrationTest`, `LensViewWidgetTest`. Every class must show `OK (N tests)` with N > 0.

- [ ] **Step 5: Commit.** `git commit -m "feat(fish-034): dim far icons with a rim vignette"` (only `LensView.kt` and the new widget test).

## Task 4 — Home lifecycle and detach regression

**Files:** none new unless a test fails.

- [ ] **Step 1: Add the test (it may already pass; say so honestly).** Append to `LensViewVignetteWidgetTest`:

```kotlin
@Test fun afterATouchedVignetteFrameAnIdleFrameIsFullyOpaque() {
    lensView.lensVignetteEnabled = true
    lensView.setLensStateForTest(0f, 0f, 1f, reduceMotion = false)
    render().recycle()
    lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
    val idleWithVignette = alphaSum(render())
    lensView.lensVignetteEnabled = false
    assertEquals(alphaSum(render()), idleWithVignette)
}
```

Run it. If it already passes because the gate requires a live touch, record "covered by existing gate, not RED" in the audit; never weaken another test to force a failure.
- [ ] **Step 2: Run** `ActHomeFocusLockLifecycleTest`, `ActHomeTiltWidgetTest`, `ActHomeQuickLensSwitchWidgetTest`, `FrmSettingsDepthOfFieldWidgetTest` and `FrmSettingsLensVignetteWidgetTest` on TECNO; all `OK (N>0)`.
- [ ] **Step 3: Commit** only if a test file changed: `git commit -m "test(fish-034): cover idle frames after a vignette drag"`.

## Task 5 — Full gate, frame budget, TECNO smoke, audit, closure

**Files:** `doc/task/AUDIT_FISH-034_2026-11-06.md`, `doc/feature.md`, `doc/task/README.md`, story move.

- [ ] **Step 1: Host gate.** `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `scripts/check-secrets.sh`, `git diff --check`. Record unit count (baseline 1525 plus new tests), lint errors/warnings (baseline 0/15) and exit codes.
- [ ] **Step 2: Device backup (before the first install).** Confirm `adb devices -l` lists KJ7 (ignore Pixel). Then:
  ```bash
  K=115333744A005844; D=/tmp/f034-$(date +%Y%m%d-%H%M%S); mkdir -p "$D"
  adb -s $K shell am force-stop com.mckimquyen.lenslauncher
  M=$(adb -s $K shell pm path com.mckimquyen.lenslauncher | sed 's/^package://' | tr -d '\r')
  T=$(adb -s $K shell pm path com.mckimquyen.lenslauncher.test | sed 's/^package://' | tr -d '\r')
  adb -s $K pull "$M" $D/before.apk; adb -s $K pull "$T" $D/before-test.apk
  adb -s $K exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > $D/before-data.tar
  adb -s $K shell 'settings get system font_scale; settings get global animator_duration_scale; settings get global transition_animation_scale; settings get global window_animation_scale; cmd locale get-app-locales com.mckimquyen.lenslauncher; cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > $D/device-state.txt
  ```
  Hash every tar member into `$D/hashes.json` and both APKs with `shasum -a 256`. Expect 18 data files and APK hashes `b1c0b81d…` (main) / `14cfa462…` (test) if the device is still at the FISH-029 baseline; otherwise record the new hashes.
- [ ] **Step 3: Device tests.** Install `app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk` and `app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk`, `pm clear com.mckimquyen.lenslauncher.test`, then run each class named in Tasks 2–4 with `am instrument -w -e class …`. Treat `OK (0 tests)` as a failure.
- [ ] **Step 4: Frame budget (acceptance criterion).** With the app foreground on Home and the vignette off, run `adb -s $K shell dumpsys gfxinfo com.mckimquyen.lenslauncher reset`, perform three drags with `adb -s $K shell input swipe 200 700 900 1500 1500`, then `dumpsys gfxinfo com.mckimquyen.lenslauncher | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile'`; save to `$D/gfx-off.txt`. Enable the setting by force-stopping the app, writing `<boolean name="lens_vignette" value="true" />` into `shared_prefs/com.mckimquyen.lenslauncher_preferences.xml` via `run-as` (pull, edit with Python, push back with `adb shell "run-as com.mckimquyen.lenslauncher sh -c 'cat > shared_prefs/com.mckimquyen.lenslauncher_preferences.xml'" < local.xml`), repeat the identical drags and save `$D/gfx-on.txt`. Report p50/p90 for both. If a run reports 0 frames or the numbers are not valid (USB-powered idle, as seen in FISH-033), state that plainly in the audit and do **not** claim the budget passed.
- [ ] **Step 5: Visual smoke.** With the setting on, use `input swipe 540 1000 540 1000 600` (hold) while capturing `screencap` mid-hold (`adb shell "input swipe 540 1000 540 1000 1500 & sleep 0.8; screencap -p /sdcard/v.png"`), pull it, and compare against an idle capture by pixel difference (changed pixels > 0 only in the grid region, far icons darker). Release and confirm the idle screenshot matches again (0 changed pixels). Disable the setting and confirm no dimming. Stop on any `AdActivity` (R4).
- [ ] **Step 6: Restore.** Force-stop, push `before-data.tar` back with `rm -rf databases shared_prefs && tar xf`, reinstall `before.apk` and `before-test.apk` with `install -r -d`, re-tar the data and compare per-file hashes, compare the settings block and re-pull the APKs to compare their hashes. Stop and disclose if anything differs.
- [ ] **Step 7: Audit.** Create `doc/task/AUDIT_FISH-034_2026-11-06.md` with: scope, evidence table (unit count, lint, device classes and counts, frame numbers or the explicit "not measurable" statement, restore hashes), defects found with their RED tests, limitations (AI translations, no owner hand smoke, chromatic aberration split out) and a score. The story may move to `done/` only if score > 9.0, every device class showed `OK (N>0)`, and restore was proven; otherwise leave it `inprogress/`. Move the story file from `todo/` to `inprogress/` at the start of Task 1 and update `doc/feature.md` and `doc/task/README.md` (also record the split-out chromatic-aberration follow-up as a new backlog line).
- [ ] **Step 8: Commit** audit/backlog/status separately; never stage `.idea/`; do not push.

## Self-review

- Spec coverage: pure math (Task 1); opt-in setting, reset and Settings row (Task 2); alpha with min-of-both rule, live-touch-only gate, reduced-motion snap, geometry/hit-test/grid-cache invariants (Task 3); idle/lifecycle (Task 4); frame budget, smoke, restore, audit (Task 5); chromatic aberration explicitly out of scope.
- Names are consistent across tasks: `LensVignette.shouldApply/intensity/alpha/combine/FLOOR_ALPHA_FRACTION/OPAQUE_ALPHA`, `KEY_LENS_VIGNETTE`, `DEFAULT_LENS_VIGNETTE`, `lensVignetteEnabled`, `swLensVignette`, `setting_lens_vignette(_hint)`.
- No dependency, no `RenderEffect`, no new per-frame allocation, no persistence beyond one boolean, no push.
