# STORE-005 — Add store-asset preflight validation

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Store automation |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | STORE-003, STORE-004 |

## User story and value

As a publisher, I want actionable checks before export so invalid or unreadable assets are caught before upload.

## Acceptance criteria

- [ ] Validate required dimensions, slide count, safe area, overflow, missing screenshot, locale completeness and contrast.
- [ ] Link each error to the affected device/locale/slide/element and block only truly invalid exports.
- [ ] Warnings are suppressible with a recorded reason; errors are not silently bypassed.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Rule-engine unit tests and editor widget tests cover each diagnostic.
- [ ] Integration fixtures include valid/invalid multi-device projects and exported bundle checks.
- [ ] Manual Play Console smoke accepts the representative output.
