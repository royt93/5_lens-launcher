# FISH-018 — Per-lens icon size

## Context

Multi-lens workspaces (FISH-008) give each lens its own distortion factor and Smart Focus toggle, stored as `UtilSettings` keys suffixed `_<lensId>`; a lens with no override inherits the shared unsuffixed key. Icon size is still global: `LensView.drawGrid` reads `KEY_ICON_SIZE` directly (`LensView.kt:1103`) and `FrmLens` writes it directly (`FrmLens.kt:113`, `:229`, `:261`).

Owner picked "per-lens icon size + icon pack" (2026-10-05). Per-lens icon pack is split into its own story: `RAppsSingleton` holds one icon set and `BitmapCache.retainKeys` trims the cache to it, so it needs an architecture change. This story is icon size only; the icon pack stays global.

## Scope

- A lens may have its own icon size. A lens without one inherits the shared value, so existing installs and single-lens setups are unchanged.
- The Icon Size slider in the Lens tab edits the active lens, like the Distortion slider.
- Reset-to-default in the Lens tab changes only the active lens.
- No Room schema change, no new UI strings, no change to the icon pack.

## Design

`util/UtilSettings.kt`, same shape as the distortion helpers:

- `getIconSize(lensId: String?): Float`: if `lensKey(KEY_ICON_SIZE, lensId)` exists, read it through `getFloatWithValidation(key, autoDefaultIconSize, MIN_ICON_SIZE, MAX_ICON_SIZE + MIN_ICON_SIZE)`; otherwise `getFloat(KEY_ICON_SIZE)` (the shared, validated value). The default lens uses the unsuffixed key, so for it both branches are the same key.
- `saveIconSize(lensId: String?, value: Float)`: `save(lensKey(KEY_ICON_SIZE, lensId), value)`.
- `duplicateLensSettings` also writes `saveIconSize(toLensId, getIconSize(fromLensId))`, materializing the effective value like distortion.
- `deleteLensSettings` also removes `"${KEY_ICON_SIZE}_$lensId"`.

`views/LensView.kt`: `drawGrid` reads `us.getIconSize(lensId)` instead of `us.getFloat(UtilSettings.KEY_ICON_SIZE)`. One read per frame, as today. `LensGridCache` already keys on `iconSizeDp`, so the cached geometry stays correct per lens and nothing else changes in the draw path.

`ui/FrmLens.kt`: the slider listener calls `saveIconSize(activeLensId, value)`; the initial slider value and the reset path use `getIconSize(activeLensId)` / `saveIconSize(activeLensId, us.autoDefaultIconSize)`. The preview `LensView` (`lensViewsSettings`) already has `lensId = activeLensId`; the plan must confirm its draw path reads per lens.

## Data flow

Drag the slider -> `saveIconSize(activeLensId, v)` -> the preview and that lens's home page redraw with `v`; other lenses are untouched. A lens never adjusted keeps following the shared value.

## Edge cases

- `lensId` null/empty -> the shared key.
- A stored value outside the allowed range or of the wrong type -> clamped to min/max, or the device default for a wrong type; never a crash.
- Deleting a lens removes its key, so a later lens with the same id starts from inheritance.
- Reset writes an explicit per-lens value (like Distortion), so that lens stops following the shared value afterwards. Owner chose this for consistency.

## Tests

- Unit (Robolectric, `UtilSettingsIconSizeTest`): inherits the shared value; a per-lens save does not change other lenses or the shared value; default lens uses the unsuffixed key; clamps below min and above max; wrong-typed stored value falls back instead of throwing; `duplicateLensSettings` copies the effective value including an inherited one; `deleteLensSettings` removes the key; the key name is stable.
- Widget (instrumentation, `FrmLensPerLensWidgetTest` pattern): the slider shows the active lens's value; dragging changes only that lens; reset changes only that lens; switching the active lens updates the slider.
- Integration: two `LensView`s with different `lensId`s and the same app list produce different grid geometry; changing one lens's size leaves the other's cached geometry intact; a duplicated lens keeps the source size; deleting a lens restores inheritance.
- Device: Samsung S24 Ultra `R5CX613VZBR` (owner instruction); full instrumentation suite and a manual check with two lenses at different sizes.

## Out of scope

Per-lens icon pack, a per-lens scale factor or animation time, an "apply to all lenses" button.
