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
