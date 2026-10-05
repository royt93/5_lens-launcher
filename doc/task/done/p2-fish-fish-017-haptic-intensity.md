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
- [x] Unit, widget, integration, lint, full instrumented, device smoke, independent review.
- [ ] Audit > 9.0 (independent re-review score; push gate)

## Deviation from the design: STRONG uses LONG_PRESS on every API, not CONFIRM on API 30+

The spec called for `STRONG` → `CONFIRM` on API 30+, `LONG_PRESS` below. Device smoke on TECNO BG6 (Android 13) found this indistinguishable from Medium:

```
$ adb -s 118743744X002560 shell dumpsys vibrator_manager | grep -A2 effect
# CONFIRM  -> plays VibrationEffect.EFFECT_CLICK
# VIRTUAL_KEY -> plays VibrationEffect.EFFECT_CLICK
# LONG_PRESS  -> plays VibrationEffect.EFFECT_HEAVY_CLICK
```

Both `CONFIRM` and `VIRTUAL_KEY` resolved to the same underlying `EFFECT_CLICK` on this device/OS combination, so "Strong" felt identical to "Medium". `LONG_PRESS` resolved to `EFFECT_HEAVY_CLICK` and is distinguishable, and it exists on every supported API level (minSdk 25), so the fix also deletes the API-30 gate, the `sdkInt` parameter on `feedbackConstant`, and the `InlinedApi` suppression that gate required. Measured after the change on BG6: Light = `TEXTURE_TICK`, Medium = `CLICK`, Strong = `HEAVY_CLICK`. Commits `90c13bb` (silence `InlinedApi` on the then-still-API-gated `CONFIRM`) and `4aafcdf` (remove the gate, switch to `LONG_PRESS` on every API) record the two steps.

## Earlier device-found fix (before the final review round)

- **`ActHomeAutoExportWidgetTest` hang on BG6** (`4a3e542`): after the test's external `onNewIntent`, `ActivityScenario` stops matching every subsequent lifecycle event of `ActHome` (the Activity's intent no longer matches the scenario's start intent), so `scenario.close()` waited forever for a transition it was structurally never going to see. Proven with stage markers showing the test reaching `finish()` and never returning from `close()`. Fixed by not calling `close()` after the test has already finished the real Activity itself, keeping only the idle wait. Verified 3/3 OK (~3.8s, was an infinite hang); `com.mckimquyen.ui` package 249/249 OK with no class excluded.

## Follow-on fixes found during the final whole-branch review

- **Ellipsized labels** (`5f0b84f`): device smoke on TECNO BG6 showed the three buttons as `Lig... / Me... / Str...` — the row kept a 52dp icon-aligned indent plus the toggle-group's default button padding, leaving only ~58dp of text room per button. Fixed by dropping the indent and shrinking the button horizontal padding to 8dp. `labelsAreNeverEllipsized` (added RED first, confirmed failing with `Light` ellipsized by 2 characters) now measures the real laid-out buttons with `Layout.getEllipsisCount`. The final review round (I2) extended this same test to the three longest-label shipped locales — Russian (`Сильная`), French (`Moyenne`), Italian (`Leggera`) — by inflating `frm_settings.xml` under a locale-overridden `Configuration`/`createConfigurationContext`, the same pattern `ActSettingsLayoutTest` already used; all three resolve with zero ellipsis at a fixed `fontScale = 1.0`. A re-review finding (#5) caught that the test relied on `Locale.getDefault()` alone to cover English, which silently stopped covering English once run on a device whose own default locale is Vietnamese (this Pixel 7 Pro); fixed by listing `Locale.ENGLISH` explicitly and keeping `Locale.getDefault()` as an additional entry.
- **Physical Enter key in search (I1/M5, pre-existing bug surfaced while this story touched `ActHome`)**: on Android 13 (TECNO BG6) a physical Enter reaches the search box's editor-action listener as `ACTION_DOWN` first, and `TextView` only replays the matching `ACTION_UP` to the listener when the DOWN was consumed — `ActHome` matched `ACTION_UP` alone, so Enter never opened the first result (regressed since 2026-09-05, found via logcat: `eventAction=0 keyCode=66`, then no UP ever delivered). Fixed with `search/SearchEnterKeyPolicy.kt` (`418a99d`): launches on DOWN, consumes the paired UP, still launches on a lone UP as a defensive branch (not observed on any measured device: TextView replays an UP to the listener only after the listener consumed a DOWN). The final review found and fixed a second-order bug in that same fix: `ActHome` set `searchEnterDownHandled = outcome.getDownHandled()` even on a DOWN that decided to `LAUNCH` but found no first result (empty query), leaving the flag stuck `true` — since the listener then returned `false` for that DOWN, `TextView` never replays its UP, so the stale `true` carried into the *next* gesture's accounting. Fixed by only ever setting the flag from a `DOWN`/`UP` the listener actually consumed or launched, and explicitly resetting it to `false` on the no-first-result early return. Also added: `repeatCount` on `SearchEnterKeyPolicy.decide` so a held Enter does not re-launch: a repeat DOWN is `CONSUME`d only while the first DOWN launched, otherwise `IGNORE`d (see the round 3 note below; the first version consumed unconditionally, which was a bug). Covered by `SearchEnterKeyPolicyTest` (repeat after a launched DOWN consumed; repeat after a no-result DOWN ignored) and a new `AppSearchIntegrationTest.unmatchedEnterDoesNotStickTheDownFlagForTheNextSearch` (search `"unmatched-query"`, Enter DOWN+UP, then search `"launchable"`, Enter DOWN+UP → first result launches, search box clears — proving the flag never got stuck from the prior no-match Enter).
- **I3 — `FrmSettings.previewHaptic` test seam**: added `@VisibleForTesting internal var onPreviewHapticPerformed: ((Int) -> Unit)? = null`, invoked with the exact `feedbackConstant` immediately before `performHapticFeedback`, nulled in `onDestroyView`. Three new widget tests prove it fires on a real tap, stays silent under reduced motion (forced via `settings put global animator_duration_scale 0`, the same technique `ActHomeLensManagementWidgetTest` already used), and stays silent for every programmatic binding path (`onValuesUpdated`/`assignValues`, `onDefaultsReset`) — both already gated by the pre-existing `bindingHapticGroup` guard, now proven rather than assumed.
- **M1** — removed the dead `import android.os.Build` from `LensView.kt`: every call site already used the fully-qualified `android.os.Build.VERSION.SDK_INT`/`VERSION_CODES`.
- **M2** — `pickingALevel_persistsIt` seeds a different level via `UtilSettings.saveHapticIntensity` before each iteration's `launch`, so checking `idFor(level)` afterward is always a real change, not a level that happened to already be selected.
- **M4** — `UtilSettings.getHapticIntensity()` wraps `prefs.getInt(...)` in `runCatching { }.getOrDefault(DEFAULT_HAPTIC_INTENSITY)`, so a wrong-typed stored value (e.g. a `String` written under the int key, which throws `ClassCastException` from `SharedPreferences.getInt`) falls back to the default instead of crashing every caller. New Robolectric test stores a `String` under `KEY_HAPTIC_INTENSITY` and asserts `getHapticIntensity() == HapticIntensity.DEFAULT`.
- **M8** — `updateHapticIntensityEnabled()` now also sets `group.isEnabled = anyOn` on the `MaterialButtonToggleGroup` itself, alongside the existing per-child loop (the group container was never disabled, only its children).

## Evidence

Devices: **TECNO BG6 `118743744X002560`** (Android 13, standing owner-approved substitute after preferred KJ7 dropped) for development and pre-review evidence; **Pixel 7 Pro `2B051FDH3006MU`** (Android 17, explicit owner-approved one-off after the post-review fix) for final verification. Pixel remained otherwise banned; no other device was touched.

- JVM unit tests after the re-review fixes: **707/707**, 0 skipped/failures/errors.
- Lint after the re-review fixes: **0 errors / 8 warnings**, unchanged; all eight are pre-existing icon-asset warnings.
- Full instrumentation after the re-review fixes on Pixel 7 Pro: **477/477 OK**, 0 failures.
- Final run on **Samsung S24 Ultra `R5CX613VZBR`** (Android 16, owner-chosen replacement after the Pixel disconnected), HEAD with the round 4 fixes and the numpad fix: full instrumentation **478/478 OK**; `AppSearchIntegrationTest` 6/6, `FrmSettingsHapticIntensityWidgetTest` 13/13, `LensViewHapticIntensityIntegrationTest` 7/7; JVM 707/707; lint 0 errors / 8 warnings.
- Affected classes on Pixel 7 Pro: `LensViewHapticIntensityIntegrationTest` **7/7**, `FrmSettingsHapticIntensityWidgetTest` **13/13**, `AppSearchIntegrationTest` **5/5**, `ActHomeAutoExportWidgetTest` **1/1**.
- Manual smoke on BG6: Light/Medium/Strong row with Medium selected on a fresh install; each level maps distinctly after the LONG_PRESS fix (`TEXTURE_TICK` / `CLICK` / `HEAVY_CLICK`); both switches off greys out and disables the buttons (this smoke predated `group.isEnabled` — see M8), one switch on re-enables them; labels readable at default and 1.3x font scale; reset returns Medium.
- Manual smoke on Pixel 7 Pro: Vietnamese labels `Nhẹ / Vừa / Mạnh` render fully; selection persists (`haptic_intensity=1` after selecting Vừa); physical Enter (`KEYCODE_ENTER`) on search launched the first result on Android 17, proving the DOWN/UP policy on the newer platform too.
- Pixel 7 Pro's Android 17 vibrator history exposes durations but not semantic effect names: Light was shorter (17–24ms), while Medium and Strong were similar (~44–47ms). Android/OEM haptic constants are semantic hints, not cross-device amplitude guarantees; the setting reliably chooses three distinct constants, but only BG6 demonstrated three distinct hardware effects.

## Test rigor (re-review)

The re-review found the I1 regression test green on the buggy code and M8 untested. Both were proven non-vacuous on the Pixel 7 Pro by temporarily reverting the fix and running the test:

- `AppSearchIntegrationTest.unmatchedEnterDoesNotStickTheDownFlagForTheNextSearch` with the flag assigned *before* the `firstOrNull()` null check (the original order): **FAILS** with `searchEnterDownHandled must be reset after a no-match Enter DOWN`; with the fix restored: passes. It reads the flag through `ActHome.isSearchEnterDownHandledForTest()` (a `@VisibleForTesting` accessor), because the stale flag is unobservable through key dispatch alone.
- `FrmSettingsHapticIntensityWidgetTest.groupIsDisabledWhenBothHapticSwitchesAreOff` and `turningASwitchOn_reenablesTheGroupLive` with `group.isEnabled = anyOn` removed: both **FAIL** with `AssertionError`; with the line restored: pass.
- The positive preview-haptic tests now pin `animator_duration_scale` to 1 (try/finally restore) and assert `shouldReduceLensMotion` is false first, so a battery-saver/thermal device fails loudly instead of passing for the wrong reason.

## Review (final whole-branch review, 2026-10-04)

Findings I1, M5 (search Enter-key stuck-flag bug), I2 (ellipsis test only covered the default locale), I3 (no test seam for the preview haptic), I4 (missing backlog/feature-doc records), M1 (dead import), M2 (non-vacuous persistence test), M4 (unsafe pref read), M8 (group `isEnabled` not set) — all fixed, each described above with its own test. No findings left open from this round.

## Re-review rounds 2 to 4

- **Round 2** (8.7): I1 test vacuous, M8 untested, preview-haptic tests coupled to a global setting, contradictory acceptance checkbox, English locale not actually covered, BG6 smoke credited with post-M8 behavior, AutoExport hang misattributed, dead default parameter, `!!` in new test code. All fixed in `ada6cda` with RED proof on Pixel 7 Pro for I1 and M8.
- **Round 3** (8.8): the round-2 fix #8 (repeat DOWN returns the incoming flag) was itself wrong. With no results, the first DOWN returns false (no `enterDown`), then a repeat DOWN was CONSUMEd, so ActHome returned true, TextView set `enterDown` and replayed the UP on release; the policy read it as a lone UP and launched whatever result had appeared during the hold. Fixed: a repeat DOWN is CONSUMEd only while the first DOWN launched, otherwise IGNOREd. Proven on Pixel 7 Pro: `AppSearchIntegrationTest.heldEnterWithNoResultsNeverLaunchesOnRelease` FAILS on the previous policy (the search box was cleared, i.e. an app was launched on release) and passes after the fix; two new `SearchEnterKeyPolicyTest` cases pin the invariant.
- **Round 4** (8.9): no behavior bug; five key sequences confirmed (held Enter with and without results, lone UP, results appearing mid-hold, IME actions). Findings were evidence drift (instrumented counts, a stale sentence about the superseded repeat rule, the lone-UP rationale) and one pre-existing duplicate launch: `KEYCODE_NUMPAD_ENTER` was not matched, so a numpad Enter DOWN went down the IME-action branch (flag false), TextView replayed the UP, and the UP launched a second time. Fixed by matching `NUMPAD_ENTER` like `ENTER` (TextView treats them identically). Proven on Samsung S24 Ultra: `AppSearchIntegrationTest.numpadEnterLaunchesTheFirstResultExactlyOnce` FAILED against an ENTER-only policy with `a numpad Enter must launch exactly once expected:<1> but was:<2>` and passes after the fix; it counts launches through `ActHome.getSearchLaunchCountForTest()` (`@VisibleForTesting(otherwise = NONE)`), because history dedupes by component and cannot distinguish one launch from two. Two unit cases pin the same behavior.
- Disclosed: `labelsAreNeverEllipsized` measures at the device's `displayMetrics.widthPixels`, so its margin varies with device width; `AppSearchIntegrationTest` still carries its pre-existing `!!` in older tests (none added by this branch).

## Disclosed, not verified

- KJ7-specific behavior was not measured because KJ7 was offline during this story. BG6 is the owner-approved substitute and Pixel 7 Pro was a one-off owner-approved final verification device.
- The post-review instrumented run happened on Pixel 7 Pro, not BG6; BG6 full-suite evidence is the earlier 472/472 run at `4aafcdf` before the post-review commit.
- Strong may feel similar to Medium on some OEMs: Pixel 7 Pro/Android 17 did not show a clearly longer or stronger Strong vibration than Medium by duration. A guaranteed cross-device amplitude difference would require a custom `Vibrator` waveform path, intentionally out of scope.
- Other shipped locales beyond `en`, `ru`, `fr`, `it` were not individually measured for ellipsis; those four were picked as the longest-label candidates by inspection of `values-*/strings.xml`.

## Audit

Reviews: 8.3 (whole branch), 8.7 (re-review of `894440b`), 8.8 (third review of `ada6cda`), 8.9 (fourth review of `25f834d`). Round 3 found a real regression in the repeat-DOWN handling and round 4 a pre-existing numpad double launch (both fixed, see below); the branch awaits one more independent review. Push only if the evidence-based audit score is above 9.0/10.
