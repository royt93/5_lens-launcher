# FISH-015 — Save Custom Lens Physics Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user save the current three lens-physics sliders (distortion, scale,
animation-time) as one reusable "Custom" preset, and re-apply it with a single tap, alongside
the existing fixed Gentle/Standard/Snappy presets.

**Architecture:** Three new `UtilSettings` keys (one per-lens matching `KEY_DISTORTION_FACTOR`'s
existing scope, two global matching `KEY_SCALE_FACTOR`/`KEY_ANIMATION_TIME`'s existing scope), a
new "Custom" button + a new icon-only "Save" button in `frm_lens.xml`, and a small refactor of
`FrmLens.applyPreset()` so it takes three raw values instead of only a `LensPhysicsPreset` enum
constant. No changes to `LensView`/`LensGridCache`.

**Tech Stack:** Kotlin, `SharedPreferences` (via `UtilSettings`), Material3 `MaterialButton`,
JUnit4 + Robolectric (unit), AndroidJUnit4 instrumented tests (widget/integration, no Espresso —
this project's device cannot build the Espresso event injector, see existing test docstrings).

## Global Constraints

- Distortion factor is per-lens (`UtilSettings.lensKey()`); scale factor and animation time are
  global. The Custom preset must follow this exact same split — not a new one.
- Exactly one Custom slot. No named multi-preset list, no rename/delete UI for it.
- `btnPresetCustom` must be disabled until `UtilSettings.hasCustomPreset()` is `true`.
- No change to `LensPhysicsPreset` enum, `LensView`, or `LensGridCache`.
- Every new/changed `lens_*` string must be translated into all 16 non-English locales — this
  project treats `MissingTranslation` as a build-blocking lint error, and
  `LensStringTranslationTest` independently pins full coverage, placeholder parity, and
  non-English-text-left-untranslated for every `lens_*` key.
- `lens_physics_preset_custom` (the button's own label) stays `translatable="false"`, matching
  its three sibling preset-name strings (`lens_physics_preset_gentle/standard/snappy`) — proper
  noun, base-locale-only by existing convention.
- Every implementation case needs unit, widget, and integration coverage per this repo's
  `doc/task/README.md` test-layer rule; a Tecno smoke pass on the session-locked device closes
  the loop.
- Device policy (current, 2026-09-27 owner decision): **TECNO only** for build/run/install/smoke
  — TECNO KJ7 (`115333744A005844`) preferred, TECNO BG6 (`118743744X002560`) standing fallback if
  KJ7 drops. Never fall back to Pixel/S24U even if attached and idle. If no TECNO is attached,
  stop and ask.

---

### Task 1: Backlog story file

**Files:**
- Create: `doc/task/todo/p2-fish-fish-015-custom-physics-presets.md`

**Interfaces:**
- Consumes: nothing (pure documentation).
- Produces: nothing later tasks depend on programmatically; this is the backlog record the final
  task moves to `doc/task/done/` once evidence exists.

- [ ] **Step 1: Write the story file**

```markdown
# FISH-015 — Save custom lens physics presets

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | FISH-004 |

## Context and evidence

`FISH-004` shipped three fixed lens-physics presets (Gentle/Standard/Snappy), each a hardcoded
(distortion, scale, animation-time) triple. A user who fine-tunes the three sliders to their own
preference has no way to return to that exact combination later except by re-dragging all three
sliders back manually.

## User story

As a user who has tuned the lens sliders to my own preference, I want to save that combination as
a Custom preset so I can return to it in one tap after trying Gentle/Standard/Snappy or after
further experimentation.

## Acceptance criteria

- [ ] A "Custom" preset button sits alongside Gentle/Standard/Snappy, disabled until the user has
      saved at least once.
- [ ] A Save button captures the three current slider values (distortion, scale,
      animation-time) into the Custom slot and enables the Custom button immediately.
- [ ] Tapping Custom re-applies the exact saved triple, same semantics as the fixed presets.
- [ ] Distortion factor in the Custom slot is per-lens; scale factor and animation-time stay
      global — matching the fixed presets' existing scope split exactly.
- [ ] Deleting a lens clears that lens's own Custom distortion override; duplicating a lens
      carries its effective Custom distortion onto the copy — matching how the fixed presets'
      underlying keys already behave.
- [ ] Reset to Default (`STANDARD`) does not touch the saved Custom slot.

## Implementation notes

Full design spec at
`docs/superpowers/specs/2026-09-30-fish-015-custom-physics-presets-design.md`.

- `UtilSettings`: `KEY_CUSTOM_DISTORTION_FACTOR` (per-lens), `KEY_CUSTOM_SCALE_FACTOR`/
  `KEY_CUSTOM_ANIMATION_TIME` (global), `hasCustomPreset()`.
- `FrmLens.applyPreset()` refactored to take `(distortion, scale, animationTimeMs)` directly so
  Custom can reuse it without a fake enum constant.
- New `btnPresetCustom` + icon-only `btnSaveCustomPreset` (new `ic_save_24dp` vector) in
  `frm_lens.xml`, directly below the existing preset row.

## Required test matrix

- [ ] Unit tests cover the six new `UtilSettings` methods: default/round-trip/fallback/clamp for
      distortion, global round-trip for scale/animation-time, `hasCustomPreset()` before/after,
      and the extended `deleteLensSettings`/`duplicateLensSettings` behavior.
- [ ] Widget tests cover: Custom starts disabled, Save enables it and persists exact values,
      Custom applies the exact saved triple, per-lens distortion isolation across two lenses.
- [ ] Integration tests cover the real `ActHome` create-lens/delete-lens flow carrying (or
      clearing) the Custom distortion override, same pattern as the existing Smart Focus/
      distortion coverage in `ActHomeLensManagementWidgetTest`.
- [ ] Smoke test on the session-locked TECNO device; record model, Android version, build, and
      timestamp.

## Verification and Definition of Done

- [ ] Required test layers pass (unit, widget, integration).
- [ ] `LensStringTranslationTest` passes (new strings translated into all 16 locales).
- [ ] Smoke checklist signed off on real hardware.
- [ ] Zero new lint or build warnings/failures.
- [ ] Post-change audit scores `> 9.0/10` before push.
```

- [ ] **Step 2: Commit**

```bash
git add doc/task/todo/p2-fish-fish-015-custom-physics-presets.md
git commit -m "docs(fish-015): add backlog story for custom lens physics presets

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: `UtilSettings` custom preset storage + unit tests

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt:90-153` (companion keys block),
  `app/src/main/java/com/mckimquyen/util/UtilSettings.kt:315-409` (per-lens methods block)
- Test: Create `app/src/test/java/com/mckimquyen/util/UtilSettingsCustomPresetTest.kt`

**Interfaces:**
- Consumes: existing `UtilSettings.lensKey(baseKey, lensId): String`,
  `getFloatWithValidation(name, default, min, max): Float`,
  `getDistortionFactor(lensId): Float`, `MIN_DISTORTION_FACTOR`, `MAX_DISTORTION_FACTOR`,
  `MIN_SCALE_FACTOR`, `MAX_SCALE_FACTOR`, `MIN_ANIMATION_TIME`, `MAX_ANIMATION_TIME`,
  `DEFAULT_DISTORTION_FACTOR`, `DEFAULT_SCALE_FACTOR`, `DEFAULT_ANIMATION_TIME`.
- Produces (used by Task 5 `FrmLens.kt` and Task 6 integration tests):
  - `fun getCustomDistortionFactor(lensId: String?): Float`
  - `fun saveCustomDistortionFactor(lensId: String?, value: Float)`
  - `fun getCustomScaleFactor(): Float`
  - `fun saveCustomScaleFactor(value: Float)`
  - `fun getCustomAnimationTime(): Long`
  - `fun saveCustomAnimationTime(value: Long)`
  - `fun hasCustomPreset(): Boolean`
  - `KEY_CUSTOM_DISTORTION_FACTOR`, `KEY_CUSTOM_SCALE_FACTOR`, `KEY_CUSTOM_ANIMATION_TIME`
    (companion constants)

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsCustomPresetTest.kt`:

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
 * FISH-015: the user-saved "Custom" lens physics preset. Distortion follows the exact same
 * per-lens split [UtilSettingsPerLensTest] already proves for [UtilSettings.KEY_DISTORTION_FACTOR]
 * - a Custom distortion override is per-lens, while Custom scale/animation-time stay global,
 * matching how the fixed Gentle/Standard/Snappy presets already treat these three fields.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsCustomPresetTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    // ---- hasCustomPreset() ----

    @Test
    fun `hasCustomPreset is false before any save`() {
        assertFalse(settings.hasCustomPreset())
    }

    @Test
    fun `hasCustomPreset is true once the custom scale is saved`() {
        settings.saveCustomScaleFactor(1.4f)
        assertTrue(settings.hasCustomPreset())
    }

    // ---- Distortion: default before any save ----

    @Test
    fun `custom distortion before any save matches the live effective distortion for that lens`() {
        assertEquals(
            settings.getDistortionFactor(work),
            settings.getCustomDistortionFactor(work),
            0.001f
        )
    }

    // ---- Distortion: per-lens round trip and fallback ----

    @Test
    fun `saving custom distortion for one lens does not affect another`() {
        settings.saveCustomDistortionFactor(work, 3.2f)

        assertEquals(3.2f, settings.getCustomDistortionFactor(work), 0.001f)
        assertEquals(
            "An untouched lens must fall back, not read the other lens's override",
            settings.getDistortionFactor(personal),
            settings.getCustomDistortionFactor(personal),
            0.001f
        )
    }

    @Test
    fun `a lens with no override inherits the base custom distortion once one exists`() {
        settings.saveCustomDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 4.0f)

        assertEquals(
            "The base custom value must be the fallback once it exists",
            4.0f,
            settings.getCustomDistortionFactor(work),
            0.001f
        )
    }

    @Test
    fun `custom distortion is clamped to the same range as the regular distortion key`() {
        settings.saveCustomDistortionFactor(work, 99f)
        val max = UtilSettings.MAX_DISTORTION_FACTOR / 2f + UtilSettings.MIN_DISTORTION_FACTOR
        assertEquals(max, settings.getCustomDistortionFactor(work), 0.001f)

        settings.saveCustomDistortionFactor(work, -5f)
        assertEquals(UtilSettings.MIN_DISTORTION_FACTOR, settings.getCustomDistortionFactor(work), 0.001f)
    }

    // ---- Scale / animation-time: global round trip ----

    @Test
    fun `custom scale factor round trips and is global`() {
        settings.saveCustomScaleFactor(1.7f)
        assertEquals(1.7f, settings.getCustomScaleFactor(), 0.001f)
        assertTrue(rawPrefs().contains(UtilSettings.KEY_CUSTOM_SCALE_FACTOR))
    }

    @Test
    fun `custom animation time round trips and is global`() {
        settings.saveCustomAnimationTime(180L)
        assertEquals(180L, settings.getCustomAnimationTime())
        assertTrue(rawPrefs().contains(UtilSettings.KEY_CUSTOM_ANIMATION_TIME))
    }

    @Test
    fun `custom animation time before any save falls back to the documented default`() {
        assertEquals(UtilSettings.DEFAULT_ANIMATION_TIME, settings.getCustomAnimationTime())
    }

    // ---- Lifecycle: delete / duplicate ----

    @Test
    fun `deleteLensSettings removes that lens's custom distortion override`() {
        settings.saveCustomDistortionFactor(work, 4.5f)
        settings.saveCustomDistortionFactor(personal, 1.5f)

        settings.deleteLensSettings(work)

        assertFalse(rawPrefs().contains("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_$work"))
        assertEquals(1.5f, settings.getCustomDistortionFactor(personal), 0.001f)
    }

    @Test
    fun `duplicateLensSettings copies the effective custom distortion onto the new lens`() {
        settings.saveCustomDistortionFactor(work, 4.5f)

        settings.duplicateLensSettings(work, personal)

        assertEquals(4.5f, settings.getCustomDistortionFactor(personal), 0.001f)
    }

    @Test
    fun `duplicating from a lens that only inherits materializes the inherited custom value`() {
        settings.saveCustomDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 3.0f)

        settings.duplicateLensSettings(work, personal)

        assertEquals(
            "work never saved its own override, so it inherits the base custom value - " +
                "duplicating it must materialize that inherited value onto personal",
            3.0f,
            settings.getCustomDistortionFactor(personal),
            0.001f
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.UtilSettingsCustomPresetTest"`
Expected: FAIL — compile error, `getCustomDistortionFactor`/`saveCustomDistortionFactor`/
`getCustomScaleFactor`/`saveCustomScaleFactor`/`getCustomAnimationTime`/
`saveCustomAnimationTime`/`hasCustomPreset`/`KEY_CUSTOM_DISTORTION_FACTOR`/
`KEY_CUSTOM_SCALE_FACTOR`/`KEY_CUSTOM_ANIMATION_TIME` are unresolved references.

- [ ] **Step 3: Add the three new keys**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`, find:

```kotlin
        const val KEY_SCALE_FACTOR = "scale_factor"
        const val KEY_ANIMATION_TIME = "animation_time"
```

Replace with:

```kotlin
        const val KEY_SCALE_FACTOR = "scale_factor"
        const val KEY_ANIMATION_TIME = "animation_time"
        // FISH-015: the single user-saved "Custom" preset slot. Distortion follows
        // KEY_DISTORTION_FACTOR's existing per-lens split (via lensKey()); scale/animation-time
        // follow KEY_SCALE_FACTOR/KEY_ANIMATION_TIME's existing global scope - same split the
        // fixed Gentle/Standard/Snappy presets already use for these three fields.
        const val KEY_CUSTOM_DISTORTION_FACTOR = "custom_distortion_factor"
        const val KEY_CUSTOM_SCALE_FACTOR = "custom_scale_factor"
        const val KEY_CUSTOM_ANIMATION_TIME = "custom_animation_time"
```

- [ ] **Step 4: Add the custom preset methods**

In the same file, find the end of the FISH-008 Phase 3 per-lens block:

```kotlin
    fun deleteLensSettings(lensId: String) {
        if (lensId.isNotEmpty() && lensId != LensWorkspace.DEFAULT_LENS_ID) {
            prefs.edit {
                remove("${KEY_DISTORTION_FACTOR}_$lensId")
                remove("${KEY_SMART_FOCUS_BIAS}_$lensId")
                remove("${KEY_PENDING_DISTORTION_FACTOR}_$lensId")
            }
        }
    }
```

Replace with:

```kotlin
    fun deleteLensSettings(lensId: String) {
        if (lensId.isNotEmpty() && lensId != LensWorkspace.DEFAULT_LENS_ID) {
            prefs.edit {
                remove("${KEY_DISTORTION_FACTOR}_$lensId")
                remove("${KEY_SMART_FOCUS_BIAS}_$lensId")
                remove("${KEY_PENDING_DISTORTION_FACTOR}_$lensId")
                remove("${KEY_CUSTOM_DISTORTION_FACTOR}_$lensId")
            }
        }
    }

    // ========================================================================
    // FISH-015: the single user-saved "Custom" lens physics preset. Distortion is per-lens
    // (same lensKey() split as KEY_DISTORTION_FACTOR); scale/animation-time are global (same
    // scope as KEY_SCALE_FACTOR/KEY_ANIMATION_TIME). Saving always writes all three together, so
    // hasCustomPreset() checking one global key is a reliable "ever saved" signal.
    // ========================================================================

    fun getCustomDistortionFactor(lensId: String?): Float {
        val key = lensKey(KEY_CUSTOM_DISTORTION_FACTOR, lensId)
        return if (prefs.contains(key)) {
            getFloatWithValidation(
                key,
                DEFAULT_DISTORTION_FACTOR,
                MIN_DISTORTION_FACTOR,
                MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR
            )
        } else if (prefs.contains(KEY_CUSTOM_DISTORTION_FACTOR)) {
            getFloatWithValidation(
                KEY_CUSTOM_DISTORTION_FACTOR,
                DEFAULT_DISTORTION_FACTOR,
                MIN_DISTORTION_FACTOR,
                MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR
            )
        } else {
            // No custom value has ever been saved for this lens or as a base default - show
            // this lens's own live effective distortion rather than inventing one.
            getDistortionFactor(lensId)
        }
    }

    fun saveCustomDistortionFactor(lensId: String?, value: Float) {
        save(lensKey(KEY_CUSTOM_DISTORTION_FACTOR, lensId), value)
    }

    fun getCustomScaleFactor(): Float = getFloatWithValidation(
        KEY_CUSTOM_SCALE_FACTOR,
        DEFAULT_SCALE_FACTOR,
        MIN_SCALE_FACTOR,
        MAX_SCALE_FACTOR / 2f + MIN_SCALE_FACTOR
    )

    fun saveCustomScaleFactor(value: Float) {
        save(KEY_CUSTOM_SCALE_FACTOR, value)
    }

    fun getCustomAnimationTime(): Long {
        val value = prefs.getLong(KEY_CUSTOM_ANIMATION_TIME, DEFAULT_ANIMATION_TIME)
        val max = MAX_ANIMATION_TIME / 2L + MIN_ANIMATION_TIME
        return value.coerceIn(MIN_ANIMATION_TIME, max)
    }

    fun saveCustomAnimationTime(value: Long) {
        save(KEY_CUSTOM_ANIMATION_TIME, value)
    }

    /** True once the user has saved a Custom preset at least once, on any lens. */
    fun hasCustomPreset(): Boolean = prefs.contains(KEY_CUSTOM_SCALE_FACTOR)
```

- [ ] **Step 5: Extend `duplicateLensSettings` to carry the custom distortion override**

Find:

```kotlin
    /** Materializes [fromLensId]'s effective values onto [toLensId], inherited ones included. */
    fun duplicateLensSettings(fromLensId: String, toLensId: String) {
        val distortion = getDistortionFactor(fromLensId)
        val smartFocus = isSmartFocusBias(fromLensId)
        saveDistortionFactor(toLensId, distortion)
        saveSmartFocusBias(toLensId, smartFocus)
    }
```

Replace with:

```kotlin
    /** Materializes [fromLensId]'s effective values onto [toLensId], inherited ones included. */
    fun duplicateLensSettings(fromLensId: String, toLensId: String) {
        val distortion = getDistortionFactor(fromLensId)
        val smartFocus = isSmartFocusBias(fromLensId)
        val customDistortion = getCustomDistortionFactor(fromLensId)
        saveDistortionFactor(toLensId, distortion)
        saveSmartFocusBias(toLensId, smartFocus)
        saveCustomDistortionFactor(toLensId, customDistortion)
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.UtilSettingsCustomPresetTest"`
Expected: PASS, all 13 tests green.

- [ ] **Step 7: Run the full unit regression to confirm no breakage**

Run: `./gradlew testDevDebugUnitTest`
Expected: PASS, all tests green (including the pre-existing `UtilSettingsPerLensTest`,
`UtilSettingsPendingDistortionTest`, `LensPhysicsPolicyTest`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt \
        app/src/test/java/com/mckimquyen/util/UtilSettingsCustomPresetTest.kt
git commit -m "feat(fish-015): add custom lens physics preset storage to UtilSettings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Localize the three new strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:214-217`
- Modify: `app/src/main/res/values-{ar,de,es,fr,hi,in,it,ja,km,ko,lo,pt,ru,th,vi,zh}/strings.xml`
  (one line each, at the line noted per locale below)

**Interfaces:**
- Consumes: nothing.
- Produces (used by Task 4 layout and Task 5 `FrmLens.kt`): string resources
  `R.string.lens_physics_preset_custom`, `R.string.lens_physics_save_custom_preset_description`,
  `R.string.lens_physics_custom_preset_saved`.

- [ ] **Step 1: Add all three strings to the base locale**

In `app/src/main/res/values/strings.xml`, find:

```xml
    <string name="setting_lens_physics_presets">Movement feel</string>
    <string name="lens_physics_preset_gentle" translatable="false">Gentle</string>
    <string name="lens_physics_preset_standard" translatable="false">Standard</string>
    <string name="lens_physics_preset_snappy" translatable="false">Snappy</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Movement feel</string>
    <string name="lens_physics_preset_gentle" translatable="false">Gentle</string>
    <string name="lens_physics_preset_standard" translatable="false">Standard</string>
    <string name="lens_physics_preset_snappy" translatable="false">Snappy</string>
    <!-- FISH-015: the single user-saved custom preset slot. -->
    <string name="lens_physics_preset_custom" translatable="false">Custom</string>
    <string name="lens_physics_save_custom_preset_description">Save current settings as Custom preset</string>
    <string name="lens_physics_custom_preset_saved">Custom preset saved</string>
```

- [ ] **Step 2: Add the two translated strings to every non-English locale**

In `app/src/main/res/values-ar/strings.xml`, find (line 206):

```xml
    <string name="setting_lens_physics_presets">إحساس الحركة</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">إحساس الحركة</string>
    <string name="lens_physics_save_custom_preset_description">حفظ الإعدادات الحالية كإعداد مسبق مخصص</string>
    <string name="lens_physics_custom_preset_saved">تم حفظ الإعداد المسبق المخصص</string>
```

In `app/src/main/res/values-de/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Bewegungsgefühl</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Bewegungsgefühl</string>
    <string name="lens_physics_save_custom_preset_description">Aktuelle Einstellungen als benutzerdefiniertes Preset speichern</string>
    <string name="lens_physics_custom_preset_saved">Benutzerdefiniertes Preset gespeichert</string>
```

In `app/src/main/res/values-es/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Sensación de movimiento</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Sensación de movimiento</string>
    <string name="lens_physics_save_custom_preset_description">Guardar ajustes actuales como preset personalizado</string>
    <string name="lens_physics_custom_preset_saved">Preset personalizado guardado</string>
```

In `app/src/main/res/values-fr/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Ressenti du mouvement</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Ressenti du mouvement</string>
    <string name="lens_physics_save_custom_preset_description">Enregistrer les réglages actuels comme préréglage personnalisé</string>
    <string name="lens_physics_custom_preset_saved">Préréglage personnalisé enregistré</string>
```

In `app/src/main/res/values-hi/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">मूवमेंट फील</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">मूवमेंट फील</string>
    <string name="lens_physics_save_custom_preset_description">मौजूदा सेटिंग्स को कस्टम प्रीसेट के रूप में सहेजें</string>
    <string name="lens_physics_custom_preset_saved">कस्टम प्रीसेट सहेजा गया</string>
```

In `app/src/main/res/values-in/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Nuansa gerakan</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Nuansa gerakan</string>
    <string name="lens_physics_save_custom_preset_description">Simpan pengaturan saat ini sebagai preset Kustom</string>
    <string name="lens_physics_custom_preset_saved">Preset Kustom disimpan</string>
```

In `app/src/main/res/values-it/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Sensazione di movimento</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Sensazione di movimento</string>
    <string name="lens_physics_save_custom_preset_description">Salva le impostazioni attuali come preset personalizzato</string>
    <string name="lens_physics_custom_preset_saved">Preset personalizzato salvato</string>
```

In `app/src/main/res/values-ja/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">動きの感触</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">動きの感触</string>
    <string name="lens_physics_save_custom_preset_description">現在の設定をカスタムプリセットとして保存</string>
    <string name="lens_physics_custom_preset_saved">カスタムプリセットを保存しました</string>
```

In `app/src/main/res/values-km/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">អារម្មណ៍នៃចលនា</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">អារម្មណ៍នៃចលនា</string>
    <string name="lens_physics_save_custom_preset_description">រក្សាទុកការកំណត់បច្ចុប្បន្នជាការកំណត់ជាមុនតាមបំណង</string>
    <string name="lens_physics_custom_preset_saved">បានរក្សាទុកការកំណត់ជាមុនតាមបំណង</string>
```

In `app/src/main/res/values-ko/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">움직임 느낌</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">움직임 느낌</string>
    <string name="lens_physics_save_custom_preset_description">현재 설정을 사용자 지정 프리셋으로 저장</string>
    <string name="lens_physics_custom_preset_saved">사용자 지정 프리셋이 저장되었습니다</string>
```

In `app/src/main/res/values-lo/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">ຄວາມຮູ້ສຶກການເຄື່ອນໄຫວ</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">ຄວາມຮູ້ສຶກການເຄື່ອນໄຫວ</string>
    <string name="lens_physics_save_custom_preset_description">ບັນທຶກການຕັ້ງຄ່າປັດຈຸບັນເປັນພຣີເຊັດແບບກຳນົດເອງ</string>
    <string name="lens_physics_custom_preset_saved">ບັນທຶກພຣີເຊັດແບບກຳນົດເອງແລ້ວ</string>
```

In `app/src/main/res/values-pt/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Sensação de movimento</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Sensação de movimento</string>
    <string name="lens_physics_save_custom_preset_description">Salvar configurações atuais como predefinição personalizada</string>
    <string name="lens_physics_custom_preset_saved">Predefinição personalizada salva</string>
```

In `app/src/main/res/values-ru/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">Ощущение движения</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Ощущение движения</string>
    <string name="lens_physics_save_custom_preset_description">Сохранить текущие настройки как пользовательский пресет</string>
    <string name="lens_physics_custom_preset_saved">Пользовательский пресет сохранён</string>
```

In `app/src/main/res/values-th/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">ความรู้สึกการเคลื่อนไหว</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">ความรู้สึกการเคลื่อนไหว</string>
    <string name="lens_physics_save_custom_preset_description">บันทึกการตั้งค่าปัจจุบันเป็นพรีเซ็ตกำหนดเอง</string>
    <string name="lens_physics_custom_preset_saved">บันทึกพรีเซ็ตกำหนดเองแล้ว</string>
```

In `app/src/main/res/values-vi/strings.xml`, find (line 198):

```xml
    <string name="setting_lens_physics_presets">Cảm giác chuyển động</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">Cảm giác chuyển động</string>
    <string name="lens_physics_save_custom_preset_description">Lưu cài đặt hiện tại thành preset Tùy chỉnh</string>
    <string name="lens_physics_custom_preset_saved">Đã lưu preset Tùy chỉnh</string>
```

In `app/src/main/res/values-zh/strings.xml`, find (line 182):

```xml
    <string name="setting_lens_physics_presets">移动手感</string>
```

Replace with:

```xml
    <string name="setting_lens_physics_presets">移动手感</string>
    <string name="lens_physics_save_custom_preset_description">将当前设置保存为自定义预设</string>
    <string name="lens_physics_custom_preset_saved">自定义预设已保存</string>
```

- [ ] **Step 3: Verify translation coverage**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.a11y.LensStringTranslationTest"`
Expected: PASS, all 4 tests green (every locale has both new keys, placeholders match — neither
new string has a placeholder — and neither is left as the English text; `lens_physics_preset_custom`
is correctly excluded from this check because it's `translatable="false"`, matching its three
sibling preset-name strings).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values*/strings.xml
git commit -m "feat(fish-015): localize custom lens physics preset strings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Save icon drawable + layout row

**Files:**
- Create: `app/src/main/res/drawable/ic_save_24dp.xml`
- Modify: `app/src/main/res/layout/frm_lens.xml:384-437`

**Interfaces:**
- Consumes: `R.string.lens_physics_preset_custom`,
  `R.string.lens_physics_save_custom_preset_description` (from Task 3).
- Produces (used by Task 5 `FrmLens.kt`): view ids `R.id.btnPresetCustom`,
  `R.id.btnSaveCustomPreset`.

- [ ] **Step 1: Create the save icon vector drawable**

Create `app/src/main/res/drawable/ic_save_24dp.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z"/>
</vector>
```

- [ ] **Step 2: Add the Custom + Save button row to the layout**

In `app/src/main/res/layout/frm_lens.xml`, find the closing of the existing preset row (the
`</LinearLayout>` right after `btnPresetSnappy`, still inside the presets `MaterialCardView`):

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnPresetSnappy"
                        style="?attr/materialButtonOutlinedStyle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="4dp"
                        android:insetTop="0dp"
                        android:insetBottom="0dp"
                        android:paddingHorizontal="0dp"
                        android:minWidth="0dp"
                        android:maxLines="1"
                        android:ellipsize="end"
                        android:textSize="13sp"
                        android:text="@string/lens_physics_preset_snappy" />

                </LinearLayout>

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>
```

Replace with:

```xml
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnPresetSnappy"
                        style="?attr/materialButtonOutlinedStyle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="4dp"
                        android:insetTop="0dp"
                        android:insetBottom="0dp"
                        android:paddingHorizontal="0dp"
                        android:minWidth="0dp"
                        android:maxLines="1"
                        android:ellipsize="end"
                        android:textSize="13sp"
                        android:text="@string/lens_physics_preset_snappy" />

                </LinearLayout>

                <!-- FISH-015: the single user-saved custom preset. Tap applies it (disabled
                     until the user has saved at least once); the icon-only button next to it
                     captures the three sliders above into this slot. -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnPresetCustom"
                        style="?attr/materialButtonOutlinedStyle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginEnd="4dp"
                        android:insetTop="0dp"
                        android:insetBottom="0dp"
                        android:paddingHorizontal="0dp"
                        android:minWidth="0dp"
                        android:maxLines="1"
                        android:ellipsize="end"
                        android:textSize="13sp"
                        android:text="@string/lens_physics_preset_custom" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnSaveCustomPreset"
                        style="?attr/materialButtonOutlinedStyle"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="4dp"
                        android:insetTop="0dp"
                        android:insetBottom="0dp"
                        android:minWidth="0dp"
                        android:paddingHorizontal="12dp"
                        app:icon="@drawable/ic_save_24dp"
                        app:iconPadding="0dp"
                        android:contentDescription="@string/lens_physics_save_custom_preset_description" />

                </LinearLayout>

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: Confirm the layout still inflates**

Run: `./gradlew lintDevDebug`
Expected: 0 new errors (unchanged warning count from before this task; `frm_lens.xml` inflates
cleanly — a broken layout XML fails lint, not just a build).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/drawable/ic_save_24dp.xml app/src/main/res/layout/frm_lens.xml
git commit -m "feat(fish-015): add Custom preset button row to lens physics settings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Wire `FrmLens.kt` + widget tests

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt` (whole file, see exact line ranges
  in steps below)
- Test: Create `app/src/androidTest/java/com/mckimquyen/ui/FrmLensCustomPresetWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getCustomDistortionFactor/saveCustomDistortionFactor/
  getCustomScaleFactor/saveCustomScaleFactor/getCustomAnimationTime/saveCustomAnimationTime/
  hasCustomPreset` (Task 2), `R.id.btnPresetCustom`/`R.id.btnSaveCustomPreset` (Task 4),
  `R.string.lens_physics_custom_preset_saved` (Task 3).
- Produces: nothing further tasks consume directly (Task 6 exercises `UtilSettings` through the
  real `ActHome` lens-management flow, not through `FrmLens`).

- [ ] **Step 1: Write the failing widget tests**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmLensCustomPresetWidgetTest.kt`:

```kotlin
package com.mckimquyen.ui

import android.content.Context
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.util.LensPhysicsPreset
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-015: the Custom preset button must start disabled, become usable only after Save is
 * tapped, and then apply the exact three values that were showing on the sliders at save time -
 * not the fixed [LensPhysicsPreset] values, which is exactly what an implementation that
 * accidentally reused [LensPhysicsPreset.STANDARD] instead of the saved value would still pass
 * if these tests only checked "did something get applied".
 */
@RunWith(AndroidJUnit4::class)
class FrmLensCustomPresetWidgetTest {

    private val workLens = "work-lens-custom"

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    private fun clearState() {
        settings.deleteLensSettings(workLens)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            .remove(UtilSettings.KEY_DISTORTION_FACTOR)
            .remove(UtilSettings.KEY_SCALE_FACTOR)
            .remove(UtilSettings.KEY_ANIMATION_TIME)
            .remove(UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR)
            .remove(UtilSettings.KEY_CUSTOM_SCALE_FACTOR)
            .remove(UtilSettings.KEY_CUSTOM_ANIMATION_TIME)
            .apply()
    }

    private fun activate(lensId: String) {
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
    }

    @Test
    fun customButton_startsDisabled() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val button = fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom)
            assertFalse("Custom must be disabled before any save", button.isEnabled)
        }
        scenario.close()
    }

    @Test
    fun saveButton_capturesCurrentSlidersAndEnablesCustom() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
            fragment.requireView().findViewById<MaterialButton>(R.id.btnSaveCustomPreset).performClick()
        }

        assertTrue(settings.hasCustomPreset())
        assertEquals(
            LensPhysicsPreset.SNAPPY.distortionFactor,
            settings.getCustomDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
        assertEquals(LensPhysicsPreset.SNAPPY.scaleFactor, settings.getCustomScaleFactor(), 0.001f)
        assertEquals(LensPhysicsPreset.SNAPPY.animationTimeMs, settings.getCustomAnimationTime())

        scenario.onFragment { fragment ->
            val button = fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom)
            assertTrue("Custom must be enabled immediately after a save", button.isEnabled)
        }
        scenario.close()
    }

    @Test
    fun customButton_appliesTheExactSavedTriple() {
        settings.saveCustomDistortionFactor(
            com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID,
            3.7f
        )
        settings.saveCustomScaleFactor(1.6f)
        settings.saveCustomAnimationTime(275L)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom).performClick()
        }

        assertEquals(
            3.7f,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        assertEquals(1.6f, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR), 0.001f)
        assertEquals(275L, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))

        scenario.onFragment { fragment ->
            val slider = fragment.requireView().findViewById<Slider>(R.id.sbDistortionFactor)
            assertEquals(3.7f, slider.value, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun customDistortion_isIsolatedPerLens() {
        activate(workLens)
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
            fragment.requireView().findViewById<MaterialButton>(R.id.btnSaveCustomPreset).performClick()
        }

        assertEquals(
            "Saving Custom on the work lens must not create a base override",
            LensPhysicsPreset.SNAPPY.distortionFactor,
            settings.getCustomDistortionFactor(workLens),
            0.001f
        )
        assertEquals(
            "The default lens must still fall back cleanly (no custom saved for it)",
            settings.getDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            settings.getCustomDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
        scenario.close()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
./gradlew assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.FrmLensCustomPresetWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: FAIL — `R.id.btnPresetCustom`/`R.id.btnSaveCustomPreset` unresolved (before Task 4) or,
if Task 4 already landed, `NullPointerException`/`IllegalStateException` because `FrmLens.kt`
does not yet find or wire those views.

- [ ] **Step 3: Add the new view fields**

In `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`, find:

```kotlin
    private var btnPresetGentle: MaterialButton? = null
    private var btnPresetStandard: MaterialButton? = null
    private var btnPresetSnappy: MaterialButton? = null
```

Replace with:

```kotlin
    private var btnPresetGentle: MaterialButton? = null
    private var btnPresetStandard: MaterialButton? = null
    private var btnPresetSnappy: MaterialButton? = null
    private var btnPresetCustom: MaterialButton? = null
    private var btnSaveCustomPreset: MaterialButton? = null
```

- [ ] **Step 4: Wire the views and click listeners, refactor `applyPreset`**

Find:

```kotlin
        btnPresetGentle = view.findViewById(R.id.btnPresetGentle)
        btnPresetStandard = view.findViewById(R.id.btnPresetStandard)
        btnPresetSnappy = view.findViewById(R.id.btnPresetSnappy)
```

Replace with:

```kotlin
        btnPresetGentle = view.findViewById(R.id.btnPresetGentle)
        btnPresetStandard = view.findViewById(R.id.btnPresetStandard)
        btnPresetSnappy = view.findViewById(R.id.btnPresetSnappy)
        btnPresetCustom = view.findViewById(R.id.btnPresetCustom)
        btnSaveCustomPreset = view.findViewById(R.id.btnSaveCustomPreset)
```

Find:

```kotlin
        btnPresetGentle?.setOnClickListener { applyPreset(LensPhysicsPreset.GENTLE) }
        btnPresetStandard?.setOnClickListener { applyPreset(LensPhysicsPreset.STANDARD) }
        btnPresetSnappy?.setOnClickListener { applyPreset(LensPhysicsPreset.SNAPPY) }
        btnShareLens = view.findViewById(R.id.btnShareLens)
        btnShareLens?.setOnClickListener { shareLensImage() }
```

Replace with:

```kotlin
        btnPresetGentle?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.GENTLE.distortionFactor,
                LensPhysicsPreset.GENTLE.scaleFactor,
                LensPhysicsPreset.GENTLE.animationTimeMs
            )
        }
        btnPresetStandard?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.STANDARD.distortionFactor,
                LensPhysicsPreset.STANDARD.scaleFactor,
                LensPhysicsPreset.STANDARD.animationTimeMs
            )
        }
        btnPresetSnappy?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.SNAPPY.distortionFactor,
                LensPhysicsPreset.SNAPPY.scaleFactor,
                LensPhysicsPreset.SNAPPY.animationTimeMs
            )
        }
        btnPresetCustom?.setOnClickListener {
            utilSettings?.let { us ->
                applyPreset(
                    us.getCustomDistortionFactor(activeLensId),
                    us.getCustomScaleFactor(),
                    us.getCustomAnimationTime()
                )
            }
        }
        btnSaveCustomPreset?.setOnClickListener { saveCurrentAsCustomPreset() }
        btnShareLens = view.findViewById(R.id.btnShareLens)
        btnShareLens?.setOnClickListener { shareLensImage() }
```

Find:

```kotlin
    /**
     * FISH-004: applies a named preset to the 3 sliders above in one tap - safe/instant, and
     * fully reversible (the sliders remain fine-tunable afterward, same as after Reset to
     * Default, which is itself just the STANDARD preset applied from a different entry point).
     */
    private fun applyPreset(preset: LensPhysicsPreset) {
        utilSettings?.let { us ->
            us.saveDistortionFactor(activeLensId, preset.distortionFactor)
            us.save(UtilSettings.KEY_SCALE_FACTOR, preset.scaleFactor)
            us.save(UtilSettings.KEY_ANIMATION_TIME, preset.animationTimeMs)
        }
        assignValues()
        lensViewsSettings?.invalidate()
    }
```

Replace with:

```kotlin
    /**
     * FISH-004/FISH-015: applies a (distortion, scale, animation-time) triple to the 3 sliders
     * above in one tap - safe/instant, and fully reversible (the sliders remain fine-tunable
     * afterward, same as after Reset to Default). Takes raw values rather than a
     * [LensPhysicsPreset] so the user-saved Custom preset (not a compile-time constant) can reuse
     * this exact same path.
     */
    private fun applyPreset(distortion: Float, scale: Float, animationTimeMs: Long) {
        utilSettings?.let { us ->
            us.saveDistortionFactor(activeLensId, distortion)
            us.save(UtilSettings.KEY_SCALE_FACTOR, scale)
            us.save(UtilSettings.KEY_ANIMATION_TIME, animationTimeMs)
        }
        assignValues()
        lensViewsSettings?.invalidate()
    }

    /** FISH-015: captures the 3 sliders' current live values into the single Custom slot. */
    private fun saveCurrentAsCustomPreset() {
        utilSettings?.let { us ->
            val distortion = sbDistortionFactor?.value ?: return
            val scale = sbScaleFactor?.value ?: return
            val animationTimeMs = sbAnimationTime?.value?.toLong() ?: return
            us.saveCustomDistortionFactor(activeLensId, distortion)
            us.saveCustomScaleFactor(scale)
            us.saveCustomAnimationTime(animationTimeMs)
        }
        refreshCustomPresetButtonState()
        android.widget.Toast.makeText(
            requireContext(),
            R.string.lens_physics_custom_preset_saved,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /** FISH-015: Custom is only usable once at least one save has ever happened. */
    private fun refreshCustomPresetButtonState() {
        btnPresetCustom?.isEnabled = utilSettings?.hasCustomPreset() == true
    }
```

- [ ] **Step 5: Refresh the button state whenever sliders/active lens refresh**

Find:

```kotlin
            val animTime = us.getLong(UtilSettings.KEY_ANIMATION_TIME).toFloat()
            val validAnim = animTime.coerceIn(100.0f, 400.0f)
            sbAnimationTime?.value = validAnim
            tvValueAnimationTime?.text = getString(R.string.unit_ms_format, validAnim.toLong())
        }
    }
```

Replace with:

```kotlin
            val animTime = us.getLong(UtilSettings.KEY_ANIMATION_TIME).toFloat()
            val validAnim = animTime.coerceIn(100.0f, 400.0f)
            sbAnimationTime?.value = validAnim
            tvValueAnimationTime?.text = getString(R.string.unit_ms_format, validAnim.toLong())
        }
        refreshCustomPresetButtonState()
    }
```

- [ ] **Step 6: Null the new fields in `onDestroyView`**

Find:

```kotlin
        btnPresetGentle = null
        btnPresetStandard = null
        btnPresetSnappy = null
        utilSettings = null
```

Replace with:

```kotlin
        btnPresetGentle = null
        btnPresetStandard = null
        btnPresetSnappy = null
        btnPresetCustom = null
        btnSaveCustomPreset = null
        utilSettings = null
```

- [ ] **Step 7: Run the widget tests to verify they pass**

Run the same `adb shell am instrument` command from Step 2.
Expected: PASS, all 4 tests in `FrmLensCustomPresetWidgetTest` green.

- [ ] **Step 8: Run the two pre-existing preset widget test files to confirm no regression**

Run:
```
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.FrmLensPhysicsPresetsWidgetTest,com.mckimquyen.ui.FrmLensPerLensWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS, all tests in both files green (the `applyPreset` signature refactor must not
change Gentle/Standard/Snappy's observable behavior).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/FrmLens.kt \
        app/src/androidTest/java/com/mckimquyen/ui/FrmLensCustomPresetWidgetTest.kt
git commit -m "feat(fish-015): wire Custom preset save/apply into FrmLens

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Integration coverage — lens create/delete carries the Custom preset

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.getCustomDistortionFactor/saveCustomDistortionFactor` (Task 2), the
  file's own existing `dao`, `settings`, `seedSecondLens()`, `openMenuAndSelect()`,
  `confirmNameDialog()`, `confirmDialog()`, `pageToSecondLens()`, `idle()` helpers (unchanged).
- Produces: nothing further tasks consume.

- [ ] **Step 1: Extend `clearLensPrefs()` to also strip the new custom-distortion keys**

Find:

```kotlin
    private fun clearLensPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val stale = prefs.all.keys.filter {
            it.startsWith("${UtilSettings.KEY_DISTORTION_FACTOR}_") ||
                it.startsWith("${UtilSettings.KEY_SMART_FOCUS_BIAS}_")
        }
        prefs.edit().apply {
            stale.forEach { remove(it) }
            remove(UtilSettings.KEY_SMART_FOCUS_BIAS)
            remove(UtilSettings.KEY_ACTIVE_LENS_ID)
        }.commit()
    }
```

Replace with:

```kotlin
    private fun clearLensPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val stale = prefs.all.keys.filter {
            it.startsWith("${UtilSettings.KEY_DISTORTION_FACTOR}_") ||
                it.startsWith("${UtilSettings.KEY_SMART_FOCUS_BIAS}_") ||
                it.startsWith("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_")
        }
        prefs.edit().apply {
            stale.forEach { remove(it) }
            remove(UtilSettings.KEY_SMART_FOCUS_BIAS)
            remove(UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR)
            remove(UtilSettings.KEY_CUSTOM_SCALE_FACTOR)
            remove(UtilSettings.KEY_CUSTOM_ANIMATION_TIME)
            remove(UtilSettings.KEY_ACTIVE_LENS_ID)
        }.commit()
    }
```

- [ ] **Step 2: Extend the add-lens test to also assert the Custom override carries over**

Find:

```kotlin
    @Test
    fun addLens_copiesTheSourceLensSettingsNotJustItsLayout() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemAdd, position = 1)
            confirmNameDialog(scenario, "Copy")

            val created = runBlocking { dao.getAll() }.find { it.name == "Copy" }
            assertNotNull("The new lens must exist", created)
            assertEquals(
                "The new lens must inherit its source's curvature",
                4.5f,
                settings.getDistortionFactor(created!!.id),
                0.001f
            )
            assertTrue(
                "The new lens must inherit its source's Smart Focus state",
                settings.isSmartFocusBias(created.id)
            )
        }
    }
```

Replace with:

```kotlin
    @Test
    fun addLens_copiesTheSourceLensSettingsNotJustItsLayout() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)
        settings.saveCustomDistortionFactor("second", 3.3f)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemAdd, position = 1)
            confirmNameDialog(scenario, "Copy")

            val created = runBlocking { dao.getAll() }.find { it.name == "Copy" }
            assertNotNull("The new lens must exist", created)
            assertEquals(
                "The new lens must inherit its source's curvature",
                4.5f,
                settings.getDistortionFactor(created!!.id),
                0.001f
            )
            assertTrue(
                "The new lens must inherit its source's Smart Focus state",
                settings.isSmartFocusBias(created.id)
            )
            assertEquals(
                "FISH-015: the new lens must also inherit its source's Custom distortion override",
                3.3f,
                settings.getCustomDistortionFactor(created.id),
                0.001f
            )
        }
    }
```

- [ ] **Step 3: Extend the delete-lens test to also assert the Custom override is cleared**

Find:

```kotlin
    @Test
    fun deleteLens_alsoClearsThatLensOwnSettings() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemDelete, position = 1)
            confirmDialog(scenario)

            assertNull(
                "The lens row must be gone",
                runBlocking { dao.getAll() }.find { it.id == "second" }
            )
            // Its prefs must not linger and re-attach to a future lens reusing the id.
            assertEquals(
                "A deleted lens's curvature override must be gone, falling back to the shared value",
                2.0f,
                settings.getDistortionFactor("second"),
                0.001f
            )
            assertFalse(settings.isSmartFocusBias("second"))
        }
    }
```

Replace with:

```kotlin
    @Test
    fun deleteLens_alsoClearsThatLensOwnSettings() {
        seedSecondLens()
        settings.saveDistortionFactor("second", 4.5f)
        settings.saveSmartFocusBias("second", true)
        settings.saveCustomDistortionFactor("second", 3.3f)
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            pageToSecondLens(scenario)

            openMenuAndSelect(scenario, itemDelete, position = 1)
            confirmDialog(scenario)

            assertNull(
                "The lens row must be gone",
                runBlocking { dao.getAll() }.find { it.id == "second" }
            )
            // Its prefs must not linger and re-attach to a future lens reusing the id.
            assertEquals(
                "A deleted lens's curvature override must be gone, falling back to the shared value",
                2.0f,
                settings.getDistortionFactor("second"),
                0.001f
            )
            assertFalse(settings.isSmartFocusBias("second"))
            assertEquals(
                "FISH-015: a deleted lens's Custom override must be gone too",
                settings.getDistortionFactor("second"),
                settings.getCustomDistortionFactor("second"),
                0.001f
            )
        }
    }
```

- [ ] **Step 4: Run the two extended tests to verify they pass**

Run:
```
adb -s <locked-device-serial> shell am instrument -w \
  -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest#addLens_copiesTheSourceLensSettingsNotJustItsLayout,com.mckimquyen.ui.ActHomeLensManagementWidgetTest#deleteLens_alsoClearsThatLensOwnSettings \
  com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS, both tests green.

- [ ] **Step 5: Run the whole file to confirm no regression**

Run:
```
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS, every test in the file green.

- [ ] **Step 6: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt
git commit -m "test(fish-015): cover custom preset carry-over on lens create/delete

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Full regression, Tecno smoke, and closing the backlog record

**Files:**
- Modify: `doc/task/README.md` (move FISH-015 from Picked to Implemented)
- Move: `doc/task/todo/p2-fish-fish-015-custom-physics-presets.md` →
  `doc/task/done/p2-fish-fish-015-custom-physics-presets.md`

**Interfaces:**
- Consumes: nothing new — this is verification and documentation only.
- Produces: nothing further tasks consume (last task in this plan).

- [ ] **Step 1: Full unit regression**

Run: `./gradlew testDevDebugUnitTest`
Expected: PASS, 0 failures, total test count increased by the 13 new
`UtilSettingsCustomPresetTest` cases over the pre-Task-2 baseline.

- [ ] **Step 2: Lint**

Run: `./gradlew lintDevDebug`
Expected: 0 errors, warning count unchanged from before this feature (no new resource/code lint
issues introduced).

- [ ] **Step 3: Full connected-test regression on the session-locked TECNO device**

Per this repo's device-scoping gotcha, install and run directly against the locked serial rather
than `connectedDevDebugAndroidTest` (which fans out to every attached device):

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
ANDROID_SERIAL=<locked-device-serial> adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
ANDROID_SERIAL=<locked-device-serial> adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: PASS on every `Frm Lens*`/`ActHomeLensManagementWidgetTest`/`ActHomeLensLabel*` class;
record the total pass count and any pre-existing unrelated flake by name (same disclosure
convention every prior story in `doc/task/README.md` uses).

- [ ] **Step 4: Manual smoke on the same device**

1. Open Settings → Lens tab.
2. Drag Distortion, Scale, and Animation Time sliders to three values clearly different from any
   fixed preset.
3. Tap the Save icon — confirm the Toast appears and Custom becomes tappable.
4. Tap Standard, confirm sliders reset. Tap Custom — confirm all three sliders return exactly to
   the values saved in step 2.
5. Create a second lens via the lens-management menu; confirm it opens with the same Custom
   button state (enabled, correct per-lens distortion) as the source lens.
6. Delete that second lens; confirm no crash and the remaining lens's Custom preset is
   unaffected.
7. Screenshot the enabled Custom button state for the story's evidence record.

- [ ] **Step 5: Move the backlog story to done with full evidence**

```bash
git mv doc/task/todo/p2-fish-fish-015-custom-physics-presets.md \
       doc/task/done/p2-fish-fish-015-custom-physics-presets.md
```

Edit the moved file's `Status` field from `todo` to `done`, check every acceptance-criteria and
test-matrix checkbox, and append an evidence paragraph under a new `## Evidence` heading stating:
the exact unit/widget/integration test counts from Steps 1 and 3, the lint result from Step 2,
the device model/serial/Android version/build/timestamp from Step 4, and a self-audit score using
this repo's 8-dimension rubric in `doc/task/README.md`'s "Audit score and push gate" section.

- [ ] **Step 6: Update `doc/task/README.md`**

Move the `FISH-015` line from the `## 📋 Picked` table to a new bullet under `## ✅ Implemented`,
following the exact one-paragraph evidence-summary style every existing bullet in that section
uses (see the `FISH-014` bullet immediately above it for the pattern), and renumber the
remaining Picked rows.

- [ ] **Step 7: Commit**

```bash
git add doc/task/README.md \
        doc/task/done/p2-fish-fish-015-custom-physics-presets.md
git commit -m "docs(fish-015): mark custom lens physics presets as done

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 8: Push, only if the audit score from Step 5 is strictly greater than 9.0/10**

```bash
git push origin dev
```

If the score is `9.0` or below, or any Step 1-4 check failed, stop here and report the specific
failing dimension instead of pushing — per this repo's push gate
(`doc/task/README.md`, "Audit score and push gate" section).
