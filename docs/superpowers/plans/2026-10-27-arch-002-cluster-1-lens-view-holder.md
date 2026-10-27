# ARCH-002 cluster 1 — `LensViewHolder` (detailed plan)

Parent: `docs/superpowers/plans/2026-10-27-arch-002-split-acthome.md`. Written from `ActHome.java` at `a9ec1e97` (2.454 lines). Device: TECNO KJ7 `115333744A005844`, screen unlocked, never `connected*` Gradle tasks.

**Goal:** One class owns the lens pager state, so `lensViews` has exactly one writer, and move ~350 lines out of `ActHome` without changing behavior.

**Decision (differs from the master plan's Kotlin assumption):** write the collaborator in **Java**, `app/src/main/java/com/mckimquyen/ui/home/LensViewHolder.java`. The move is then a verbatim copy with `host.` / `this.` prefixes, instead of a hand translation of ~250 lines of lifecycle-sensitive code to Kotlin, which would itself be a behavior risk. A Kotlin conversion can be a later, separate step.

## What moves (all verbatim)

| From `ActHome.java` | To `LensViewHolder` |
|---|---|
| fields `lensViews`, `lensPager`, `lensTabMediator`, `lensPageIndicator`, `tvLensName`, `lensPagerAdapter`, `currentLenses`, `lensManagementReturnTargetId`, `pendingLensMoveUndo`, `resurrectPromptedLensIds` | same names, package-private/private |
| `lensPageChangeCallback` (219-251) | same |
| `setupViews` lines 529-553 (pager, indicator, name, adapter, mediator, listeners) | `attach(View root)` |
| `updateLensNavigationChrome` (618-641) | same name |
| `refreshLensList` (651-707) | same name |
| `bindLensView` both overloads (714-806) | same names |
| `restorePendingPinchIfAny`, `maybeShowResurrectSnackbar`, `lensViewAt` (811-858) | same names |
| `onLensesChanged` (1088-1100) | same name |
| `onDestroy` lines 2431-2438 (undo, mediator, callback) | `onDestroy()` |

`pendingAutoExportLens` **stays in `ActHome`** (set by `consumeAutoExportExtra`, cluster 4 territory); the holder reads and clears it through the host.

## Host interface `LensViewHolderHost` (Java, package `com.mckimquyen.ui.home`)

Only what the moved code calls back into `ActHome`:

```java
public interface LensViewHolderHost {
    android.app.Activity activity();                    // getPackageManager, getString, getApplication, findViewById
    UtilSettings utilSettings();                         // ActHome's private field
    java.util.ArrayList<App> listApp();                  // may be null
    java.util.ArrayList<App> scopedApps(String lensId);
    boolean isSearchShowing();                           // searchView != null && searchView.isShowing()
    void updateHiddenRecoveryButton();
    void showPinchCurvatureSnackbar(float curvature);
    void showLensManagementMenu(View anchor);
    void dismissLensManagementMenu();
    View anchorAtPressedPoint(View lens, float x, float y);
    void openLensManagement();
    void openSearchFromHome();
    boolean pendingAutoExportLens();
    void clearPendingAutoExportLens();
    void setActiveLensView(LensView view);               // the single writer of ActHome.lensViews
    LensView activeLensView();                           // current value (may be null)
    void exportActiveLensImage();
}
```

`ActHome` implements it with one-line delegates to its existing methods (their names and visibility do not change).

## Members that stay on `ActHome` as delegates (tests reference them)

**`lensViews` decision (68 test lines read the field `activity.lensViews`, so the field must keep that exact name and package-private visibility):** the **storage** stays on `ActHome` as `LensView lensViews`, but it is **written only by `LensViewHolder`**, through a host method `void setActiveLensView(LensView view)`. That gives one writer (the holder) and zero test edits. The audit checks that `lensViews =` appears exactly once in `ActHome.java`, inside `setActiveLensView`. Add `setActiveLensView` to `LensViewHolderHost`; the holder reads the current view through `host.activeLensView()` (also added).

Also keep as delegates: `refreshLensList()` (3 test lines), `updateLensNavigationChrome()`, `bindLensView` is private (5 test lines use the name: re-check they are comments or `LensPagerAdapter` binds before deleting).

`lensPager`, `currentLenses`, `lensPagerAdapter`, `lensPageIndicator`, `tvLensName` are used at ~60 places in the other clusters. They become `lensHolder.pager()`, `lensHolder.currentLenses()`, `lensHolder.adapter()`, `lensHolder.indicator()`, `lensHolder.nameLabel()`. This is a mechanical rename done with a script plus a compile; no logic changes.

## Characterization tests (before moving)

Pure decisions that exist in this block and are not unit-tested today:

1. Chrome visibility rule: `visible = !clean && !list && !searching && lensCount > 1`.
2. Position-to-lens lookup used by `updateLensNavigationChrome`: `position in 0 until size`.

Extract these two as static pure methods on `LensViewHolder` (`chromeVisible(boolean clean, boolean list, boolean searching, int lensCount)` and `isValidPosition(int position, int size)`), write `LensViewHolderLogicTest` (JVM) for all combinations, switch the old code to call them **inside `ActHome` first**, run `ActHomeLensLabelWidgetTest`, `ActHomeCleanLensModeIntegrationTest`, `ActHomeMultiLensWidgetTest` on KJ7, then move. Mutation-check these two methods only.

## Steps

- [ ] 1. Write `LensViewHolderLogicTest` (red: unresolved reference), add the two static methods, green.
- [ ] 2. Make `ActHome.updateLensNavigationChrome` call `chromeVisible`; run the three classes above on KJ7. Commit nothing yet.
- [ ] 3. Create `LensViewHolderHost` and `LensViewHolder`; copy the table's code verbatim; compile.
- [ ] 4. In `ActHome`: implement the host, delete the moved code, replace external references by script (`lensPager` -> `lensHolder.pager()`, etc.), keep the delegates, call `lensHolder.onDestroy()` in `onDestroy`.
- [ ] 5. `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`: green.
- [ ] 6. Mutation on `chromeVisible`/`isValidPosition`, compile-checked; clean rebuild after.
- [ ] 7. Install on KJ7; run `/tmp/run_acthome.sh` (expect 35/35) plus `MoveAppToLensWidgetTest`, `LensManagementActivityWidgetTest`, `FrmLensWidgetTest`, `LauncherIntegrationTest`.
- [ ] 8. Check: `grep -c "lensViews =" ActHome.java` is 1; `wc -l`; commit `refactor(home): move lens pager state into LensViewHolder` with before/after line counts.

## Stop conditions

Any red class stops the cluster. If a moved block needs a rewrite (not a copy) to compile, stop and record why instead of improvising.
