# FISH-016 — iOS-style pull-down search

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | owner-approved design |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | Clean the Lens, FISH-009, UI-022 |

## Owner decision (2026-10-02)

Owner picked two complementary search paths:

1. Swipe downward from the top activation zone of the lens, matching the familiar iOS pull-down-search interaction without stealing one-finger fisheye panning everywhere else.
2. A "Search apps" item in the empty-space long-press menu as an accessible, discoverable fallback.

Owner also picked a Clean-mode toggle in that same menu. Gesture behavior must remain identical in Clean and non-Clean modes.

Detailed approved design: `docs/superpowers/specs/2026-10-02-fish-016-swipe-down-search-design.md`.

## User story

As a user, I want a familiar pull-down gesture to open app search even when Clean mode hides the search bar, without losing normal fisheye navigation.

## Acceptance criteria

- [ ] Pulling down from the top 20% of `LensView` opens full app search.
- [ ] Gesture conditions use named thresholds and a pure, unit-tested predicate.
- [ ] Gesture behaves identically whether Clean mode is on or off.
- [ ] Pulling down from the rest of the lens preserves current fisheye panning.
- [ ] Partial candidate pulls cannot accidentally launch an app on release.
- [ ] Existing pinch, horizontal paging, icon long-press, and empty-space long-press remain intact.
- [ ] Empty-space menu includes Search and Clean-mode toggle using existing translated strings.
- [ ] Closing search restores search-bar visibility from current settings rather than forcing it visible.
- [ ] Unit, widget, integration, translation regression, full-suite, lint, and TECNO KJ7 smoke evidence are recorded.
- [ ] Audit score is strictly greater than 9.0/10 before push.

## Test baseline before implementation

On 2026-10-02, direct full instrumentation on locked TECNO KJ7 first produced one non-repeating locale assertion and later crashed with Android's `UiAutomationService already registered!`. Root-cause investigation could not reproduce either failure in isolation or in targeted class order. A clean second full-suite run passed **431/431 tests**. No speculative TEST-005 code change was made.
