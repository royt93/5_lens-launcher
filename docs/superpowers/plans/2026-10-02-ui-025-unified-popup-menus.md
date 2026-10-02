# UI-025 Unified Popup Menus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the lens-management popup rounded like every other popup, and open the icon quick-actions popup next to the pressed icon instead of the screen centre.

**Architecture:** The lens menu only needs its `PopupMenu` built from a `PopupMenuTheme`-wrapped context. The icon menu gets a transient 1×1 invisible anchor `View` placed in `LensView`'s parent `FrameLayout` at the pressed icon's resting (base) cell; it is removed on dismiss and on detach. A JVM source-scan test makes any future un-themed `PopupMenu` fail the build.

**Tech Stack:** Kotlin, Java, Android Views, `androidx.appcompat.widget.PopupMenu`, JUnit4, AndroidX Test.

## Global Constraints

- minSdk 25, target/compileSdk 37.
- No new dependency, string, preference key, timer, observer, or coroutine.
- No change to `bg_popup_menu.xml` (16 dp), dialog shape, menu content, or menu item ids.
- The icon anchor uses the icon's **base** rect from `LensView.getAppBounds(index, Rect)`, never the live magnified rect (`mRectToSelect`).
- At most one anchor at a time; removed on popup dismiss **and** in `onDetachedFromWindow`; every failure falls back to the old `this` + `Gravity.CENTER` path and never throws.
- Tests derive expected positions from the device (`getAppBounds`, `getLocationOnScreen`); no hardcoded pixel values.
- Locked device: TECNO KJ7 serial `115333744A005844`. Never address the attached Pixel 7 Pro. Never run a Gradle `connected*` task. Run instrumentation only with `adb -s 115333744A005844 shell am instrument …`.
- A test that opens `ActHome` must not leave `clean_lens_mode` set; after any manual smoke, delete it from the device preferences (command in Task 3).
- Every production change follows RED → GREEN; watch each new test fail for the stated reason first.
- Done requires unit, widget, integration, lint, full direct-device instrumentation, physical smoke, independent `/code-review`, and audit strictly greater than 9.0/10. Nothing is pushed without the owner's explicit say-so.

## Shared shell snippets (run from the repo root)

Build and install both APKs on the locked device:

```bash
S=115333744A005844
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s $S install -r "$(find app/build/outputs/apk/dev/debug -name '*.apk' | head -1)"
adb -s $S install -r "$(find app/build/outputs/apk/androidTest/dev/debug -name '*.apk' | head -1)"
adb -s $S shell input keyevent KEYCODE_WAKEUP
```

Run instrumented classes (replace `CLASSES` with a comma-separated list):

```bash
adb -s 115333744A005844 shell am instrument -w -e class CLASSES \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

## File Map

- `app/src/main/java/com/mckimquyen/ui/ActHome.java` — wrap the lens menu context in `PopupMenuTheme`.
- `app/src/main/java/com/mckimquyen/views/LensView.kt` — transient icon anchor, popup lifecycle, test accessors.
- `app/src/test/java/com/mckimquyen/ui/PopupMenuThemeConventionTest.kt` — new JVM source scan.
- `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt` — rounded-theme assertion.
- `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt` — unattached fallback leaves no anchor.
- `app/src/androidTest/java/com/mckimquyen/views/LensViewQuickActionsIntegrationTest.kt` — anchor position, lifecycle, single-anchor.
- `doc/task/*/p2-ui-ui-025-unified-popup-menus.md`, `doc/feature.md` — story lifecycle and evidence.

---

### Task 1: Rounded Lens Menu and a Guard Against Square Popups

**Files:**
- Create: `doc/task/todo/p2-ui-ui-025-unified-popup-menus.md`, then `git mv` it to `doc/task/inprogress/`
- Create: `app/src/test/java/com/mckimquyen/ui/PopupMenuThemeConventionTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java` (`showLensManagementMenu`, imports)
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt`

**Interfaces:**
- Produces: convention that every `PopupMenu(` in `app/src/main` takes a first argument named `wrapper` or `themed`, built from `R.style.PopupMenuTheme`.

- [ ] **Step 1: Create the story and mark it in progress**

Create `doc/task/todo/p2-ui-ui-025-unified-popup-menus.md`:

```markdown
# UI-025 — Unified popup menus

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed (owner report + code audit) |
| Epic | Home-screen usability |
| Estimate | 3-5 SP |
| Risk | Medium |
| Dependencies | UI-022, FISH-008, FISH-016 |

## Owner decision (2026-10-02)

Owner saw a square-cornered popup next to rounded dialogs. Audit: `ActHome.showLensManagementMenu()` builds `new PopupMenu(this, anchor)` and skips `PopupMenuTheme`; every other popup already uses it. Owner chose one story covering both rounding and anchoring the icon quick-actions menu next to the pressed icon (it opened in the screen centre; see the `ponytail:` note in `LensView.showQuickActionsMenu`).

Design: `docs/superpowers/specs/2026-10-02-ui-025-unified-popup-menus-design.md`. Plan: `docs/superpowers/plans/2026-10-02-ui-025-unified-popup-menus.md`.

## Acceptance criteria

- [ ] The lens-management menu uses `PopupMenuTheme` (16 dp rounded background).
- [ ] A JVM test fails if any `PopupMenu(` in `app/src/main` is built from an un-themed context.
- [ ] Long-pressing an icon opens its quick-actions menu anchored at that icon's cell, for different icons.
- [ ] The anchor view is removed on dismiss and on detach; never more than one exists.
- [ ] Unattached or non-FrameLayout parents fall back to the old path without crashing.
- [ ] Unit, widget, integration, lint, full instrumented, TECNO KJ7 smoke, independent review, audit > 9.0.
```

Then run:

```bash
git mv doc/task/todo/p2-ui-ui-025-unified-popup-menus.md doc/task/inprogress/p2-ui-ui-025-unified-popup-menus.md
sed -i '' 's/^| Status | todo |$/| Status | inprogress |/' doc/task/inprogress/p2-ui-ui-025-unified-popup-menus.md
```

- [ ] **Step 2: Write the failing JVM source-scan test**

Create `app/src/test/java/com/mckimquyen/ui/PopupMenuThemeConventionTest.kt`:

```kotlin
package com.mckimquyen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI-025: the lens-management popup shipped square because one call site built its PopupMenu
 * from a bare Activity instead of a PopupMenuTheme-wrapped context. This is a convention guard:
 * every PopupMenu constructed in main sources must take a context named `wrapper` or `themed`,
 * and its file must reference PopupMenuTheme. It cannot prove the wrapper is built correctly -
 * the device widget test resolves the real theme attribute for that.
 */
class PopupMenuThemeConventionTest {

    private val themedContextNames = setOf("wrapper", "themed")

    // Matches `PopupMenu(x,` and `new PopupMenu(x,` but not `ListPopupMenu(` or `a.PopupMenu(`.
    private val constructorCall = Regex("""(?<![\w.])(?:new\s+)?PopupMenu\(\s*(\w+)\s*,""")

    private fun mainSources(): List<File> {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val root = sequenceOf(
            File(workingDirectory, "src/main/java"),
            File(workingDirectory, "app/src/main/java")
        ).first { it.isDirectory }
        return root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }

    @Test
    fun `the scan really finds the known popup call sites`() {
        val sites = mainSources().sumOf { constructorCall.findAll(it.readText()).count() }
        assertTrue("expected at least the four known PopupMenu call sites, found $sites", sites >= 4)
    }

    @Test
    fun `every PopupMenu is built from a PopupMenuTheme wrapped context`() {
        val offenders = mutableListOf<String>()
        for (file in mainSources()) {
            val text = file.readText()
            for (match in constructorCall.findAll(text)) {
                val firstArgument = match.groupValues[1]
                if (firstArgument !in themedContextNames) {
                    offenders += "${file.name}: PopupMenu($firstArgument, ...) is not a themed context"
                } else if (!text.contains("PopupMenuTheme")) {
                    offenders += "${file.name}: PopupMenu($firstArgument, ...) but no PopupMenuTheme in file"
                }
            }
        }
        assertEquals("un-themed popups render square corners: $offenders", emptyList<String>(), offenders)
    }
}
```

- [ ] **Step 3: Run it and verify RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.ui.PopupMenuThemeConventionTest`
Expected: `the scan really finds…` PASS; `every PopupMenu is built…` FAIL with `ActHome.java: PopupMenu(this, ...) is not a themed context`. If it fails for any other reason (compile error, zero offenders), stop and fix the test.

- [ ] **Step 4: Write the failing device widget test**

In `ActHomeLensManagementWidgetTest.kt`, add before `emptySpaceLongPress_createsASecondLensEndToEnd`:

```kotlin
    /**
     * UI-025: the lens menu must be built from the same rounded popup theme as every other popup.
     * PopupMenu has no theme getter, so read the theme off its MenuBuilder's context and resolve
     * the real `popupMenuStyle` attribute - the style a square menu would NOT resolve to.
     */
    @Test
    fun lensMenu_resolvesTheRoundedPopupStyle() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()

            scenario.onActivity { activity ->
                activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    .onEmptySpaceLongPressListener!!.onEmptySpaceLongPress()

                val builder = activity.lensManagementMenu!!.menu as androidx.appcompat.view.menu.MenuBuilder
                val resolved = android.util.TypedValue()
                assertTrue(
                    "popupMenuStyle must resolve in the menu's context",
                    builder.context.theme.resolveAttribute(androidx.appcompat.R.attr.popupMenuStyle, resolved, true)
                )
                assertEquals(
                    "the lens menu must use the rounded popup style, not the default square one",
                    R.style.RoundedPopupMenuStyle,
                    resolved.resourceId
                )
            }
        }
    }
```

- [ ] **Step 5: Build, install, verify RED on KJ7**

Run the build/install snippet, then the run snippet with `CLASSES=com.mckimquyen.ui.ActHomeLensManagementWidgetTest#lensMenu_resolvesTheRoundedPopupStyle`.
Expected: FAIL `the lens menu must use the rounded popup style, not the default square one expected:<…> but was:<…>`. If `resolveAttribute` returns false or the cast throws, fix the test (not the production code) and re-run until it fails for the stated reason.

- [ ] **Step 6: Implement the minimal fix**

In `ActHome.java`, add `import android.view.ContextThemeWrapper;` next to the other `android.view.*` imports (keep alphabetical placement), then change `showLensManagementMenu`:

```java
    private void showLensManagementMenu(View anchor) {
        // UI-025: same rounded popup theme as AppAdapter/SearchResultAdapter/LensView - a bare
        // Activity context skips it and renders the default square popup.
        Context themed = new ContextThemeWrapper(this, R.style.PopupMenuTheme);
        PopupMenu menu = new PopupMenu(themed, anchor);
```

Leave everything after that line unchanged. If `android.content.Context` is not already imported in `ActHome.java`, add `import android.content.Context;`.

- [ ] **Step 7: Verify GREEN (unit + device) and regression**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.ui.PopupMenuThemeConventionTest` — Expected: PASS.
Run the build/install snippet, then the run snippet with `CLASSES=com.mckimquyen.ui.ActHomeLensManagementWidgetTest` — Expected: all PASS (existing 19 + 1 new). Existing menu tests drive `onLensMenuItemSelected` directly and must not change.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java \
  app/src/test/java/com/mckimquyen/ui/PopupMenuThemeConventionTest.kt \
  app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt \
  doc/task/inprogress/p2-ui-ui-025-unified-popup-menus.md
git commit -m "fix(ui): theme the lens menu popup and guard against square popups

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Anchor the Icon Quick-Actions Menu at the Pressed Icon

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt` (imports; `showAppOptionsAtIndex`, `showQuickActionsMenu`, `onDetachedFromWindow`)
- Modify: `app/src/androidTest/java/com/mckimquyen/views/LensViewQuickActionsIntegrationTest.kt`
- Modify: `app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt`

**Interfaces:**
- Consumes: `LensView.getAppBounds(index: Int, outRect: Rect): Boolean` (existing, base cell rect in `LensView` coordinates).
- Produces (test seams): `internal val quickActionsAnchorForTest: View?`, `internal val quickActionsMenuForTest: PopupMenu?` on `LensView`.

- [ ] **Step 1: Write the failing integration tests**

Replace the imports and add tests in `LensViewQuickActionsIntegrationTest.kt`. Keep the two existing tests unchanged. Add these imports:

```kotlin
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
```

Add these members to the class:

```kotlin
    private companion object {
        const val GRID_APP_COUNT = 12
        const val FIRST_INDEX = 0
        const val LAST_INDEX = GRID_APP_COUNT - 1
        const val LAYOUT_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }

    private fun gridApps() = ArrayList(
        (0 until GRID_APP_COUNT).map {
            App(packageName = "com.ui025.test.app$it", name = "App$it", label = "App $it")
        }
    )

    /** Launches ActHome and waits until the grid has been drawn once, so icon bounds exist. */
    private fun launchWithDrawnGrid(): ActivityScenario<ActHome> {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { it.findViewById<LensView>(R.id.lensViews).setApps(gridApps()) }
        val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                ready = it.findViewById<LensView>(R.id.lensViews).getAppBounds(LAST_INDEX, Rect())
            }
            if (!ready) SystemClock.sleep(POLL_MS)
        }
        assertTrue("the lens grid never drew, so icon bounds are unavailable", ready)
        return scenario
    }

    /** Screen position the popup anchor must have for [index]: the icon cell's bottom-centre. */
    private fun expectedAnchorScreenPosition(lensView: LensView, index: Int): Pair<Int, Int> {
        val bounds = Rect()
        assertTrue(lensView.getAppBounds(index, bounds))
        val lensOnScreen = IntArray(2).also { lensView.getLocationOnScreen(it) }
        return (lensOnScreen[0] + bounds.centerX()) to (lensOnScreen[1] + bounds.bottom)
    }

    private fun anchorScreenPosition(anchor: View): Pair<Int, Int> {
        val onScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        return onScreen[0] to onScreen[1]
    }

    @Test
    fun quickActionsMenu_isAnchoredAtThePressedIconsCell() {
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                lensView.setApps(gridApps())

                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                val firstAnchor = lensView.quickActionsAnchorForTest
                assertNotNull("an anchor must exist while the menu is open", firstAnchor)
                val firstPosition = anchorScreenPosition(firstAnchor!!)
                assertEquals(expectedAnchorScreenPosition(lensView, FIRST_INDEX), firstPosition)

                assertTrue(lensView.showAppOptionsAtIndex(LAST_INDEX))
                val secondPosition = anchorScreenPosition(lensView.quickActionsAnchorForTest!!)
                assertEquals(expectedAnchorScreenPosition(lensView, LAST_INDEX), secondPosition)

                assertNotEquals(
                    "different icons must anchor at different places, not one fixed spot",
                    firstPosition,
                    secondPosition
                )
            }
        }
    }

    @Test
    fun quickActionsMenu_neverLeavesMoreThanOneAnchor() {
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                lensView.setApps(gridApps())
                val parent = lensView.parent as ViewGroup
                val before = parent.childCount

                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                assertTrue(lensView.showAppOptionsAtIndex(LAST_INDEX))

                assertEquals("exactly one anchor may be added", before + 1, parent.childCount)
            }
        }
    }

    @Test
    fun quickActionsMenu_removesItsAnchorWhenDismissed() {
        launchWithDrawnGrid().use { scenario ->
            var parent: ViewGroup? = null
            var before = 0
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                lensView.setApps(gridApps())
                parent = lensView.parent as ViewGroup
                before = parent!!.childCount
                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                assertEquals(before + 1, parent!!.childCount)
                lensView.quickActionsMenuForTest!!.dismiss()
            }
            val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
            while (parent!!.childCount != before && SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                SystemClock.sleep(POLL_MS)
            }
            assertEquals("dismissing the menu must remove its anchor", before, parent!!.childCount)
            scenario.onActivity {
                assertNull(it.findViewById<LensView>(R.id.lensViews).quickActionsAnchorForTest)
            }
        }
    }

    @Test
    fun quickActionsMenu_removesItsAnchorWhenTheLensIsDetached() {
        var anchor: View? = null
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                lensView.setApps(gridApps())
                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                anchor = lensView.quickActionsAnchorForTest
                assertNotNull(anchor!!.parent)
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        assertNull("a detached lens must not leave its anchor attached to the page", anchor!!.parent)
    }
```

Add `import androidx.test.core.app.ActivityScenario` is already present; keep it.

- [ ] **Step 2: Add the unattached-fallback widget test**

In `LensViewWidgetTest.kt`, add near the other UI-022 tests:

```kotlin
    @Test
    fun quickActionsMenu_onAnUnattachedView_failsCleanlyAndLeavesNoAnchor() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            layoutSingleAppGridAndDispatchDown()
            val shown = lensView.showAppOptionsAtIndex(0)
            org.junit.Assert.assertFalse("no window token, so the menu cannot show", shown)
            org.junit.Assert.assertNull(
                "no parent FrameLayout means no anchor may be created",
                lensView.quickActionsAnchorForTest
            )
        }
    }
```

- [ ] **Step 3: Verify RED**

Run: `./gradlew compileDevDebugAndroidTestKotlin`
Expected: FAIL with `Unresolved reference 'quickActionsAnchorForTest'` and `'quickActionsMenuForTest'` only.

- [ ] **Step 4: Implement the anchor**

In `LensView.kt` add imports `android.view.ViewGroup` and `android.widget.FrameLayout` (keep import order), then add these members next to the long-press fields:

```kotlin
    // UI-025: LensView draws every icon on one canvas, so there is no child view to anchor a
    // popup to. A transient 1x1 invisible View is placed in the parent FrameLayout at the pressed
    // icon's resting cell and removed again on dismiss/detach. It uses the BASE rect (the cell the
    // icon returns to), not the live magnified rect, so it does not depend on animation state.
    private var mQuickActionsAnchor: View? = null
    private var mQuickActionsMenu: PopupMenu? = null

    @androidx.annotation.VisibleForTesting
    internal val quickActionsAnchorForTest: View? get() = mQuickActionsAnchor

    @androidx.annotation.VisibleForTesting
    internal val quickActionsMenuForTest: PopupMenu? get() = mQuickActionsMenu

    /** Returns the new anchor, or null when this view has no FrameLayout parent or icon bounds. */
    private fun attachQuickActionsAnchor(index: Int): View? {
        val frame = parent as? FrameLayout ?: return null
        val bounds = Rect()
        if (!getAppBounds(index, bounds)) return null
        val anchorX = left + bounds.centerX()
        val anchorY = top + bounds.bottom
        val anchor = View(context)
        frame.addView(
            anchor,
            FrameLayout.LayoutParams(QUICK_ACTIONS_ANCHOR_SIZE_PX, QUICK_ACTIONS_ANCHOR_SIZE_PX).apply {
                leftMargin = anchorX
                topMargin = anchorY
            }
        )
        // PopupMenu reads the anchor's screen position immediately, before the next layout pass.
        anchor.measure(
            View.MeasureSpec.makeMeasureSpec(QUICK_ACTIONS_ANCHOR_SIZE_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(QUICK_ACTIONS_ANCHOR_SIZE_PX, View.MeasureSpec.EXACTLY)
        )
        anchor.layout(
            anchorX,
            anchorY,
            anchorX + QUICK_ACTIONS_ANCHOR_SIZE_PX,
            anchorY + QUICK_ACTIONS_ANCHOR_SIZE_PX
        )
        mQuickActionsAnchor = anchor
        return anchor
    }

    private fun removeQuickActionsAnchor() {
        mQuickActionsAnchor?.let { (it.parent as? ViewGroup)?.removeView(it) }
        mQuickActionsAnchor = null
    }
```

Add to the `companion object` (next to the other private constants):

```kotlin
        private const val QUICK_ACTIONS_ANCHOR_SIZE_PX = 1
```

Change `showAppOptionsAtIndex` to pass the index:

```kotlin
        return showQuickActionsMenu(list[index], index)
```

Replace `showQuickActionsMenu` (and delete its `ponytail:` paragraph, replacing it with a one-line pointer to this anchor design):

```kotlin
    private fun showQuickActionsMenu(app: App, index: Int): Boolean {
        return try {
            // A previous menu's dismiss listener removes the previous anchor; do this first so it
            // can never remove the anchor created for this press.
            mQuickActionsMenu?.dismiss()
            val anchor = attachQuickActionsAnchor(index)
            val wrapper = ContextThemeWrapper(context, R.style.PopupMenuTheme)
            val popupMenu = PopupMenu(
                wrapper,
                anchor ?: this,
                if (anchor != null) Gravity.NO_GRAVITY else Gravity.CENTER
            )
            popupMenu.inflate(R.menu.menu_search_result)
            popupMenu.menu.findItem(R.id.menuItemUnpin).isVisible = app.pinnedZone != PinnedZone.NONE
            popupMenu.setForceShowIcon(true)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menuItemElementAppInfo -> {
                        startQuickActionIntent(UtilApp.appInfoIntent(app.packageName.toString()))
                        true
                    }
                    R.id.menuItemElementUninstall -> {
                        startQuickActionIntent(UtilApp.uninstallIntent(app.packageName.toString()))
                        true
                    }
                    R.id.menuItemPinStart -> {
                        pinQuickAction(app, PinnedZone.START)
                        true
                    }
                    R.id.menuItemPinEnd -> {
                        pinQuickAction(app, PinnedZone.END)
                        true
                    }
                    R.id.menuItemUnpin -> {
                        pinQuickAction(app, PinnedZone.NONE)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.setOnDismissListener {
                if (mQuickActionsMenu === popupMenu) {
                    removeQuickActionsAnchor()
                    mQuickActionsMenu = null
                }
            }
            mQuickActionsMenu = popupMenu
            popupMenu.show()
            true
        } catch (_: Exception) {
            removeQuickActionsAnchor()
            mQuickActionsMenu = null
            false
        }
    }
```

In `onDetachedFromWindow()`, directly after `resetSearchSwipeState()`, add:

```kotlin
        // UI-025: an open icon menu is a window owned by this view; it and its anchor must not
        // outlive the view (rotation, page recycle, Activity destroy).
        mQuickActionsMenu?.dismiss()
        removeQuickActionsAnchor()
        mQuickActionsMenu = null
```

- [ ] **Step 5: Verify GREEN on KJ7 and regression**

Run the build/install snippet, then the run snippet with:
`CLASSES=com.mckimquyen.views.LensViewQuickActionsIntegrationTest,com.mckimquyen.views.LensViewWidgetTest,com.mckimquyen.a11y.LensViewAccessibilityWidgetTest,com.mckimquyen.ui.ActHomeLensManagementWidgetTest,com.mckimquyen.ui.ActHomePinchWidgetTest,com.mckimquyen.ui.ActHomeMultiLensWidgetTest`
Expected: all PASS, including the 2 pre-existing quick-actions integration tests (they call `showAppOptionsAtIndex(0)` without waiting for a draw; if bounds are not ready the fallback path must still return `true`, which is why the fallback exists).

- [ ] **Step 6: Mutation-check the two behaviours that matter**

Copy `LensView.kt` aside (`cp … /tmp/LensView.ui025`). Mutant A: in `attachQuickActionsAnchor` replace `bounds.bottom` with `0`; rebuild/install; `quickActionsMenu_isAnchoredAtThePressedIconsCell` must FAIL. Restore. Mutant B: delete the three added lines in `onDetachedFromWindow`; `quickActionsMenu_removesItsAnchorWhenTheLensIsDetached` must FAIL. Restore from the copy and confirm `git diff` shows only the intended change and the tests are green again.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mckimquyen/views/LensView.kt \
  app/src/androidTest/java/com/mckimquyen/views/LensViewQuickActionsIntegrationTest.kt \
  app/src/androidTest/java/com/mckimquyen/views/LensViewWidgetTest.kt
git commit -m "feat(ui): open the icon quick-actions menu beside the pressed icon

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Verification, Smoke, Review, and Close-out

**Files:**
- Move: `doc/task/inprogress/p2-ui-ui-025-unified-popup-menus.md` → `doc/task/done/`
- Modify: that story file, `doc/feature.md`

**Interfaces:**
- Consumes: complete UI-025 behaviour from Tasks 1–2.

- [ ] **Step 1: Full JVM and lint gates**

Run:
```bash
./gradlew testDevDebugUnitTest --rerun-tasks -q
./gradlew lintDevDebug -q
```
Read real counts (Gradle prints none):
```bash
python3 - <<'EOF'
import glob,re
t=f=e=s=0
for p in glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml'):
    m=re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',open(p).read(2000))
    if m:
        a,b,c,d=map(int,m.groups()); t+=a;s+=b;f+=c;e+=d
print(f"JVM tests={t} skipped={s} failures={f} errors={e}")
x=open('app/build/reports/lint-results-devDebug.xml').read()
print("lint errors:",len(re.findall(r'severity="Error"',x)),"warnings:",len(re.findall(r'severity="Warning"',x)))
EOF
```
Expected: 0 failures/errors (previous baseline 666 tests; this adds 2), lint 0 errors and 8 warnings.

- [ ] **Step 2: Full instrumented suite on the locked device, starting from clean preferences**

```bash
S=115333744A005844; PREF=shared_prefs/com.mckimquyen.lenslauncher_preferences.xml
adb -s $S shell am force-stop com.mckimquyen.lenslauncher
adb -s $S shell "run-as com.mckimquyen.lenslauncher sed -i '/clean_lens_mode/d' $PREF"
adb -s $S shell input keyevent KEYCODE_WAKEUP
adb -s $S shell am instrument -w com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner \
  > /tmp/ui-025-instrumented.log 2>&1
grep -E "^OK \(|^FAILURES|^Tests run|INSTRUMENTATION_RESULT|^Error in" /tmp/ui-025-instrumented.log
```
Expected: `OK (…)` with 444 + new tests, 113 classes. A failure is investigated by reading the stack trace and re-running that class in isolation before deciding anything; a known infrastructure crash (`UiAutomationService already registered!`) is rerun once and both outcomes are documented.

- [ ] **Step 3: Physical smoke on TECNO KJ7 (check `mCurrentFocus` and real state at every step)**

Use only serial `115333744A005844`. If an ad overlays the UI, stop and report type and location before continuing (R4). Launch `am start -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome`, confirm focus is `ActHome`, wait for icons, then:

1. Long-press empty space: the menu has rounded corners (screenshot).
2. Long-press an icon in the top row, then the middle, then the bottom row: each menu opens beside that icon, not at the screen centre (screenshot each).
3. With the icon menu open, dump the page layout and confirm exactly one extra 1×1 child; dismiss it and confirm the child is gone (`adb shell dumpsys activity top | grep -c "LensView"` is not enough; use `uiautomator dump` child counts or the `quickActions` test result as corroboration and say which was used).
4. With the icon menu open, rotate the device and confirm no crash and no stray anchor after the menu closes.
5. Pinch, pan, pull-down search, horizontal lens paging, and Clean toggle still work.

Afterwards restore the device: force-stop the app and delete `clean_lens_mode` from the preference file using the command in Step 2; confirm `grep -c clean_lens` prints 0.

- [ ] **Step 4: Independent review**

Invoke the `code-review` skill with `high` on the UI-025 commits. Reproduce every behavioural finding with a failing test before fixing it; record findings that are not fixed, with the reason, in the story rather than dropping them.

- [ ] **Step 5: Close the story and tracker**

```bash
git mv doc/task/inprogress/p2-ui-ui-025-unified-popup-menus.md doc/task/done/p2-ui-ui-025-unified-popup-menus.md
```
Set `Status | done`, tick every acceptance box that has evidence, and append exact JVM, lint, instrumented, smoke, review, and audit evidence plus anything disclosed-not-fixed (including that rotation was verified by smoke rather than an automated test, and that the anchor position uses the resting cell, not the magnified one). Move UI-025 from `📋 Picked` to `✅ Implemented` in `doc/feature.md`.

- [ ] **Step 6: Final checks and commit**

```bash
git diff --check
git status --short
git add doc app
git commit -m "docs(ui): close UI-025 with device evidence and audit

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Expected: no whitespace errors; only intended UI-025 files changed. Do not push.
