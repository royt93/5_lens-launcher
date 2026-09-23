# UI-021 — Adopt the predictive back gesture

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Modern navigation |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`minSdkVersion 25` / `targetSdkVersion 37`. `AndroidManifest.xml` does not set
`android:enableOnBackInvokedCallback`, and no code implements
`OnBackAnimationCallback`/`OnBackPressedCallback` with a predictive-back-aware
progress handler. The app relies on the legacy `OnBackPressedCallback`
(confirmed in use for the home search overlay per `UI-010`) without the
Android 13+ swipe-back preview. This is the one still-missing piece of an
otherwise thorough Material You pass (`UI-001`..`UI-020`).

## User story

As a user on Android 13+, I want the system's predictive-back preview
(a peek of the underlying screen while swiping) to work in this launcher,
matching the rest of the OS.

## Acceptance criteria

- [ ] `android:enableOnBackInvokedCallback="true"` set app-wide (minSdk 25 devices
      silently ignore it; no behavior regression below API 33).
- [ ] Every current back-press consumer identified via `OnBackPressedCallback`
      audited for predictive-back readiness (`ActHome`'s search overlay per
      `UI-010`, any dialog/bottom-sheet dismiss handlers, `ActSettings` tab/back
      stack) — migrated to `BackEventCompat`-aware handling where a custom
      close animation exists (the search overlay), left as plain callbacks
      elsewhere.
- [ ] Search overlay close gesture shows the real predictive-back shrink/fade
      preview instead of an abrupt dismiss.
- [ ] No regression to `UI-010`'s existing back-button-closes-search fix.

## Required test matrix

- [ ] Unit: N/A — this story is gesture/animation wiring with no pure logic to unit test; reviewed as not applicable.
- [ ] Widget/UI: verify `OnBackPressedCallback`/predictive-back callback is registered and enabled/disabled correctly across the search-open/closed states (mirrors the existing `UI-010` back-handling test).
- [ ] Integration: real back-gesture simulation on an API 34+ emulator/device confirming the preview animates and the final state matches a normal back press.
- [ ] Smoke: swipe-back gesture on the designated device (predictive back requires a real gesture, not `adb shell input keyevent KEYCODE_BACK`) — record video or `uiautomator` bounds before/after.

## Verification and Definition of Done

- [ ] Predictive back preview visibly works on an API 34+ device with gesture nav.
- [ ] 3-button nav and API < 33 behavior unchanged (regression-checked).
- [ ] Lint clean, no new warnings.
