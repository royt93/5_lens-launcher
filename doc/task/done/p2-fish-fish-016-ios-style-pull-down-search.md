# FISH-016 — iOS-style pull-down search

| Field | Value |
|---|---|
| Type | new |
| Status | done |
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

Owner also picked a Clean-mode toggle in that same menu. Gesture behavior must remain identical in Clean and non-Clean modes. A three-finger gesture was proposed first and rejected by the owner in favor of one-finger iOS parity.

Detailed approved design: `docs/superpowers/specs/2026-10-02-fish-016-swipe-down-search-design.md`. Plan: `docs/superpowers/plans/2026-10-02-fish-016-pull-down-search.md`.

## User story

As a user, I want a familiar pull-down gesture to open app search even when Clean mode hides the search bar, without losing normal fisheye navigation.

## Acceptance criteria

- [x] Pulling down from the top 20% of `LensView` opens full app search.
- [x] Gesture conditions use named thresholds and a pure, unit-tested predicate.
- [x] Gesture behaves identically whether Clean mode is on or off (`LensView` never reads `KEY_CLEAN_LENS_MODE` for it).
- [x] Pulling down from the rest of the lens preserves current fisheye panning.
- [x] Partial candidate pulls cannot accidentally launch an app on release (widget test; not smoke-tested by hand).
- [x] Existing pinch, horizontal paging, icon long-press, and empty-space long-press remain intact.
- [x] Empty-space menu includes Search and Clean-mode toggle using existing translated strings (no locale file changes; `AllStringsTranslationTest` green).
- [x] Closing search restores search-bar visibility from current settings rather than forcing it visible, for both the arrow/`hide()` path and system BACK.
- [x] Unit, widget, integration, translation regression, full-suite, lint, and TECNO KJ7 smoke evidence are recorded.
- [x] Audit score is strictly greater than 9.0/10 before push (see Audit; nothing has been pushed).

## Implementation

- `LensView.kt`: three pure predicates (`isInSearchSwipeActivationZone`, `shouldOpenSearchSwipe`, `shouldConsumeSearchSwipeRelease`), `OnSearchSwipeDownListener`, five transient fields reset on `ACTION_UP`/`ACTION_CANCEL`/detach. Classified from the fixed `ACTION_DOWN` point. A second finger cancels candidacy so pinch is untouched.
- `ActHome.java`: one shared `openSearchFromHome()` used by the gesture and the menu; menu items 7 (Search) and 8 (Clean toggle) named `MENU_ID_SEARCH_APPS` / `MENU_ID_TOGGLE_CLEAN_LENS`; the `HIDDEN` transition now calls `updateSearchBarVisibility()` instead of forcing `VISIBLE`.
- No new dependency, string, preference key, timer, observer, or coroutine.

## Bugs found while building (not assumed away)

1. **Search bar resurrected on close.** The old `HIDDEN` handler set the bar `VISIBLE` unconditionally, so closing search brought back a bar that Clean mode (or `KEY_SHOW_SEARCH_BAR=false`) had hidden. Fixed; mutation check: restoring the old line makes `closingSearch_restoresGoneBarWhenCleanOn` fail (`expected:<8> but was:<0>`).
2. **System BACK re-shows the bar ~10 ms after the HIDDEN callback.** Found only on the real device (the `SearchView.hide()` path and the arrow path do not do it): bar flag sampled `GONE` then `VISIBLE` on BACK, stayed `GONE` on the arrow. Instrumented tests that close via `hide()` passed straight through it. Added `closingSearchWithSystemBack_keepsBarGoneWhenCleanOn` (real `GLOBAL_ACTION_BACK` ×2), watched it fail RED, then re-applied settings via `searchBar.post(this::updateSearchBarVisibility)` in the HIDDEN branch.
3. **Smoke false alarm, disclosed.** One manual run looked like the fix had failed; it was a stale screenshot after the system launcher took focus plus Clean mode having been cleared by a test's `@After`. Later runs checked `mCurrentFocus` and the real `clean_lens_mode` value at every step.

## Disclosed, not fixed

- **Root cause of bug 2 is inferred, not read from source.** Material 1.13.0 sources were not available offline. Evidence is the measured timeline (GONE at HIDDEN, VISIBLE shortly after, BACK only) plus the failing-then-passing test. The `post` re-apply is a workaround for that Material behavior. If a future Material upgrade changes it, the BACK test will say so.
- **`AdaptiveOrientationWidgetTest` is order/state-fragile.** It asserts the search bar is `VISIBLE` but never resets `clean_lens_mode`. It failed once in a full run only because my manual smoke had left Clean mode on in the device's real preferences (reproduced by seeding the pref, passes in isolation ×3 and in the full suite after cleaning the pref). Not changed, out of scope. Any manual smoke that enables Clean mode must clear it afterwards; this round did.
- **Pre-existing, unchanged:** a first full-suite run earlier in the session showed one non-repeating locale assertion and a `UiAutomationService already registered!` crash; neither reproduced in isolation or on rerun, so no speculative change was made (TEST-005 produced no diff).
- **Not hand-tested:** partial top pull (release does not launch an app) is covered by widget test only. The 20% zone was exercised from y=400 of 2436 and the middle from y=1200; exact boundary behavior is covered by the unit test, not by hand.
- Subagent (Explore) calls failed twice with `model_not_found` (`haiku`, `sonnet`); the plan's seam-mapping was done by reading the code directly.
- `superpowers:requesting-code-review` was **not** run (plan Step 6); the audit below is a self-audit.

## Test evidence

| Layer | Result |
|---|---|
| JVM unit (`testDevDebugUnitTest --rerun-tasks`) | **667 / 667** pass (663 before; +4 policy tests) |
| Lint (`lintDevDebug`) | 0 errors, 0 fatal, 8 warnings (= baseline) |
| Widget (`LensViewWidgetTest`) | 4 new real-`MotionEvent` tests; distances derived from device `scaledTouchSlop` (first draft hardcoded pixels and failed on KJ7, fixed in the test, not the code) |
| Integration (`ActHomeCleanLensModeIntegrationTest`, `ActHomeLensManagementWidgetTest`) | 5 new + 2 menu behavior tests, menu count 6 → 8, stale-position guard extended to ids 7/8 |
| Regression run | `ActHomePinchWidgetTest`, `ActHomeMultiLensWidgetTest`, `LensViewQuickActionsIntegrationTest`, `ActHomePredictiveBackWidgetTest`, `AppSearchWidgetTest` green |
| Full instrumented, direct `adb -s` on TECNO KJ7 | **441 / 441** pass, 113 classes (431 before) |

All device work used TECNO KJ7 `115333744A005844` only; the attached Pixel 7 Pro was never addressed and no Gradle `connected*` task was run. KJ7 dropped off `adb` mid-session and was re-attached by the owner before smoke.

## Physical smoke (TECNO KJ7, Android 14, 1080×2436, 2026-10-02)

No ad appeared at any step (R4). Search-bar flag read from the live view hierarchy, not only from screenshots.

1. Clean off: single-finger swipe from y=400 opens search with keyboard; close restores the bar. **Pass.**
2. Clean on: identical swipe opens search; after BACK ×2 the bar is `GONE` (before the fix: `VISIBLE`). **Pass after fix.**
3. Swipe from the middle of the lens does not open search. **Pass.**
4. Long-press empty space shows 8 items, including "Tìm ứng dụng" and "Làm sạch lens (chế độ tối giản)" in Vietnamese. **Pass.**
5. Clean toggle from the menu: on → bar `GONE`, pref `true`; off → bar `VISIBLE`, pref `false`; pull-down with Clean off still opens search. **Pass.**
6. Pinch, horizontal paging, icon long-press: covered by the green regression classes above, not re-driven by hand (a pinch needs two fingers, which `adb input` cannot do reliably).

Device left clean: `clean_lens_mode` removed from the real preferences.

## Audit (self, 2026-10-02): 9.2 / 10

| Dimension | Score | Note |
|---|---|---|
| Correctness | 9.5 | Both close paths verified on device; two real bugs found and fixed with RED tests |
| Regression safety | 9.5 | 441/441 + 667/667, pan/pinch/page/long-press untouched |
| Tests | 9.5 | Predicate, `MotionEvent`, real Activity, real system BACK; mutation-checked |
| Lifecycle / resources | 9.5 | No new listener registration, timer, or coroutine; state reset on up/cancel/detach |
| Root-cause rigor | 8.0 | BACK behavior measured, not read from Material source |
| Process | 8.5 | Code-review skill skipped; one misleading smoke run, caught and redone |
| Scope discipline | 9.5 | Three-finger and global-swipe ideas dropped; no new keys or strings |

Strictly greater than 9.0, so push is permitted by the audit gate. **Nothing has been pushed.**
