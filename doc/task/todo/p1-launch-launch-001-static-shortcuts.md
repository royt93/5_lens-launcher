# LAUNCH-001 — Repair and test static launcher shortcuts

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Launcher entry points |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`app/src/main/res/xml/shortcuts.xml` targets `com.mckimquyen`, while the application ID is `com.mckimquyen.lenslauncher`. Shortcuts may fail to resolve.

## User story

As a user, I need long-press shortcuts to open the correct launcher screen every time.

## Acceptance criteria

- [ ] Target the actual application component without duplicating a fragile package string.
- [ ] Every shortcut has a localized short/long label and correct exported destination.
- [ ] App and Settings shortcuts resolve on dev and production variants.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Instrumentation test resolves and launches every shortcut intent.
- [ ] Manual long-press smoke test passes on API 25 and a current Android version.
