# FEAT-001 — Add search-first app navigation

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fast navigation |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | CORE-001, A11Y-001 |

## User story and value

As a user with many apps, I want instant local search with recent/favorite ranking so I can launch without scanning the full grid.

## Acceptance criteria

- [ ] Search labels and package aliases locally with accent/case-insensitive matching and deterministic ranking.
- [ ] Support keyboard/IME, clear/back, empty/no-result and TalkBack flows.
- [ ] Recent/favorite signals are local, resettable and optional.
- [ ] Search opens within 150 ms on the agreed Tecno baseline and returns incremental results smoothly.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover normalization/ranking; widget tests cover input/states/accessibility.
- [ ] Integration tests launch real results after package changes and process recreation.
- [ ] Tecno smoke covers 300+ synthetic apps, locale switching and offline use.
