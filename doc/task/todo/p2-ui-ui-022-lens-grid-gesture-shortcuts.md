# UI-022 — Gesture shortcuts directly on the lens grid

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
| Epic | Home-screen usability |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | FISH-004 (already shipped, physics/gesture baseline) |

## Context and evidence

`SEARCH-003` already shipped inline row actions (info/pin start/pin end/
unpin/uninstall) on **search results**, via a shared `UtilApp.appInfoIntent`/
`uninstallIntent` extracted specifically so `AppAdapter` and
`SearchResultAdapter` don't duplicate logic. The main fisheye grid
(`LensView`) has no equivalent — a user has to open Apps tab or search to
reach these actions today, breaking the "everything from the lens" premise.

Real risk, not hypothetical: `LensView` already owns drag/pan fisheye physics
tuned by `FISH-004` (Gentle/Standard/Snappy presets) and its own hit-test
geometry (`LensGridCache`, the file `PERF-001` identified as the hottest path
in the codebase). A new long-press+swipe gesture must not fight the existing
pan gesture or `LensPhysicsPolicy`'s reduced-motion handling.

## User story

As a user, I want to long-press an icon on the main lens grid and swipe to a
quick action (info/pin/uninstall) without leaving the home screen.

## Acceptance criteria

- [ ] Reuses `UtilApp.appInfoIntent`/`uninstallIntent` as-is — no new intent
      construction logic duplicated a third time.
- [ ] Gesture disambiguation: a long-press-then-directional-swipe must not
      trigger while a normal pan/drag is in progress, and a normal pan must
      not accidentally arm the long-press state. Written as an explicit state
      machine, not timing heuristics alone.
- [ ] Respects `LensPhysicsPolicy.shouldReduceLensMotion()` — no motion-heavy
      gesture feedback (e.g. an animated action-reveal) under reduced motion/
      battery saver/thermal throttling; a plain, immediate menu is the
      degraded fallback.
- [ ] No change to `LensGridCache`'s existing per-frame allocation profile
      (`PERF-001`'s guarantee) — gesture state lives outside the render hot
      path.
- [ ] Accessibility: the same actions remain reachable via a non-gesture path
      (e.g. existing long-press context menu, if one exists, or a new one) —
      a swipe-only affordance is not acceptable per this project's `A11Y-001`
      standard.

## Required test matrix

- [ ] Unit: gesture state machine — every transition (idle→armed→
      triggered/cancelled), including the "started as pan, must not arm"
      case and the "long-press held past pan threshold" case.
- [ ] Widget/UI: action reveal UI, reduced-motion fallback rendering,
      non-gesture accessible path.
- [ ] Integration: real touch-event sequences (`MotionEvent` injection or
      Espresso `GeneralSwipeAction`) proving normal pan is unaffected and the
      new gesture fires only on its own input pattern.
- [ ] Smoke: on the designated device, confirm the existing fisheye drag/pan
      feel is unchanged (side-by-side with pre-change build if possible) and
      the new gesture reliably triggers/doesn't false-positive during normal
      use.

## Verification and Definition of Done

- [ ] Zero regression to `FISH-004`'s physics presets or `LensGridCache`'s
      hit-test correctness (both have existing test suites — must stay green).
- [ ] Real-device proof the new gesture and the existing pan gesture don't
      fight each other in practice, not just in unit tests.
