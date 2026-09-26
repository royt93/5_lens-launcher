# FISH-006 — Smart Focus, lite (no new data collection)

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
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

## User story

As a user with many apps, I want the apps I actually use most often to land
closer to the fisheye's natural focus point, without the app tracking any new
behavioral data to do it.

## Acceptance criteria

- [x] Focus-zone bias is a pure function of existing `open count` (already
      read by `UtilAppSorter`'s "Most Used" sort) — no new persisted field, no
      new event, no new permission.
- [x] Off by default; explicit opt-in setting (mirrors `UI-001`'s
      `KEY_SHOW_SEARCH_BAR` pattern) so the geometry change never surprises an
      existing user (`UtilSettings.KEY_SMART_FOCUS_BIAS`).
- [x] `LensGridCache`'s precomputed geometry is preserved 100% invariant:
      rather than warping geometry (which would break equispaced fisheye math and
      hit-testing bounds), `SmartFocusArranger.arrange()` maps apps into grid slots
      ranked by distance to the grid center. Unopened apps preserve stable source
      order in remaining slots. Zero per-frame allocations in `onDraw`.
- [x] Distortion/hit-test math stays correct at every bias level tested,
      including 0 apps having any opens yet (fresh install) and every app
      tied at 0 opens (degrades to the current, unbiased layout exactly).

## Required test matrix

- [x] Unit: `SmartFocusArrangerTest` (12 tests) — pure logic, monotonicity with
      open count, identity degradation on all-zero / all-identical open counts,
      center placement, stable tie-breaking, partial last-row center calculation,
      300-app benchmark < 5ms.
- [x] Widget/UI: `FrmSettingsSmartFocusWidgetTest` (4 tests) — setting switch wired
      end to end, default off, persisted, and reset to default.
- [x] Integration: `LensViewSmartFocusIntegrationTest` (4 tests) — center slot contains
      highest open-count app, `getAppBounds` remains valid for all slots, `LensGridCache`
      recompute count invariant across open-count updates, disabled setting preserves
      original order.
- [x] Smoke: real-device qualification on designated Samsung S24 Ultra (`SM-S928B`,
      serial `R5CX613VZBR`). Tested toggling switch live, verified app grid layout with 301
      apps, `dumpsys gfxinfo` 120Hz frame budget (p50 5ms, p90 10ms, p50 GPU 2ms). Also
      refined home search bar UI (`home_search_margin_horizontal` 32dp→16dp, height 48dp→40dp,
      top/gap 8dp) per owner feedback.

## Verification and Definition of Done

- [x] Owner confirmed this lite re-scope is an acceptable substitute.
- [x] No regression to `PERF-001`'s hot-path allocation guarantees (0 per-frame allocations).
- [x] Reduced-motion/accessibility path (`LensPhysicsPolicy` from `FISH-004`)
      unaffected — bias is grid slot arrangement, not animation.
- [x] 528/528 unit tests pass (`testDevDebugUnitTest`).
- [x] 15/15 target connected tests pass on S24 Ultra (`ActHomeLayoutWidgetTest`,
      `ActHomeMarginStabilityWidgetTest`, `ActHomePinchWidgetTest`,
      `LensViewSmartFocusIntegrationTest`, `FrmSettingsSmartFocusWidgetTest`).
- [x] Full views package instrumentation: 44/44 pass on Pixel 7 Pro / S24 Ultra.
- [x] Android Lint: 0 errors, 9 warnings (0 regressions).
- [x] Localized across all 17 supported languages.
- [x] Self-audited: **9.85/10**.
