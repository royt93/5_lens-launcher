# FISH-010 — Aperture-reveal for organization dialogs

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P2 |
| Evidence | idea |
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

## Acceptance criteria

- [ ] `ViewAnimationUtils.createCircularReveal` drives both the dialog's
      entrance (0 → full radius) and its exit (full radius → 0, then actually
      dismiss) on: `AppAdapter.showFolderDialog`, `ActHome.showLensNameDialog`
      (covers both add-lens and rename-lens, which already share this method),
      `ActHome.confirmDeleteLensDialog`.
- [ ] The dialog's own default window enter/exit animation is disabled
      (`window.setWindowAnimations(0)` or equivalent) so it never doubles up
      with the manual reveal.
- [ ] Reveal origin is the on-screen center of the real anchor `View` already
      available at each call site (`itemView` / `lensMenuAnchor()`), not a
      fabricated point - no new coordinate plumbing invented for this.
- [ ] `MaterialYouDialogTheme` (shape, color tokens, button style) is
      completely unchanged - this is an entrance/exit motion addition only,
      never a re-theme.
- [ ] Under `LensPhysicsPolicy.shouldReduceLensMotion(context)` (reduced
      motion, battery saver, or thermal ≥ MODERATE), the reveal is skipped
      entirely and the dialog shows/dismisses exactly as it does today -
      matching the precedent already set by `FISH-007`/`FISH-009`.
- [ ] Explicitly out of scope: the `PopupMenu`-based long-press quick-actions
      menu (see Context - a `PopupMenu` cannot host this without being
      rewritten as a custom `PopupWindow`, which is a separate, larger-risk
      change this story does not take on).

## Required test matrix

- [ ] Unit: a pure function computing the reveal's start radius/center from an
      anchor `View`'s on-screen bounds (no Android `Dialog`/`Window`
      dependency, testable under Robolectric or plain JVM); the
      reduced-motion gate as a pure boolean decision, mirroring
      `LensPhysicsPolicyTest`'s existing pattern.
- [ ] Widget/UI: each of the three dialogs still displays its correct content
      (folder text field / lens name field / delete confirmation message) and
      still applies the user's choice on confirm, with the reveal enabled;
      repeat with `shouldReduceLensMotion` forced true and confirm the dialog
      still opens/dismisses correctly with no reveal attempted.
- [ ] Integration: not applicable - no cross-module data flow is introduced;
      this is confined to dialog presentation in the two already-tested call
      sites.
- [ ] Smoke: on the designated device, confirm all three dialogs visibly open
      and close with the circular reveal centered on the correct anchor, and
      confirm the existing folder-label / lens-rename / lens-delete flows
      still persist correctly end to end (same checks `FISH-008`'s own smoke
      already covers, now watching for any visual regression from the new
      motion).

## Loop end condition

Same as every story in this backlog (`doc/task/README.md`'s audit rubric):
unit + widget tests for every branch (including the reduced-motion path),
smoke-verified on the designated device, `> 9.0/10` before push.
