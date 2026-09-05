# REL-001 — Clear Android lint release blockers

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P0 |
| Evidence | confirmed |
| Epic | Release readiness |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |
| External prerequisites | Translation review |

## Context and evidence

`./gradlew lintDevDebug` currently fails with 5 errors: two missing `android.permission.VIBRATE` findings in `ActVipManagement.kt`, `android:tint` in two language layouts, and `no_internet` missing from 15 locales.

## User story

As a release engineer, I need lint to be green so actionable regressions cannot be hidden by a broken gate.

## Acceptance criteria

- [ ] Declare/use vibration safely or replace it with permission-free view haptic feedback.
- [ ] Replace incompatible tint attributes with AppCompat equivalents.
- [ ] Add reviewed translations for every shipped locale; do not suppress a translatable string.
- [ ] Triage 167 warnings into follow-up stories or justified local suppressions; do not create a baseline that hides current errors.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] `./gradlew testDevDebugUnitTest lintDevDebug` passes.
- [ ] VIP feedback works on API 25 and current target API.
- [ ] Language-switch smoke test covers at least English, Vietnamese, Arabic, and one CJK locale.
