# FISH-008 — Multi-lens workspaces

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 13 SP → split required before implementation |
| Risk | High |
| Dependencies | FEAT-002 (pinned zones, already shipped), FEAT-006 (layout export/import, not yet shipped — do that first, it doubles as the serialization format this story needs) |

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

- [ ] Data model: `PinnedZone`/`AppOrganizationRules` extended with a lens
      identity, migrated from the current single-implicit-lens shape without
      losing any existing user's current layout (this is a Room schema
      change — needs an explicit, tested migration, not a destructive one).
- [ ] `ActHome` pages between lenses (`ViewPager2`, consistent with the
      existing `ActSettings` tab pattern) — `LensView`/`LensGridCache` render
      per-lens data, no shared mutable state leaking between lenses.
- [ ] Creating a first lens from an existing single-lens install is lossless
      and automatic (the current layout becomes "Lens 1", not silently
      dropped).
- [ ] Scope decision needed from owner before implementation: do per-lens
      physics presets (`FISH-004`) and focus bias (`FISH-006`, if shipped)
      belong in v1, or is a shared-physics/per-lens-layout-only v1 the right
      first cut? Flag via `AskUserQuestion`-style owner check-in before
      committing to either, don't assume.

## Required test matrix

- [ ] Unit: Room migration correctness (old single-lens data → "Lens 1"),
      per-lens data isolation logic.
- [ ] Widget/UI: lens-switch paging, create/rename/delete lens flows.
- [ ] Integration: real migration test against a database file shaped like an
      actual pre-migration install (not a synthetic empty one); real
      multi-lens persistence round trip.
- [ ] Smoke: on the designated device, create a second lens, configure a
      distinct layout, swipe between both, restart the app, confirm both
      persist correctly.

## Loop end condition

Same as every story in this backlog (`doc/task/README.md`'s audit rubric):
score the actual diff, not the completion claim. Given this story's own
required split, each split-out story closes independently against that
rubric — unit + widget + integration tests for every branch, smoke-verified
on the designated device, `> 9.0/10` before push. The Room migration split
specifically must not be scored `> 9.0` without a real pre-migration-database
integration test, given the data-loss risk of getting it wrong.
