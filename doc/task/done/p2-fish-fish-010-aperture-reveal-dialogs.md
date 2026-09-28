# FISH-010 — Aperture-reveal for organization dialogs

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | LensPhysicsPolicy (already shipped) |

## Context and evidence

Generated in a 2026-09-27 `/loop` brainstorming round (owner asked for new
fisheye-brand-fitting ideas, explicitly excluding ads/VIP and anything chaining
back to already-declined `INSIGHT-001`/`ADS-001`/`VIP-001`/`REL-002`). The
owner picked this from four offered ideas, then two rounds of feasibility
checking against the actual codebase narrowed the original pitch
("iris/aperture animation replaces the folder-open moment") down to what is
real and buildable here:

- This launcher has **no tap-to-expand icon folder** anywhere. `folderName`
  (`FEAT-002`) is a free-text organizational label shown as one line in the
  Apps tab list (`AppAdapter`'s `organization_folder_label`), never a visual
  container on the home lens grid. There is no "opening a folder" moment to
  redesign.
- The long-press quick-actions menu (`LensView.showQuickActionsMenu`,
  `UI-022`) is a system `PopupMenu` — a `PopupWindow` Android manages itself,
  with no supported hook for a custom open/close animation short of replacing
  it with a hand-built `PopupWindow`. The code's own `ponytail` note on that
  same method already deferred a much smaller cosmetic change there
  (per-icon anchor precision) as "non-trivial extra plumbing for a purely
  cosmetic gain" - the same judgment applies here, more so.
- The **Set folder** dialog (`AppAdapter.showFolderDialog`) and the **rename
  lens** / **delete lens** confirmation dialogs (`ActHome.showLensNameDialog`,
  `ActHome.confirmDeleteLensDialog`) are real `AlertDialog`s built via
  `MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)` - a
  real `Window` this app already owns, where a custom enter/exit animation is
  a supported, native pattern (`ViewAnimationUtils.createCircularReveal`,
  API 21, below this app's `minSdk 25` floor - no new dependency).
- Neither call site has the tapped finger's raw coordinates (both dialogs are
  opened from a `PopupMenu` `MenuItem` click, which carries no touch
  position) - but both have a real anchor `View` already in scope
  (`AppAdapter`'s `itemView` for the row being organized;
  `ActHome.lensMenuAnchor()`, already built for `FISH-008` Phase 3) whose
  on-screen center is a faithful, always-correct substitute origin.

Scope is deliberately narrower than the original pitch: these three
`AlertDialog`s only, not the `PopupMenu`-based quick-actions menu.

## User story

As a user, I want organization dialogs (rename/delete a lens, set an app's
folder label) to open and close with a camera-aperture-style circular reveal
centered on what I was interacting with, instead of the generic Material fade,
so the launcher's own lens/camera identity shows up in its own UI chrome, not
just the home-screen grid.

## Implementation

- `util/ApertureRevealHelper.kt` (new): pure `calculateParams(anchorRect,
  containerWidth, containerHeight)` computes the reveal center/max-radius from
  an anchor rect (defaults to container center, clamps an off-screen anchor);
  `shouldAnimate(reduceMotion, hasWindow, isAttached)` is the pure gate;
  `prepareDialog`/`revealShownDialog`/`dismissWithReveal` wire
  `ViewAnimationUtils.createCircularReveal` onto a `Dialog`'s decor view,
  disabling the default window animation so it never doubles up.
- `AppAdapter.showFolderDialog`: dialog built with `.create()` instead of
  `.show()`, buttons wired manually so the positive/neutral click paths run
  `dismissWithReveal(..., itemView) { applyOrganization(...) }` — the actual
  organization write only happens in the post-dismiss callback.
- `ActHome.showLensNameDialog` (add-lens + rename-lens) and
  `ActHome.confirmDeleteLensDialog`: same pattern, anchored on
  `lensMenuAnchor()` (the page-dots indicator), reusing the exact anchor
  `FISH-008` Phase 3 already resolved for the menu itself.
- All three call `LensPhysicsPolicy.shouldReduceLensMotion(context)` through
  `ApertureRevealHelper`'s shared gate — reduced motion, battery saver, or
  thermal ≥ MODERATE skips the reveal and dismisses/applies instantly.

## Acceptance criteria

- [x] `ViewAnimationUtils.createCircularReveal` drives both the dialog's
      entrance (0 → full radius) and its exit (full radius → 0, then actually
      dismiss) on: `AppAdapter.showFolderDialog`, `ActHome.showLensNameDialog`
      (covers both add-lens and rename-lens, which already share this method),
      `ActHome.confirmDeleteLensDialog`.
- [x] The dialog's own default window enter/exit animation is disabled
      (`window.setWindowAnimations(0)` or equivalent) so it never doubles up
      with the manual reveal.
- [x] Reveal origin is the on-screen center of the real anchor `View` already
      available at each call site (`itemView` / `lensMenuAnchor()`), not a
      fabricated point - no new coordinate plumbing invented for this.
- [x] `MaterialYouDialogTheme` (shape, color tokens, button style) is
      completely unchanged - this is an entrance/exit motion addition only,
      never a re-theme. Confirmed visually on-device (see smoke below):
      identical rounded-card shape/buttons/typography as before this story.
- [x] Under `LensPhysicsPolicy.shouldReduceLensMotion(context)` (reduced
      motion, battery saver, or thermal ≥ MODERATE), the reveal is skipped
      entirely and the dialog shows/dismisses exactly as it does today -
      matching the precedent already set by `FISH-007`/`FISH-009`.
- [x] Explicitly out of scope: the `PopupMenu`-based long-press quick-actions
      menu (see Context - a `PopupMenu` cannot host this without being
      rewritten as a custom `PopupWindow`, which is a separate, larger-risk
      change this story does not take on). Untouched in this diff.

## Required test matrix

- [x] Unit: `ApertureRevealHelperTest` (7 cases, Robolectric) — pure
      `calculateParams` geometry (null anchor, empty anchor, corner anchors,
      off-screen anchor clamping, zero/negative container dimensions) and the
      `shouldAnimate` reduced-motion/window/attach gate as plain booleans.
- [x] Widget/UI: `AppAdapterFolderDialogWidgetTest` (5 cases) covers the Set
      folder dialog's content, confirm/clear/cancel button behavior, and a
      reduced-motion case (`animator_duration_scale=0`) proving the folder is
      applied synchronously with no reveal. `ActHomeLensManagementWidgetTest`
      (15 cases, 2 added this round) covers rename/delete/add-lens dialog
      content and persistence plus `renameLens_underReducedMotion_...` /
      `deleteLens_underReducedMotion_...` — forced `animator_duration_scale=0`,
      dialog dismisses synchronously (no reveal), and the DB write still lands
      once its own `Dispatchers.IO` coroutine completes (see Audit round).
- [x] Integration: not applicable - no cross-module data flow is introduced;
      this is confined to dialog presentation in the two already-tested call
      sites.
- [x] Smoke: on the designated device (TECNO KJ7, this session's locked
      device - S24 Ultra not connected, see `[[feedback-device-target]]`),
      confirmed all three dialogs live:
      - **Set folder**: opened from the Apps tab row menu, theme unchanged,
        typed + OK persisted the folder ("Organization updated" toast, list
        re-sorted), Cancel/Clear paths already covered by the widget suite.
        `screenrecord` + `ffmpeg` frame extraction caught the circular
        un-reveal mid-collapse (a shrinking white disc at the anchor) on
        confirm, proving the real animation runs on hardware, not only in the
        test harness.
      - **Rename lens**: opened via long-press on `ActHome`'s empty grid
        space (the actual `HOME`-category activity, not `ActSettings`'
        preview grid), prefilled current name, edited + OK persisted
        (re-opening the dialog showed the new name), Cancel discarded.
      - **Delete lens**: `Delete lens` menu item correctly disabled with one
        lens; added a second lens, then it enabled; confirm dialog showed the
        correct lens name and warning message, confirming deleted the lens
        and correctly clamped back to the remaining one (page-dots indicator
        hidden again, matching the single-lens contract).
      - One debug test-ad banner appeared during smoke (tab switch); per the
        project's ad-detection rule the session paused and the owner
        confirmed "done" before continuing - it never covered the dialogs
        under test.

## Audit round: one test-authoring gap found and fixed while extending coverage

While completing the required test matrix, `ActHomeLensManagementWidgetTest`
had no reduced-motion case at all for rename/delete (only
`AppAdapterFolderDialogWidgetTest` did). Adding
`renameLens_underReducedMotion_stillAppliesInstantly` and
`deleteLens_underReducedMotion_stillAppliesInstantly` initially **failed**
(`expected:<1> but was:<2>` for delete, rename's row not found) — not a
production bug: `LensWorkspace.renameLens`/`deleteLens` launch their DB write
on `Dispatchers.IO` via `ApplicationScope.scope.launch(Dispatchers.Main.immediate)`,
so it is always asynchronous relative to the dialog's own `performClick()`,
reduced motion or not. The reduced-motion contract only promises the *dialog*
dismisses synchronously (no reveal); the underlying persistence was always
one coroutine hop away, exactly like every other rename/delete test in this
file (which already `idle()` after confirming). Fixed by asserting
`dialog.isShowing == false` synchronously inside the `performClick()` block
(proving no reveal ran), then `idle()`-ing before reading the DB (proving the
write still lands). Both tests pass; no production code changed as a result
of this fix - it corrected a wrong assumption in the new test only.

## Verification and Definition of Done

- [x] `./gradlew testDevDebugUnitTest`: 580 passed, 0 failed (full regression,
      forced `--rerun`; includes the 7 new `ApertureRevealHelperTest` cases).
- [x] `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.adt.AppAdapterFolderDialogWidgetTest,com.mckimquyen.ui.ActHomeLensManagementWidgetTest`
      on TECNO KJ7: 20/20 passed (5 + 15, including the 2 reduced-motion cases
      added this round).
- [x] `./gradlew lintDevDebug`: build succeeds; no new issues attributable to
      `ApertureRevealHelper.kt`, `AppAdapter.java`, or `ActHome.java` (grepped
      the XML report for those paths — no matches; the 10 pre-existing issues
      in the report are unrelated baseline).
- [x] Smoke-verified on TECNO KJ7 (this session's locked device) - see above.
- [x] A post-change audit record scores the round `> 9.0/10` before push. —
      See "Audit score" below: **9.4/10**.
- [x] Evidence and status are updated before moving this file to `done`.

## Audit score

Scored per `doc/task/README.md`'s rubric. Self-scored in-session (no
independent AUDIT-001 tooling exists yet) — treat as a single-reviewer
estimate, not a substitute for a second pass.

| Dimension | Weight | Score | Notes |
|---|---:|---:|---|
| Correctness and acceptance criteria | 2.0 | 2.0 | All six acceptance criteria met and directly verified (geometry, window-animation suppression, anchor origin, unchanged theme, reduced-motion gate, quick-actions menu untouched). |
| Unit-test quality and coverage | 1.5 | 1.5 | 7 deterministic Robolectric cases cover every `calculateParams` edge (null/empty/corner/off-screen/degenerate-container anchors) and the 4-combination `shouldAnimate` gate. |
| Widget/UI-test quality and coverage | 1.0 | 0.95 | 20 cases across the three dialogs including reduced-motion for all three; the delete-lens reduced-motion test caught and fixed a real test-authoring gap before push (see Audit round). Minor gap: no widget test asserts the *entrance* reveal's geometry directly (only its absence under reduced motion) - covered instead by on-device smoke. |
| Integration-test quality and coverage | 1.0 | 1.0 | Correctly scoped "not applicable" - no new cross-module data flow; the DB-write timing subtlety this story did surface is covered by the widget tests above, not a separate integration seam. |
| S24 Ultra / designated-device smoke | 1.0 | 0.95 | Full live verification of all three dialogs on TECNO KJ7 (this session's approved substitute - S24 Ultra unavailable), including `screenrecord`-captured proof the circular reveal actually plays on hardware. Not a full point only because the *entrance* reveal wasn't separately frame-captured (only the exit/collapse was), and one test-ad interruption paused the run mid-session (handled per policy, not a gap in the feature itself). |
| Security/privacy/Play readiness | 1.0 | 1.0 | No new permissions, no data collection, no theme/branding drift; purely a local motion addition. |
| Performance, lifecycle and regression risk | 1.0 | 0.95 | Reveal work is a single `ValueAnimator` per dialog open/close, gated off entirely under reduced motion/battery saver/thermal; `AnimatorListenerAdapter.onAnimationCancel` also completes the dismiss so a cancelled animation can't leak an undismissed dialog. No measured perf regression (dialogs are infrequent, low-cost UI, not a hot path like `LensView.onDraw`). |
| Maintainability and documentation truth | 1.0 | 1.0 | `ApertureRevealHelper` is a single, focused, well-commented object; inline `FISH-010` comments at every call site; this story file records the actual round history including the test bug found and fixed, not just the happy path. |
| **Total** | **10** | **9.35** | **Push gate met (> 9.0).** |
