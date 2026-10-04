# FISH-017 — Haptic intensity setting

| Field | Value |
|---|---|
| Type | feature |
| Status | done |
| Priority | P2 |
| Evidence | confirmed (code audit + device measurement) |
| Epic | Fisheye Smart |
| Estimate | 3-5 SP |
| Risk | Low |
| Dependencies | none |

## Owner decision (2026-10-04)

Hover and launch haptics already existed (`KEY_VIBRATE_APP_HOVER` default off, `KEY_VIBRATE_APP_LAUNCH` default on), both hardcoded to `HapticFeedbackConstants.VIRTUAL_KEY`. Owner picked "haptic intensity" over a new hover tick. Design: `docs/superpowers/specs/2026-10-04-fish-017-haptic-intensity-design.md`. Plan: `docs/superpowers/plans/2026-10-04-fish-017-haptic-intensity.md`.

## Acceptance criteria

- [x] One shared Light / Medium / Strong setting drives both the hover and launch haptic in `LensView`.
- [x] `MEDIUM` = `VIRTUAL_KEY`, the pre-existing behavior, and is the default — an install that never touches the setting is unchanged.
- [x] Global key (`KEY_HAPTIC_INTENSITY`, no `_<lensId>` suffix): device hardware preference, not per-lens.
- [x] `FrmSettings` shows a `MaterialButtonToggleGroup` below the two haptic switches; selecting a level saves it and plays one preview haptic; the group (and every child button) is disabled while both switches are off; reset-to-defaults restores Medium.
- [x] Corrupt/out-of-range/wrong-typed stored values fall back to the default instead of crashing.
- [x] Labels never ellipsize, in the default locale and in the longest-label shipped locales.
- [x] Unit, widget, integration, lint, full instrumented, device smoke, independent review, audit > 9.0.

## Deviation from the design: STRONG uses LONG_PRESS on every API, not CONFIRM on API 30+

The spec called for `STRONG` → `CONFIRM` on API 30+, `LONG_PRESS` below. Device smoke on TECNO BG6 (Android 13) found this indistinguishable from Medium:

```
$ adb -s 118743744X002560 shell dumpsys vibrator_manager | grep -A2 effect
# CONFIRM  -> plays VibrationEffect.EFFECT_CLICK
# VIRTUAL_KEY -> plays VibrationEffect.EFFECT_CLICK
# LONG_PRESS  -> plays VibrationEffect.EFFECT_HEAVY_CLICK
```

Both `CONFIRM` and `VIRTUAL_KEY` resolved to the same underlying `EFFECT_CLICK` on this device/OS combination, so "Strong" felt identical to "Medium". `LONG_PRESS` resolved to `EFFECT_HEAVY_CLICK` and is distinguishable, and it exists on every supported API level (minSdk 25), so the fix also deletes the API-30 gate, the `sdkInt` parameter on `feedbackConstant`, and the `InlinedApi` suppression that gate required. Measured after the change on BG6: Light = `TEXTURE_TICK`, Medium = `CLICK`, Strong = `HEAVY_CLICK`. Commits `90c13bb` (silence `InlinedApi` on the then-still-API-gated `CONFIRM`) and `4aafcdf` (remove the gate, switch to `LONG_PRESS` on every API) record the two steps.

## Follow-on fixes found during the final whole-branch review

- **Ellipsized labels** (`5f0b84f`): device smoke on TECNO BG6 showed the three buttons as `Lig... / Me... / Str...` — the row kept a 52dp icon-aligned indent plus the toggle-group's default button padding, leaving only ~58dp of text room per button. Fixed by dropping the indent and shrinking the button horizontal padding to 8dp. `labelsAreNeverEllipsized` (added RED first, confirmed failing with `Light` ellipsized by 2 characters) now measures the real laid-out buttons with `Layout.getEllipsisCount`. The final review round (I2) extended this same test past the default English strings to the three longest-label shipped locales — Russian (`Сильная`), French (`Moyenne`), Italian (`Leggera`) — by inflating `frm_settings.xml` under a locale-overridden `Configuration`/`createConfigurationContext`, the same pattern `ActSettingsLayoutTest` already used; all three resolve with zero ellipsis at a fixed `fontScale = 1.0`.
- **Physical Enter key in search (I1/M5, pre-existing bug surfaced while this story touched `ActHome`)**: on Android 13 (TECNO BG6) a physical Enter reaches the search box's editor-action listener as `ACTION_DOWN` first, and `TextView` only replays the matching `ACTION_UP` to the listener when the DOWN was consumed — `ActHome` matched `ACTION_UP` alone, so Enter never opened the first result (regressed since 2026-09-05, found via logcat: `eventAction=0 keyCode=66`, then no UP ever delivered). Fixed with `search/SearchEnterKeyPolicy.kt` (`418a99d`): launches on DOWN, consumes the paired UP, still launches on a lone UP for devices that only deliver UP. The final review found and fixed a second-order bug in that same fix: `ActHome` set `searchEnterDownHandled = outcome.getDownHandled()` even on a DOWN that decided to `LAUNCH` but found no first result (empty query), leaving the flag stuck `true` — since the listener then returned `false` for that DOWN, `TextView` never replays its UP, so the stale `true` carried into the *next* gesture's accounting. Fixed by only ever setting the flag from a `DOWN`/`UP` the listener actually consumed or launched, and explicitly resetting it to `false` on the no-first-result early return. Also added: `repeatCount` on `SearchEnterKeyPolicy.decide` so holding Enter down repeats `CONSUME`s instead of re-launching. Covered by a new `SearchEnterKeyPolicyTest` case (repeat > 0 → CONSUME) and a new `AppSearchIntegrationTest.unmatchedEnterDoesNotStickTheDownFlagForTheNextSearch` (search `"unmatched-query"`, Enter DOWN+UP, then search `"launchable"`, Enter DOWN+UP → first result launches, search box clears — proving the flag never got stuck from the prior no-match Enter).
- **`ActHomeAutoExportWidgetTest` hang on BG6** (`4a3e542`): after the test's external `onNewIntent`, `ActivityScenario` stops matching every subsequent lifecycle event of `ActHome` (the Activity's intent no longer matches the scenario's start intent), so `scenario.close()` waited forever for a transition it was structurally never going to see. Proven with stage markers showing the test reaching `finish()` and never returning from `close()`. Fixed by not calling `close()` after the test has already finished the real Activity itself, keeping only the idle wait. Verified 3/3 OK (~3.8s, was an infinite hang); `com.mckimquyen.ui` package 249/249 OK with no class excluded.
- **I3 — `FrmSettings.previewHaptic` test seam**: added `@VisibleForTesting internal var onPreviewHapticPerformed: ((Int) -> Unit)? = null`, invoked with the exact `feedbackConstant` immediately before `performHapticFeedback`, nulled in `onDestroyView`. Three new widget tests prove it fires on a real tap, stays silent under reduced motion (forced via `settings put global animator_duration_scale 0`, the same technique `ActHomeLensManagementWidgetTest` already used), and stays silent for every programmatic binding path (`onValuesUpdated`/`assignValues`, `onDefaultsReset`) — both already gated by the pre-existing `bindingHapticGroup` guard, now proven rather than assumed.
- **M1** — removed the dead `import android.os.Build` from `LensView.kt`: every call site already used the fully-qualified `android.os.Build.VERSION.SDK_INT`/`VERSION_CODES`.
- **M2** — `pickingALevel_persistsIt` seeds a different level via `UtilSettings.saveHapticIntensity` before each iteration's `launch`, so checking `idFor(level)` afterward is always a real change, not a level that happened to already be selected.
- **M4** — `UtilSettings.getHapticIntensity()` wraps `prefs.getInt(...)` in `runCatching { }.getOrDefault(DEFAULT_HAPTIC_INTENSITY)`, so a wrong-typed stored value (e.g. a `String` written under the int key, which throws `ClassCastException` from `SharedPreferences.getInt`) falls back to the default instead of crashing every caller. New Robolectric test stores a `String` under `KEY_HAPTIC_INTENSITY` and asserts `getHapticIntensity() == HapticIntensity.DEFAULT`.
- **M8** — `updateHapticIntensityEnabled()` now also sets `group.isEnabled = anyOn` on the `MaterialButtonToggleGroup` itself, alongside the existing per-child loop (the group container was never disabled, only its children).

## Evidence

Device: **TECNO BG6 `118743744X002560`**. TECNO KJ7 `115333744A005844` (the preferred device) dropped off `adb devices` partway through this story; BG6 is the standing owner-approved substitute for exactly this situation, not a one-off exception — used for every build/install/test/smoke run from that point on, including this final review round.

- JVM unit tests: 701→702 across the story's own commits (`e5979b4` baseline, `418a99d` at 702/702); final review round's changes (I1/M5, I3, M2, M4, M8, M1) add further unit cases — see the final full run recorded in `.superpowers/sdd/final-fix-report.md` for the exact post-review total.
- Lint: 0 errors / 8 warnings, unchanged.
- Full instrumentation on BG6: 472/472 OK at `4aafcdf` (pre-review baseline); re-verified after the review round — see the final report for the exact post-review total.
- `FrmSettingsHapticIntensityWidgetTest`: 10/10 before the review round (widget), extended by this round's I2/I3/M2 changes.
- `LensViewHapticIntensityIntegrationTest`: unaffected by this round's changes, re-run as part of the full suite regression.
- `AppSearchIntegrationTest`: 3/3 before the review round, extended by one new regression test (I1/M5).
- Manual smoke on TECNO BG6 (pre-review): Light/Medium/Strong row with Medium selected on a fresh install; each level vibrates distinctly after the LONG_PRESS fix; both switches off greys out and disables all three buttons, one switch on re-enables them; labels readable at default and 1.3x font scale; reset-to-defaults returns to Medium.

## Review (final whole-branch review, 2026-10-04)

Findings I1, M5 (search Enter-key stuck-flag bug), I2 (ellipsis test only covered the default locale), I3 (no test seam for the preview haptic), I4 (missing backlog/feature-doc records), M1 (dead import), M2 (non-vacuous persistence test), M4 (unsafe pref read), M8 (group `isEnabled` not set) — all fixed, each described above with its own test. No findings left open from this round.

## Disclosed, not verified

- KJ7-specific behavior for the LONG_PRESS/CONFIRM deviation was not re-measured on KJ7 itself (it was offline for this entire story); the `dumpsys vibrator_manager` evidence above is BG6-only. The fix (LONG_PRESS on every API, no version gate) does not depend on which device measured it, but a second confirmation on KJ7 once it reconnects would close this out fully.
- Other 15 shipped locales beyond the 4 now covered by `labelsAreNeverEllipsized` (en, ru, fr, it) were not individually measured for ellipsis; those four were picked as the longest-label candidates by inspection of `values-*/strings.xml`, not by an exhaustive per-locale pixel measurement.

## Audit

See `.superpowers/sdd/final-fix-report.md` for this round's final test counts and score.
