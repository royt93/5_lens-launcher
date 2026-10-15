# Lens Manager Material You Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Redesign the lens manager into a visually coherent Material 3 browse/edit experience, including explicit status/navigation bar contrast and a pinned edit action bar.

**Architecture:** Keep `LensManagementActivity` as state/flow owner and `LensGroupAdapter` as grouped-list renderer. Browse uses a compact Material toolbar and open list; edit uses selection count in toolbar and bottom actions above navigation insets. Existing Room/group policy and stable ID bulk operations remain the only data path. Existing Material3 theme roles provide colors in light/dark modes.

**Tech Stack:** Kotlin, Java, Android XML Views, Material Components Material3, AndroidJUnit4, Room, SharedPreferences, Gradle.

## Global Constraints

- Work only on existing branch `dev`; preserve all existing uncommitted FISH-022 changes until final local commit.
- Use TECNO KJ7 serial `115333744A005844` only; direct `adb -s` commands; never use Gradle `connected*` tasks.
- Never push. After tests and final audit reach score >9/10, create one local commit containing approved FISH-022 work and UI redesign, with required coauthor attribution.
- Keep FISH-022 semantics unchanged: stable IDs; canonical labels; global lens order; existing group assignment transaction; no search/filter chips; no new dependency or Group table.
- Use existing `Theme.Material3.DayNight`, theme color roles, `LensGroupPolicy`, `LensWorkspaceDao.assignGroup`, collapsed-state settings, and Home target-lens intent flow.
- Before each screenshot: inspect current visible UI for ad overlays; if an overlay ad appears, stop immediately and wait for user confirmation.
- Add regression tests before behavior changes. Every new listener/callback/resource must have a clear lifecycle/dispose path.

---

## File map

- Modify `app/src/main/java/com/mckimquyen/ui/LensManagementActivity.kt`: browse/edit state, toolbar title/action, pinned bottom action layout behavior and insets, quick-entry initial selection.
- Modify `app/src/main/java/com/mckimquyen/ui/LensGroupAdapter.kt`: grouped header/lens rows, active state, group counts, stable selection and accessibility descriptions.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`: pass active lens ID when current-lens group action launches manager; preserve menu item IDs and manager entry.
- Modify `app/src/main/res/layout/act_lens_management.xml`: compact Material toolbar, RecyclerView, bottom action bar.
- Modify `app/src/main/res/layout/item_lens_group_header.xml` and `item_lens_group_lens.xml`: secondary group metadata, subtle active indication, touch target and state layer.
- Modify `app/src/main/res/menu/menu_lens_group_edit.xml`: toolbar mode action with explicit title/icon.
- Modify `app/src/main/AndroidManifest.xml` only if screen/window bar attributes cannot be set safely at Activity runtime.
- Modify `app/src/main/res/values/styles.xml` only if current theme roles lack required Material 3 toolbar/button styling; use semantic existing roles, no literal color palette.
- Modify `app/src/main/res/values*/strings.xml` only for genuinely new UI copy, keeping all 17 locale files complete and Russian/Arabic plurals valid.
- Test `app/src/androidTest/java/com/mckimquyen/ui/LensManagementActivityWidgetTest.kt`, `ActHomeLensManagementWidgetTest.kt`, `ActHomeQuickLensSwitchWidgetTest.kt`, and `ActHomeMultiLensWidgetTest.kt`.
- Update `doc/task/inprogress/p2-fish-fish-022-lens-groups.md` with verified evidence; update `doc/feature.md` only if status summary changes.

## Task 1: Pin down browse/edit presentation behavior

**Files:** `app/src/androidTest/java/com/mckimquyen/ui/LensManagementActivityWidgetTest.kt`

**Interfaces:** Existing `editingGroupsForTest`, `selectedLensIdsForTest`, toolbar menu `menuLensGroupEditToggle`, `R.id.lensGroupActions`, and `R.id.tvLensGroupEditSelectionCount` remain the UI state surface for tests.

- [ ] **Step 1: Add failing toolbar/edit assertions**

Add tests that assert browse state has compact “Manage lenses” title, one Edit menu item, hidden selection count, hidden bottom action bar; tapping actual toolbar menu action enters edit, changes title to `%d selected`, exposes bottom actions, starts actions disabled, and updates the count/live region when selecting one/two lens rows. Assert Done returns to browse and clears selected IDs.

- [ ] **Step 2: Run the focused tests and verify expected RED**

Run: `./gradlew assembleDevDebugAndroidTest`
Then install APKs on locked TECNO and run:
`adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.LensManagementActivityWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: new assertions fail because current edit toolbar title is static and bottom bar is not pinned; unrelated existing cases stay distinguishable.

- [ ] **Step 3: Implement toolbar state minimally**

Keep one toolbar action item in both modes. In browse set title to `R.string.lens_management_title`, action label to `R.string.lens_group_edit_action`, icon to `ic_edit_24dp`. In edit set toolbar title via `getString(R.string.lens_group_selected_count, selectedLensIds.size)`, action label to `R.string.done`, and remove Edit icon. Toolbar action toggles mode. Selection callback updates count. Do not hide the toolbar action in either mode.

- [ ] **Step 4: Run tests and verify GREEN**

Rebuild/install both APKs, run only the focused manager class. Expected: new mode/count tests and previous manager tests pass.

## Task 2: Implement pinned Material 3 edit action bar with insets

**Files:** `app/src/main/res/layout/act_lens_management.xml`, `LensManagementActivity.kt`, `LensManagementActivityWidgetTest.kt`.

**Interfaces:** Existing `showAssignGroupDialog()` and `ungroupSelected()` remain mutation entry points. `groupOperationInProgress` remains the enable/disable source.

- [ ] **Step 1: Add failing action-bar interaction/layout tests**

Add assertions that `lensGroupActions` is a sibling below the weighted RecyclerView, is GONE in browse, visible only in edit mode, stays enabled only with selected IDs and idle operation, and remains positioned above navigation inset. Keep `RecyclerView` content bottom padding/insets sufficient so last row can scroll above bar.

- [ ] **Step 2: Verify tests fail against current layout**

Run the focused manager instrumentation test via explicit serial. Expected: hierarchy/visibility or inset assertion fails before layout change.

- [ ] **Step 3: Place actions in a bottom Material 3 surface**

Use a `MaterialCardView` or `MaterialShapeable` container only if needed for a single tonal separation surface; use `?attr/colorSurfaceContainer` / `?attr/colorSurfaceContainerHigh`, `?attr/colorOnSurface`, outline roles, no literal color. Anchor action container after weighted list. Apply system-bar bottom inset to action surface with `ViewCompat.setOnApplyWindowInsetsListener` and set matching list bottom padding; preserve original padding and restore listener/avoid accumulating offsets. Use tonal MaterialButton for Assign and TextButton for Remove with `minHeight >= 48dp`, horizontal layout weights so long locales can wrap without clipping. Disable both while empty selection or operation in progress. Remove legacy top action strip.

- [ ] **Step 4: Run browse/edit and accessibility tests**

Build APKs; run full `LensManagementActivityWidgetTest`. Expected: pass, including no test data mutation when canceling dialogs.

## Task 3: Fix toolbar and both system-bar icon tints explicitly

**Files:** `LensManagementActivity.kt`, optionally `styles.xml` or manifest only if runtime policy cannot express the theme-correct behavior; widget test in manager suite.

**Interfaces:** Use AndroidX `WindowInsetsControllerCompat` / `WindowCompat` already installed in the project; use Material `MaterialColors.getColor(context, attr, fallback)` for role resolution.

- [ ] **Step 1: Add failing assertions for status and navigation bar contrast policy**

Add a focused widget assertion that manager sets explicit status/nav icon appearance consistent with actual surface luminance, with both bars configured for light/dark. Keep assertions behavioral (resolved role and controller appearance), not device-specific hex snapshots.

- [ ] **Step 2: Run assertion and verify RED**

Run manager widget class after packaging. Expected: current manifest/theme transparent bars leave status icon appearance unmanaged and assertion fails.

- [ ] **Step 3: Apply explicit theme-driven bar treatment**

Resolve the manager surface/background role and `colorOnSurface` from the activity theme. Set status/navigation bar backgrounds to a suitable Material3 surface role. Compute light/dark system-icon appearance from resolved background luminance using AndroidX `WindowInsetsControllerCompat.setAppearanceLightStatusBars` and `setAppearanceLightNavigationBars`; configure both bar icons, not status only. Account for API 29+ contrast enforcement and edge-to-edge insets without forcing a static white/black icon tint across themes. Apply base theme color/icon appearance again when Activity pauses/destroys only if state was altered beyond that Activity window. Avoid bare white arrow asset; use an AppCompat/Material navigation icon or tint it with `colorOnSurface`.

- [ ] **Step 4: Run theme tests and inspect fresh screenshot**

Run manager suite, launch manager on TECNO, inspect UI hierarchy for ads before capture. If no overlay ad, capture light and dark screenshots if both modes can be selected without changing persisted user language/settings; verify toolbar icon, status icons, nav icons, and background contrast. If an overlay ad appears, stop and wait for user.

## Task 4: Refine list hierarchy and current-lens entry

**Files:** `LensGroupAdapter.kt`, both row layouts, `LensManagementActivity.kt`, `ActHome.java`, manager/Home widget tests.

**Interfaces:** Keep `LensGroupPolicy.sections`, stable lens IDs, `EXTRA_MANAGE_LENS_ID`, and transactional DAO unchanged.

- [ ] **Step 1: Add failing row/entry tests**

Assert current-lens popup adds exactly one correctly labeled action without renumbering existing IDs; selecting it launches manager with `EXTRA_MANAGE_LENS_ID` for the active lens. Manager starts edit mode with that valid ID selected; missing/blank IDs fall back to browse without crash or accidental selection. Assert group count, active indicator, selected checkbox, and TalkBack descriptions. Ensure accessibility row summary contains group name/count/expand action while interactive header target remains >=48dp.

- [ ] **Step 2: Verify RED**

Run focused Home + manager tests; expected: quick entry intent lacks the ID or manager state/visual semantics fail.

- [ ] **Step 3: Implement minimal visual hierarchy**

Use section title typography and `colorOnSurfaceVariant` for count/chevron. Replace box-like count/active rectangles with small rounded Material3 chips only where active/group metadata needs state; use `colorSecondaryContainer`/`colorOnSecondaryContainer` or `colorPrimaryContainer`/`colorOnPrimaryContainer`. Keep each group header a plain section row, not a card. Use Material state-layer selectable backgrounds and 48dp target bounds. Preserve expand labels and row content description. Add `EXTRA_MANAGE_LENS_ID` as default-null intent extra; validate ID against rows loaded from Room before preselecting. Current-lens popup launches manager with that lens ID; no direct Room write outside existing DAO path.

- [ ] **Step 4: Run focused Home/manager suites**

Run `LensManagementActivityWidgetTest`, `ActHomeLensManagementWidgetTest`, `ActHomeQuickLensSwitchWidgetTest`, and `ActHomeMultiLensWidgetTest` directly on locked KJ7. Expected: all pass, and existing menu item IDs remain unchanged.

## Task 5: Locale, lifecycle, and whole-diff review

**Files:** all changed UI/resource files, tests, FISH-022 story, `doc/feature.md`.

- [ ] **Step 1: Run locale completeness and translation checks**

Run `./gradlew testDevDebugUnitTest --tests com.mckimquyen.a11y.AllStringsTranslationTest --tests com.mckimquyen.a11y.LensStringTranslationTest`. Add new strings to every supported locale; Russian `few/many` and Arabic `zero/one/two/few/many/other` plurals must be complete. Check long French/German/Russian labels do not clip toolbar/actions.

- [ ] **Step 2: Check listeners, Activity lifecycle, and selected-lens edge cases**

Ensure toolbar and insets listeners are owned by the Activity view tree; dismiss dialogs on destroy; no retained view/controller/listener. Test rotate/recreate with selected IDs if covered by supported configuration, selected lens deleted before action, and unknown `EXTRA_MANAGE_LENS_ID` fallback.

- [ ] **Step 3: Review diff against approved spec**

Run `git diff --check`; inspect full diff for unrelated `.idea` changes and preserve them (do not revert). Confirm no new dependencies and no search/chips. Verify changed `ActHome` preserves all previous menu ID values.

## Task 6: Final verification, manual smoke, audit, and local commit

**Files:** `doc/task/inprogress/p2-fish-fish-022-lens-groups.md`, `doc/feature.md`.

- [ ] **Step 1: Build and run all required checks**

Run `./gradlew clean assembleDevDebugAndroidTest assembleDevDebug`, then direct explicit-serial instrumentation for manager/Home widget suites, `WorkspaceBackupImportIntegrationTest`, `AppDatabaseMigrationIntegrationTest`; run `./gradlew testDevDebugUnitTest --rerun-tasks`, `./gradlew lintDevDebug`, `scripts/check-secrets.sh`, `git diff --check`. Expected: 0 failed tests and 0 lint errors; disclose warnings.

- [ ] **Step 2: Complete ad-gated TECNO smoke**

Before screenshots, inspect launcher/menu/manager each time for actual ad overlays. If any overlay ad appears, stop immediately and wait for owner “done”. With ad-free screen, capture browse/edit-selected, verify explicit status + navigation icon colors and insets, create one temporary group via popup and bulk flows, assign/ungroup, collapse/expand, switch lens, export/import backup; restore test data after the smoke. Never use a screenshot that captures an ad.

- [ ] **Step 3: Record final audit without overclaiming**

Review each FISH-022 acceptance item (Room migration, ordering/canonicalization, manager browse/edit, current-lens action, backup/rollback, all locales, accessibility, lifecycle, screenshots). Score using repository owner standard. Keep story and feature marked In progress if any criterion remains open or score <=9.

- [ ] **Step 4: Commit locally only after all gates pass**

Stage only intended FISH-022 files plus approved existing feature changes; inspect staged diff and status. Create one local commit on `dev` with message ending `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Never push. If an unrelated user-owned `.idea` change is present, exclude it from the commit unless owner explicitly asks otherwise.
