# FISH-014 Implementation Plan — Active Lens Name Label on Home Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the active lens name directly above the dot indicator on `ActHome` when multiple lenses exist, keeping single-lens UI uncluttered while giving multi-lens users immediate spatial awareness.

**Architecture:** Pure resolver `LensLabelResolver.resolveActiveLensName` handles name fallback logic. `act_home.xml` gains a centered bottom `TextView` `tvLensName` above `lensPageIndicator`. `ActHome.java` centralizes visibility and text updates in `updateLensNavigationChrome()`, called on swipe, reload, list-mode toggle, rename, create, and delete.

**Tech Stack:** Kotlin, Java (ActHome), Android Views, Material Design, JUnit4, Robolectric, AndroidX Test.

## Global Constraints

- Android minSdk 25, compileSdk 37
- Zero new string resources needed (uses raw `LensWorkspace.name`)
- Single-lens devices must show `View.GONE` for both dots and label
- Long-press on label must open the existing lens-management menu
- All three test layers required: Unit (Robolectric), Widget (ActHome UI states), Integration (Room + ViewPager2 lifecycle)
- Target device for smoke: session-locked device (Pixel 7 Pro `2B051FDH3006MU` or TECNO if reconnected)

---

### Task 1: Create `LensLabelResolver` and Unit Tests

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensLabelResolver.kt`
- Create: `app/src/test/java/com/mckimquyen/util/LensLabelResolverTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  package com.mckimquyen.util

  object LensLabelResolver {
      fun resolveActiveLensName(
          lenses: List<LensWorkspace>,
          activeLensId: String?
      ): String?
  }
  ```

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/LensLabelResolverTest.kt`:

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensLabelResolverTest {

    private val defaultLens = LensWorkspace(id = "default", name = "Lens 1", orderIndex = 0)
    private val workLens = LensWorkspace(id = "work", name = "Work", orderIndex = 1)
    private val travelLens = LensWorkspace(id = "travel", name = "Travel", orderIndex = 2)

    @Test
    fun emptyList_returnsNull() {
        assertNull(LensLabelResolver.resolveActiveLensName(emptyList(), "work"))
    }

    @Test
    fun matchingActiveId_returnsThatLensName() {
        val lenses = listOf(defaultLens, workLens, travelLens)
        assertEquals("Work", LensLabelResolver.resolveActiveLensName(lenses, "work"))
        assertEquals("Travel", LensLabelResolver.resolveActiveLensName(lenses, "travel"))
    }

    @Test
    fun nullActiveId_fallsBackToFirstLensName() {
        val lenses = listOf(defaultLens, workLens)
        assertEquals("Lens 1", LensLabelResolver.resolveActiveLensName(lenses, null))
    }

    @Test
    fun nonMatchingActiveId_fallsBackToFirstLensName() {
        val lenses = listOf(defaultLens, workLens)
        assertEquals("Lens 1", LensLabelResolver.resolveActiveLensName(lenses, "non-existent-id"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests LensLabelResolverTest`
Expected: FAIL (unresolved reference `LensLabelResolver`).

- [ ] **Step 3: Implement `LensLabelResolver.kt`**

Create `app/src/main/java/com/mckimquyen/util/LensLabelResolver.kt`:

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.LensWorkspace

/**
 * FISH-014: resolves the display name for the currently active lens workspace.
 * Pure logic, independent of Android framework/Context, fully unit-testable.
 */
object LensLabelResolver {

    @JvmStatic
    fun resolveActiveLensName(
        lenses: List<LensWorkspace>,
        activeLensId: String?
    ): String? {
        if (lenses.isEmpty()) return null
        if (activeLensId != null) {
            val matched = lenses.firstOrNull { it.id == activeLensId }
            if (matched != null) return matched.name
        }
        return lenses.first().name
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests LensLabelResolverTest`
Expected: PASS (4 tests pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/LensLabelResolver.kt app/src/test/java/com/mckimquyen/util/LensLabelResolverTest.kt
git commit -m "feat(fish-014): add LensLabelResolver and unit tests

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Add `tvLensName` to Layout and Wire in `ActHome.java`

**Files:**
- Modify: `app/src/main/res/layout/act_home.xml:23-38`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:166,437,450,510,827,924,985,1449,1456,1467`

**Interfaces:**
- Consumes: `LensLabelResolver.resolveActiveLensName` from Task 1
- Produces: `tvLensName` View in `ActHome`, centralized in `updateLensNavigationChrome()`

- [ ] **Step 1: Add `tvLensName` to `act_home.xml`**

In `app/src/main/res/layout/act_home.xml`, directly above `lensPageIndicator`:

```xml
    <!-- FISH-014: displays active lens name when multiple lenses exist. Centered above dots.
         Long-press opens the lens management menu. GONE when single lens. -->
    <TextView
        android:id="@+id/tvLensName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="56dp"
        android:paddingHorizontal="12dp"
        android:paddingVertical="4dp"
        android:textAppearance="?attr/textAppearanceLabelMedium"
        android:textColor="?attr/colorOnSurfaceVariant"
        android:maxLines="1"
        android:ellipsize="end"
        android:visibility="gone"
        tools:visibility="visible"
        tools:text="Lens 1" />
```

- [ ] **Step 2: Add field and bind `tvLensName` in `ActHome.java`**

In `ActHome.java`:
1. Add field declaration next to `lensPageIndicator`:
```java
    private TabLayout lensPageIndicator;
    private TextView tvLensName;
```
2. In `setupViews()`:
```java
        lensPageIndicator = findViewById(R.id.lensPageIndicator);
        tvLensName = findViewById(R.id.tvLensName);
```
3. Attach long-click listener to `tvLensName`:
```java
        tvLensName.setOnLongClickListener(v -> {
            showLensManagementMenu(v);
            return true;
        });
```

- [ ] **Step 3: Implement `updateLensNavigationChrome()` helper in `ActHome.java`**

Add method:
```java
    /**
     * FISH-014: synchronizes visibility and text for the lens navigation chrome (both dots indicator
     * and the active lens name label) in lockstep across all lifecycle, swipe, search, and mode states.
     */
    void updateLensNavigationChrome() {
        if (lensPageIndicator == null) return;
        boolean hasApps = listApp != null && !listApp.isEmpty();
        boolean isList = utilSettings != null && utilSettings.isListMode();
        boolean isSearchShowing = searchView != null && searchView.isShowing();
        boolean visible = !isList && !isSearchShowing && hasApps && currentLenses != null && currentLenses.size() > 1;

        lensPageIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (tvLensName != null) {
            tvLensName.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && currentLenses != null) {
                int position = lensPager != null ? lensPager.getCurrentItem() : 0;
                if (position >= 0 && position < currentLenses.size()) {
                    tvLensName.setText(currentLenses.get(position).getName());
                } else {
                    String activeId = utilSettings != null
                            ? utilSettings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                            : null;
                    tvLensName.setText(LensLabelResolver.resolveActiveLensName(currentLenses, activeId));
                }
            }
        }
    }
```

- [ ] **Step 4: Update call sites in `ActHome.java` to use `updateLensNavigationChrome()`**

1. In `lensPageChangeCallback.onPageSelected(int position)`:
   Add `updateLensNavigationChrome();` after the `KEY_ACTIVE_LENS_ID` save block.
2. In `refreshLensList()` (line ~510):
   Replace `lensPageIndicator.setVisibility(lenses.size() > 1 ? View.VISIBLE : View.GONE);` with `updateLensNavigationChrome();`.
3. In `createLensDialog` callback (line ~827):
   Replace `lensPageIndicator.setVisibility(lenses.size() > 1 ? View.VISIBLE : View.GONE);` with `updateLensNavigationChrome();`.
4. In `renameLensDialog` callback (line ~880):
   Add `updateLensNavigationChrome();` after `submitLenses`.
5. In `confirmDeleteLensDialog` callback (line ~924):
   Replace `lensPageIndicator.setVisibility(lenses.size() > 1 ? View.VISIBLE : View.GONE);` with `updateLensNavigationChrome();`.
6. In `setupSearch()` search state changes (line ~985):
   Replace `lensPageIndicator.setVisibility(View.GONE);` with:
   ```java
   lensPageIndicator.setVisibility(View.GONE);
   if (tvLensName != null) tvLensName.setVisibility(View.GONE);
   ```
7. In `updateModeVisibility()` (lines ~1449, 1456, 1467):
   Replace the `lensPageIndicator` blocks with `updateLensNavigationChrome();`.
8. In `assignApps()`:
   Ensure `updateLensNavigationChrome()` runs so initial app load shows the chrome.

- [ ] **Step 5: Run full unit test suite**

Run: `./gradlew testDevDebugUnitTest`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/act_home.xml app/src/main/java/com/mckimquyen/ui/ActHome.java
git commit -m "feat(fish-014): wire tvLensName and updateLensNavigationChrome in ActHome

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Add Widget Tests (`ActHomeLensLabelWidgetTest`)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensLabelWidgetTest.kt`

**Interfaces:**
- Tests `tvLensName` visibility and text behavior across multi-lens states.

- [ ] **Step 1: Write `ActHomeLensLabelWidgetTest.kt`**

```kotlin
package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensLabelWidgetTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = cleanDb()

    @After
    fun tearDown() = cleanDb()

    private fun cleanDb() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun singleLens_hidesLensNameLabel() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertNotNull("tvLensName must exist in layout", tvName)
                assertEquals(
                    "Lens name label must be GONE when only one lens exists",
                    View.GONE,
                    tvName.visibility
                )
            }
        }
    }

    @Test
    fun multipleLenses_showsActiveLensNameAndUpdatesOnSwipe() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertEquals(
                    "Lens name label must be VISIBLE when multiple lenses exist",
                    View.VISIBLE,
                    tvName.visibility
                )
                assertEquals(
                    "Default lens name should be displayed initially",
                    "Lens 1",
                    tvName.text.toString()
                )
            }

            // Swipe to second page
            scenario.onActivity { activity ->
                activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
            }
            idle()

            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                assertEquals(
                    "Lens name label must update to 'Work' on page swipe",
                    "Work",
                    tvName.text.toString()
                )
            }
        }
    }

    @Test
    fun longPressOnLensNameLabel_triggersManagementMenu() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            idle()
            var menuOpened = false
            scenario.onActivity { activity ->
                val tvName = activity.findViewById<TextView>(R.id.tvLensName)
                // performLongClick returns true if the OnLongClickListener handled it
                menuOpened = tvName.performLongClick()
            }
            assertTrue("Long-pressing tvLensName must invoke the management menu handler", menuOpened)
        }
    }
}
```

- [ ] **Step 2: Run widget test on connected device**

Run:
```bash
ANDROID_SERIAL=2B051FDH3006MU ./gradlew installDevDebug installDevDebugAndroidTest
adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensLabelWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS (3 tests pass).

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensLabelWidgetTest.kt
git commit -m "test(fish-014): add widget tests for active lens name label

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Add Integration Test (`ActHomeLensLabelIntegrationTest`)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensLabelIntegrationTest.kt`

**Interfaces:**
- Tests Room + SharedPreferences + ViewPager2 + Activity recreation boundary for lens label.

- [ ] **Step 1: Write `ActHomeLensLabelIntegrationTest.kt`**

```kotlin
package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-014: verifies active lens name label across the real Room + SharedPreferences +
 * ViewPager2 + Activity recreation boundary.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensLabelIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    private val workLensId = "work-lens-integration"

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() {
        utilSettings.deleteLensSettings(workLensId)
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        cleanDb()
    }

    private fun cleanDb() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun restoredActiveLensFromPreferences_showsCorrectLabelAcrossRecreate() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = workLensId, name = "Work Space", orderIndex = 1))
        }
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, workLensId)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        scenario.onActivity { activity ->
            val tvName = activity.findViewById<TextView>(R.id.tvLensName)
            assertEquals("Label must be visible for 2 lenses", View.VISIBLE, tvName.visibility)
            assertEquals("Label must match restored active lens name", "Work Space", tvName.text.toString())
        }

        // Recreate activity
        scenario.recreate()
        idle()

        scenario.onActivity { activity ->
            val tvName = activity.findViewById<TextView>(R.id.tvLensName)
            assertEquals("Label must remain visible after recreate", View.VISIBLE, tvName.visibility)
            assertEquals("Label must retain restored active lens name after recreate", "Work Space", tvName.text.toString())
        }

        scenario.close()
    }

    @Test
    fun deletingSecondLens_hidesLabelImmediately() {
        runBlocking {
            dao.insertOrUpdate(LensWorkspace(id = workLensId, name = "Work Space", orderIndex = 1))
        }

        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        scenario.onActivity { activity ->
            val tvName = activity.findViewById<TextView>(R.id.tvLensName)
            assertEquals(View.VISIBLE, tvName.visibility)
            // Trigger deletion of the second lens via menu action
            activity.onLensMenuItemSelected(3, 1) // 3 = confirmDeleteLensDialog
        }
        idle()

        // Delete from DB directly to simulate confirmation and trigger reload
        runBlocking {
            dao.getById(workLensId)?.let { dao.delete(it) }
        }
        scenario.onActivity { activity ->
            activity.refreshLensList()
        }
        idle()

        scenario.onActivity { activity ->
            val tvName = activity.findViewById<TextView>(R.id.tvLensName)
            assertEquals(
                "Deleting second lens leaves 1 lens; label must be GONE",
                View.GONE,
                tvName.visibility
            )
        }

        scenario.close()
    }
}
```

- [ ] **Step 2: Run integration test on connected device**

Run:
```bash
ANDROID_SERIAL=2B051FDH3006MU ./gradlew installDevDebug installDevDebugAndroidTest
adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensLabelIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS (2 tests pass).

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensLabelIntegrationTest.kt
git commit -m "test(fish-014): add integration tests for active lens name label

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Smoke Test on Device, Code Review, Backlog Move & Push

**Files:**
- Move: `doc/task/todo/p2-fish-fish-014-active-lens-name-label.md` -> `doc/task/done/p2-fish-fish-014-active-lens-name-label.md`
- Update: `doc/task/README.md`

- [ ] **Step 1: Install devDebug APK on connected device**

Check device serial and install:
```bash
ANDROID_SERIAL=2B051FDH3006MU ./gradlew installDevDebug
```

- [ ] **Step 2: Manual smoke test on device**

1. Launch Launcher
2. Long-press empty grid space -> Add Lens -> Name: "Work"
3. Verify "Work" or "Lens 1" label appears above dots indicator
4. Swipe between pages -> verify label toggles between "Lens 1" and "Work"
5. Long-press label -> verify lens management menu opens
6. Rename "Work" to "Office" -> verify label updates to "Office"
7. Delete "Office" lens -> verify label and dots disappear (1 lens left)
8. Screenshot evidence and logcat check

- [ ] **Step 3: Run full verification suite (unit + lint)**

```bash
./gradlew testDevDebugUnitTest lintDevDebug
```
Expected: 0 failures, 0 new lint errors.

- [ ] **Step 4: Code review via `superpowers:requesting-code-review`**

Dispatch code reviewer subagent, resolve any feedback.

- [ ] **Step 5: Move story to `done/`, update `README.md`, commit and push**

Update story checkboxes, fill in smoke evidence, move file to `doc/task/done/p2-fish-fish-014-active-lens-name-label.md`, update `doc/task/README.md`.
```bash
git add doc/task/
git commit -m "docs(fish-014): mark active lens name label as done

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git push origin dev
```
