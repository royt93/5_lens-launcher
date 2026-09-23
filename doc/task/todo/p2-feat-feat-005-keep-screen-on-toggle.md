# FEAT-005 — Add a "Keep screen on" toggle

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
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

- [ ] New `UtilSettings.KEY_KEEP_SCREEN_ON` boolean key, default `false` (no
      behavior change for existing users).
- [ ] New switch row in `FrmSettings`, same pattern as the existing
      `swShowSearchBar` (read on init, save on toggle, reset-to-default path
      included).
- [ ] `ActHome` applies `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`
      when the setting is on and the flag is cleared in `onPause`/`onDestroy`
      (or toggled live via `getWindow().clearFlags(...)` when the user flips
      the setting while `ActHome` is already visible) — must not leak the flag
      into other activities (`ActSettings`, `ActAbout`, etc. are unaffected;
      confirmed by design since the flag is a per-`Window` property, not global).
- [ ] Battery-saver interaction: `FLAG_KEEP_SCREEN_ON` is a standard Android
      flag Android's own battery saver can still override (documented
      behavior, not something this app needs to special-case) — call this out
      in the settings row's description text so it isn't reported as a "bug"
      later.

## Required test matrix

- [ ] Unit: `UtilSettings` get/save round-trip for the new key, default value
      when unset (mirrors existing `UtilSettings` preference tests).
- [ ] Widget/UI: `FrmSettings` switch reflects stored value on init, toggling
      it calls `save()` with the new value, reset-to-defaults clears it back to
      `false` (mirrors the existing `swShowSearchBar` widget test).
- [ ] Integration: real `Window` flag state asserted on `ActHome` — flag set
      when the setting is on and `ActHome` resumes, cleared on pause/when the
      setting is off (mirrors `DISPLAY-001`'s real `Window`/`Display` integration
      test pattern for a similar per-Activity `Window` property).
- [ ] Smoke: enable the setting on the designated device, confirm the screen
      does not time out while sitting on the home screen for longer than the
      device's configured timeout; confirm normal timeout behavior returns
      inside another app (e.g. Settings) and with the toggle off.

## Verification and Definition of Done

- [ ] Toggle works both directions, live (no relaunch needed).
- [ ] No flag leakage into other activities.
- [ ] Translated setting label/description across all locales (this project
      treats `MissingTranslation` as build-blocking, per `LINT-005`).
