# FISH-018 — Per-lens icon size

| Field | Value |
|---|---|
| Type | feature + bugfix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed (code audit + device) |
| Epic | Fisheye Smart |
| Risk | Low |
| Dependencies | FISH-008 (multi-lens workspaces) |

## Owner decision (2026-10-05)

Per-lens icon size only; per-lens icon pack is split out as its own later story (it needs an architecture change: icons live in one global `BitmapCache`). Design: `docs/superpowers/specs/2026-10-05-fish-018-per-lens-icon-size-design.md`. Plan: `docs/superpowers/plans/2026-10-05-fish-018-per-lens-icon-size.md`.

## Acceptance criteria

- [x] Each lens has its own icon size: `UtilSettings.getIconSize(lensId)` / `saveIconSize(lensId, value)`, key `min_icon_size_<lensId>`; the default lens and null/empty ids use the legacy unsuffixed key.
- [x] A lens without an override inherits the shared value, so pre-multi-lens installs are unchanged.
- [x] `LensView.drawGrid` lays out with its own lens icon size (`LensGridCache` already keys on `iconSizeDp`).
- [x] The Lens tab slider edits the active lens only; Reset to default resets the active lens's icon size and distortion only (other lenses keep theirs); scale factor and animation time stay global, as before.
- [x] Duplicating a lens copies the size; deleting a lens removes its override.
- [x] Corrupt/out-of-range stored values clamp or fall back to the default.
- [x] Unit, widget, integration tests; lint; full instrumentation; device smoke.
- [x] Audit > 9.0 (independent review: 9.2/10; push gate passed)
- Deferred: per-lens icon pack (separate story).

## Bug found during device smoke (pre-existing, fixed in `a223616`)

A swiped-back lens page came back blank (reproduced on TECNO KJ7 and Samsung S24 Ultra with 2 lenses; present before FISH-018).

- **Root cause**: a ViewPager2 page is a RecyclerView item. Swiping away detaches it and swiping back re-attaches the SAME `LensView` without running its constructor. `onDetachedFromWindow` nulls `mUtilSettings`, `mAccessibilityHelper`, `mPackageManager` (BUG-05 leak prevention), so `onDraw` found null settings and drew nothing. Logs showed `settingsNull=true` on the re-attached page; the other lens page was fine (an earlier "no app rows" data hypothesis was disproved).
- **Fix**: `LensView.onAttachedToWindow` restores settings, accessibility helper (extracted `installAccessibilityHelper()`) and `PackageManager`, then invalidates. `PackageManager` matters because `launchApp` / `launchAppAtIndex` are guarded on it and only `ActHome.bindLensView` sets it: without the restore a swiped-back page draws but a tap launches nothing.
- **Tests**: `LensViewReattachIntegrationTest` (7) runs in a real Activity window (a detached `FrameLayout` version passed vacuously on buggy code and was rewritten). RED 4/5 before the fix; after adding `aReattachedView_canStillLaunchApps`, mutation proof: removing the `mPackageManager` restore fails exactly that test, restoring it passes 6/6.

## Evidence

Devices: **Samsung S24 Ultra `R5CX613VZBR`** (owner-requested for FISH-018) for the feature work; it disconnected, then the owner chose **TECNO KJ7 `115333744A005844`** for the blank-page debugging and final verification.

- S24 Ultra, feature complete: JVM 720, lint 0 errors/8 warnings, full instrumentation 494/494; prefs inspection: default lens writes `min_icon_size`, second lens writes `min_icon_size_<uuid>`.
- KJ7, final HEAD (`a223616`): JVM **722/722**, lint **0 errors / 8 warnings** (pre-existing icon warnings), full instrumentation **501/501 OK**.
- KJ7 manual smoke: cold start, swipe lens 1 -> 2 -> 1, all pages draw icon grids after the fix.
- Not seen on screen: two lenses with different icon sizes side by side; covered by `LensViewIconSizeIntegrationTest` (8) and `FrmLensIconSizeWidgetTest` (8, real drag), plus `UtilSettingsIconSizeTest` (15).

## Audit

- Round 1 (8.0/10): `mApps` null after re-attach, corrupt shared key / NaN handling, Reset doc claim.
- Round 2 (**9.2/10**): all round 1 findings resolved. 0 Critical, 0 Important. Push gate > 9.0 passed.

