# Lens dialogs — one shared Material You layer (design)

Date: 2026-10-28. Trigger: owner feedback while closing FISH-022: "dialog create group và các dialog liên quan phải dùng chung common dialog, tuân thủ nghiêm ngặt Material You". Device: TECNO KJ7 `115333744A005844` only.

## Evidence (code, 2026-10-28)

- `LensManagementActivity` builds the create-group and assign-group dialogs with a bare `AlertDialog.Builder(this)`: no `MaterialYouDialogTheme`, a plain `EditText` as the name field, no padding/typography of the shared theme.
- Rename and delete in the same Activity use `MaterialAlertDialogBuilder` but each builds its own `EditText` + `FrameLayout` with hand-set 24dp/8dp padding.
- `LensMenuCoordinator` repeats the same hand-built name field for create/rename lens, plus its own delete, freeze-confirm, "choose apps" and "hidden apps" dialogs. `LensView.chooseMoveTarget` builds its own list dialog.
- The only shared helper is `Context.showDialog2` (title + message + two buttons); nothing shared exists for text input or choice lists.
- Tests read these dialogs: `android.R.id.edit` (12 reads in three classes), the field error text (`LensManagementActivityWidgetTest:436`), `lensGroupDialogForTest`, `lensDialog`, `select_dialog_listview`/`alertTitle` lookups (`MoveAppToLensWidgetTest`, state-restoration and manager tests), and UI-032 draft restore across recreation.

## Decisions (owner, 2026-10-28)

1. Scope: **every lens dialog** (group create/assign, rename, delete, create lens, choose apps, hidden apps, freeze confirm, move-to-lens chooser). Settings/Apps/Home dialogs that are not about lenses are out of scope.
2. Name field: **outlined `TextInputLayout`** (Material 3).
3. Choosing an existing group when assigning: **bottom sheet**, built by the same shared layer.
4. **No new strings**: reuse the existing resources; no new translations.

## Design

New `ui/dialog/LensDialogs.kt` (Kotlin, `@JvmStatic` entry points so Java callers such as `LensMenuCoordinator` use it too). Every dialog is created here with `MaterialYouDialogTheme`; callers never touch `MaterialAlertDialogBuilder` for lens dialogs again.

| Entry point | Used by | Notes |
|---|---|---|
| `textInput(context, titleRes, initialText, hintRes, validate, onConfirm)` | create group, rename lens, create lens | Outlined `TextInputLayout` + `TextInputEditText`; the inner edit text keeps `android.R.id.edit`; confirm button validates and keeps the dialog open with `TextInputLayout.error`; text survives recreation |
| `confirm(context, titleRes, message, positiveRes, destructive, onConfirm)` | delete lens, freeze/unfreeze | Positive button uses the shared button style; `destructive` styles Delete with the error colour role |
| `multiChoice(context, titleRes, adapter, initial, onConfirm)` | choose apps, hidden apps | The checked set lives in the caller-owned `Set`, never read back from the `ListView` (existing rule from the long-list fix) |
| `singleChoice(context, titleRes, labels, onPick)` | move-to-lens chooser | Same theme and buttons |
| `choiceSheet(context, titleRes, labels, createLabelRes, onPick, onCreate)` | assign to group | `BottomSheetDialog` with the existing `TransBottomSheetDialog` theme; last row is "Create group"; same lifecycle contract as the dialogs above |

Shared rules in one place: theme, 24dp text-field padding taken from dimens, button order and labels, cancel-on-outside, dismiss listener that nulls the caller's reference, and haptic reject on a failed validation.

### What stays where

- Aperture reveal animation stays in `LensMenuCoordinator`; it wraps the dialog returned by `LensDialogs`.
- Test-visible fields keep their names: `lensDialog` (Home), `lensGroupDialogForTest` (manager). Because the assign step becomes a bottom sheet, `lensGroupDialogForTest` changes type from `AlertDialog` to `android.app.Dialog`; the tests that call `getListView()`/`getButton()` on the assign step are rewritten to drive the sheet's rows. The create-group, rename and delete dialogs remain `AlertDialog`.
- UI-032 recreation: `STATE_DIALOG_KIND/TEXT/TARGET` keep working; the draft text is read from the `TextInputEditText` (still `android.R.id.edit`).

### Errors and lifecycle

- Blank name: error on the `TextInputLayout`, dialog stays open, confirm does not write (current behaviour, new widget).
- Detached Activity: callbacks check `isFinishing/isDestroyed` as today.
- `onDestroy` dismisses the dialog and the sheet.
- Accessibility: the field keeps its label; error text is announced by `TextInputLayout`; sheet rows are at least 48dp.

### Out of scope

Settings/Apps/Home dialogs, date pickers, any change to lens/group data rules, new strings or translations, the aperture animation.

## Testing (TECNO KJ7)

- **Unit (JVM):** name validation rule extracted as a pure function (blank/whitespace rejected, trimmed otherwise), red first, mutation-checked.
- **Widget:** each entry point shows with the Material You theme (assert the theme overlay attributes the dialog resolves, not hard-coded colours); outlined style on the name field; error shown through `TextInputLayout.error` and dialog stays open; draft text survives recreation; checked set survives scroll in `multiChoice`; sheet lists existing groups plus "Create group" and picks correctly; Delete uses the destructive style.
- **Regression:** `LensManagementActivityWidgetTest`, `LensManagementStateRestorationTest`, `LensManagementEdgeToEdgeWidgetTest`, `LensManagerAccessibilityWidgetTest`, the 36 `ActHome*` classes, `MoveAppToLensWidgetTest`, `MaterialYouDialogWidgetTest`.
- **Smoke:** create group, assign via sheet, rename, delete on the real device, with byte-for-byte app-data restore.
- Gate: `testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest`, then `lintDevDebug` separately, `git diff --check`, `scripts/check-secrets.sh`; audit `> 9.0` before push.

## Sequencing with FISH-022

FISH-022 closing is on hold: its dialogs are what this work rewrites, so its final smoke and audit must run on the new dialogs. The untracked `doc/task/AUDIT_FISH-022_2026-10-28.md` (score 9,0, test evidence from before this change) is a draft and will be updated after this work.

## Risks

- `lensGroupDialogForTest` type change touches several widget tests; each one is updated, not deleted.
- A bottom sheet in `LensManagementActivity` must respect edge-to-edge insets; covered by the existing edge-to-edge widget test plus a sheet-specific assertion.
- The shared layer must not change the aperture reveal timing; covered by the existing `ActHomeLensManagementWidgetTest` and dialog-lifecycle tests.
