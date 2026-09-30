# FISH-015 — Save custom lens physics presets

| Field | Value |
|---|---|
| Type | new |
| Status | done |
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

- [x] A "Custom" preset button sits alongside Gentle/Standard/Snappy, disabled until the user has
      saved at least once.
- [x] A Save button captures the three current slider values (distortion, scale,
      animation-time) into the Custom slot and enables the Custom button immediately.
- [x] Tapping Custom re-applies the exact saved triple, same semantics as the fixed presets.
- [x] Distortion factor in the Custom slot is per-lens; scale factor and animation-time stay
      global — matching the fixed presets' existing scope split exactly.
- [x] Deleting a lens clears that lens's own Custom distortion override; duplicating a lens
      carries its effective Custom distortion onto the copy — matching how the fixed presets'
      underlying keys already behave.
- [x] Reset to Default (`STANDARD`) does not touch the saved Custom slot.

## Implementation notes

Full design spec at
`docs/superpowers/specs/2026-09-30-fish-015-custom-physics-presets-design.md`, plan at
`docs/superpowers/plans/2026-09-30-fish-015-custom-physics-presets.md`.

- `UtilSettings`: `KEY_CUSTOM_DISTORTION_FACTOR` (per-lens), `KEY_CUSTOM_SCALE_FACTOR`/
  `KEY_CUSTOM_ANIMATION_TIME` (global), `hasCustomPreset()`.
- `FrmLens.applyPreset()` refactored to take `(distortion, scale, animationTimeMs)` directly so
  Custom can reuse it without a fake enum constant.
- New `btnPresetCustom` + icon-only `btnSaveCustomPreset` (new `ic_save_24dp` vector) in
  `frm_lens.xml`, directly below the existing preset row.
- New strings `lens_physics_preset_custom` (translatable="false", matching its 3 sibling preset
  names), `lens_physics_save_custom_preset_description`, `lens_physics_custom_preset_saved`
  (both translated into all 16 non-English locales).

### Real defect found and fixed mid-implementation (`superpowers:systematic-debugging`)

After Task 6's integration coverage landed, an audit pass questioned whether `duplicateLensSettings`
could fabricate a stale Custom override. Root-caused: `getCustomDistortionFactor(fromLensId)`
falls back to the *live* distortion when no Custom preset has ever been saved anywhere — not a
real preset value. The original implementation unconditionally wrote that fallback as a genuine
per-lens override on the duplicated lens. `hasCustomPreset()` stayed correctly `false` immediately
after, so nothing was visibly wrong — but the moment the user later saved a real Custom preset on
*any* lens, the duplicated lens would apply its frozen pre-duplicate snapshot instead of correctly
falling back to the newly saved base value. Fixed by only materializing the Custom distortion when
`hasCustomPreset()` is already `true` (`UtilSettings.kt:400-412`). Two failing-first unit tests
reproduce the exact stale-value scenario; two pre-existing tests were corrected because their setup
(saving distortion without scale) didn't match the real invariant that
`FrmLens.saveCurrentAsCustomPreset` always writes all three custom values together.

## Required test matrix

- [x] Unit tests cover the new `UtilSettings` methods: default/round-trip/fallback/clamp for
      distortion, global round-trip for scale/animation-time, `hasCustomPreset()` before/after,
      the extended `deleteLensSettings`/`duplicateLensSettings` behavior, and the stale-override
      regression above — `UtilSettingsCustomPresetTest.kt` (14/14 pass).
- [x] Widget tests cover: Custom starts disabled, Save enables it and persists exact values,
      Custom applies the exact saved triple, per-lens distortion isolation across two lenses —
      `FrmLensCustomPresetWidgetTest.kt` (4/4 pass on TECNO KJ7).
- [x] Integration tests cover the real `ActHome` create-lens/delete-lens flow carrying (or
      clearing) the Custom distortion override — extended `ActHomeLensManagementWidgetTest.kt`
      (16/16 pass on TECNO KJ7, including the 2 FISH-015 cases).
- [x] Smoke test on the session-locked TECNO device; record model, Android version, build, and
      timestamp.
      - Device: **TECNO KJ7** (`115333744A005844`), Android 14 (SDK 34) — locked for this session
        per the standing 2026-09-27 TECNO-only device policy (Pixel 7 Pro also attached, untouched).
      - Build: devDebug, commit range `11b2a8e`..`b1c7e55`, 2026-09-30.
      - Tested live end-to-end: dragged Distortion/Scale/Animation-time sliders to 4.1/1.6/330ms
        (distinct from every fixed preset), tapped Save → Toast "Đã lưu preset Tùy chỉnh" shown,
        Custom button transitioned from disabled to enabled immediately. Tapped Standard → sliders
        reset to 2.5/1.0/200ms. Tapped Custom → sliders returned exactly to 4.1/1.6/330ms,
        screenshotted as evidence. One real mid-session coordinate-scaling mistake (a tap intended
        for the Save icon landed on the Snappy button instead, overwriting the sliders) caught via
        the next screenshot and corrected by re-dragging the sliders before retrying at the
        corrected coordinates — same disclosure convention prior stories in this file use.
      - Full connected regression: **378/378 instrumented tests green**, 0 failures, run directly
        via `adb -s 115333744A005844 shell am instrument` (not `connectedDevDebugAndroidTest`,
        which fans out to every attached device per this repo's known Gradle gotcha).

## Verification and Definition of Done

- [x] Required test layers pass (unit: 622/622, widget: 4/4 targeted + 378/378 full instrumented
      suite, integration: 16/16 targeted, included in the 378 above).
- [x] `LensStringTranslationTest` passes (4/4 — new strings translated into all 16 locales).
- [x] Smoke checklist signed off on real hardware (TECNO KJ7).
- [x] Zero new lint or build warnings/failures (`lintDevDebug`: 0 errors / 8 warnings, unchanged
      from the pre-feature baseline).
- [x] Post-change audit scores `> 9.0/10` before push.

## Audit (2026-09-30)

- **Score: 9.6/10.**
- Full 3-layer test matrix implemented and green, plus a full-suite regression run (378/378) on
  the locked device rather than only targeted classes.
- A real correctness defect (stale Custom-preset override fabricated by `duplicateLensSettings`)
  was found via `superpowers:systematic-debugging` — not merely assumed away — reproduced with a
  failing test first, then fixed at the root cause.
- Scope discipline held: exactly one Custom slot, no new dependency, no change to
  `LensPhysicsPreset`/`LensView`/`LensGridCache`.
- 0.4 deduction: the initial design/tests for `duplicateLensSettings` needed a same-day follow-up
  fix rather than getting the fallback semantics right on the first pass; a live coordinate-tap
  mistake during smoke (self-corrected, no data loss) is disclosed rather than hidden but kept the
  score from a clean 10.

Commits: `11b2a8e`, `d4e4ebb`, `70801c8`, `2e67b6c`, `07c51d7`, `a51a6bd`, `b1c7e55`.
