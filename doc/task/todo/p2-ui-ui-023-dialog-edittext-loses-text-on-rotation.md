# UI-023 — Dialog EditText loses typed text on rotation

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Quality |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Found during the `FISH-012` transient-state audit (2026-09-29). Two programmatic
`EditText`s are created without `setId()`, so Android's default
`onSaveInstanceState` view-hierarchy walk skips them entirely — a config change
(rotation, fold, locale switch) mid-typing loses whatever the user had typed,
silently:

- `adt/AppAdapter.java:613-641` — the "Set folder" dialog's name field. Persist
  only happens on the dialog's positive/neutral button (`:600-611`,
  `applyOrganization`).
- `ui/FrmSettings.kt:212-234` — the custom search-hint-text dialog's field.
  Persist only happens on the positive button (`:229`,
  `utilSettings?.save(UtilSettings.KEY_SEARCH_HINT_TEXT, ...)`).

The exact same bug, for the lens-rename dialog, was already found and fixed at
`ui/ActHome.java:765-767` (`input.setId(android.R.id.edit)`) — it was just never
applied to these other two call sites.

## User story

As a user renaming a folder or setting a custom search hint, I want my typed
text to survive an accidental rotation, so a config change doesn't silently
wipe what I just typed.

## Acceptance criteria

- [ ] Both `EditText`s get a stable id (`android.R.id.edit`, matching the
      already-fixed lens-rename dialog) so the framework's default
      `onSaveInstanceState` preserves their text across a config change.
- [ ] A widget test proves it for each dialog: type text, rotate (or simulate
      the equivalent config-change save/restore cycle), reopen/observe the
      dialog, confirm the text survived.

## Implementation notes

One line per file (`input.setId(android.R.id.edit)`, added right after each
`EditText` is constructed) — no behavior change beyond preserving state across
a config change. `android.R.id.edit` is safe to reuse across both dialogs
since each is a distinct `AlertDialog` instance with its own view hierarchy —
`ActHome`'s lens-rename dialog already establishes this is a safe, working
pattern in this codebase.

## Verification and Definition of Done

- [ ] Unit tests: not applicable — no pure logic changes; the whole fix is a
      framework-recognized id enabling default state-saving behavior.
- [ ] Widget/UI tests cover the config-change-survives-with-typed-text
      behavior for both dialogs.
- [ ] Integration tests: not applicable — no cross-subsystem boundary.
- [ ] Smoke tests pass on the designated Tecno device; record model, Android
      version, build and timestamp.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.
