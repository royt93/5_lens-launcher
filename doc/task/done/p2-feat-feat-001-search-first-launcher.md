# FEAT-001 — Add search-first app navigation

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fast navigation |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | CORE-001, A11Y-001 |

## User story and value

As a user with many apps, I want instant local search with recent/favorite ranking so I can launch without scanning the full grid.

## Acceptance criteria

- [x] Search labels and package aliases locally with accent/case-insensitive matching and deterministic ranking.
- [x] Support keyboard/IME, clear/back, empty/no-result and TalkBack flows.
- [x] Recent/favorite signals are local, resettable and optional. Recent launches are persisted locally and resettable; favorite ranking is supported by the engine and becomes user-facing with FEAT-002.
- [x] Search opens within 150 ms on the agreed Tecno baseline and returns incremental results smoothly.

## Required test matrix

- [x] Unit tests cover deterministic logic, validation, state, ranking, visibility filtering and the 400-app performance budget.
- [x] Widget/UI tests cover input, clear, empty/no-result rendering, recreation and back behavior.
- [x] Integration tests cover local recent-history persistence/reset and the full connected Android suite.
- [x] Smoke tested the exact candidate on the designated Tecno device with model, Android/HiOS version, build SHA, network state, timestamp and log evidence recorded in the audit.

## Verification and Definition of Done

- [x] Unit tests cover normalization/ranking; widget tests cover input, visible states and lifecycle restoration.
- [x] Integration and physical-device checks launch real results after candidate installation/package refresh and process recreation.
- [x] Tecno smoke covers 400 synthetic apps, English/Vietnamese locale switching and offline use.

## Implementation and audit notes — 2026-09-05

- Added a search overlay to `ActHome` with IME action, clear/back handling, empty/no-result states, bounded scrollable results and localized strings for all 16 supported locales.
- Added deterministic local matching for labels, package aliases and component names, including Vietnamese `đ` and Unicode combining-mark normalization.
- Added a local-only MRU store with an explicit clear action; no search or launch history is sent over the network.
- Reused the existing biometric-aware `UtilApp.launchComponent` path, so locked apps retain their authentication behavior.
- Audit fixed a pre-existing stale-snapshot seam: empty app lists and label/component/open-count changes now refresh both the lens and active search results.
- Re-audit fixed missing `appsUpdated`/`appsEdited` subscriptions, filtered-empty lens state, stable-ID collision risk, internal-whitespace normalization, duplicate MRU ranking and physical Enter handling.
- Verification: 150 unit tests passed, lint reported 0 errors, and 52 connected tests passed exclusively on TECNO KJ7 / Android 14 / HiOS 13.6.0.
- Physical-device benchmark across 400 synthetic apps: 56.02 ms average, 74 ms p95 and 89 ms maximum over 50 measured searches.
- Physical smoke verified English/Vietnamese resources, offline search, TalkBack focus/input/speech activity, recent-history restoration after force-stop, and launching the real Android Settings result.
- Full audit record: `doc/task/AUDIT_FEAT_001_2026-09-05.md`.
