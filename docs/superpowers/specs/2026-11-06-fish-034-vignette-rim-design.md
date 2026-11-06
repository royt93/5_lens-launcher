# FISH-034 — Lens rim vignette — Design

Date: 2026-11-06  
Story: `doc/task/todo/p3-fish-fish-034-chromatic-vignette-rim.md`  
Device: TECNO KJ7 `115333744A005844` only. No push.

## Goal

Add an opt-in vignette: icons far from the lens focus dim smoothly while the lens is magnified, giving the warp a more optical feel. Never change geometry, hit-testing, launch, haptics or `LensGridCache`.

## Decisions (owner, 2026-11-06)

- **Scope: vignette only.** The chromatic-aberration half of the story title is **split out** (recorded in the story and backlog as a separate follow-up) because it multiplies icon draws and endangers the 120 Hz budget (FISH-007 measured full-resolution layers pushing drag p50 from 6 ms to 27 ms).
- **When:** only while the lens is magnified (finger/pan) and faded by the existing lens animation multiplier. Idle grid is unchanged. No vignette for pinned focus (FISH-029) or tilt (FISH-033): they have no live touch, so the transition is 0.
- **How:** per-icon alpha from distance to the touch centre. No `RenderEffect`, no overlay gradient, no `ColorFilter`. One code path for every API level (minSdk 25), so no separate API < 31 fallback is needed; alpha is already the mechanism the DoF fallback uses.
- **Conflict with Depth-of-Field:** when both are enabled and DoF is in `Mode.ALPHA`, take the **lower** of the two alphas per icon (`min`), never multiply. DoF `Mode.BLUR` does not touch alpha, so vignette alpha applies on its own.
- Opt-in switch in Settings, default **off**, included in reset-to-defaults. Clean Lens mode does **not** disable it (vignette is not chrome). Reduced motion (`LensPhysicsPolicy.shouldReduceLensMotion()`, which also covers battery saver and thermal throttling) does **not** turn the effect off either; it makes the transition snap in/out instead of animating, exactly as `DepthOfField.transition` already does.

## Architecture

1. **Pure math in a new `util/LensVignette.kt`** (Context-free, unit-tested):
   - `FLOOR_ALPHA_FRACTION` (icons far from focus never drop below this share of opacity so they stay legible/tappable; start equal to DoF's `MIN_FALLBACK_ALPHA` = 0.5).
   - `intensity(focusDistance, transition)`: reuses `DepthOfField.focusDistance` semantics (0 near focus … 1 farthest); a sharp-zone radius (reuse `DepthOfField.FOCUS_ZONE`) keeps the area around the finger fully opaque; scaled by `transition`.
   - `alpha(intensity)`: `OPAQUE_ALPHA` at intensity 0 down to `OPAQUE_ALPHA * FLOOR_ALPHA_FRACTION` at 1.
   - `combine(dofAlpha, vignetteAlpha)` = `min` of both.
   - `shouldApply(enabled, lensVisible)` gate (reduced motion only affects the transition, not whether the effect applies).
2. **`LensView.drawGrid`**: compute `vignetteTransition` once per frame (same `DepthOfField.transition(animationMultiplier, mReduceMotion)` already computed), and inside the existing per-cell loop set `mPaintIcons.alpha` from `LensVignette.alpha(...)` combined with the DoF fallback alpha. Reuse the already-computed `focusDistance` per cell where DoF is on; otherwise compute it once per cell with the same function. **No new allocation** inside the loop; restore `mPaintIcons.alpha = OPAQUE_ALPHA` after the loop exactly as DoF does.
3. **Setting**: `UtilSettings.KEY_LENS_VIGNETTE = "lens_vignette"`, `DEFAULT_LENS_VIGNETTE = false`, boolean getter wiring in the same `when` that serves `KEY_DEPTH_OF_FIELD`; `ResetEntries` flag entry; `FrmSettings` switch + hint under the Depth-of-field row (copying the existing row structure: `rlSwitchLensVignetteParent`, `swLensVignette`, `tvLensVignetteHint`); nulled in `onDestroyView`.
4. **Strings**: `setting_lens_vignette`, `setting_lens_vignette_hint` in base + all 16 locale folders; `LensStringTranslationTest`-style coverage extended to `setting_*` keys only if an existing test already enforces them, otherwise assert key presence in a small new unit test.
5. **State**: the toggle is read once per lens show/hide in `LensAnimation` (same cadence as `depthOfFieldEnabled`), never per frame, and nulled on detach with the other references.

## Error and edge behavior

- Non-finite `focusDistance`/`transition` inputs return opaque alpha (fail visible, never hide an icon).
- Alpha is clamped to `[floor, 255]`; an icon is never fully transparent, so hit-testing and TalkBack bounds stay meaningful.
- Reduced motion: transition snaps (reuses `DepthOfField.transition`'s rule); battery saver/thermal throttling already feed `shouldReduceLensMotion`.
- Drawing with an unattached view or empty app list is a no-op like today.

## Tests (RED before code)

### Unit (`LensVignetteTest`)
- Fully opaque inside the focus zone and whenever `transition == 0`.
- Monotonic: farther distance never yields higher alpha.
- Floor: alpha at distance 1, transition 1 equals `255 * FLOOR_ALPHA_FRACTION` rounded; never below it.
- `combine` returns the lower alpha and is symmetric; `combine(255, x) == x`.
- Gate truth table (`enabled`, `lensVisible`); reduced motion snaps the transition (covered through `DepthOfField.transition`).
- Non-finite inputs → 255.
- Setting key/default/reset entry present and default false.

### Widget / integration (TECNO)
- Real `LensView` draw with the setting on and a live touch: far icon bitmap draws with reduced alpha, near icon opaque (read back via a small `@VisibleForTesting` last-frame alpha probe following `dofBandCounts`).
- Rects from `drawnRectsForTest()` are **identical** with vignette on and off (geometry untouched); `touchCoordinatesForTest()`/hit-test unchanged.
- Idle lens (no touch) draws fully opaque even with the setting on.
- With DoF fallback alpha forced on, per-icon alpha equals the `min` of both.
- `LensGridCache.recomputeCount` unchanged across vignette frames.
- Settings fragment: switch persists, hint present, reset-to-defaults turns it off.

### Frame budget (acceptance criterion carried from the story)
- Same method as FISH-007: `dumpsys gfxinfo` p50/p90 over a scripted drag on TECNO before and after (setting off vs on), recorded in the audit. Report honestly if the device cannot give valid frames (USB power / idle 0 frames, as seen for FISH-033); do not claim a pass without numbers.

### Smoke (TECNO, adb with ≥80 ms hold so a frame draws between DOWN and UP)
- Backup APK/data/hashes first; enable the switch, drag across the grid, screenshot: far icons visibly dimmer, near icons opaque; release returns to opaque; disable switch restores. Restore device and compare hashes/settings. Stop on any ad overlay (R4); never tap “Start (Ads may appear)”.

## Scope / non-goals

- No chromatic aberration (separate story), no per-lens override, no slider, no new dependency, no `RenderEffect`, no overlay view, no change to Clean mode semantics, no persistence beyond the single boolean.
- FISH-035 stays separate and is not started here.
