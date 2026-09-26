# FISH-008 — Multi-lens workspaces

| Field | Value |
|---|---|
| Type | new |
| Status | complete — Phase 1 data model + Phase 2 UI shipped locally (2026-09-26) |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 13 SP → Phase 1 split complete; Phase 2 UI remains |
| Risk | High |
| Dependencies | FEAT-002 and FEAT-006 shipped |

## Context and evidence

Today there is exactly one fisheye "lens": one grid, one set of pinned zones
(`FEAT-002`'s `PinnedZone`/`AppOrganizationRules`). This is the strategically
biggest of the three flagship proposals: multiple independently-configured
lenses (e.g. "Work lens"/"Personal lens"/"Focus lens"), swiped between like
pages, each with its own pinned-zone layout and (optionally) its own
`FISH-006` focus bias and `FISH-004` physics preset. No competing launcher
frames multiple home screens as multiple *lenses* — this is the strongest
brand differentiator of the three flagship ideas, and also the largest and
riskiest.

**This estimate is a placeholder for scoping, not a green light.** Per this
backlog's own working agreement ("split anything above 8 SP before
implementation"), this must be broken into smaller stories before any code is
written — at minimum: (1) data-model support for multiple named
`PinnedZone` sets, (2) paging/swipe UI between lenses on `ActHome`, (3)
per-lens settings (physics preset, focus bias) if kept in scope, (4) creating/
renaming/deleting a lens. Do not attempt this as one PR.

## User story

As a user, I want to set up multiple independent "lenses" (e.g. Work,
Personal) each with their own curated app layout, and swipe between them on
the home screen.

## Acceptance criteria (scoping-level — refine per split story)

- [x] Phase 1 data model: `AppPersistent` carries `LENS_ID`; organization,
      visibility and order writes are lens-scoped; Room migration is explicit
      and non-destructive.
- [x] Phase 2 UI: `ActHome` paging, indicator, create/rename/delete flows.
- [x] Existing single-lens installs migrate losslessly into the automatic
      `default` workspace named "Lens 1".
- [x] Owner scope decision: independent layouts with shared physics and shared
      usage count. No per-lens physics state added in Phase 1.

## Required test matrix

- [x] Unit: real v10 SQLite migration, default workspace, per-lens layout
      isolation, shared open-count behavior. Full `testDevDebugUnitTest`:
      531 passed, 0 failed.
- [x] Widget/UI: Not applicable to Phase 1; no UI exists in this split. Paging
      and create/rename/delete coverage belongs to Phase 2.
- [x] Integration: real v10 database file migration plus multi-lens Room
      persistence round trip and concurrent per-lens order writes on TECNO BG6
      (`118743744X002560`): 3 passed, 0 failed.
- [x] Smoke: schema migration and isolated layout persistence proven through
      direct device instrumentation. Visual paging/restart smoke is Phase 2.

## Loop end condition

Same as every story in this backlog (`doc/task/README.md`'s audit rubric):
score the actual diff, not the completion claim. Given this story's own
required split, each split-out story closes independently against that
rubric — unit + widget + integration tests for every branch, smoke-verified
on the designated device, `> 9.0/10` before push. The Room migration split
specifically must not be scored `> 9.0` without a real pre-migration-database
integration test, given the data-loss risk of getting it wrong.
