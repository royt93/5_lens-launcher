# FISH-015 — Save Custom Lens Physics Presets Design

- **Date:** 2026-09-30
- **Status:** Approved
- **Story:** `p2-fish-fish-015-custom-physics-presets`
- **Target branch:** `dev`

## 1. Problem

`FISH-004` shipped three fixed presets (Gentle/Standard/Snappy) that each apply a hardcoded
(distortion, scale, animation-time) triple. A user who fine-tunes the three sliders to their own
liking has no way to return to that exact combination later except by re-dragging all three
sliders back manually — the only "memory" today is whichever raw values happen to still be
persisted.

## 2. Goals

- Let the user save the current slider values as one reusable "Custom" preset and re-apply it
  with a single tap, same interaction shape as the existing three fixed presets.
- Exactly one Custom slot (no named multi-preset list) — smallest change that satisfies the
  3 SP estimate.
- Preserve `FISH-004`'s exact scope split: distortion factor is per-lens, scale/animation-time
  are global — the Custom slot follows the same split, not a new one.
- Custom button starts disabled/hidden of meaning until the user has saved at least once; no
  crash or garbage values from tapping it before any save.
- No change to `LensView`/`LensGridCache` rendering or hit-test math — same reasoning `FISH-004`
  already established (a preset is just three `UtilSettings` writes).

## 3. Architecture

### Data — `UtilSettings.kt`

Three new keys, matching the existing key's own scope exactly:

```kotlin
const val KEY_CUSTOM_DISTORTION_FACTOR = "custom_distortion_factor" // per-lens via lensKey()
const val KEY_CUSTOM_SCALE_FACTOR = "custom_scale_factor"           // global
const val KEY_CUSTOM_ANIMATION_TIME = "custom_animation_time"       // global
```

New methods, placed next to the existing per-lens distortion block:

```kotlin
fun getCustomDistortionFactor(lensId: String?): Float
fun saveCustomDistortionFactor(lensId: String?, value: Float)
fun getCustomScaleFactor(): Float
fun saveCustomScaleFactor(value: Float)
fun getCustomAnimationTime(): Long
fun saveCustomAnimationTime(value: Long)
fun hasCustomPreset(): Boolean
```

- `getCustomDistortionFactor`/`saveCustomDistortionFactor` mirror `getDistortionFactor`/
  `saveDistortionFactor`: same `lensKey()` helper and same validation/clamp
  (`MIN_DISTORTION_FACTOR`..`MAX_DISTORTION_FACTOR/2f+MIN_DISTORTION_FACTOR`). A lens with no
  custom override reads the un-suffixed base custom key when it exists; if no base custom value
  exists either (for example, the first save happened on a non-default lens), it falls back to
  that lens's current effective distortion via `getDistortionFactor(lensId)`. This avoids
  inventing a Custom distortion for a lens the user never configured.
- `getCustomScaleFactor`/`saveCustomScaleFactor` and `getCustomAnimationTime`/
  `saveCustomAnimationTime` are plain global reads/writes (same shape as `KEY_SCALE_FACTOR`/
  `KEY_ANIMATION_TIME` today), with the same clamp ranges as their non-custom counterparts.
- `hasCustomPreset()` returns `prefs.contains(KEY_CUSTOM_SCALE_FACTOR)`. Saving always writes all
  three keys together in one call, so this single global key is a reliable "has the user ever
  saved a Custom preset" signal regardless of which lens was active at save time.

Existing lifecycle methods extended, not replaced:

- `deleteLensSettings(lensId)` also removes `"${KEY_CUSTOM_DISTORTION_FACTOR}_$lensId"`.
- `duplicateLensSettings(fromLensId, toLensId)` also materializes
  `saveCustomDistortionFactor(toLensId, getCustomDistortionFactor(fromLensId))` — same
  "copy the effective value, override or fallback, onto the new lens" behavior the method
  already applies to `KEY_DISTORTION_FACTOR`.

### UI — `frm_lens.xml`

The existing Gentle/Standard/Snappy row is untouched. A new row is added directly below it,
inside the same `MaterialCardView`:

```
[btnPresetCustom  (weight=1, outlined style, text)] [btnSaveCustomPreset (icon-only, wrap_content)]
```

- `btnPresetCustom` — same `?attr/materialButtonOutlinedStyle` as the three existing buttons,
  text `@string/lens_physics_preset_custom` ("Custom"). Disabled (`enabled=false`, dimmed by the
  Material style automatically) whenever `!hasCustomPreset()`.
- `btnSaveCustomPreset` — icon-only `MaterialButton` (`app:icon="@drawable/ic_save_24dp"`, no
  text, `minWidth="0dp"`), `contentDescription`
  `@string/lens_physics_save_custom_preset_description`. New vector drawable
  `ic_save_24dp.xml`, same style as the existing `ic_share_24dp.xml` (24dp, single white
  `fillColor` path, tinted by the button style like every other icon button in this file).

### Logic — `FrmLens.kt`

- `applyPreset(preset: LensPhysicsPreset)` is refactored to `applyPreset(distortion: Float,
  scale: Float, animationTimeMs: Long)`. The three existing preset click listeners pass
  `preset.distortionFactor/.scaleFactor/.animationTimeMs` explicitly; body is unchanged
  otherwise. This lets the Custom button reuse the exact same apply path without a fake
  `LensPhysicsPreset` enum entry (enum values are compile-time constants, unusable for a
  user-saved value).
- `btnPresetCustom.setOnClickListener` calls
  `applyPreset(us.getCustomDistortionFactor(activeLensId), us.getCustomScaleFactor(),
  us.getCustomAnimationTime())`.
- `btnSaveCustomPreset.setOnClickListener` reads the three live slider values (`sbDistortionFactor
  ?.value`, `sbScaleFactor?.value`, `sbAnimationTime?.value`), calls the three custom save
  methods, shows a `Toast` (`@string/lens_physics_custom_preset_saved`), then calls
  `refreshCustomPresetButtonState()`.
- New private `refreshCustomPresetButtonState()`: `btnPresetCustom?.isEnabled =
  utilSettings?.hasCustomPreset() == true`. Called from `assignValues()` (covers both initial
  `onViewCreated` and every `refreshActiveLens()`/`onResume()` call already made there) — no new
  call site needed since `assignValues()` already runs whenever the active lens could have
  changed.
- `onDestroyView()` nulls the two new view fields (`btnPresetCustom`, `btnSaveCustomPreset`),
  same pattern as every other field in that method.

## 4. Behavior

- **Never saved:** Custom button visible but disabled/dimmed; tapping it does nothing (disabled
  buttons don't fire `OnClickListener`).
- **First save:** tapping the Save icon captures the three current slider values, Custom button
  becomes enabled immediately (same fragment instance, no restart needed).
- **Apply Custom:** identical semantics to Gentle/Standard/Snappy — writes through the same
  `UtilSettings` keys the sliders already read, sliders/UI refresh via `assignValues()`,
  `lensViewsSettings?.invalidate()`.
- **Per-lens distortion:** saving Custom on lens A, switching to lens B (which has never saved
  its own Custom distortion), tapping Custom on B applies B's own live distortion at time of
  last save if B ever saved one, otherwise falls back to A's (or whichever lens's) saved base
  custom distortion — same fallback shape `FISH-008 Phase 3` already established for the
  non-custom distortion key.
- **Scale/animation-time:** always the single global Custom value regardless of active lens,
  matching how the fixed presets already behave for these two fields.
- **Delete/duplicate lens:** existing lens-management flows (`ActHome` create/delete) carry the
  per-lens custom distortion the same way they already carry the regular per-lens distortion —
  no new call site, the extended `UtilSettings` methods absorb this.
- **Reset to Default (`onDefaultsReset`)** is untouched: it still only writes the `STANDARD`
  values, leaving the saved Custom slot alone (same relationship the three fixed presets already
  have with Reset).

## 5. Tests

### Unit — extend `UtilSettingsTest` (or a focused new test class if that file doesn't already
exist for this area)

1. `getCustomDistortionFactor` before any save returns the same default `getDistortionFactor`
   would.
2. `saveCustomDistortionFactor(lensA, x)` then `getCustomDistortionFactor(lensA)` round-trips `x`.
3. A second lens with no per-lens override falls back to the base custom key's value.
4. `getCustomScaleFactor`/`getCustomAnimationTime` round-trip and clamp the same way their
   non-custom counterparts do.
5. `hasCustomPreset()` is `false` before any save, `true` after `saveCustomScaleFactor` (the
   representative key) is written.
6. `deleteLensSettings(lensId)` removes that lens's custom distortion override.
7. `duplicateLensSettings(from, to)` copies the effective custom distortion value onto `to`.

### Widget — `FrmLensCustomPresetWidgetTest`

1. `btnPresetCustom` starts disabled on a fresh fragment.
2. Tapping `btnSaveCustomPreset` after moving all three sliders enables `btnPresetCustom` and
   persists the exact three values.
3. Tapping `btnPresetCustom` after a save applies the exact saved (distortion, scale,
   animation-time) triple to the sliders and to `UtilSettings`.
4. Switching active lens (mirroring `FrmLensPerLensWidgetTest`'s existing setup) shows each
   lens's own custom distortion, sharing the same custom scale/animation-time.

### Integration — extend the existing lens-management integration coverage (real
`SharedPreferences` + real create/delete lens flow, same pattern `FrmLensPerLensWidgetTest`/
`ActHomeLensManagementWidgetTest` already use)

1. Save Custom on lens A, create lens B via the real management menu, confirm B inherits A's
   effective custom distortion (via `duplicateLensSettings`).
2. Delete lens A, confirm its custom distortion key is gone from `SharedPreferences` (no leak).

### Smoke (session-locked device)

1. Drag all three sliders to distinct non-default values.
2. Tap Save — confirm the enable transition and the Toast.
3. Tap Standard, then tap Custom — confirm sliders return exactly to the saved values.
4. Switch to a second lens, verify Custom applies that lens's own distortion but the same
   shared scale/animation-time.

## 6. Non-Goals

- Multiple named custom presets or a preset list/picker UI.
- Renaming or deleting the Custom slot independently (only overwritten by Save).
- Any change to `LensPhysicsPreset` enum, `LensView`, or `LensGridCache`.
- Migrating existing users' current slider values into the Custom slot automatically — the slot
  starts genuinely empty/disabled for everyone until they explicitly tap Save.
