# FISH-040 — Chromatic aberration at the lens rim — Design

Date: 2026-11-07  
Story: `doc/task/todo/p3-fish-fish-040-chromatic-aberration-rim.md`  
Branch: `dev` only (owner rule). Device: Samsung S24 Ultra `R5CX613VZBR` (owner, every session; owner allows closing ads via adb, never click ad content). No push unless the owner asks.

## Goal

An opt-in optical effect: icons far from the lens focus show a small red/blue colour fringe, like a real lens. It must never change geometry, hit-testing, launch, haptics or `LensGridCache`, and it must stay inside the 120 Hz frame budget.

## Decisions (owner, 2026-11-07)

- **Mechanism: draw three times, for few icons.** For an affected icon draw the original bitmap in place, then two extra copies: a red-only copy shifted outward from the lens focus and a blue-only copy shifted inward, both added on top. Works on every API (minSdk 25): no `RenderEffect`, no `RuntimeShader`, one code path.
- **Cap: at most 8 icons per frame**, the 8 farthest from the focus among icons outside the sharp zone. Worst case is 16 extra `drawBitmap` per frame, a bound the audit can state and measure. An icon outside the cap is drawn normally.
- **When: live touch only.** Same gate as the vignette: `hasActiveTouch && DepthOfField.transition(...) > 0`. Not while focus is pinned (FISH-029), not on tilt (FISH-033), not idle. The offset scales with the lens transition, so reduced motion snaps it exactly like DoF and the vignette.
- **Offset: 1 to 3 dp by distance.** Zero inside the sharp zone, rising linearly with the vignette's normalized focus distance to a maximum of 3 dp at the farthest icon, along the unit vector from the focus centre to the icon centre (red outward, blue inward).
- **Channel split: ADD.** Red copy keeps only the red channel (`ColorMatrixColorFilter`), blue copy keeps only the blue channel, both drawn with `PorterDuff.Mode.ADD` so the original stays the green and the centre. Alpha is the same per-icon alpha the icon already uses (`min` of the DoF fallback alpha and the vignette alpha, never the product), so fringes fade exactly with the icon.
- **Switch:** own setting `chromatic_aberration`, default **off**, included in reset-to-defaults. FISH-024 halo, FISH-034 vignette and FISH-039 focus peaking are unchanged and independent.

## Design details settled here (derived from the code, not asked)

1. **Centre.** Distance and direction use the touch coordinates `mTouchX/Y`, the same centre `DepthOfField.focusDistance` already receives for the vignette. No new centre.
2. **Reuse of the distance.** The cell loop already computes `focusDistance` when `dofMode != OFF || vignetteOn`; the gate is extended with `chromaOn` so one distance serves all three effects. No second distance computation.
3. **Choosing the 8 farthest without allocation.** The loop already visits every cell, so a `TopFarthest(8)` (fixed arrays, allocated once, reset each frame) records each outside-zone icon's distance and cell index as the loop runs. The set is only complete after the last cell, so the fringes are drawn in a **second, small pass** over just the selected indices after the icon loop. The pass needs each selected cell's final (warped) rect and bitmap: `TopFarthest.offer` returns the slot (0..7) the entry now occupies, or -1 when the distance did not make the cut, and the loop copies the cell's rect into a view-owned `mChromaRects` (8 `RectF`, allocated once) only for kept entries; the second pass looks the bitmap up again by cell index through the same `RAppsSingleton.getAppIcon(iconCacheKey)` call the main draw uses (a cache hit; the second pass never requests a reload).
4. **Z-order.** Fringes are drawn after all icons and before the focus halo, name label and focus ring, so they sit under the UI chrome. A fringe never covers the label or chip.
5. **DoF BLUR (API 31+).** A far icon is in band 1 or 2 and is drawn on a downsampled blurred layer, where a 1 to 3 dp fringe would be invisible and wasted. On the BLUR path the second pass draws on the main canvas, over the blurred layer; if the audit shows the fringe fights the blur visually, the fallback decision is to skip fringes for icons whose DoF band is above 1. The first implementation applies fringes to band 0 and band 1 only and records the decision.
6. **Flag sampling.** Read once per lens show in `LensAnimation.init` next to the vignette flag, and nulled/reset on detach with the other state. Never read per frame.
7. **Reduced motion and Clean mode.** Reduced motion snaps the transition (offset appears and disappears instantly). Clean mode does not hide it (not chrome), like the vignette.
8. **API < 31.** Nothing differs: `ColorMatrixColorFilter` and `PorterDuff.Mode.ADD` exist on API 1+. The DoF ALPHA fallback keeps its `min` alpha rule.

## Architecture

1. **Pure `views/ChromaticAberration.kt`** (Context-free, unit-tested):
   - constants `MAX_ICONS = 8`, `MAX_OFFSET_DP = 3f`, `MIN_OFFSET_DP = 1f` (used only when the offset is non-zero).
   - `fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean`.
   - `fun offsetPx(focusDistance: Float, transition: Float, density: Float): Float` — 0 inside the sharp zone or when the transition is 0 or any input is non-finite; otherwise `density * (MIN + (MAX - MIN) * ramp)` scaled by `transition`, where `ramp = (distance - FOCUS_ZONE) / (1 - FOCUS_ZONE)` clamped to 0..1.
   - `fun direction(touchX, touchY, cellX, cellY, out: FloatArray)` — unit vector from touch to cell written into `out[0..1]`; zero vector when the cell is on the touch point.
   - `class TopFarthest(capacity: Int)` - fixed arrays, `reset()`, `offer(distance: Float, index: Int): Int` (returns the slot now holding the entry, or -1 when it did not make the cut; an entry displaced by a farther one frees its slot for the new entry), `count`, `indexAt(slot)`, `distanceAt(slot)`; keeps the `capacity` largest distances, ignores non-finite, no allocation after construction.
2. **`LensView`**: one extra `Paint` for the red copy and one for the blue copy (colour filters built once in `setupPaints`, `PorterDuffXfermode(ADD)` set once); per frame only `alpha` changes. A reused `TopFarthest(8)`, a view-owned `mChromaRects` (8 `RectF`, written during the cell loop) and one `mScratchChromaRect` that the second pass uses to shift a copy of a selected rect by the offset (`set` then `offset`), so nothing is allocated per frame. The second pass iterates the selected slots, looks up the icon bitmap, and draws red then blue.
3. **Setting**: `UtilSettings.KEY_CHROMATIC_ABERRATION = "chromatic_aberration"`, `DEFAULT_CHROMATIC_ABERRATION = false`, boolean getter branch; `ResetEntries` flag entry; `FrmSettings` switch row below the vignette row (`rlSwitchChromaticAberrationParent`, `swChromaticAberration`, `tvChromaticAberrationHint`, nulled in `onDestroyView`).
4. **Strings**: `setting_chromatic_aberration`, `setting_chromatic_aberration_hint` in base and the 16 locales (AI-written, recorded as a limitation).
5. **State**: no new persistence beyond the single boolean.

## Error and edge behavior

- Non-finite coordinates, zero-size view or empty list: no fringe, no crash.
- Fewer than 8 outside-zone icons: only those get fringes. No outside-zone icon: nothing.
- A missing or recycled icon bitmap: skip that icon's fringes and trigger the existing reload path only through the main draw (the second pass never requests reloads).
- Icon at the touch point (zero direction): offset is 0 for that icon.

## Tests (RED before code)

### Unit
- `ChromaticAberration.offsetPx`: zero inside the zone and at transition 0, monotonic with distance, 1 dp at the zone edge plus, 3 dp at the farthest, scales with density and transition, non-finite is 0.
- `shouldApply` truth table.
- `direction`: unit length, correct sign, zero when coincident, no allocation (writes into the provided array).
- `TopFarthest`: keeps the largest N in any insertion order, fewer than N, ties, ignores non-finite, `reset`, never exceeds capacity; `offer` returns a stable slot for kept entries and -1 for rejected ones, and a displaced slot is reused.
- Setting key, default false, reset entry present (`ResetEntriesTest` key set updated).
- Source guard (like `FocusPeakingNoAllocationTest`): the new draw function bodies contain no allocation tokens and exactly the expected number of `drawBitmap` calls (two).

### Widget / integration
- Live touch + setting on: red and blue fringe pixels appear next to far icons only (pixel probe of a red-only and a blue-only icon), none at near icons; at most 8 icons fringed (probe `chromaIconsForTest`).
- Idle, pinned focus and tilt: no fringe even with the setting on.
- Setting off: zero fringes in every state; default off.
- Geometry invariant: `drawnRectsForTest()`, selected index, touch coordinates and `LensGridCache.recomputeCount` identical on and off.
- Alpha: with the vignette on, a fringed icon's fringe alpha equals the icon's (min rule), never the product; DoF ALPHA fallback agrees.
- Reduced motion snaps the offset, Clean mode does not hide it.
- Real `ActHome` on API 31+ with DoF on: BLUR path still active and fringes drawn for band 0 and 1 only.
- Settings fragment: switch persists, hint present, reset turns it off (`FrmSettingsChromaticAberrationWidgetTest`).

### Frame budget (acceptance criterion)
Same method as FISH-034/039: ten diagonal drags with the setting off vs on, `dumpsys gfxinfo` after `reset`, valid only above 1600 frames; also report p90/p95/p99. If on exceeds the 120 Hz budget the owner decision in the story applies (reduce the cap or the affected icons); the audit never claims a pass without valid numbers. Run on the locked S24 Ultra and, if available, record that the second device was not measured.

### Smoke
Back up APKs/data/hashes first. Enable the switch by a real touch (UiAutomation, as in FISH-039), drag slowly with real touch events, screenshot mid-drag: red/blue fringes on far icons, none near the finger; release returns to idle (0 changed pixels). Restore and compare hashes/settings. If an ad covers the UI close it via adb (never click ad content, never tap "Start (Ads may appear)").

## Scope / non-goals

- No `RenderEffect`/`RuntimeShader`, no per-pixel shader, no per-lens override, no strength slider, no colour choice, no change to the vignette, DoF, focus peaking or halo, no TalkBack change, no new dependency.
- The cap of 8 and the offset range are provisional named constants tuned on synthetic evidence; real-eye tuning is out of scope.
