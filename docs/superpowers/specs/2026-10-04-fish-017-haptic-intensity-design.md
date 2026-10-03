# FISH-017 — Haptic intensity setting

## Context

Hover and launch haptics already exist (`KEY_VIBRATE_APP_HOVER` default off, `KEY_VIBRATE_APP_LAUNCH` default on). Both hardcode `HapticFeedbackConstants.VIRTUAL_KEY` in `LensView.performHoverVibration` / `performLaunchVibration`. Owner picked "haptic intensity" instead of a new hover tick (2026-10-04).

## Scope

One shared 3-level setting (Light / Medium / Strong) for hover and launch haptics. Only the effect type changes; the two on/off switches and every existing gate (`LensPhysicsPolicy.shouldReduceLensMotion`, `mAnimationHiding`) stay as they are.

## Design

- `util/HapticIntensity.kt`: enum `LIGHT`, `MEDIUM`, `STRONG`.
  - `feedbackConstant(sdkInt)`: `LIGHT` -> `CLOCK_TICK`, `MEDIUM` -> `VIRTUAL_KEY` (today's behavior, so default changes nothing), `STRONG` -> `CONFIRM` on API 30+, `LONG_PRESS` below.
  - `from(ordinal: Int)`: unknown, negative or out-of-range -> `DEFAULT`.
  - Pure Kotlin, no Android context, unit-testable on the JVM.
- `UtilSettings`: `KEY_HAPTIC_INTENSITY` (Int ordinal), `DEFAULT_HAPTIC_INTENSITY = MEDIUM.ordinal`. Global key, no `_<lensId>` suffix: device hardware preference, not a per-lens one.
- `LensView`: both vibration functions read the setting and call `performHapticFeedback(HapticIntensity.from(..).feedbackConstant(Build.VERSION.SDK_INT))`.
- `FrmSettings` + `frm_settings.xml`: `MaterialButtonToggleGroup` (single selection) below the two haptic switches. Selecting a level saves it and fires one preview haptic. Group disabled while both switches are off. The existing reset action restores the default.

## Edge cases

- No vibration motor: `performHapticFeedback` is a no-op, no crash.
- Large font scale / long translations: buttons must not clip; verify at max font scale.
- Reduced-motion: preview haptic is suppressed too.

## Tests

- Unit: enum -> constant per API level (24..37 boundaries around 30), `from()` bad values, `UtilSettings` default/save/reset.
- Widget: group reflects saved level, disabled when both switches off, selecting writes prefs.
- Integration: real `LensView` emits the constant for the saved level on hover and on launch; silent under reduced motion; unchanged default behavior for an install with no saved key.
- Device smoke: per R3, device picked via AskUserQuestion before any build.

## Out of scope

Separate hover/launch levels, custom vibration waveforms, per-lens intensity.
