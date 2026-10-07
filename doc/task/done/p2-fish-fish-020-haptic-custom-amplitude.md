# FISH-020 — Custom Haptic Amplitude via VibrationEffect

| Field | Value |
|---|---|
| Type | feature |
| Status | done |
| Priority | P2 |
| Evidence | code complete + dumpsys verification |
| Estimate | 5 SP |
| Date | 2026-10-07 |

## Summary

Replaced `HapticFeedbackConstants` with `VibrationEffect.createOneShot()` in `HapticIntensity` enum to fix STRONG level feeling identical to MEDIUM on some devices. Used explicit duration (ms) and amplitude (0–255) differentiation, tuned on TECNO KJ7.

## Problem Statement

FISH-017 introduced three haptic levels (LIGHT, MEDIUM, STRONG) using system `HapticFeedbackConstants`. On Android 17 Pixel, STRONG (`LONG_PRESS`) felt identical to MEDIUM (`VIRTUAL_KEY`), both producing the same tactile sensation. Root cause: System constants vary by OEM and Android version; no guaranteed cross-device distinction.

## Solution: Duration-Based Differentiation

Replaced system constants with duration + amplitude tuple:

| Level | Duration | Amplitude | Intent |
|---|---|---|---|
| LIGHT | 20 ms | 80/255 | Quick, subtle tap |
| MEDIUM | 40 ms | 128/255 | Standard app feedback |
| STRONG | 70 ms | 200/255 | Long, intense pulse |

## Implementation

### HapticIntensity enum
- Removed `feedbackConstant` property
- Added `duration: Long` (ms) and `amplitude: Int` (0–255) properties
- Each level has hardcoded values tuned on TECNO KJ7

### LensView.performIntensityHaptic()
- Added `vibratorProvider` seam for testing
- Uses `VibrationEffect.createOneShot()` on API 26+
- Fallback to `performHapticFeedback(VIRTUAL_KEY)` on API 25
- Encodes amplitude in `onHapticPerformed` seam for test verification

### FrmSettings.previewHaptic()
- Apply same VibrationEffect pattern for haptic preview

### Tests
- **HapticIntensityTest**: 8 unit tests for duration/amplitude/ordering/edge cases
- **LensViewHapticIntensityIntegrationTest**: 6 integration tests using onHapticPerformed seam
- **FrmSettingsHapticIntensityWidgetTest**: updated seam expectation to amplitude value
- Lifecycle: both seams (onHapticPerformed, vibratorProvider) nulled on detach

## Verification

All device work on TECNO KJ7 `115333744A005844` only, via direct `adb -s`; no Gradle `connected*` task. Pixel/S24U never addressed.

| Layer | Result |
|---|---|
| JVM unit (`testDevDebugUnitTest --rerun-tasks`) | **722 / 722**, 0 failures (8 in `HapticIntensityTest`) |
| Lint (`lintDevDebug`) | 0 errors, 0 fatal, 8 warnings (= baseline) |
| Full instrumented on KJ7 | **504 run, 500 pass, 3 skipped (assumptions), 1 fail** |
| Failing test rerun alone | `ActHomeMultiLensRecentAppsIntegrationTest` OK x3; not touched by this diff, one-off in the full run |
| Haptic classes alone | `LensViewHapticIntensityIntegrationTest` 6/6, `FrmSettingsHapticIntensityWidgetTest` 13/13 |

Skipped: 2 DND tests (device DND on) and `reducedMotion_preventsHaptic` (reduced motion not forced on KJ7), so the reduced-motion gate is not exercised on device by the new test.

### Device smoke (KJ7, Android 14)

First run had `haptic_feedback_enabled=0`, so every record was `ignored_for_settings`. After enabling it, tapping Nhe / Vua / Manh in Settings produced `finished` records:

| Level | System record | Code |
|---|---|---|
| Nhe | `duration=20`, `amplitude=0.314` | 20 ms / 80 |
| Vua | `duration=40`, `amplitude=0.502` | 40 ms / 128 |
| Manh | `duration=70`, `amplitude=0.784` | 70 ms / 200 |

KJ7 motor reports `mCapabilities=[]` (no amplitude control), so only duration separates the levels there.
Setting restored to `0` afterwards.

## Scope & Non-Scope

### In Scope
- Replace `HapticFeedbackConstants` with `VibrationEffect` in enum and all call sites
- Duration + amplitude properties for three levels
- Test coverage: unit, integration, lifecycle
- Code review and self-audit

### Out of Scope
- User-customizable duration/amplitude (hardcoded per level)
- Settings UI (FrmSettings toggle already complete from FISH-017)
- Other haptic feedback sites (only LensView/FrmSettings in scope)
- Amplitude control hardware detection (accepted limitation per design spec)

## Known Limits

- Duration/amplitude values are starting points, not measured cross-device — only verified on TECNO KJ7 via dumpsys
- `amplitude` parameter only applies when `Vibrator.hasAmplitudeControl()` is true; on motors without amplitude support, only duration differentiates levels
- `onHapticPerformed` seam semantics changed: now passes amplitude (Int) instead of constant (Int), requiring test updates
- Integration tests verify gates and seams only; VibrationEffect details (duration/amplitude in effect struct) verified in dumpsys, not via reflection

## Code Quality

- No new dependencies added; `VibrationEffect` is part of Android SDK (API 26+)
- No new permissions (VIBRATE already in manifest)
- Backward compatible: API 25 falls back to `performHapticFeedback(VIRTUAL_KEY)` for all levels
- Lifecycle cleanup: both test seams (vibratorProvider, onHapticPerformed) nulled in `onDetachedFromWindow()`

## Files Modified

1. `app/src/main/java/com/mckimquyen/util/HapticIntensity.kt` — enum properties
2. `app/src/main/java/com/mckimquyen/views/LensView.kt` — vibratorProvider seam + performIntensityHaptic()
3. `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt` — previewHaptic() using VibrationEffect
4. `app/src/test/java/com/mckimquyen/util/HapticIntensityTest.kt` — 8 unit tests
5. `app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt` — 6 integration tests
6. `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsHapticIntensityWidgetTest.kt` — seam expectation updated

## Audit

Self-audit **pending owner hand-feel check**. Not scored above the push gate until the owner confirms the three levels feel distinct on KJ7 (not measurable by `dumpsys`). Open items: hand-feel unconfirmed; `reducedMotion_preventsHaptic` skipped on device; integration tests prove gates and seams, not the `VibrationEffect` arguments (those are proven by the `dumpsys` records above); one unrelated flake in the full run.

## Next

Not pushed. Waiting on owner hand-feel verdict; if Vua and Manh feel alike, raise Manh duration (e.g. 100 ms). FEAT-010 (auto-switch lens by schedule) planned for next sprint.

---

**Git Commits:**
- `46d6f7c` feat(haptic): FISH-020 replace HapticFeedbackConstants with duration/amplitude
- `68390c0` test(haptic): FISH-020 rewrite integration tests for VibrationEffect
- `7b85c08` test(haptic): FISH-020 simplify integration tests, remove Mockito dependency
