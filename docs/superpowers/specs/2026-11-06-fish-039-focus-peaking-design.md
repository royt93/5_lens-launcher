# FISH-039 — Focus peaking for low vision — Design

Date: 2026-11-06  
Story: `doc/task/todo/p3-fish-fish-039-focus-peaking-low-vision.md`  
Branch: `dev` only (owner rule). Device: chosen at execution time (R3); no push unless the owner asks.

## Goal

An opt-in bold outline around **every icon inside the sharp zone** of the lens, so a low-vision user can see which cluster is in focus. It never changes geometry, hit-testing, launch, haptics, `LensGridCache` or any existing effect.

## Decisions (owner, 2026-11-06)

- **What:** a thick outline on every icon whose focus distance is inside the sharp zone (`DepthOfField.FOCUS_ZONE`, normalized 0.2). One ring per icon, drawn right after the icon. Works with depth-of-field on or off.
- **Look:** the existing user highlight colour (`UtilSettings.highlightColorInt()`, Material You aware), stroke **4 dp** (the FISH-024 halo is 2.5 dp). Ring radius = icon half-width + the existing halo padding, so the stroke sits outside the bitmap and does not hide it.
- **When:** only while a **finger is down** (live touch + magnified lens) **or focus is pinned** (FISH-029). Never idle, never tilt (FISH-033): tilt drifts continuously and would drag the outline around.
- **Switch:** its own setting `focus_peaking`, default **off**, included in reset-to-defaults. The FISH-024 `Show focus halo` switch and its thin ring are unchanged.
- No new allocation in `onDraw`: one reusable `Paint`, one settings read and one colour/width update per frame, per-cell work is arithmetic and one `drawCircle`.

## Design details settled here (not asked, derived from the code)

1. **Which centre.** `DepthOfField.focusDistance` today receives `mTouchX/Y`, which are `-Float.MAX_VALUE` while focus is pinned. Peaking therefore uses the frame's visual focus centre (`focusCenter` from `resolveVisualFocusCenter`: live touch wins, else the pin, else null). `focusCenter == null` (idle or tilt) means no outline. Vignette and DoF keep using the touch centre exactly as now.
2. **Alpha.** The DoF transition is 0 when only pinned, so the outline is not scaled by it. Alpha is full (255) when pinned without a touch. With a live touch it follows `DepthOfField.transition(animationMultiplier, reduceMotion)`, so it appears with the magnification and, under reduced motion, snaps on/off like DoF and the vignette.
3. **Reduced motion and Clean mode.** The outline has no animation of its own. Reduced motion only changes the touch fade (snap). Clean mode does **not** hide it: it is an accessibility aid, not chrome (same reasoning as the vignette).
4. **Flag sampling.** The pinned state has no `LensAnimation`, so the flag cannot only be read there. It is read once in `LensAnimation.init` (touch path, like the vignette) **and** in `toggleFocusLockAt` when a pin is made, and nulled on detach with the other references.
5. **Z-order.** The outline is drawn in the same cell loop right after the icon (on the same canvas the icon used, so a DoF band-0 layer keeps it sharp). Magnified neighbours drawn later may overlap an earlier outline at the very centre; accepted, and measured in the smoke.
6. **FISH-024 overlap.** The thin halo of the single hovered icon is drawn later and sits on top; both rings are circles with the same centre, so the result reads as one thicker ring there.

## Architecture

1. **Pure `views/FocusPeaking.kt`** (Context-free, unit-tested), beside `DepthOfField`/`LensVignette`:
   - `const val STROKE_DP = 4f`.
   - `fun shouldApply(enabled: Boolean, hasFocusCentre: Boolean, lensVisible: Boolean): Boolean`.
   - `fun isSharp(focusDistance: Float): Boolean` — `focusDistance.isFinite() && focusDistance <= DepthOfField.FOCUS_ZONE`.
   - `fun alpha(pinnedOnly: Boolean, transition: Float): Int` — 255 when pinned without a touch, else `round(255 * transition.coerceIn(0,1))`, 0 for non-finite.
2. **`LensView.drawGrid`**: compute `peakingOn` once per frame; per cell, when on, compute `FocusPeaking.isSharp(DepthOfField.focusDistance(centreX, centreY, baseRect centre, …))` and draw the ring with the cached paint. Restore nothing else (the paint is its own).
3. **Setting**: `UtilSettings.KEY_FOCUS_PEAKING = "focus_peaking"`, `DEFAULT_FOCUS_PEAKING = false`, boolean getter branch next to `KEY_LENS_VIGNETTE`; `ResetEntries` flag; `FrmSettings` switch + hint under the vignette row (`rlSwitchFocusPeakingParent`, `swFocusPeaking`, `tvFocusPeakingHint`, nulled in `onDestroyView`).
4. **Strings**: `setting_focus_peaking`, `setting_focus_peaking_hint` in base and the 16 locales. AI-written translations are recorded as a limitation.
5. **Tests** — see below. No new dependency.

## Error and edge behavior

- Non-finite coordinates, zero-sized view or an empty list: no outline, no crash (same guards as the grid).
- Pin cleared (double tap, pause, detach, cancel): the outline disappears on the next frame because `focusCenter` becomes null.
- Rotation preview (FISH-035): the outline follows the displayed order automatically (it is drawn per displayed cell).
- Locked or empty lens: nothing drawn.

## Tests (RED before code)

### Unit
- `FocusPeaking.isSharp`: inside zone true, boundary true, outside false, non-finite false.
- `shouldApply`: truth table of enabled × centre × visible.
- `alpha`: pinned-only is 255 regardless of transition; touch alpha follows transition (0, 0.5, 1) and clamps; non-finite is 0.
- Setting key, default `false`, reset entry present (`ResetEntriesTest` key set updated), `UtilSettings` getter.

### Widget / integration
- Live touch + setting on: the ring is drawn for the sharp icons and not for far icons (count probe `@VisibleForTesting`, like `focusHaloDrawCallCount`).
- Pinned focus without touch + setting on: rings drawn at full alpha; pin released: none.
- Idle and tilt: no ring even with the setting on.
- Setting off: zero rings in every state; default off.
- Geometry invariant: `drawnRectsForTest()` identical with the setting on/off; `LensGridCache.recomputeCount` unchanged; selection and touch coordinates unchanged.
- Reduced motion: touch ring snaps (alpha 255 immediately), Clean mode does not hide it.
- DoF on (both ALPHA and BLUR paths): ring still drawn for band-0 icons.
- Settings fragment: switch persists, hint present, reset turns it off.

### Frame budget (acceptance criterion carried from FISH-034)
Same `dumpsys gfxinfo` method as FISH-034: ten diagonal drags with the setting off vs on, valid frame counts, recorded honestly (one run each, device and USB-power caveats).

### Smoke (adb with ≥ 80 ms hold)
Backup APK/data/hashes first; enable the switch; slow diagonal drag; screenshot shows a bold ring around the cluster under the finger and none on far icons; release returns to no ring; pin focus and see the ring remain; disable the switch. Restore and compare hashes/settings. Stop on any ad overlay (R4); never tap "Start (Ads may appear)".

## Scope / non-goals

- No edge-detection peaking, no per-icon outline from bitmap alpha, no thickness slider, no per-lens override, no colour picker, no change to the FISH-024 halo, DoF or vignette, no TalkBack change, no new dependency.
- FISH-040 (chromatic aberration) is separate and not started.
