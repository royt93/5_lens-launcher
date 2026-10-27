# FISH-038 — Move an app to another lens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From the lens grid's long-press menu, let the user move an app to another lens, with a single-level Undo.

**Architecture:** A pure `LensMovePlan` computes the new scope/selection/hidden state of the source and target lens. `LensMoveApplier` writes it through `UtilSettings` (and `LensAppScopeEditor`'s empty-selection rule) and snapshots exactly the six affected preference keys with the UI-034 `snapshotKeys/restoreKeys`. `LensView` shows the menu item and a target-lens chooser and reports the move to `ActHome`, which shows the Snackbar Undo and owns the one pending snapshot.

**Tech Stack:** Kotlin/Java, SharedPreferences (`UtilSettings`), Material AlertDialog/Snackbar, JUnit4, Robolectric, AndroidJUnitRunner.

## Global Constraints

- Real drag-and-drop is OUT of scope; it becomes follow-up story `FISH-038b` after ARCH-002. FISH-038 closes as a partial delivery and its drag criteria move to FISH-038b.
- A lens is a display scope (scope + selection + hidden in SharedPreferences). No Room schema or data change.
- Source lens in `ALL`: hide the app (keep `ALL`). Source lens in `SELECTED`: remove from selection; an empty result becomes `ALL`, never a blank lens.
- Target lens in `SELECTED`: add to selection. Target in `ALL`: leave scope and selection untouched. Always remove the app from the target's hidden set.
- Same lens or unknown target: no-op.
- Menu item is visible only when 2+ lenses exist; the current lens is never offered as a target.
- Undo is single-level, in memory, owned by `ActHome`; cleared in `onDestroy`.
- Strings go into `values` and all 16 existing locale folders (`ar de es fr hi in it ja km ko lo pt ru th vi zh`); reuse existing `reset_undo` for the Undo button.
- No new dependency, permission, or gesture change; pan/pinch/edge-swipe untouched.
- Device is locked: TECNO KJ7 `115333744A005844` only. Never run Gradle `connected*` tasks. The screen must be awake and unlocked (check `dumpsys power` `mWakefulness=Awake` and `dumpsys window` `isKeyguardShowing=false`); if not, ask the user to unlock and wait for "done".
- If a screenshot shows an ad covering the UI, stop and wait for the user's `done`.
- Do not stage `.idea/` or unrelated files.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/util/LensMovePlan.kt`: pure plan (`LensState`, `LensMovePlan`).
- Create `app/src/main/java/com/mckimquyen/util/LensMoveApplier.kt`: reads both lenses, applies a plan, returns the undo snapshot.
- Modify `app/src/main/java/com/mckimquyen/views/LensView.kt`: menu item, chooser, `OnAppMovedToLensListener`.
- Modify `app/src/main/res/menu/menu_search_result.xml`: `menuItemMoveToLens`.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`: bind the listener, Snackbar Undo, pending snapshot lifecycle.
- Modify `app/src/main/res/values*/strings.xml` (17 files).
- Create tests: `app/src/test/java/com/mckimquyen/util/LensMovePlanTest.kt`, `LensMoveApplierTest.kt`, `app/src/androidTest/java/com/mckimquyen/ui/MoveAppToLensWidgetTest.kt`.
- Create `doc/task/todo/p3-fish-fish-038b-drag-apps-between-lenses.md`; update the FISH-038 story, `doc/task/README.md`, `doc/feature.md`; create `doc/task/AUDIT_FISH-038_2026-10-27.md`.

## Task 1: Pure move plan

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensMovePlan.kt`
- Create: `app/src/test/java/com/mckimquyen/util/LensMovePlanTest.kt`

**Interface produced:**

```kotlin
data class LensState(val scope: LensAppScope, val selection: Set<String>, val hidden: Set<String>)

object LensMovePlan {
    /** New state for both lenses, or null when the move is a no-op. */
    fun plan(appId: String, source: LensState, target: LensState): Pair<LensState, LensState>?
}
```

- [ ] **Step 1: Write the failing tests** in `LensMovePlanTest.kt`:

```kotlin
package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensMovePlanTest {
    private val id = "com.chat-Main"
    private val other = "com.mail-Main"

    private fun state(scope: LensAppScope, selection: Set<String> = emptySet(), hidden: Set<String> = emptySet()) =
        LensState(scope, selection, hidden)

    @Test
    fun `selected to selected moves the id`() {
        val (src, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.SELECTED, setOf(other)))
        )
        assertEquals(state(LensAppScope.SELECTED, setOf(other)), src)
        assertEquals(state(LensAppScope.SELECTED, setOf(other, id)), dst)
    }

    @Test
    fun `selected source left empty becomes all so the lens is never blank`() {
        val (src, _) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id)), state(LensAppScope.SELECTED, setOf(other)))
        )
        assertEquals(LensAppScope.ALL, src.scope)
        assertEquals(emptySet<String>(), src.selection)
    }

    @Test
    fun `all source hides the app and stays all`() {
        val (src, _) = requireNotNull(LensMovePlan.plan(id, state(LensAppScope.ALL), state(LensAppScope.SELECTED, setOf(other))))
        assertEquals(LensAppScope.ALL, src.scope)
        assertEquals(setOf(id), src.hidden)
    }

    @Test
    fun `all target keeps its scope and selection`() {
        val (_, dst) = requireNotNull(LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.ALL)))
        assertEquals(LensAppScope.ALL, dst.scope)
        assertEquals(emptySet<String>(), dst.selection)
    }

    @Test
    fun `moving clears the id from the target hidden set`() {
        val (_, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.ALL), state(LensAppScope.ALL, hidden = setOf(id, other)))
        )
        assertEquals(setOf(other), dst.hidden)
    }

    @Test
    fun `an id already in the target selection is not duplicated`() {
        val (_, dst) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.SELECTED, setOf(id, other)), state(LensAppScope.SELECTED, setOf(id)))
        )
        assertEquals(setOf(id), dst.selection)
    }

    @Test
    fun `source hidden entries and other ids are preserved`() {
        val (src, _) = requireNotNull(
            LensMovePlan.plan(id, state(LensAppScope.ALL, hidden = setOf(other)), state(LensAppScope.ALL))
        )
        assertEquals(setOf(other, id), src.hidden)
    }

    @Test
    fun `a blank app id is a no-op`() {
        assertNull(LensMovePlan.plan("", state(LensAppScope.ALL), state(LensAppScope.ALL)))
    }
}
```

- [ ] **Step 2: Run and confirm red.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.LensMovePlanTest' --rerun`
Expected: compile failure, unresolved `LensState` / `LensMovePlan`.

- [ ] **Step 3: Implement.**

```kotlin
package com.mckimquyen.util

/** FISH-038: one lens's app scope, selection and hidden set as stored in preferences. */
data class LensState(val scope: LensAppScope, val selection: Set<String>, val hidden: Set<String>)

/** FISH-038: pure rules for moving one app from a source lens to a target lens. */
object LensMovePlan {
    /** New state for (source, target), or null when [appId] is blank. The caller rejects same-lens moves. */
    fun plan(appId: String, source: LensState, target: LensState): Pair<LensState, LensState>? {
        if (appId.isBlank()) return null
        val newSource = when (source.scope) {
            LensAppScope.SELECTED -> {
                val remaining = source.selection - appId
                // Same safeguard as LensAppScopeEditor.apply: an empty selection means "show everything".
                if (remaining.isEmpty()) source.copy(scope = LensAppScope.ALL, selection = emptySet())
                else source.copy(selection = remaining)
            }
            LensAppScope.ALL -> source.copy(hidden = source.hidden + appId)
        }
        val newTarget = when (target.scope) {
            LensAppScope.SELECTED -> target.copy(selection = target.selection + appId, hidden = target.hidden - appId)
            LensAppScope.ALL -> target.copy(hidden = target.hidden - appId)
        }
        return newSource to newTarget
    }
}
```

- [ ] **Step 4: Run green, then mutate.** Run the same command; expect PASS. Mutate one at a time with `--rerun` (each mutation must compile and turn at least one test red, then restore): drop the `remaining.isEmpty()` branch; replace `source.hidden + appId` with `source.hidden`; replace `target.selection + appId` with `target.selection`; remove `- appId` on the target hidden set (both branches, two mutations); remove the blank guard.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/util/LensMovePlan.kt app/src/test/java/com/mckimquyen/util/LensMovePlanTest.kt
git commit -m "feat(lens): model moving an app between lenses"
```

## Task 2: Apply a move and snapshot for undo

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensMoveApplier.kt`
- Create: `app/src/test/java/com/mckimquyen/util/LensMoveApplierTest.kt`

**Consumes:** `LensMovePlan.plan`, `UtilSettings.getLensAppScope/getLensAppSelection/getLensHiddenAppIds/saveLensAppScope/saveLensAppSelection/saveLensHiddenAppIds`, `UtilSettings.snapshotKeys/restoreKeys/lensKey` (public since UI-034), `ResetSnapshot`.

**Interface produced:**

```kotlin
class LensMoveApplier(private val settings: UtilSettings) {
    class Result(val snapshot: ResetSnapshot)
    /** Writes the move and returns the undo snapshot, or null when nothing was changed. */
    fun move(appId: String, sourceLensId: String, targetLensId: String): Result?
    fun undo(snapshot: ResetSnapshot): Boolean
}
```

- [ ] **Step 1: Write failing Robolectric tests** (pattern: `UtilSettingsResetSnapshotTest`; `@RunWith(RobolectricTestRunner::class) @Config(manifest = Config.NONE, sdk = [28])`, clear default prefs in `@Before`, build `UtilSettings(RuntimeEnvironment.getApplication())`). Cases, each with exact asserts:
  1. Source `SELECTED {id, other}` and target `SELECTED {other}` stored under lens ids `"default"` and `"work"`: after `move(id, "default", "work")` the stored selections are `{other}` and `{other, id}`.
  2. Source `ALL` (nothing stored): after the move, `getLensHiddenAppIds(source) == {id}` and `getLensAppScope(source) == ALL`.
  3. Snapshot restore: after `move` then `undo(result.snapshot)`, all six keys (`lensKey(KEY_LENS_APP_SCOPE/KEY_LENS_APP_SELECTION/KEY_LENS_HIDDEN_APP_IDS, lens)` for both lenses) equal their pre-move raw values, and keys that were absent are absent again (assert with `prefs.contains`).
  4. An unrelated sentinel key (`"ui_sentinel"`) is untouched by move and undo.
  5. `move(id, "work", "work")` returns `null` and writes nothing.
  6. A second lens id other than `default` uses the suffixed keys (`lens_app_selection_work`), proving `lensKey` is honoured.

- [ ] **Step 2: Run and confirm red** (`LensMoveApplier` unresolved).

- [ ] **Step 3: Implement.**

```kotlin
package com.mckimquyen.util

import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.settings.ResetSnapshot

/** FISH-038: writes a lens-to-lens move through [UtilSettings] and gives back an exact undo snapshot. */
class LensMoveApplier(private val settings: UtilSettings) {
    class Result(val snapshot: ResetSnapshot)

    fun move(appId: String, sourceLensId: String, targetLensId: String): Result? {
        if (sourceLensId == targetLensId) return null
        val (newSource, newTarget) = LensMovePlan.plan(appId, stateOf(sourceLensId), stateOf(targetLensId)) ?: return null
        val snapshot = settings.snapshotKeys(keysOf(sourceLensId) + keysOf(targetLensId))
        write(sourceLensId, newSource)
        write(targetLensId, newTarget)
        AppEventManager.notifyLensScopeChanged(sourceLensId)
        AppEventManager.notifyLensScopeChanged(targetLensId)
        return Result(snapshot)
    }

    fun undo(snapshot: ResetSnapshot): Boolean {
        val restored = settings.restoreKeys(snapshot)
        if (restored) AppEventManager.notifyLensScopeChanged(null)
        return restored
    }

    private fun stateOf(lensId: String) = LensState(
        settings.getLensAppScope(lensId), settings.getLensAppSelection(lensId), settings.getLensHiddenAppIds(lensId)
    )

    private fun write(lensId: String, state: LensState) {
        // Keep LensAppScopeEditor as the only scope/selection writer; it enforces empty -> ALL.
        LensAppScopeEditor(settings).apply(lensId, state.selection)
        settings.saveLensHiddenAppIds(lensId, state.hidden)
    }

    private fun keysOf(lensId: String): Set<String> = setOf(
        settings.lensKey(UtilSettings.KEY_LENS_APP_SCOPE, lensId),
        settings.lensKey(UtilSettings.KEY_LENS_APP_SELECTION, lensId),
        settings.lensKey(UtilSettings.KEY_LENS_HIDDEN_APP_IDS, lensId)
    )
}
```

Note: `saveLensHiddenAppIds` requires non-blank ids; `LensMovePlan` already rejects a blank `appId`.

- [ ] **Step 3b: Check `notifyLensScopeChanged(null)` is harmless.** Read `AppEventManager.notifyLensScopeChanged(data: Any? = null)` and the `ActHome` observer; if the observer needs a lens id, pass both ids on undo instead. Adjust the code and add a test asserting both lens ids are notified on undo using a `LiveData` observer (`InstantTaskExecutorRule`, as in `IconPackResetRefreshTest`).

- [ ] **Step 4: Run green, then mutate** (each must compile and turn a test red; restore after): snapshot taken after `write`; snapshot only the source keys; skip the target `write`; drop the same-lens guard; drop the `restoreKeys` call in `undo`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/util/LensMoveApplier.kt app/src/test/java/com/mckimquyen/util/LensMoveApplierTest.kt
git commit -m "feat(lens): apply a lens move with an exact undo snapshot"
```

## Task 3: Menu item, chooser and Undo Snackbar

**Files:**
- Modify: `app/src/main/res/menu/menu_search_result.xml`, `app/src/main/res/values*/strings.xml` (17 files)
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`, `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/MoveAppToLensWidgetTest.kt`

**Consumes:** `LensMoveApplier`, `LensWorkspace.loadAll`, `ResetSnapshot`, existing `menu_remove_from_lens`/`lens_hide_app` pattern.

- [ ] **Step 1: Add strings.** In `values/strings.xml` after `lens_hide_app`: `menu_move_to_lens` = "Move to lens…", `move_to_lens_title` = "Move to which lens?", `move_to_lens_done` = "Moved to %1$s". Add translated values to all 16 locale files. After editing, run a script that parses every `values*/strings.xml` and asserts the three names exist in all 17 files and that each `move_to_lens_done` keeps exactly one `%1$s`.

- [ ] **Step 2: Add the menu item** to `menu_search_result.xml` next to `menuItemRemoveFromLens`:

```xml
<item
    android:id="@+id/menuItemMoveToLens"
    android:icon="@drawable/ic_arrow_forward_24dp"
    android:title="@string/menu_move_to_lens"
    android:visible="false"
    app:iconTint="@color/popup_menu_icon_tint" />
```

Before using `ic_arrow_forward_24dp`, check `app/src/main/res/drawable/` for an existing arrow/forward/swap icon and use that exact name; if none exists reuse `ic_close_24dp`'s style by adding a vector drawable with a Material "arrow_forward" path.

- [ ] **Step 3: Write the widget test first** (`MoveAppToLensWidgetTest.kt`, package `com.mckimquyen.ui`, same fixture style as `ActHomeSearchFilterWidgetTest`: `DeviceStateGuard` rule, cancel `appRefreshPipeline`, set `RApplication.sDisableAutoAppRefresh = true`, seed `RAppsSingleton.instance.apps`, snapshot/restore workspace prefs, create two lens rows with `LensWorkspace` DAO). Tests:
  1. `moveMovesTheAppAndUndoRestoresBothLenses`: lens A `SELECTED {app1, app2}`, lens B `SELECTED {app2}`; call the same entry point the menu uses (`LensView.moveAppToLensForTest(app, "B")` added in Step 4) and assert A `{app2}`, B `{app1, app2}`, then click the Snackbar action (find it in the activity window like `ResetDefaultsWidgetTest.clickSnackbarAction`) and assert both restored exactly.
  2. `menuItemIsHiddenWithASingleLens`: with one lens, the popup menu's `menuItemMoveToLens.isVisible` is false.
  3. `targetChooserNeverListsTheCurrentLens`: `LensView.moveTargetsForTest(lenses)` excludes the current lens id.

- [ ] **Step 4: Run it and confirm red** (compile errors for the missing test hooks), then implement in `LensView.kt`:

```kotlin
/** FISH-038: told after a successful move so the host can offer Undo; LensView is not an Activity. */
fun interface OnAppMovedToLensListener {
    fun onAppMoved(targetLensName: String, undo: () -> Boolean)
}
```

Add `var onAppMovedToLensListener: OnAppMovedToLensListener? = null`, null it in the detach block next to the other listeners (line ~1761). In `showQuickActionsMenu` add:

```kotlin
popupMenu.menu.findItem(R.id.menuItemMoveToLens).isVisible = mHasOtherLenses
```

`mHasOtherLenses` is a private `Boolean` set by a public `setHasOtherLenses(Boolean)` that `ActHome.bindLensView` calls from `currentLenses.size() > 1` (the same list it already holds). In the click listener add:

```kotlin
R.id.menuItemMoveToLens -> { chooseMoveTarget(app); true }
```

```kotlin
private fun chooseMoveTarget(app: App) {
    LensWorkspace.loadAll { lenses ->
        val targets = moveTargets(lenses, lensId)
        val ctx = context
        if (targets.isEmpty() || !isAttachedToWindow) return@loadAll
        MaterialAlertDialogBuilder(ctx, R.style.MaterialYouDialogTheme)
            .setTitle(R.string.move_to_lens_title)
            .setItems(targets.map { it.name }.toTypedArray()) { _, which -> moveAppToLens(app, targets[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private fun moveAppToLens(app: App, target: LensWorkspace) {
    val settings = mUtilSettings ?: return
    val applier = LensMoveApplier(settings)
    val result = applier.move(LensAppScope.identifierOf(app), lensId, target.id) ?: return
    onAppMovedToLensListener?.onAppMoved(target.name) { applier.undo(result.snapshot) }
}

companion object { internal fun moveTargets(lenses: List<LensWorkspace>, currentLensId: String) = lenses.filter { it.id != currentLensId } }
```

(Add the `moveTargets` function to the existing companion object instead of declaring a second one.) Add `@VisibleForTesting internal fun moveAppToLensForTest(app: App, targetLensId: String, targetName: String)` that calls the same `applier.move` path, plus `moveTargetsForTest`.

In `ActHome.java` add a field `private Function0<Boolean> pendingLensMoveUndo;` and in `bindLensView` set `view.setHasOtherLenses(currentLenses.size() > 1)` and:

```java
view.setOnAppMovedToLensListener((targetName, undo) -> {
    pendingLensMoveUndo = undo;
    Snackbar.make(lensPager, getString(R.string.move_to_lens_done, targetName), Snackbar.LENGTH_LONG)
            .setAction(R.string.reset_undo, v -> {
                if (pendingLensMoveUndo != null && pendingLensMoveUndo.invoke()) pendingLensMoveUndo = null;
                else Toast.makeText(this, R.string.reset_undo_failed, Toast.LENGTH_LONG).show();
            }).show();
    return Unit.INSTANCE;
});
```

Clear `pendingLensMoveUndo = null` in `onDestroy`. Use `Function0`/`Unit` imports the file already uses; do not add a new dependency.

- [ ] **Step 5: Unit and widget runs.** `./gradlew testDevDebugUnitTest --rerun --tests 'com.mckimquyen.*Lens*' --tests 'com.mckimquyen.ui.*'` must be green. Then follow the device policy: confirm the screen is awake and unlocked, back up `databases/` + `shared_prefs/` (non-empty tar), build `assembleDevDebug assembleDevDebugAndroidTest`, install on `115333744A005844`, and run `MoveAppToLensWidgetTest` three times plus regression classes one at a time with `timeout`: `ActHomeSearchFilterWidgetTest`, `ActHomeTemporaryHidingWidgetTest`, `LensManagementActivityWidgetTest`, `FrmLensWidgetTest`.

- [ ] **Step 6: Mutation on the device and units.** Each must compile; restore after: remove the `menuItemMoveToLens.isVisible` assignment; make `moveTargets` include the current lens; skip `onAppMovedToLensListener?.onAppMoved`; make the Snackbar action do nothing. At least one assertion of `MoveAppToLensWidgetTest` or a unit test must fail per mutation. Restore the device data (`rm -rf databases shared_prefs` then untar the backup) and verify with `diff -rq`, plus locale and font scale.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/res app/src/main/java/com/mckimquyen/views/LensView.kt app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/androidTest/java/com/mckimquyen/ui/MoveAppToLensWidgetTest.kt
git commit -m "feat(lens): move an app to another lens from its menu with undo"
```

## Task 4: Gate, audit, story close and follow-up story

**Files:**
- Create: `doc/task/AUDIT_FISH-038_2026-10-27.md`, `doc/task/todo/p3-fish-fish-038b-drag-apps-between-lenses.md`
- Modify: the FISH-038 story, `doc/task/README.md`, `doc/feature.md`

- [ ] Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun`; record unit XML totals and lint errors/warnings; run `git diff --check` and `bash scripts/check-secrets.sh`.
- [ ] Create `FISH-038b` from the original story text: drag-and-drop between lenses, drag feedback, no breakage of pan/pinch/edge-swipe, depends on ARCH-002, note that the menu path from FISH-038 already provides the a11y alternative. Use the repo's story template (header table, Context, Acceptance criteria, Verification and Definition of Done, loop prompt).
- [ ] Audit: verify no Room or schema change, the plan's four decisions, the source `ALL` hides and keeps `ALL`, selection emptiness safeguard, six-key snapshot scope, listener cleared on detach, `pendingLensMoveUndo` cleared in `onDestroy`, 17 string files complete, no gesture code touched. Record mutation outcomes, device model/API/serial/time, restore proof, and limits: no drag-and-drop, undo lost when `ActHome` is destroyed, moving from an `ALL` lens uses the hidden set so "restore hidden apps" on that lens brings the app back, translations not reviewed by native speakers. Score must be > 9.0 to push.
- [ ] Update the FISH-038 story: tick the criteria met (a11y alternative, tests, smoke), move the drag criteria to FISH-038b, set status `done (partial: menu path)`, and move it to `done/`. Update `doc/task/README.md` (FISH-038 done 2026-10-27, add the FISH-038b row) and `doc/feature.md` (one entry, exactly one `## 🟡 In progress` heading).
- [ ] Commit docs, then push `origin/dev` only if the score is > 9.0. Never stage `.idea/`.

## Self-review

- Spec coverage: plan rules (Task 1), apply + six-key snapshot + undo (Task 2), menu, chooser, Snackbar, listener lifecycle, 16 locales (Task 3), device smoke and restore (Task 3), audit, partial close and FISH-038b (Task 4). Drag-and-drop is explicitly deferred.
- Type consistency: `LensState`, `LensMovePlan.plan`, `LensMoveApplier.move/undo/Result.snapshot`, `OnAppMovedToLensListener.onAppMoved(String, () -> Boolean)`, `ResetSnapshot` are named the same in every task.
- Open verification points are explicit instructions, not placeholders: the `notifyLensScopeChanged(null)` behaviour (Task 2 step 3b) and the drawable name (Task 3 step 2) are checked against the code before use.
