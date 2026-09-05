# STORE-002 — Make project autosave revision-safe

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Store tooling reliability |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | STORE-001 |

## Context and evidence

`useProject()` launches asynchronous debounced saves without a single-flight queue or revision. Slow earlier writes and multiple tabs can overwrite newer state.

## User story

As a screenshot designer, I need autosave never to silently lose the newest edit.

## Acceptance criteria

- [ ] Add monotonic project revision/ETag and reject stale writes with a visible conflict state.
- [ ] Serialize autosaves and flush the latest state on page lifecycle boundaries when possible.
- [ ] Keep file authoritative while clearly reporting local-cache-only state.
- [ ] Migrate existing schema-v2 projects without losing decks, transforms, locales or connected-canvas choice.

## Required test matrix

- [ ] Unit tests cover deterministic logic, validation, state and failure branches; otherwise record a reviewed `Not applicable` reason.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle; otherwise record a reviewed `Not applicable` reason.
- [ ] Integration tests cover affected subsystem boundaries and process/persistence behavior; otherwise record a reviewed `Not applicable` reason.
- [ ] Smoke test the exact candidate on the designated Tecno device and record model, Android/HiOS version, build SHA, network state, timestamp and log evidence.

## Verification and Definition of Done

- [ ] Deterministic tests simulate slow/out-of-order responses and two tabs.
- [ ] Crash/interrupted-write test leaves either old or new valid JSON, never a partial file.
- [ ] Undo/redo and reset behavior remain correct across save conflicts.
