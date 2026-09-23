# FISH-006 — Smart Focus, lite (no new data collection)

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | CORE-001, DB-001, PERF-001 (all already shipped) |

## Context and evidence

`FISH-001` (Smart Focus Map, `todo/p2-fish-fish-001-smart-focus-map.md`) scoped
the same core value — auto-elevating frequently-used apps into the fisheye's
prime focus positions — with a dependency on `INSIGHT-001` (on-device usage
insights). `INSIGHT-001` is declined (chains back to `ADS-001` staying with the
external Ad SDK team), which transitively blocks `FISH-001`.

This story re-scopes the same user value **without** `INSIGHT-001`: `FEAT-002`
already persists an `open count` per app in Room (`AppPersistent`/
`AppPersistentDao`) for its existing "Most Used" sort type. That data is
sufficient to bias fisheye focus-zone placement toward high-open-count apps —
no new tracking, no new privacy surface, nothing that touches `ADS-001`'s
territory.

This is a **re-scope proposal, not an automatic go** — flag to the owner
before starting: it still touches `LensView`/`LensGridCache`, the highest-risk
hot-path file in the codebase (per `PERF-001`'s own risk notes), and the
original `FISH-001` may have wanted richer signals than a static open-count
(e.g. recency, time-of-day) that this lite version deliberately does not
attempt.

## User story

As a user with many apps, I want the apps I actually use most often to land
closer to the fisheye's natural focus point, without the app tracking any new
behavioral data to do it.

## Acceptance criteria

- [ ] Focus-zone bias is a pure function of existing `open count` (already
      read by `UtilAppSorter`'s "Most Used" sort) — no new persisted field, no
      new event, no new permission.
- [ ] Off by default; explicit opt-in setting (mirrors `UI-001`'s
      `KEY_SHOW_SEARCH_BAR` pattern) so the geometry change never surprises an
      existing user.
- [ ] `LensGridCache`'s precomputed geometry either accepts a bias input
      cleanly or this story documents, with evidence, why it cannot and what
      the alternative is (e.g. reordering the app list before layout instead
      of shifting geometry) — decide this before writing render code, not
      during.
- [ ] Distortion/hit-test math stays correct at every bias level tested,
      including 0 apps having any opens yet (fresh install) and every app
      tied at 0 opens (must degrade to the current, unbiased layout exactly).

## Required test matrix

- [ ] Unit: bias function pure logic — monotonic with open count, degrades to
      identity at all-zero, clamps at extremes (one app with all opens).
- [ ] Widget/UI: setting toggle wired end to end (`FrmSettings` row, default
      off, persisted).
- [ ] Integration: `LensView`/`LensGridCache` renders correctly with bias on
      across a realistic app-count range (mirrors `PERF-001`'s existing hot-path
      test coverage).
- [ ] Smoke: real-device before/after screenshot comparison on the designated
      device with a real mixed-usage app list (not a synthetic uniform one).

## Verification and Definition of Done

- [ ] Owner confirms this lite re-scope is an acceptable substitute for the
      original `FISH-001` value before implementation starts.
- [ ] No regression to `PERF-001`'s hot-path allocation guarantees (no new
      per-frame allocation introduced).
- [ ] Reduced-motion/accessibility path (`LensPhysicsPolicy` from `FISH-004`)
      unaffected — bias is geometry, not animation.
