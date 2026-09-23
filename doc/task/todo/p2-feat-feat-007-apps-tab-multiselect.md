# FEAT-007 — Multi-select bulk actions in the Apps tab

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Home-screen usability |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | PERF-003 (already shipped, DiffUtil adapter baseline) |

## Context and evidence

`UI-020` recorded a real 346-app inventory on one owner device with an
unpaged, show-everything grid. `FrmApps`/`AppAdapter` (already `DiffUtil`-driven
per `PERF-003`) currently only support one-at-a-time actions per row. At that
scale, hiding/pinning/uninstalling apps one by one is genuinely tedious —
this uses the platform's native `ActionMode` (contextual action bar), not a
custom-built selection UI.

## User story

As a user with a large app list, I want to select multiple apps at once in
the Apps tab and hide/pin/uninstall them together.

## Acceptance criteria

- [ ] Long-press enters selection mode via `ActionMode`/`ActionMode.Callback`
      (native platform pattern, not a hand-rolled toolbar).
- [ ] `AppAdapter` gains selection state (a `Set<String>` of selected
      component identities, matching this codebase's existing app-identity
      convention from `AppDiffCallback`) — rendered via `DiffUtil` payload
      updates, not a full `notifyDataSetChanged()` (would reintroduce exactly
      what `PERF-003` removed).
- [ ] Bulk actions available: hide/unhide, pin, uninstall (each reuses the
      existing single-app codepath in a loop — no new bulk-specific business
      logic duplicated).
- [ ] Bulk uninstall shows one system confirmation per app (Android's own
      `ACTION_UNINSTALL_PACKAGE` doesn't support multi-package in one
      dialog) — set this expectation explicitly in the UI so it isn't
      reported as a bug later.
- [ ] Selection state is cleared on tab switch / process death — never
      silently stale.

## Required test matrix

- [ ] Unit: selection-set logic (add/remove/clear/select-all), `DiffUtil`
      payload correctness for selection-only changes (must not re-bind
      unrelated fields).
- [ ] Widget/UI: `ActionMode` enters/exits correctly, bulk-action buttons
      enabled/disabled based on selection content (e.g. "unhide" only shown
      when at least one selected app is hidden).
- [ ] Integration: real bulk hide/pin persisted through Room and reflected
      back in `RAppsSingleton` after the operation (mirrors `FEAT-002`'s
      existing persistence integration tests).
- [ ] Smoke: on the designated device with a realistic large app count,
      select 5+ apps, bulk-hide, confirm the home grid updates; bulk-uninstall
      2+ apps, confirm each system dialog appears in sequence.

## Verification and Definition of Done

- [ ] Live-verified bulk hide/pin/uninstall on the designated device.
- [ ] No `notifyDataSetChanged()` reintroduced (grep-verified against
      `PERF-003`'s cleanup).
- [ ] Accessibility: selection state and count announced (TalkBack), per this
      project's `A11Y-001` standard (see `UI-020`'s count-header precedent).
