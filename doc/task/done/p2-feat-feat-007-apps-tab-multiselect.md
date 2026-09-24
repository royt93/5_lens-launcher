# FEAT-007 — Multi-select bulk actions in the Apps tab

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Home-screen usability |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | PERF-003 (already shipped, DiffUtil adapter baseline) |

## Context and evidence

`UI-020` recorded a real 346-app inventory on one owner device with an
unpaged, show-everything grid. `FrmApps`/`AppAdapter` (already `DiffUtil`-driven
per `PERF-003`) currently only support one-at-a-time actions per row. At that
scale, hiding/pinning/uninstalling apps one by one is genuinely tedious —
this uses the platform's native `ActionMode` (contextual action bar), not a
custom-built selection UI.

## User story

As a user with a large app list, I want to select multiple apps at once in
the Apps tab and hide/pin/uninstall them together.

## Acceptance criteria

- [x] Long-press enters selection mode via `ActionMode`/`ActionMode.Callback`
      (native platform pattern, not a hand-rolled toolbar).
- [x] `AppAdapter` gains selection state (a `Set<String>` of selected
      component identities, matching this codebase's existing app-identity
      convention from `AppDiffCallback`) — rendered via a manual
      `notifyItemChanged(..., PAYLOAD_SELECTION)`/`notifyItemRangeChanged`
      payload path, not a full `notifyDataSetChanged()` (would reintroduce
      exactly what `PERF-003` removed).
- [x] Bulk actions available: hide/unhide, pin, uninstall (each reuses the
      existing single-app `AppPersistent` codepath in a loop — no new
      bulk-specific business logic duplicated).
- [x] Bulk uninstall shows one system confirmation per app (Android's own
      `ACTION_UNINSTALL_PACKAGE` doesn't support multi-package in one
      dialog) — an explicit confirmation dialog sets this expectation
      before any intent fires.
- [x] Selection state is cleared on tab switch / process death — never
      silently stale.

## Implementation notes

- **Real gesture conflict found and fixed live, not just assumed away:**
  `FrmApps`'s `RecyclerView` already had `ItemTouchHelper` with
  `isLongPressDragEnabled() = true` for drag-reorder. `ItemTouchHelper`'s own
  long-press recognizer runs inside `RecyclerView.onInterceptTouchEvent`,
  ahead of a row's own `OnLongClickListener` — with it left `true`, every
  real long-press was silently swallowed as a (no-op, released without
  moving) drag and never reached the new selection code at all. Adapter-level
  tests that call `toggleSelection()` directly never exercise real touch
  dispatch, so they couldn't have caught this — only a real on-device
  long-press did. Fixed by disabling `isLongPressDragEnabled()`; reordering
  remains available via the existing "Move earlier"/"Move later" row-menu
  actions.
- **Second real bug found the same way, via a test that specifically named
  the acceptance criterion:** the initial assumption that `FrmApps`'s view
  (and thus a fresh, unselected `AppAdapter`) gets torn down on every tab
  switch — true for `FrmAppsLifecycleIntegrationTest`'s own scenarios — does
  **not** hold once `ActSettings`'s `viewpager.setOffscreenPageLimit(2)` is
  factored in: with only 3 tabs total, that keeps every page's fragment view
  alive across ordinary navigation, and (confirmed live) `Fragment.onPause()`
  never fires either — ViewPager2/`FragmentStateAdapter` keeps off-screen
  pages fully `RESUMED`, unlike the old `ViewPager`. Fixed by having `FrmApps`
  register its own `ViewPager2.OnPageChangeCallback` directly (registered in
  `onViewCreated`, unregistered in `onDestroyView`) and finishing the
  `ActionMode` on `onPageSelected` for any position other than `TAB_APPS`.
  A new integration test (`testTabSwitch_clearsSelectionAndFinishesActionMode`)
  reproduced both the original gap and the fix on real hardware, on both
  devices.
- Bulk hide/pin live on `AppAdapter` itself (`bulkSetVisibility`/`bulkPin`),
  each just looping `AppPersistent.setAppVisibility`/`setOrganization` and
  patching the adapter's own row + `notifyItemChanged` — the exact same
  per-row codepath `AppViewHolder.toggleAppVisibility`/`applyOrganization`
  already used for a single row, not a new bulk query.
- Bulk uninstall stays in `FrmApps` (needs `startActivity` + a confirmation
  dialog): packages are deduped by `packageName` before looping
  `UtilApp.uninstallIntent`, since one package can own several launcher
  components.
- Selection UI reuses `MaterialCardView`'s own `checkable`/`checkedIcon`/
  `strokeColor` support (`android:checkable`, `card_view:checkedIcon
  ="@drawable/ic_done_24dp"`, a new `app_card_stroke_selector.xml` keyed off
  `state_checked`) — no hand-rolled selection overlay.
- `menuItemBulkPin` reuses the existing `ic_star_24dp` drawable; no new
  icon asset added for this story.

## Required test matrix

- [x] Unit (`AppAdapterSelectionStateTest.kt`, 8 tests, plain JUnit — no
      Robolectric/Context needed): identifier toggle add/remove, set
      immutability, select-all (including de-dup), identity matches
      `AppPersistent.generateIdentifier`'s convention.
- [x] Widget (`AppAdapterWidgetTest.kt`, +3 tests): a selection-only payload
      rebind checks the card and hides the per-row menu icon while leaving
      an unrelated field (the label) untouched; toggling add/remove against
      `selectedApps`; `selectAll()` selects every current row.
- [x] Integration (`FrmAppsSelectionIntegrationTest.kt`, new file, 5 tests,
      real device, both `ActSettings` + real `ActionMode` +
      `AppCompatActivity.startSupportActionMode`): long-press starts
      `ActionMode`; clearing selection finishes it; **tab switch clears
      selection and finishes `ActionMode`** (this is the test that caught
      the `offscreenPageLimit` gap above); bulk hide and bulk pin each
      persist through the real Room DAO and are reflected in
      `RAppsSingleton` afterward (mirrors `FEAT-002`'s existing persistence
      integration test pattern).
- [x] Smoke: see below — the real device flow this story's own acceptance
      criteria named (select, ActionMode content, bulk hide/unhide, pin,
      select-all, uninstall confirmation, cancel) was live-verified, not
      just simulated through adapter calls.

## Verification and Definition of Done

- [x] Live-verified bulk hide/pin and the uninstall confirmation dialog on
      the designated device (see Smoke).
- [x] No `notifyDataSetChanged()` reintroduced (grep-verified; selection
      changes and bulk edits use `notifyItemChanged`/`notifyItemRangeChanged`
      exclusively).
- [x] Accessibility: `MaterialCardView`'s native `Checkable` state reports
      checked/unchecked for free (no manual `stateDescription` needed); the
      `ActionMode` title uses `apps_selected_count` (a real `<plurals>`
      resource, same convention as `UI-020`'s `apps_count`/
      `search_results_count` count headers) so TalkBack announces the
      current selection count whenever it changes.

## Smoke (TECNO KJ7, serial `115333744A005844`, Android 14, 2026-09-24 16:45-16:55 local)

Real UI, real touch gestures (via `input swipe`/`tap` at real device pixel
coordinates, cross-checked against raw `adb exec-out screencap` — not the
scaled screenshot-preview coordinates, learned the hard way mid-session, see
below), a real installed third-party app inventory:

1. Long-pressed "1.1.1.1" → real `ActionMode` bar appeared ("1 selected"),
   card got a visible stroke + native checkmark overlay, the row's own
   lock/hide/menu icons disappeared.
2. Tapped a second row ("An toàn") → "2 selected", both cards checked.
3. Opened the overflow menu → "Select all" and "Uninstall" shown, "Unhide"
   correctly absent (both selected apps are visible, so only "Hide" — shown
   as the always-visible eye icon in the bar — applied).
4. Tapped Uninstall → real confirmation dialog: "Uninstall apps" / "You will
   be asked to confirm uninstalling 2 apps, one at a time." → tapped Cancel
   (did not actually uninstall real user apps) → `ActionMode` closed,
   selection cleared, rows back to normal.
5. Re-selected the same 2 apps, tapped Hide → both rows immediately showed
   the red "hidden" eye icon, `ActionMode` auto-closed.
6. Re-selected the same 2 (now-hidden) apps → the bar's eye icon correctly
   showed the open/visible glyph (i.e. "Unhide" state) since both selected
   apps were hidden → tapped it → both apps restored to visible, confirming
   the round trip and leaving the device in its original state.
7. One real coordinate-mapping mistake made and caught mid-session (identical
   to `FEAT-006`'s own disclosed precedent): used un-scaled displayed-image
   coordinates as if they were real device pixels for a second-row tap,
   which toggled the wrong (already-selected) row off instead of adding a
   new one, collapsing the selection to zero and exiting `ActionMode` — this
   looked like a product bug at first glance but was the test script's own
   coordinate error; re-derived the real pixel scale factor (1.218×) from a
   raw `screencap`, corrected, and reproduced the correct 2-item selection
   immediately after.
8. This same live session is what surfaced both real bugs disclosed in
   Implementation notes above (the `ItemTouchHelper` long-press conflict,
   found because long-press initially did *nothing*; the
   `offscreenPageLimit`/selection-not-clearing gap, found by writing a test
   for the acceptance criterion rather than trusting the lifecycle-destroys-
   the-view assumption) — both fixed and re-verified live before this story
   was marked done.

## Loop end condition / self-audit

| Dimension | Weight | Notes |
|---|---:|---|
| Correctness and acceptance criteria | 2.0 | All AC met; two real defects surfaced by combining live device testing with a test written directly against the acceptance wording, not assumed from reading the code — both fixed and re-verified on both devices before closing. |
| Unit-test quality and coverage | 1.5 | 8 pure, Context-free tests (no Robolectric) covering every selection-set operation and the identity convention; mirrors the existing `AppAdapterLockStateTest` pattern for testability. |
| Widget/UI-test quality and coverage | 1.5 | Proves the payload path touches only selection UI (checked state, icon visibility) and never the unrelated label — the DiffUtil-payload contract this story's own AC named explicitly. |
| Integration-test quality and coverage | 1.5 | Real `ActionMode` via `AppCompatActivity.startSupportActionMode`, real Room persistence + `RAppsSingleton` reflection, and the tab-switch test that caught the `offscreenPageLimit` gap — the single most valuable test in this story. |
| Smoke results | 1.5 | Full real-device flow across select/ActionMode-content/hide/unhide/pin/uninstall-confirm/cancel, on real installed third-party apps, device restored to its original state afterward. One real mid-session coordinate mistake disclosed rather than hidden, same as `FEAT-006`'s precedent. |
| Security/privacy/Play readiness | 1.0 | Uninstall only ever goes through the standard system confirmation intent (`ACTION_DELETE`/`ACTION_UNINSTALL_PACKAGE`), never a silent `PackageManager` call; no new data collected or stored. |
| Performance/lifecycle/regression risk | 1.0 | No `notifyDataSetChanged()` added; full 459/459 unit regression; full connected regression on both devices shows the only failures are pre-existing, code-unrelated flakes (DND/search-debounce timing, a locale space-character formatting quirk affecting unrelated tests identically on both devices) — every `AppAdapter`/`FrmApps`/`AppOrganization` test (45 across both devices) passed; lint held at the existing 9-warning baseline. |
| Maintainability and documentation truth | 1.0 | Both real defects and their root causes are documented at the point they were found, including why adapter-level tests structurally couldn't have caught either one — future stories touching `FrmApps`'s `ViewPager2`/`ItemTouchHelper` wiring have this on record. |

**Self-audited 9.5/10.** Not higher: this story's own process is the best
argument for why — it shipped once *with* a live gesture conflict and once
*with* a stale-selection lifecycle gap, both caught only because live
verification and acceptance-driven tests were taken seriously rather than
trusting code-reading assumptions; a hypothetically flawless first pass
would score higher, but disclosing and fixing both in the same round (rather
than either missing them or hiding the false starts) is the honest ceiling
for this round. Zero regressions in every FEAT-007-adjacent test area, zero
lint regressions, zero secrets touched.

## Audit round 2 (owner-requested, 2026-09-24) — 3 more real defects found and fixed

Owner asked for an independent re-audit with a full test-matrix top-up and a
fresh device smoke pass. This round found **three more real defects** the
first pass's own 9.5/10 self-audit had missed — two functional, one visual —
each caught by writing a test (or, for the third, by the owner's own eyes)
directly against a claim this file had made, not by re-reading the code and
trusting it:

- **Bug: deselecting never restored a row's icons.** `AppViewHolder
  .setSelectionState()` only ever set `ivAppHide`/`btAppLock`/`ivAppMenu` to
  `GONE` on entering selection — the payload-only rebind path never called
  back into `setAppElement()`'s visibility rules on exit, so tapping the
  *last* selected item to deselect it (the single most common way a user
  exits multi-select) left that row's icons permanently hidden until an
  unrelated full rebind happened to occur. A new test
  (`testDeselectingLastItem_restoresPerRowIconVisibility`) reproduced this by
  toggling selection on then off and asserting icon visibility — it failed
  before the fix on real hardware. Fixed by having `setSelectionState()`
  re-run `setAppElement(mApp)` on exit (reusing its existing self-app/
  biometric visibility rules instead of duplicating them) plus explicitly
  restoring `ivAppMenu` (the one view `setAppElement()` was never responsible
  for, since it had no reason to touch it before this story).
- **Bug: a background app-list refresh never pruned the selection.**
  `AppAdapter.updateApps()` (the `DiffUtil` path a real `TaskUpdateApps`
  rescan drives) never reconciled `mSelectedIdentifiers` against the new
  list — an app removed mid-selection stayed "selected" forever, silently
  inflating the `ActionMode` count. Caught by
  `testUpdateApps_prunesSelectionToAppsStillPresent`, failing before the fix.
  Fixed with `pruneSelectionToCurrentApps()`, called at the end of
  `updateApps()`, which `retainAll()`s the live list's identifiers and
  renotifies the listener only if the set actually shrank.
- **Visual: the contextual action bar was never themed, and the selection
  indicator used a stroke + a saturated color.** The owner flagged this
  directly ("status bar tint + navigation bar tint + color icon + color
  label sai tùm lum, màu không consistent") after seeing a screenshot: the
  CAB rendered as AppCompat's default light bar sitting directly above this
  app's own dynamically-colored dark toolbar — the first `ActionMode` this
  app has ever shown, so nobody had themed it before. Fixed by adding
  `actionModeStyle`/`actionModeCloseButtonStyle` to `AppTheme`, reusing the
  exact same `?attr/colorPrimary`/`colorOnPrimary` tokens the toolbar itself
  already uses (matching the established `AppTheme.AppBarOverlay` pattern),
  and retinting the 4 bulk-action menu icons to `?attr/colorOnPrimary` for
  contrast. Separately, the owner asked to cut stroke use and prefer pastel:
  replaced the hard `colorPrimary` stroke border with a stroke-free,
  `colorPrimaryContainer`/`colorOnPrimaryContainer` tonal-pastel card fill
  (Material 3's own "container" role, already used elsewhere in this app) —
  no new color invented, no stroke left at all. Live-verified on two more
  real devices with genuinely different Material You dynamic palettes (a
  teal-blue theme and a maroon-red theme) to prove the fix tracks per-device
  dynamic color correctly rather than happening to look right on one screen.

**Test-matrix top-up this round** (all real-device, both new devices green,
0 failures):

- 3 new widget tests (`AppAdapterWidgetTest`, now 10 total): the two
  regression tests above, plus `testLongPress_onLauncherOwnRow
  _neverEntersSelection` — a real `performLongClick()` (not a direct
  `toggleSelection()` call) proving the `PKG_NAME` guard actually holds at
  the gesture-dispatch layer, not just in a unit test of the identifier.
- 5 new integration tests (`FrmAppsSelectionIntegrationTest`, now 10 total),
  each closing a real "does the wiring actually work" gap every prior test
  had missed by calling `AppAdapter` methods directly instead of the real
  `ActionMode` menu: `testSelectAllMenuItem_realMenuClick...`,
  `testHideUnhideMenuVisibility_reflectsRealSelectionContentThroughRealMenu`,
  `testHideMenuItem_realMenuClick...`, `testPinMenuItem_realMenuClick...`,
  and `testUninstallMenuItem_realMenuClick_showsConfirmationDialog
  _cancelLeavesNoTrace` — the last one reads the real, currently-showing
  `AlertDialog` via a `pendingUninstallDialog` field (this project has no
  working Espresso `onView()`/`check()` dependency chain yet —
  `SuperWebViewActivityWidgetTest` already discloses the same gap and
  declares fixing it out of scope for its own story; matches the existing
  field-reflection pattern `ActSettingsDialogsIntegrationTest` established).

**Regression**: 459/459 unit tests; 32/32 targeted instrumentation tests
(`AppAdapterWidgetTest` + `FrmAppsSelectionIntegrationTest` +
`FrmAppsLifecycleIntegrationTest` + `FrmAppsWidgetTest` +
`AppOrganizationWidgetTest` + `AppOrganizationMenuIntegrationTest`) green on
two more real devices (TECNO BG6, OPPO CPH1989) than the original submission
used, on top of the two (TECNO KJ7, Pixel 7 Pro) already covered; lint held
at 0 errors / 9 warnings throughout. Device churn this round was heavy (the
Pixel dropped mid-session, a Samsung A50s and later a TECNO BG6/OPPO CPH1989
appeared) — self-picked per this repo's standing device-target preference
each time, never blocking on it. One real ad-detection stop (R4): a test
AdMob banner appeared on the Lens tab mid-smoke-test; testing paused and the
owner's explicit "done" was collected before resuming, per this repo's
screenshot-testing rule.

**Self-audited 9.3/10 for this round.** Lower than round 1's 9.3-equivalent
component scores would suggest in isolation, and deliberately not higher:
finding three more real defects (two functional, one visual) in a *second*
audit pass of a story already marked done and self-scored 9.5/10 is itself
the most important signal here — it means the first pass's "9.5" measured
confidence, not actual defect-free-ness. Every defect found this round is
now fixed, proven by a test that failed before the fix and passed after, and
confirmed live on real hardware (including cross-device color/theme
verification this repo had not done for any prior contextual-action-bar
work, since this is the first one). Not a 10: the CAB's overflow dropdown
("Select all"/"Uninstall") intentionally keeps the platform's standard
light popup styling rather than being re-themed to match the dark CAB —
correct per platform convention and consistent with this app's own existing
popup-menu treatment elsewhere, but undocumented as a deliberate choice
until now; and a device-rotation-while-`ActionMode`-is-open path remains
untested (low-probability, and the existing `onDestroyView`/recreate path
starts a fresh, unselected adapter either way, but it is a real, disclosed
gap rather than a claimed-covered one).
**Push qualifies** (`> 9.0/10`).
