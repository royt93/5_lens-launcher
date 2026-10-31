# FISH-030 — Pincushion lens profile

Date: 2026-10-31
Status: Owner-approved design

## Context

FISH-027 added per-lens Fisheye, Anamorphic, and Cylindrical profiles, using existing positive distortion magnitude. FISH-030 adds reverse/pincushion behavior without changing legacy distortion storage, backup ranges, or the default Fisheye experience. Owner chose a separate lens profile, not negative values or a second preset system; “open the lower slider bound” in the original todo is superseded by this decision.

## User-visible behavior

- Add `PINCUSHION` as a fourth per-lens profile in the existing Lens Settings profile control.
- Fisheye remains default for every existing lens and keeps current pixel output exactly.
- Pincushion reflects the Fisheye displacement around the lens point: if `f` is the existing shifted coordinate and `L` is lens position, reverse result is `L - (f - L)`. The same transform applies to shifted and scaled centers, so generated rectangle and hit-test remain identical.
- Profile axis policies remain unchanged: Fisheye and Pincushion warp both axes, Anamorphic only X, Cylindrical only Y. Hood falloff applies after computing the profile's directed displacement, keeping the zero-distance edge and out-of-hood identity behavior.
- Distortion remains a positive magnitude in existing `KEY_DISTORTION_FACTOR` bounds. Do not allow negative values, add a separate Pincushion strength, or add another physics preset.

## Storage and compatibility

- Reuse `UtilSettings.KEY_LENS_PROFILE` per-lens key and `LensProfile` enum; no new preference key, Room column, or migration.
- Existing Fisheye/Anamorphic/Cylindrical stored enum names remain valid. Unknown/missing profile continues to default to Fisheye.
- Extend `LensPhysicsBackup.lensProfile` parsing to accept `PINCUSHION`; older backup without field continues to preserve current profile. Duplicate/delete already operate on the same profile key and must remain covered.
- Default distortion/import validation remains positive-only; profile is the sole direction selector.

## Implementation boundaries

- `util/LensProfile.kt`: add profile and direction-aware X/Y shift/scale methods; keep current profiles byte-for-byte equivalent through existing formula path. Avoid duplicating fisheye math; apply reflected displacement helper.
- `util/UtilCalculator.java`: if reflection needs a shared pure helper, add the smallest overload/helper adjacent to `shiftPoint`; do not change existing overload outputs.
- `views/LensView.kt`: use profile-aware shifted/scaled centers, with the same final rectangle feeding draw and hit-test. Keep FISH-028 hood applied to the directed displacement.
- `ui/LensSettingsActivity.kt`, `res/layout/act_lens_settings.xml`, and 17 locale `strings.xml` files: fourth choice and concise explanation.
- `model/LayoutBackup.kt`, `UtilSettings.kt`: extend accepted enum value only if current name-based parser rejects it; no new field/key.
- Add pure math tests, backup compatibility tests, LensView draw/hit-test integration tests, and settings widget test for per-lens persistence/recreation. No Ads/IAP/monetization or TalkBack work.

## Verification

- TDD: profile math tests first; prove RED because `PINCUSHION` is absent, then GREEN.
- Unit: reflection about lens point for both sides, exact Fisheye equivalence, idle sentinel, hood FULL and partial, positive finite icon size, legacy stored profiles/default, backup round-trip and unknown-name rejection.
- Integration: use actual LensView drawn rects and assert both axes move opposite Fisheye, selected hit-test rect equals a drawn rect; verify old profiles retain expected geometry.
- Widget: selecting Pincushion writes only target lens, survives recreate; existing profile choices and Slider/hood remain unchanged.
- Gate: unit, lint, assemble app/androidTest, secrets scan, diff check. TECNO KJ7 `115333744A005844` smoke and exact app-data restore; stop if ad covers screenshot. Report Android version/build hash and compare source APK hash with installed APK.
- Update audit and backlog. Do not push without current explicit authorization; audit must exceed 9.0 to qualify.

## Accepted trade-offs

- Pincushion is the exact reflected Fisheye displacement, not an independently tunable distortion curve.
- No slider can go below zero; no negative prefs are introduced.
- The existing positive distortion magnitude and hood percentage fully determine strength/range.
