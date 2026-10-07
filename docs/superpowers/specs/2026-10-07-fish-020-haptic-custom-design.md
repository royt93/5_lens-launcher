# FISH-020 — Custom Haptic Amplitude via VibrationEffect

**Date:** 2026-10-07  
**Author:** Claude Code  
**Status:** Design (awaiting implementation)

## Problem Statement

FISH-017 (haptic intensity) introduced three levels (LIGHT, MEDIUM, STRONG) using `HapticFeedbackConstants`. On Android 17 Pixel device, STRONG (`LONG_PRESS`) felt identical to MEDIUM (`VIRTUAL_KEY`), both producing the same tactile sensation. This undermines the "strong" differentiation and reduces user perception of choice.

**Root cause:** System `HapticFeedbackConstants` effects vary by OEM and Android version; we cannot guarantee distinct behavior across all devices using constants alone.

## Solution: Duration-Based Differentiation

Replace `HapticFeedbackConstants` with `VibrationEffect.createOneShot(duration, amplitude)` for all three levels, using hardcoded duration and amplitude values that scale clearly:

| Level | Duration | Amplitude | Intent |
|---|---|---|---|
| LIGHT | 20 ms | 80/255 | Quick, subtle tap |
| MEDIUM | 40 ms | 128/255 | Standard app feedback |
| STRONG | 70 ms | 200/255 | Long, intense pulse |

This approach gives us direct control over both duration and intensity, independent of platform variations.

## Architecture

### 1. HapticIntensity Enum (Update)

**File:** `app/src/main/java/com/mckimquyen/util/HapticIntensity.kt`

**Current state:**
```kotlin
enum class HapticIntensity {
    LIGHT, MEDIUM, STRONG;
    val feedbackConstant: Int
        get() = when (this) {
            LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
            STRONG -> HapticFeedbackConstants.LONG_PRESS
        }
    companion object {
        val DEFAULT = MEDIUM
        fun from(ordinal: Int): HapticIntensity = entries.getOrNull(ordinal) ?: DEFAULT
    }
}
```

**New state:**
- Remove `feedbackConstant` property
- Add `duration: Long` (milliseconds) and `amplitude: Int` (0–255) properties
- Keep `DEFAULT = MEDIUM`, `from(ordinal: Int)` unchanged
- Example:
  ```kotlin
  LIGHT { duration = 20L; amplitude = 80 }
  MEDIUM { duration = 40L; amplitude = 128 }
  STRONG { duration = 70L; amplitude = 200 }
  ```

### 2. LensView Haptic Trigger (Update)

**File:** `app/src/main/java/com/mckimquyen/views/LensView.kt`

**Current `performIntensityHaptic()`:**
```kotlin
private fun performIntensityHaptic() {
    val level = mUtilSettings?.getHapticIntensity() ?: HapticIntensity.DEFAULT
    val constant = level.feedbackConstant
    onHapticPerformed?.invoke(constant)
    performHapticFeedback(constant)
}
```

**New `performIntensityHaptic()`:**
```kotlin
private fun performIntensityHaptic() {
    val level = mUtilSettings?.getHapticIntensity() ?: HapticIntensity.DEFAULT
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API 26+: use VibrationEffect
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(level.duration, level.amplitude)
            vibrator.vibrate(effect)
            onHapticPerformed?.invoke(level.amplitude) // test seam
        }
    } else {
        // API 25: fallback to performHapticFeedback (acceptable, FISH-017 baseline)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onHapticPerformed?.invoke(HapticFeedbackConstants.VIRTUAL_KEY) // test seam
    }
}
```

**Note:** Existing test seam `onHapticPerformed` callback remains; updated to pass amplitude (or fallback constant) for verification.

### 3. Settings & Persistence (No Change)

`UtilSettings.getHapticIntensity()` and `KEY_HAPTIC_INTENSITY` storage remain unchanged. The enum's ordinal-based serialization works as-is (LIGHT=0, MEDIUM=1, STRONG=2).

### 4. Dependencies

- **No new imports** beyond existing `android.os.VibrationEffect`, `android.os.Vibrator` (part of Android SDK)
- **No new permissions** (VIBRATE already in manifest)
- **Backward compat:** API 25 supported via fallback to `performHapticFeedback(VIRTUAL_KEY)` for all levels

---

## Testing Strategy

### Unit Tests (`HapticIntensityTest`)

**File:** `app/src/test/java/com/mckimquyen/util/HapticIntensityTest.kt`

1. **Enum properties exist and are distinct**
   - `LIGHT.duration == 20L && LIGHT.amplitude == 80`
   - `MEDIUM.duration == 40L && MEDIUM.amplitude == 128`
   - `STRONG.duration == 70L && STRONG.amplitude == 200`
   - `LIGHT.duration < MEDIUM.duration < STRONG.duration`
   - `LIGHT.amplitude < MEDIUM.amplitude < STRONG.amplitude`

2. **Enum `from(ordinal)` handles unknown/corrupt values**
   - `from(-1)` → `DEFAULT` (MEDIUM)
   - `from(99)` → `DEFAULT`
   - `from(1)` → `MEDIUM`

### Widget Tests (`FrmSettingsHapticIntensityWidgetTest`)

**File:** `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsHapticIntensityWidgetTest.kt`

1. **MaterialButtonToggleGroup renders 3 buttons** (Nhẹ, Vừa, Mạnh)
2. **Tapping each button saves correct value to UtilSettings**
   - Tap LIGHT → `getHapticIntensity()` == LIGHT
   - Tap MEDIUM → `getHapticIntensity()` == MEDIUM
   - Tap STRONG → `getHapticIntensity()` == STRONG
3. **Existing tests (toggle enable/disable per vibrate switches) still pass**

### Integration Tests (`LensViewHapticIntensityIntegrationTest`)

**File:** `app/src/androidTest/java/com/mckimquyen/views/LensViewHapticIntensityIntegrationTest.kt`

1. **Setup:** Inflate LensView, seed UtilSettings with each HapticIntensity level, mock `Vibrator` via spy or testable pattern
2. **Trigger hover/launch via simulated MotionEvent**
   - Simulate finger inside icon → triggers `performHoverVibration()`
   - Simulate finger lift on icon → triggers `performLaunchVibration()`
3. **Verify vibrator.vibrate(VibrationEffect) called**
   - Capture effect argument via Mockito/ArgumentCaptor or spy
   - Assert `effect.duration` and `effect.amplitude` match the set level
4. **Verify onHapticPerformed test seam fires** with correct value

### Smoke Test (TECNO KJ7 only)

**Device:** TECNO KJ7 (Android 14)  
**Steps:**
1. Open Settings → Lens → Haptic Intensity
2. Set to LIGHT, long-press an app icon on Home → observe brief tap
3. Set to MEDIUM, long-press → observe longer pulse
4. Set to STRONG, long-press → observe longest, most intense pulse
5. **Measure via `dumpsys vibrator_manager`:**
   - On device terminal: `dumpsys vibrator_manager | grep -A 20 vibrator` before/during each haptic
   - Confirm duration increases (timing logged by system)
   - Hand sensation should clearly distinguish all three levels

**No Pixel or emulator testing** (per device policy: TECNO KJ7 only).

---

## Error Handling & Edge Cases

1. **Vibrator unavailable or doesn't have vibrator capability**
   - `getSystemService(VIBRATOR_SERVICE)` returns null or `vibrator.hasVibrator()` is false
   - Silently skip vibration; no crash
   - Existing guards in `performHoverVibration()` and `performLaunchVibration()` already protect against null `UtilSettings`

2. **API < 26 (minSdk 25)**
   - `VibrationEffect.createOneShot()` available only on API 26+
   - Fallback to `performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)` for all levels
   - Acceptable: FISH-017 baseline confirmed VIRTUAL_KEY works cross-device
   - Document this in code comment

3. **Corrupt or future UtilSettings value**
   - `HapticIntensity.from(ordinal)` returns `DEFAULT` (MEDIUM) if unknown
   - No crash, graceful degradation

---

## Scope & Non-Scope

### In Scope
- Replace `HapticFeedbackConstants` with `VibrationEffect` in `HapticIntensity` enum
- Update `LensView.performIntensityHaptic()` to use VibrationEffect
- Unit, widget, integration test coverage
- TECNO KJ7 smoke test and duration verification

### Out of Scope
- User-customizable amplitude or duration (hardcoded per level)
- Settings UI changes (FrmSettings toggle already complete from FISH-017)
- Multi-lens per-lens haptic settings (defer to FISH-019 or later)
- Other haptic feedback sites (only `LensView` hover/launch in this story)

---

## Delivery Checklist

- [ ] HapticIntensity enum updated with duration/amplitude properties, `feedbackConstant` removed
- [ ] LensView.performIntensityHaptic() uses VibrationEffect.createOneShot() on API 26+, fallback on API 25
- [ ] HapticIntensityTest: 9 unit tests (properties, ordering, enum.from edge cases)
- [ ] FrmSettingsHapticIntensityWidgetTest: 5 widget tests (3 button levels, toggle persistence, switch interaction)
- [ ] LensViewHapticIntensityIntegrationTest: 6 integration tests (mock vibrator, hover/launch triggers, effect argument verification)
- [ ] All tests pass on TECNO KJ7 (JVM + instrumented)
- [ ] Lint 0 errors
- [ ] Smoke test on TECNO KJ7: LIGHT < MEDIUM < STRONG confirmed by hand + dumpsys
- [ ] Code review + audit > 9.0/10
- [ ] Spec reviewed and approved before implementation starts

