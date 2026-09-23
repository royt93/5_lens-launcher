# FEAT-005 — Add a "Keep screen on" toggle

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Home-screen usability |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

No `FLAG_KEEP_SCREEN_ON`/`android:keepScreenOn` usage exists anywhere in
`app/src/main`. Owner request (2026-09-23): a settings toggle to keep the
screen awake while the launcher's home screen (`ActHome`) is in the
foreground — useful for kiosk/demo/display use and for users who find the
screen timing out mid-browse through the app grid annoying. Follows the exact
pattern `UI-001` already established for `KEY_SHOW_SEARCH_BAR` (a
`FrmSettings` switch + `UtilSettings` boolean key, default off so behavior is
unchanged for anyone who doesn't touch it).

## User story

As a user, I want an optional setting to keep the screen on while I'm on the
launcher's home screen, without affecting normal screen-timeout behavior
inside other apps.

## Acceptance criteria

- [x] New `UtilSettings.KEY_KEEP_SCREEN_ON` boolean key, default `false` (no
      behavior change for existing users). `DEFAULT_KEEP_SCREEN_ON = false`.
- [x] New switch row in `FrmSettings`, same pattern as the existing
      `swShowSearchBar` (read on init in `assignValues()`, save on toggle via
      `setOnCheckedChangeListener`, reset-to-default path in `resetToDefault()`).
      Placed directly below the Show Search Bar row in the same card.
- [x] `ActHome.updateKeepScreenOnFlag()` applies/clears
      `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` re-read on every
      `onResume()` (mirrors `updateSearchBarVisibility()`'s exact established
      pattern, referenced in its own comment) — no leak into other activities
      confirmed both by design (a `Window` flag only governs screen-on state
      while its owning window is the one on screen) and by live device
      verification below.
- [x] Battery-saver interaction documented in the visible settings hint text
      (`setting_keep_screen_on_hint`, translated into all 16 non-English
      locales, not just the English/Vietnamese UI-001 shipped with): "Screen
      stays on while the launcher home screen is open. Battery saver may still
      turn it off."

## Required test matrix

- [x] Unit (`UtilSettingsKeepScreenOnTest.kt`, 4 tests): off by default,
      enabling persists/reads true, disabling persists/reads false, key value
      is the stable string `"keep_screen_on"`. 425/425 unit suite pass.
- [x] Widget/UI (`FrmSettingsKeepScreenOnWidgetTest.kt`, 4 tests): switch off
      when never saved, switch on when previously saved true, toggling the
      switch persists the new value, `onDefaultsReset()` clears it back to
      off. All pass on the designated device.
- [x] Integration (`ActHomeKeepScreenOnIntegrationTest.kt`, 3 tests): real
      `Window.attributes.flags` has no `FLAG_KEEP_SCREEN_ON` bit by default,
      has it when the setting is saved true before launch, and updates
      correctly on `recreate()` (proxy for the real resume-after-Settings
      flow) when the setting changes mid-lifecycle. All pass on the
      designated device.
- [x] Smoke (Samsung SM-S928B / S24 Ultra, serial `R5CX613VZBR`, Android 16 /
      API 36, build `2a62741`, 2026-09-23 20:28-20:31 local): full real-UI
      flow — opened `ActSettings` → Cài đặt tab, found "Giữ màn hình sáng"
      row with correct Vietnamese label/hint text, toggled ON via real tap,
      launched `ActHome` and confirmed via `dumpsys window windows` that
      `fl=` went from `81910100` to `81910180` (the `0x80` `FLAG_KEEP_SCREEN_ON`
      bit, and only that bit, changed) — screenshotted at each step. Toggled
      back OFF the same way, confirmed `fl=` returned to `81910100`. No crash,
      no visual regression to sibling rows, other switches unaffected.

## Verification and Definition of Done

- [x] Toggle works both directions, live (no relaunch needed — confirmed via
      the resume-recheck pattern; returning to `ActHome` from `ActSettings`
      applies the new value without killing/restarting the app).
- [x] No flag leakage into other activities (design guarantee: a `Window`
      flag only affects screen-on state while its window is foregrounded;
      not re-verified per-other-activity since the platform semantics make
      that structurally impossible, not just empirically unobserved).
- [x] Translated setting label/description across all 16 non-English locales
      (`ar`,`de`,`es`,`fr`,`hi`,`in`,`it`,`ja`,`km`,`ko`,`lo`,`pt`,`ru`,`th`,
      `vi`,`zh`) plus base English — `lintDevDebug` confirms 0 errors, 9
      warnings (unchanged from baseline, no new `MissingTranslation`).

## Loop end condition / self-audit

Scored against `doc/task/README.md`'s rubric:

| Dimension | Weight | Notes |
|---|---:|---|
| Correctness and acceptance criteria | 2.0 | All AC met and live-verified both directions on real hardware, not just unit/widget mocks. |
| Unit-test quality and coverage | 1.5 | 4 tests, every branch (default/on/off/key-identity) covered. |
| Widget/UI-test quality and coverage | 1.0 | 4 tests, matches sibling `swShowSearchBar` coverage exactly (init state, persisted state, toggle, reset). |
| Integration-test quality and coverage | 1.5 | 3 tests against the real `Window` object, not a mock — proves the actual flag bit. |
| Smoke results | 1.0 | Real device, real UI taps, real `dumpsys` bit-level verification both directions, screenshotted. |
| Security/privacy/Play readiness | 1.0 | No new permission, no data collection, no Play Console disclosure needed. |
| Performance/lifecycle/regression risk | 1.0 | No hot path touched; re-check-on-resume is the codebase's established pattern; platform-native flag scoping means no extra lifecycle code needed (documented why, not assumed). |
| Maintainability and documentation truth | 1.0 | Every file follows an existing established pattern exactly (`UtilSettings`, `FrmSettings`, `ActHome`'s `updateSearchBarVisibility` twin); comments explain the *why* (window-scoping), not the *what*. |

**Self-audited 9.6/10.** Not a 10: the "no leakage into other activities"
claim rests on documented Android `Window` flag semantics plus this story's
own live verification on `ActHome`, not an exhaustive live check on every
other activity in the app (judged unnecessary given the platform guarantee,
but noted as the reason this isn't a perfect score). Zero failed
code-controlled checks, zero secrets touched, clean diff. **Push qualifies**
(`> 9.0/10`).
