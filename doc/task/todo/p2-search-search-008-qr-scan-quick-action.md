# SEARCH-008 — QR/barcode scan quick action

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Search expansion |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | SEARCH-006 (already shipped, same delegation pattern) |

## Context and evidence

`SEARCH-006` established the pattern this should follow exactly: a typed
query fires an implicit `Intent` and hands off entirely to whatever app the
user already has installed to handle it (`ACTION_WEB_SEARCH` → default
browser), so this app makes no network call and adds no dependency. There is
no reliable single cross-device implicit-intent action for "scan a barcode"
the way there is for web search, so this needs a short compatibility check
(see Acceptance criteria) rather than a one-line intent fire — but the
**principle stays identical**: delegate to an installed app, do not bundle
ML Kit/ZXing or any scanning library into this app.

## User story

As a user, I want to type "scan"/"qr" in search and jump straight into a
barcode scan using whatever scanner app I already have, without this
launcher bundling its own scanning code.

## Acceptance criteria

- [ ] `QuickActionEngine` entry for `scan`/`qr`/`barcode` (+ Vietnamese
      equivalents).
- [ ] Attempts a known implicit scan intent (e.g. `SCAN` action used by
      several scanner apps); if nothing resolves it (`PackageManager`
      `resolveActivity` check first, same defensive pattern `SEARCH-003`
      already uses for `LauncherApps` `SecurityException` fallback), falls
      back to opening Play Store search for "QR scanner" rather than silently
      no-op'ing or crashing.
- [ ] Zero new library dependency added to `app/build.gradle` for this
      feature — if no implicit intent can reliably reach an installed
      scanner, that is a signal to descope to the Play Store fallback only,
      not a signal to bundle ML Kit.
- [ ] No CAMERA permission requested by this app itself — the delegated app
      owns that permission entirely.

## Required test matrix

- [ ] Unit: `QuickActionEngine` keyword resolution, resolve-vs-no-resolve
      branch logic (mock `PackageManager.resolveActivity`).
- [ ] Widget/UI: row renders; tap fires the correct branch (scanner intent vs.
      Play Store fallback).
- [ ] Integration: real `PackageManager` query against whatever's on the test
      device (may need a scanner app installed on the smoke device
      specifically to exercise the success path — disclose if none available
      and only the fallback path was verified).
- [ ] Smoke: on the designated device, confirm the fallback path at minimum;
      confirm the direct-scan path if a compatible scanner app is present.

## Verification and Definition of Done

- [ ] Live-verified on the designated device (at least the fallback path;
      direct-scan path if device state allows).
- [ ] Confirmed no new dependency landed in `app/build.gradle` for this story.
