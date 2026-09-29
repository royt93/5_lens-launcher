# FISH-012 — Persist pinch-adjust curvature across a process kill

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | FISH-009 (already shipped) |

## Context and evidence

Triggered by a user complaint ("nhiều tính năng không work") investigated live this
session: a codebase-diversity audit plus a real repro on the designated device
(TECNO KJ7, `115333744A005844`, owner-approved to temporarily set this app as
default launcher for the repro) showed `adb shell am kill` while backgrounded, then
`KEYCODE_HOME`, resurrects the process cleanly — no crash, `RApplication`'s
dynamically-registered receivers re-register fine, disproving that half of the
original hypothesis — but `FISH-009`'s in-flight pinch-to-adjust curvature
(`LensView.liveDistortionFactor`) was lost silently, with zero trace, whenever the
kill landed before the user answered the "Save as default" confirmation Snackbar.
Cold-restart lag to `Fully Drawn` was also measured at the time: ~2.7s
(`ActivityTaskManager: Fully drawn ... +2s693ms`).

A follow-up codebase-wide audit for the same class of bug (session-only state a
user could reasonably believe is already saved) found several more candidates.
Per owner decision, this story fixes only the pinch-adjust case; two others were
filed as separate backlog entries and deliberately not touched here:
`doc/task/todo/p2-ui-ui-023-dialog-edittext-loses-text-on-rotation.md` and
`doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md`.

Full design/implementation trail:
`docs/superpowers/specs/2026-09-29-pinch-adjust-persist-across-kill-design.md`
(design spec) and
`docs/superpowers/plans/2026-09-29-pinch-adjust-persist-across-kill.md`
(implementation plan, with every deviation found during execution disclosed
inline rather than hidden).

## User story

As a user who pinches to adjust the lens curvature and then has the app killed in
the background (OS memory reclaim, OEM aggressive background management, or any
other cause) before answering the "Save as default" prompt, I want my adjustment
to still be offered back to me the next time the app opens, instead of silently
disappearing.

## Acceptance criteria

- [x] The pinch value is persisted to a distinct pending key
      (`UtilSettings.KEY_PENDING_DISTORTION_FACTOR`, per-lens suffixed, same
      convention as the real distortion key) the instant the gesture ends —
      before the Snackbar even shows — so it survives a kill regardless of how
      soon after gesture-end the kill happens.
- [x] Answering the prompt normally (Save or dismiss/timeout) clears the pending
      key, same as before this story — no change to the in-session UX.
- [x] On the next bind of the lens the pinch happened on — whether later the same
      session (killed while backgrounded, user returns) or after a full relaunch
      — a leftover pending value restores the live/preview curvature silently and
      re-shows the exact same confirmation Snackbar, only for the page currently
      on screen.
- [x] Works correctly for a pinch made on a lens that was **not** the active one
      when the kill happened (multi-lens case) — confirmed via
      `ActHomePinchPersistenceIntegrationTest`.
- [x] Deleting a lens clears its own leftover pending key too (`deleteLensSettings`).

## Implementation notes

- `UtilSettings` gains `getPendingDistortionFactor`/`savePendingDistortionFactor`/
  `clearPendingDistortionFactor`, following the existing `lensKey`-suffixed,
  `prefs.contains`-gated pattern used by `getDistortionFactor` verbatim.
- `LensView`'s two gesture-end call sites (`onScaleEnd`, `ACTION_UP`) were
  deduplicated into one `reportPinchFinished` function (`@VisibleForTesting
  internal`, since driving a real `ScaleGestureDetector` span change via
  synthetic `MotionEvent`s to reach it is impractical/flaky) that persists the
  pending key before notifying the listener. `commitLiveDistortionFactor`/
  `resetLiveDistortionFactor` both clear it. A new `restoreLiveDistortionFactor`
  sets the live value without touching the pending key.
- `ActHome.bindLensView` silently restores a lens's pending value on **every**
  bind (not just the active page), and a new `maybeShowResurrectSnackbar` helper
  re-shows the confirmation Snackbar — called from both `bindLensView` (the
  active page being freshly bound/rebound) **and**
  `lensPageChangeCallback.onPageSelected` (swiping onto a page that was already
  bound as a prefetched `ViewPager2`/`RecyclerView` neighbour, which does not go
  through `bindLensView` again). A `resurrectPromptedLensIds` per-Activity-instance
  guard makes calling both places safe — see the real bug this caught, below.

**Real bugs found and fixed only by testing on real hardware (all disclosed in
the plan's own deviation notes, not just claimed):**

1. The first draft of the `LensView` tests simulated a finished pinch by directly
   invoking the listener (mirroring this file's own pre-existing test style) —
   which bypasses `reportPinchFinished` entirely and would have shipped with the
   persistence code silently untested. Caught by the very first test run
   (`NullPointerException`, not a false pass).
2. Calling `maybeShowResurrectSnackbar` from both `bindLensView` and
   `onPageSelected` for the same cold-launch page (confirmed live: `onPageSelected`
   does fire for position 0 at cold launch) meant the second call dismissed the
   first Snackbar — and that dismissal's callback calls `resetLiveDistortionFactor()`,
   which (because of this same story's own change) also clears the pending key.
   Net effect: the resurrect prompt appeared and then immediately un-resurrected
   itself, silently — the exact bug class this story exists to fix, self-inflicted
   by the fix's own two call sites. Fixed with the `resurrectPromptedLensIds` guard.
3. The new cross-lens integration test's own `setCurrentItem` swipe persists
   `KEY_ACTIVE_LENS_ID` as real production behavior; its `tearDown()` didn't reset
   it, which leaked into a *separate, later* `am instrument` invocation of
   `ActHomePinchWidgetTest` (SharedPreferences persist on-device across separate
   instrumentation runs) and broke a pre-existing test there. Fixed by resetting
   it in `tearDown()`; confirmed fixed by rerunning both classes back-to-back in
   the exact order that originally broke it.
4. Mutation-checks for the two `maybeShowResurrectSnackbar` call sites turned out
   inconclusive — removing either one alone still left the current test suite
   green, because `ViewPager2`/`RecyclerView`'s real bind/prefetch timing on this
   device happened to cover the gap either way in the specific scenarios tested.
   Both call sites are kept on the architectural grounds this codebase already
   documents (`bindLensView`'s own pre-existing "FISH-008 Phase 3 fix" comment: a
   config-change rebind does not refire `onPageSelected`) — disclosed as not
   proven-by-mutation rather than forced into a misleading pass.

## Required test matrix

- **Unit**: `UtilSettingsPendingDistortionTest` (6 tests) — pending key contract:
  null-when-unset, per-lens independence, clear, default-lens unsuffixed key,
  `deleteLensSettings` cleanup.
- **Widget**: `ActHomePinchWidgetTest` (+3 tests) — resurrect Snackbar reappears
  with the correct value on bind, Save from a resurrected Snackbar still persists
  and clears pending, no Snackbar when nothing is pending.
- **Integration**: `LensViewPinchIntegrationTest` (+4 tests, gesture-end persists
  pending, commit/reset clear it, `restoreLiveDistortionFactor` sets live state)
  and new `ActHomePinchPersistenceIntegrationTest` (1 test, cross-lens: a pending
  value on a non-active lens restores silently then shows its Snackbar once
  swiped to, confirmed non-flaky over 3 consecutive real-device reruns).
- **Tecno smoke**: see below.

## Test evidence

- Unit: `./gradlew testDevDebugUnitTest -q` — **601/601 pass, 0 failures**
  (baseline + 10 new: 6 `UtilSettingsPendingDistortionTest`).
- Instrumented (full suite, no class filter, TECNO KJ7):
  `adb -s 115333744A005844 shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
  — **364/365 pass**. The 1 failure
  (`AppSearchIntegrationTest#supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly`)
  is the same long-documented pre-existing `RApplication` background-scan-race
  flake pattern this repo's README has disclosed across many prior rounds —
  unrelated to this diff (touches only `search/` files this story never modified).
  All FISH-012-owned test classes independently reran clean multiple times:
  `LensViewPinchIntegrationTest` 12/12, `ActHomePinchWidgetTest` 6/6 (twice, before
  and after the cross-test-leak fix), `ActHomePinchPersistenceIntegrationTest`
  1/1 (3 consecutive reruns). One single, non-reproducing failure in the
  pre-existing `ActHomeLensManagementWidgetTest#deleteLens_alsoClearsThatLensOwnSettings`
  appeared once; passed standalone and on two subsequent full-class reruns
  (16/16 both times) — disclosed as a one-off flake, not traced to this diff.
- Lint: `./gradlew lintDevDebug -q` — **0 errors, 8 warnings** (unchanged baseline).
- Tecno smoke (TECNO KJ7, `115333744A005844`, Android 14):
  1. Normal (non-killed) Save and dismiss/timeout flows re-verified unaffected.
  2. The actual repro this story exists to fix: a pending value
     (`pending_distortion_factor=4.0`, distinct from the already-saved
     `distortion_factor=3.2`) was seeded into the real on-device
     `SharedPreferences` file — a direct, disclosed methodology substituting for
     physically driving a two-finger pinch via `adb`, which has no reliable
     multitouch primitive; the pinch-gesture mechanics themselves are separately
     proven by `LensViewPinchIntegrationTest`'s real `MotionEvent` sequences
     against a laid-out `LensView` on this same hardware. The app was then
     launched, pushed to background, killed via `adb shell am kill
     com.mckimquyen.lenslauncher` (confirmed dead via `pidof`), and brought back
     to the foreground — reproducing the exact `am kill` scenario from the
     original investigation.
  3. **Screenshot evidence**: the resurrected Snackbar appeared reading
     "Hệ số biến dạng: 4,0x" / "Lưu mặc định" — the exact injected pending value,
     distinct from the previously-saved 3.2, proving the restore path reads the
     correct number on a real device.
  4. The dismiss/timeout branch was also observed live: after the Snackbar's
     `LENGTH_LONG` window elapsed, the pending key was gone from the on-device
     prefs file and `distortion_factor` remained unchanged at 3.2 — matching the
     intended revert-without-corrupting-the-saved-value behavior. The Save branch
     is proven by the `resurrectedSnackbar_saveActionStillPersistsAndClearsPending`
     widget test's real `performClick()` on this same device (a manual `adb input
     tap` attempt at the Save button missed the Snackbar's narrow display window
     twice due to round-trip latency between screenshot/uiautomator-dump/tap
     commands — disclosed rather than silently retried until it happened to work).

## Device policy note

TECNO KJ7 (`115333744A005844`) only, per the standing session policy
(`feedback_device_target` memory). Setting this app as the temporary default
launcher for the original repro, and again for this story's smoke, was
owner-approved via `AskUserQuestion` each time and reverted back to the device's
real default (HiOS) immediately after.

## Audit score

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 1.95 | All criteria met and evidenced; multi-lens and lens-delete edge cases explicitly covered. |
| Unit-test quality and coverage | 1.5 | 1.45 | Full contract coverage for the new pending-key API, mirrors established per-lens test style. |
| Widget/UI-test quality and coverage | 1.0 | 0.95 | Resurrect, Save-from-resurrect, and no-op cases covered; real Snackbar/button interaction. |
| Integration-test quality and coverage | 1.5 | 1.4 | Gesture-end persistence, commit/reset clearing, and the cross-lens scenario all covered; two mutation-checks honestly disclosed as inconclusive rather than forced. |
| Tecno + general smoke results | 1.0 | 0.9 | Real `am kill` repro with screenshot evidence; Save-branch manual tap missed its window twice and was not force-repeated, relying on the equivalent automated test instead — disclosed, small deduction. |
| Security/privacy/Play readiness | 1.0 | 1.0 | No new permission, network, or data-exposure surface; purely local `SharedPreferences`. |
| Performance, lifecycle and regression risk | 1.0 | 0.95 | No new allocations in the render hot path; one self-inflicted regression (double-show clearing pending) was found and fixed within this same round, not left latent. |
| Maintainability and documentation truth | 1.0 | 0.95 | Every deviation, including two inconclusive mutation-checks and a cross-test leak, disclosed inline rather than hidden. |
| **Total** | **10.0** | **9.55** | |

Score **9.55/10** — exceeds the `> 9.0` push gate.

## Backlog cross-references

- Deferred from this same audit, not implemented here:
  `doc/task/todo/p2-ui-ui-023-dialog-edittext-loses-text-on-rotation.md`,
  `doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md`.
- Builds on `doc/task/done/p2-fish-fish-009-pinch-adjust-curvature.md` (live
  pinch-to-adjust itself) and the per-lens convention established in
  `doc/task/done/p2-fish-fish-008-multi-lens-workspaces.md`.
