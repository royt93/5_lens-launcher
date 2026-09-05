# ID — Story title

| Field | Value |
|---|---|
| Type | One primary value: `fix` / `enhance` / `new` / `idea` / `exclusive` |
| Status | `todo` |
| Priority | `P0`–`P3` |
| Evidence | `confirmed` / `conditional` / `decision` / `idea` |
| Epic | Epic name |
| Estimate | Fibonacci SP |
| Risk | Low / Medium / High / Critical |
| Dependencies | IDs or `None` |

## Context and evidence

Describe the current behavior and cite exact repository paths/symbols.

## User story

As a …, I want …, so that …

## Acceptance criteria

- [ ] Observable, testable result.

## Implementation notes

State boundaries and intended approach without turning the story into a code dump.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches.
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [ ] Smoke tests pass on the designated Tecno device and the supported API baseline; record model, Android version, build and timestamp.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.
