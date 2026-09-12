# LINT-008 — Flatten act_vip_management.xml's deepest nesting chain

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Rendering performance |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Owner-picked follow-up, deliberately deferred out of `LINT-002`/`LINT-006` because a correct fix needed a real structural change, not a one-line edit. `act_vip_management.xml` had more than 10 view levels at its deepest point (the "Feature" rows inside the VIP-benefits card), each measure/layout pass walking that whole chain — real, if modest, rendering cost on a screen the user can open repeatedly.

## Investigation and changes

Traced the exact deepest chain: `FrameLayout(root) → LinearLayout → NestedScrollView → LinearLayout → LinearLayout(section1) → CardView(features) → LinearLayout(card content) → LinearLayout(feature row, horizontal) → LinearLayout(feature text column, vertical) → TextView`. The three repeated "icon + two-line text" feature rows (No Ads / Customization / Priority Support) were each two nested `LinearLayout`s (a horizontal row wrapping a vertical text column) purely to lay out an icon beside a title+subtitle pair — a textbook case for a single `ConstraintLayout` instead.

Replaced each of the 3 feature rows with one `androidx.constraintlayout.widget.ConstraintLayout` (already used elsewhere in this codebase, e.g. `a_splash.xml`, and already on the resolved classpath — no new dependency): the icon is constrained top/bottom to the parent (centers it against the two-line text block exactly like the original `gravity="center_vertical"` did), the title is constrained to the icon's end and the parent's top, and the subtitle is constrained below the title. This removes one `ViewGroup` level per feature row (2 nested `LinearLayout`s → 1 `ConstraintLayout`), which was enough to bring the whole file under the 10-level threshold.

Did not touch the top of the tree (root `FrameLayout` → `LinearLayout` → `AppBarLayout`/`NestedScrollView`, which manually reimplements what a `CoordinatorLayout` normally does) — collapsing that into a `CoordinatorLayout` would remove another level and modernize the pattern to match `act_about.xml`, but it changes real scroll-collapse/toolbar behavior and was judged out of scope for a lint-driven depth fix; left as a possible future improvement rather than risking a real behavior change here.

## Required test matrix

- [x] Unit tests: Not applicable — pure layout restructuring, no logic changed.
- [x] Widget/UI tests: `FVipManagementWidgetTest` (existing, 8 tests) re-ran clean, which is the direct proof the new `ConstraintLayout` blocks inflate correctly (a bad `app:layout_constraint*` reference throws `InflateException` immediately on `setContentView`, which would have failed every test in this class, not just a targeted one).
- [x] Integration tests: Not applicable.
- [x] Smoke test the exact candidate on a real device and record model, Android version, build SHA, network state, timestamp and log evidence, **including a visual check** since this is a structural change to a real user-facing screen. — See Test evidence below.

## Test evidence

- **Compile + resource link** — `./gradlew :app:processDevDebugResources`: clean (confirms `ConstraintLayout` resolves without an explicit `build.gradle` dependency — already transitively present, the same way `a_splash.xml` already relied on it).
- **Unit** — `./gradlew testDevDebugUnitTest`: full suite passes, 0 failures.
- **Lint** — `./gradlew :app:lintDevDebug`: `TooDeepLayout` fully gone (1 → 0). 22 → 21 warnings, 0 errors.
- **Targeted widget** (TECNO KJ7) — `FVipManagementWidgetTest` (8/8) + `VipPluralsInstrumentedTest` (4/4, from `LINT-007`) pass.
- **Visual verification** (TECNO KJ7) — launched `ActVipManagement` via a temporary in-process screenshot test (removed after use; the activity is `exported=false` per `SEC-003` so it can't be reached via a plain `adb shell am start`) and visually confirmed the 3 feature rows render identically to the pre-change design: icon vertically centered against the two-line title/subtitle text, correct spacing, no clipping or overlap.
- **Full instrumentation regression — aborted, unrelated cause**: `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` ran cleanly through 27 of 29 test classes (everything up to and including `com.mckimquyen.views.SuperWebViewActivityHardeningTest`, all dots/passes, no failure markers) and then hung for over 20 minutes. Diagnosed: the TECNO KJ7 had lost network connectivity (`adb shell ping 8.8.8.8` → "Network is unreachable"), and `SuperWebViewActivityHardeningTest`/the two `SuperWebViewActivity*` classes after it load a real HTTPS page per `SEC-003`'s design — with no network, that hangs rather than failing fast. Killed the stuck `am instrument` process and uninstalled both APKs; this is a device-connectivity problem, not a regression from this diff (`LINT-008` touches only `act_vip_management.xml`, nothing WebView-related). The targeted run below is what actually covers this story's changed surface.
- Device: TECNO KJ7 (TECNO-KJ7), serial `115333744A005844`, Android 14 (SDK 34), 2026-09-12. Owner re-authorized this one-off (third time this session) after re-confirming the S24 Ultra was still disconnected — see the `feedback-device-target` memory.

## Device policy note

- S24 Ultra (`R5CX613VZBR`) remained disconnected for this story. The assistant asked again rather than assuming the prior one-off exceptions carried forward automatically; owner re-authorized TECNO KJ7 specifically for this story too. Standing S24U-only policy unchanged once it reconnects.

## Audit score (2026-09-12, self-audit against the README rubric)

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | The exact deepest chain was traced (not guessed), the minimal structural change that removes it was applied, and the deliberately-not-attempted larger restructuring (CoordinatorLayout) is documented with its reason. |
| Unit-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable — no logic exists to unit test in a pure layout restructuring. |
| Widget/UI-test quality and coverage | 1.0 | 1.0 | Reused existing coverage that would fail loudly on any inflation break, plus an ad-hoc visual screenshot check for the one thing widget tests alone can't catch (a subtle positional/visual regression). |
| Integration-test quality and coverage | 1.5 | 1.5 | Correctly reasoned as Not applicable. |
| Tecno + general smoke | 1.0 | 0.85 | Real device, targeted tests plus a genuine visual check (not just "doesn't crash"); the full-suite regression ran clean through 27 of 29 classes before a device-connectivity issue (unrelated to this diff) hung the remaining WebView tests — docked for not getting a completed full-suite number, not for evidence quality. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No security-relevant surface touched. |
| Performance, lifecycle and regression risk | 1.0 | 1.0 | Fewer measure/layout passes per feature row, verified visually identical output; the untouched top-of-tree structure means zero risk to scroll/toolbar behavior. |
| Maintainability and documentation truth | 1.0 | 1.0 | Documents the exact chain that was too deep, why `ConstraintLayout` was the right tool here, and names the further improvement that was deliberately not attempted and why. |
| **Total** | **10.0** | **9.85** | **Exceeds the > 9.0 push gate.** |
