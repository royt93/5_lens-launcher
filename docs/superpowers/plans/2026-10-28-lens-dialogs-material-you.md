# Lens dialogs — shared Material You layer Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline (owner chose inline execution). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Every lens dialog is built by one shared `LensDialogs` layer that follows Material 3 strictly, replacing the bare `AlertDialog.Builder` and the hand-built name fields.

**Architecture:** Kotlin object `ui/dialog/LensDialogs` with `@JvmStatic` entry points (callable from `LensMenuCoordinator.java`). It owns theme, outlined `TextInputLayout`, button wiring and the dismiss contract; callers keep their own references (`lensDialog`, `lensGroupDialog`) and lifecycle. The assign-to-group step becomes a `BottomSheetDialog` built by the same object.

**Tech Stack:** Kotlin, Java, Material Components 1.13.0 (`MaterialAlertDialogBuilder`, `TextInputLayout`, `BottomSheetDialog`), JUnit4, AndroidJUnitRunner.

## Global Constraints

- Device: TECNO KJ7 `115333744A005844` only. Never send anything to another device.
- **No new strings and no new translations**: reuse `lens_group_create`, `lens_group_name_hint`, `lens_group_error_blank`, `lens_name_hint`, `lens_group_edit_title` and the existing lens/delete/cancel/ok strings.
- Name field is an **outlined** `TextInputLayout`; the inner `TextInputEditText` keeps `android.R.id.edit`.
- Assign-to-group picker is a **bottom sheet**; every other lens dialog stays an `AlertDialog` using `R.style.MaterialYouDialogTheme`.
- Keep: `LensManagementActivity.lensGroupDialogForTest`, `ActHome.lensDialog`, `STATE_DIALOG_KIND/TEXT/TARGET_LENS_ID` draft restore (UI-032), `ApertureRevealHelper` calls in `LensMenuCoordinator`, `ActHomeLensDialogLifecycleWidgetTest` behaviour.
- No data-rule change (groups, lens scope, backup). No change to Settings/Apps/Home dialogs that are not about lenses.
- Gate: `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, then `lintDevDebug` separately; `git diff --check`; `bash scripts/check-secrets.sh`. Never stage `.idea`. Audit `> 9.0` before push.
- Run device test classes one `adb -s 115333744A005844 shell am instrument -w -e class <FQCN> com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner` at a time; the screen must be awake and unlocked; check each screenshot for ads (R4).

## File map

- Create `app/src/main/java/com/mckimquyen/ui/dialog/LensNameRules.kt`: pure name validation and trimming.
- Create `app/src/main/java/com/mckimquyen/ui/dialog/LensDialogs.kt`: `textInput`, `confirm`, `multiChoice`, `singleChoice`, `choiceSheet`.
- Test `app/src/test/java/com/mckimquyen/ui/dialog/LensNameRulesTest.kt`.
- Test `app/src/androidTest/java/com/mckimquyen/ui/dialog/LensDialogsWidgetTest.kt`.
- Modify `ui/LensManagementActivity.kt` (rename, delete, create group, assign sheet), `ui/home/LensMenuCoordinator.java` (create/rename lens, delete lens, freeze, choose apps, hidden apps), `views/LensView.kt` (`chooseMoveTarget`).
- Modify the manager tests that read the assign-step `listView` (`LensManagementActivityWidgetTest`, `LensManagementStateRestorationTest`) to drive the sheet.

## Task 1: Pure name rules (red → green)

**Interfaces:** Produces `LensNameRules.normalize(raw: CharSequence?): String?` (trimmed text, `null` when blank) and `LensNameRules.MAX_LENGTH = 64`.

- [ ] **Step 1: Write the failing test** in `LensNameRulesTest.kt`: `null`, `""`, `"   "`, `"\t\n"` → `null`; `"  Daily  "` → `"Daily"`; `"a".repeat(64)` kept; `"a".repeat(65)` → `null`; interior spaces kept (`"My  Group"`); a name with only a zero-width/other non-printing char is **not** special-cased (kept) — assert exactly this so the rule stays simple.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.ui.dialog.LensNameRulesTest'` → FAIL: unresolved `LensNameRules`.
- [ ] **Step 3: Implement** `object LensNameRules { const val MAX_LENGTH = 64; fun normalize(raw: CharSequence?): String? = raw?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH } }`.
- [ ] **Step 4: Run green**, then mutate each operand once (`trim`, `isNotEmpty`, `<= MAX_LENGTH`, `?.`) with `/tmp/mutui.sh`; every compiling mutation must fail a named test.
- [ ] **Step 5: Commit** only the rules file and its test.

## Task 2: `LensDialogs.textInput` and `confirm` (red → green on TECNO)

**Interfaces:**
- Produces `LensDialogs.textInput(context: Context, @StringRes titleRes: Int, @StringRes hintRes: Int, initialText: String?, @StringRes blankErrorRes: Int, onConfirm: (String) -> Unit, onDismiss: Runnable?, dismissesOnConfirm: Boolean = true): AlertDialog` — **not yet shown**; caller assigns its field then calls `show()`.
- Produces `LensDialogs.confirm(context, @StringRes titleRes, message: CharSequence, @StringRes positiveRes, destructive: Boolean, onConfirm: Runnable, onDismiss: Runnable?, dismissesOnConfirm: Boolean = true): AlertDialog` (not shown). `dismissesOnConfirm = false` lets a caller (the aperture reveal in `LensMenuCoordinator`) own the dismissal.
- Both use `MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)`, `setCancelable(true)`, and wire the dismiss listener.

- [ ] **Step 1: Write failing widget tests** in `LensDialogsWidgetTest.kt` (real `LensManagementActivity` context so the theme resolves): (a) `textInput` shows an outlined `TextInputLayout` — assert `boxBackgroundMode == BOX_BACKGROUND_OUTLINE` — containing a `TextInputEditText` with id `android.R.id.edit` and the initial text; (b) blank confirm keeps the dialog showing and sets `TextInputLayout.error` to `getString(blankErrorRes)`; (c) a valid name calls `onConfirm` once with the trimmed text and dismisses; (d) 65 characters are rejected with the same error; (e) `confirm(destructive = true)` positive button text equals the resource and its text colour equals the theme's `colorError`; (f) cancel never calls `onConfirm`; (g) `onDismiss` runs on cancel and on confirm.
- [ ] **Step 2: Run red** (compile fails on missing `LensDialogs`). Build `assembleDevDebugAndroidTest`, install on KJ7.
- [ ] **Step 3: Implement** `LensDialogs.kt`. The name field layout is built in code: `TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle)` with `hint = getString(hintRes)`, a `TextInputEditText` child with `id = android.R.id.edit`, `isSingleLine = true`, `setSelectAllOnFocus(true)` when `initialText != null`; horizontal padding `dimenPx(R.dimen.dialog_text_padding)` — add `dialog_text_padding` = `24dp` to `res/values/dimens.xml` (a dimen, not a string, so no translation). On `OnShowListener`, positive click: `val name = LensNameRules.normalize(edit.text); if (name == null) { layout.error = getString(blankErrorRes); edit.performHapticFeedback(rejectConstant); return }; if (dismissesOnConfirm) dialog.dismiss(); onConfirm(name)`; clear `layout.error` on text change.
- [ ] **Step 4: Run green**, then mutations on the device test: remove the error assignment, make blank accepted, drop the trim, drop the length check, swap outlined for filled, remove `destructive` colour — every compiling mutation must be caught.
- [ ] **Step 5: Commit** `LensDialogs.kt`, dimen, test.

## Task 3: Switch the manager's rename, delete and create-group to the shared layer

**Files:** `ui/LensManagementActivity.kt`.

- [ ] **Step 1: Run the existing red-baseline.** Run `LensManagementActivityWidgetTest` and `LensManagementStateRestorationTest` on KJ7 first and record the counts (28 and 3); these must stay green except the tests rewritten in Task 5.
- [ ] **Step 2: Replace** `showRenameLensDialog`, `showDeleteLensDialog` and `showCreateGroupDialog` bodies with calls to `LensDialogs.textInput` / `confirm`. Keep: `activeDialogKind`/`activeDialogLensId` assignments, `lensGroupDialog = …` before `show()`, the dismiss listener that nulls `lensGroupDialog`, `activeDialogKind`, `activeDialogLensId`, the `setOnCancelListener` that calls `setGroupOperationIdle()` for create-group, and the haptics. `showCreateGroupDialog` passes `R.string.lens_group_create`, `R.string.lens_group_name_hint`, `R.string.lens_group_error_blank`; rename passes `R.string.lens_rename_title`, `R.string.lens_name_hint`, `R.string.lens_group_error_blank`.
- [ ] **Step 3: Update `onSaveInstanceState`** only if the draft lookup path changes: it must still find the `EditText` by `android.R.id.edit` and save its text.
- [ ] **Step 4: Run** `LensManagementActivityWidgetTest`, `LensManagementStateRestorationTest`, `LensManagementEdgeToEdgeWidgetTest`, `LensManagerAccessibilityWidgetTest`. Update only assertions that read the old plain `EditText.error`: `blankGroupNameIsRejectedWithoutWritingAndKeepsDialogOpen` now reads `TextInputLayout.error` (find the layout as `edit.parent.parent`), same expected string.
- [ ] **Step 5: Mutation**: skip trimming, skip setting `lensGroupDialog`, skip the dismiss reset — each must be caught.
- [ ] **Step 6: Commit** the activity and the updated tests.

## Task 4: Switch `LensMenuCoordinator` and `LensView` dialogs

**Files:** `ui/home/LensMenuCoordinator.java`, `views/LensView.kt`.

- [ ] **Step 1: Baseline** `ActHomeLensManagementWidgetTest` (28), `ActHomeLensDialogLifecycleWidgetTest`, `ActHomeLensFreezeWidgetTest`, `ActHomeLensAppsDialog*`, `ActHomeTemporaryHidingWidgetTest`, `MoveAppToLensWidgetTest` on KJ7.
- [ ] **Step 2: Create/rename lens** (`showLensNameDialog`) → `LensDialogs.textInput`. The aperture reveal wraps the returned dialog exactly as now (`prepareDialog`, `revealShownDialog`, `dismissWithReveal`); the positive handler that runs the reveal must still call `onDone` after the reveal. Pass `dismissesOnConfirm = false` so the coordinator controls dismissal through the reveal.
- [ ] **Step 3: Delete lens** → `confirm(destructive = true)` with the same reveal wrapping (`dismissesOnConfirm = false`); **freeze confirm** → `confirm(destructive = false)`.
- [ ] **Step 4: Choose apps / hidden apps** → `LensDialogs.multiChoice(context, titleRes, adapter, initialChecked, positiveRes, onConfirm)` (adds the entry point): the checked ids live in the caller-owned `Set`, the `ListView` uses `CHOICE_MODE_MULTIPLE`, restore checks rows from the set, never read back from the `ListView`. The empty hidden-apps case is a `confirm`-style message dialog with only Cancel.
- [ ] **Step 5: Move-to-lens chooser** in `LensView.chooseMoveTarget` → `LensDialogs.singleChoice(context, R.string.move_to_lens_title, labels) { index -> … }`.
- [ ] **Step 6: Run** the baseline classes; assertions that read `select_dialog_listview`/`alertTitle` keep working because the dialogs are still Material alert dialogs.
- [ ] **Step 7: Mutation**: reveal skipped, checked set read back from the list, wrong lens index — each caught.
- [ ] **Step 8: Commit.**

## Task 5: Assign-to-group becomes a bottom sheet

**Interfaces:** Produces `LensDialogs.choiceSheet(context, @StringRes titleRes, labels: List<String>, @StringRes createRes: Int, onPick: (Int) -> Unit, onCreate: Runnable, onCancel: Runnable): BottomSheetDialog` (not shown). The create row is the last item and is not part of `labels`.

- [ ] **Step 1: Rewrite the failing manager tests first.** In `LensManagementActivityWidgetTest` and `LensManagementStateRestorationTest`, replace `clickDialogChoice`/`dialog.listView` for the assign step with a helper that finds the sheet's `RecyclerView` row by text and performs a click; `lensGroupDialogForTest` becomes `android.app.Dialog?`. Add: sheet lists existing groups then "Create group" (assert order); picking "Personal" assigns it; "Create group" opens the name dialog; cancel returns to idle (`groupOperationInProgress` false, buttons enabled); the sheet respects the bottom inset (its container padding or `fitsSystemWindows` equals the system bar inset when present).
- [ ] **Step 2: Run red** (`lensGroupDialogForTest` still `AlertDialog`).
- [ ] **Step 3: Implement** `choiceSheet` with `BottomSheetDialog(context, R.style.TransBottomSheetDialog)`: a `RecyclerView` of rows (min height 56dp, `MaterialTextView` with `textAppearanceBodyLarge`, ripple), then a divider and the create row; set `behavior.state = STATE_EXPANDED`; call `onCancel` from `setOnCancelListener`. In `LensManagementActivity.showAssignGroupDialog` replace the `AlertDialog.Builder` call; keep `groupLoadVersion`, `groupOperationInProgress`, and the error `Toast` branch unchanged; `lensGroupDialog` and its test accessor change type to `android.app.Dialog`; `onSaveInstanceState` saves the kind only (no draft) for the sheet.
- [ ] **Step 4: Run** the manager classes, the restoration test (assign picker restored after recreation shows groups), and the edge-to-edge test.
- [ ] **Step 5: Mutation**: wrong index mapping, create row first, no cancel callback, not expanded — each caught.
- [ ] **Step 6: Commit.**

## Task 6: Gate, regression, smoke, audit, FISH-022 close

- [ ] **Step 1: Full gate** (Global Constraints) and record unit counts.
- [ ] **Step 2: Back up KJ7** to `/tmp/b-lensdlg-tecno/before.tar` (record member count and sha256 of each member), locale `[en]`, font `1.15`, Contacts denied.
- [ ] **Step 3: Regression on KJ7:** `LensDialogsWidgetTest`, the four manager classes, `MaterialYouDialogWidgetTest`, the 36 `ActHome*` classes, `MoveAppToLensWidgetTest`, `FrmLensWidgetTest`, `LauncherIntegrationTest`, `WorkspaceBackupImportIntegrationTest`, `AppDatabaseMigrationIntegrationTest`. Stop at the first red; a red in `AppSearchIntegrationTest` is the known flaky and is re-run 3 times with the result reported.
- [ ] **Step 4: Real smoke on KJ7** (screens checked for ads): create group with an outlined field, validation error on blank, assign through the sheet, rename, delete; capture screenshots.
- [ ] **Step 5: Restore the app data** and compare every member hash; locale/font/permission unchanged.
- [ ] **Step 6: Audit** `doc/task/AUDIT_LENS-DIALOGS_2026-10-28.md` and finalise `doc/task/AUDIT_FISH-022_2026-10-28.md` (replace the pre-change evidence with the runs above, score honestly); story FISH-022 `inprogress/` → `done/`, update `doc/task/README.md` and `doc/feature.md` (exactly one `## 🟡 In progress`). Push `origin/dev` only if both scores `> 9.0`.

## Self-review

- Spec coverage: shared layer (Tasks 2, 4, 5), outlined field (Task 2), bottom sheet (Task 5), all lens dialogs (Tasks 3-5), no new strings (Global Constraints, `dialog_text_padding` is a dimen), test-visible names kept (Task 3, 5), UI-032 draft restore (Task 3 Step 3), reveal animation untouched (Task 4 Step 2), FISH-022 sequencing (Task 6).
- No placeholders; types match across tasks: `textInput(..., onConfirm: (String) -> Unit, onDismiss: Runnable?)` is used identically in Tasks 3 and 4 with the added `dismissesOnConfirm` default.
- Known risk called out: assign-step tests read `listView`; they are rewritten in Task 5 Step 1 before the implementation.
