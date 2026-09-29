# FISH-013 — Auto-export-lens intent extra can be silently lost

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP (needs its own design pass before implementation — not a one-line fix) |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Found during the `FISH-012` transient-state audit (2026-09-29). `FrmLens`'s
share button (`ui/FrmLens.kt:141-146`) starts `ActHome` carrying a one-shot
`EXTRA_AUTO_EXPORT_LENS` intent extra. `ActHome.consumeAutoExportExtra`
(`ui/ActHome.java:313-314`) **removes** that extra from the `Intent` the moment
it reads it — before the export it guards has actually run
(`ui/ActHome.java:521-523`, `:575-578`). The story's own comment at `:570-574`
already discloses a related bind-timing race this exact design has to work
around.

If the process dies (or that disclosed race resolves unluckily) in the window
between "extra consumed" and "export actually completes", the share request
is gone with no way to retry it — the `Intent` no longer carries the flag, and
nothing else remembers it was requested. The user taps "Share lens image" and,
on some devices/timings, nothing ever happens.

## User story

As a user tapping "Share lens image" in `FrmLens`, I want the share to either
happen or fail with a visible message, never silently do nothing.

## Acceptance criteria

- [ ] The one-shot request survives a process kill between "extra consumed"
      and "export completes" — either by not clearing the request until the
      export truly finishes (success or failure), or by persisting a pending
      flag the same way `FISH-012` persists the pinch adjustment (a
      pending-key + resurrect-on-next-bind pattern — reuse that shape if it
      fits once actually designed).
- [ ] On unrecoverable failure (e.g. the requesting lens no longer exists),
      the user sees an explicit message rather than silence.

## Implementation notes

**Needs its own brainstorm/design pass** before implementation — the two
candidate shapes above trade off differently (persisting *intent* to export
vs. deferring when the extra is cleared), and `ActHome`'s existing
`pendingAutoExportLens` bind-race comment (`:570-574`) needs to be re-read
carefully so a fix here doesn't reopen that already-solved race. Not scoped
further than this in `FISH-012`'s audit, per the owner's explicit decision to
keep that story limited to the pinch-adjust case only.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic once a design exists.
- [ ] Widget/UI tests cover visible behavior (success and failure messaging).
- [ ] Integration tests cover the process-boundary/timing scenario this story
      exists to fix.
- [ ] Smoke tests pass on the designated Tecno device; record model, Android
      version, build and timestamp.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.
