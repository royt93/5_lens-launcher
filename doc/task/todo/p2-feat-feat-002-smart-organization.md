# FEAT-002 — Add folders, favorites and pinned zones

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | App organization |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | CORE-001, DB-001 |

## User story and value

As a user, I want stable personal zones and optional local categories without losing the Fisheye spatial model.

## Acceptance criteria

- [ ] Pin/unpin and folder/category edits persist with stable component IDs.
- [ ] Automatic suggestions never rearrange committed user placement without approval.
- [ ] Uninstalled/reinstalled apps have defined cleanup/recovery behavior.
- [ ] Drag, keyboard and accessibility actions provide equivalent control.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Unit tests cover ordering/conflicts/migration; widget tests cover editing states.
- [ ] Integration tests cover package removal, restore and sorting interaction.
- [ ] Tecno smoke verifies touch targets, long press, rotation/process death and performance.
