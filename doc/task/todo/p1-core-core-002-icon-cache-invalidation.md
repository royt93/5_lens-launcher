# CORE-002 — Correct icon cache identity and invalidation

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Launcher rendering |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | CORE-001 |

## Context and evidence

`BitmapCache.put()` keeps the first bitmap for a key and refuses replacement. Keys use package name only, so icon-pack changes, package upgrades, and multiple launcher activities in one package can display stale or colliding icons.

## User story

As a user, I need icon changes to appear immediately and consistently in both Fisheye and app-management views.

## Acceptance criteria

- [ ] Cache key includes `ComponentName`, icon-pack identity/version and package version/update token.
- [ ] Define overwrite and invalidation for package changed/removed, icon-pack change, locale change and memory pressure.
- [ ] Keep one bounded bitmap owner and never recycle a bitmap still used by a view.
- [ ] Sorting does not drop apps whose bitmap is temporarily unavailable.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Tests cover same-package activities, icon-pack switching, package replacement and eviction.
- [ ] `LensView` and `AppAdapter` show the same current icon.
- [ ] Memory profile stays within the agreed cache budget.
