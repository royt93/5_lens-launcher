# Clean the Lens (Minimalist Mode) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the "Clean the Lens" Minimalist Mode feature that hides all chrome (search bar, multi-lens dots, app hover labels, notification badges) via an opt-in toggle in Settings.

**Architecture:** Add `UtilSettings.KEY_CLEAN_LENS_MODE` (`clean_lens_mode`). Connect a switch in `FrmSettings.kt`. Hook `ActHome.updateSearchBarVisibility()` and `updateLensNavigationChrome()` to force `View.GONE` when enabled. Hook `LensView.kt` to suppress hover app labels and notification badges when clean mode is active.

**Tech Stack:** Kotlin, Java (ActHome), Android Views, Material Design 3, SharedPreferences, JUnit4, Robolectric, AndroidX Test.

## Global Constraints
- Target minSdk 25, compileSdk 37.
- Zero extra dependencies.
- Strict 3-tier test coverage: Unit test + Widget test + Integration test (Rule R5).
- No memory leaks, safe nullable handling, no magic numbers.

---

### Task 1: Add Setting Key & Unit Tests

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Test: `app/src/test/java/com/mckimquyen/util/CleanLensModeUnitTest.kt`

**Interfaces:**
- Produces: `UtilSettings.KEY_CLEAN_LENS_MODE`, `UtilSettings.DEFAULT_CLEAN_LENS_MODE`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/CleanLensModeUnitTest.kt`:
```kotlin
package com.mckimquyen.util

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CleanLensModeUnitTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings = UtilSettings(context)

    @Test
    fun defaultCleanLensMode_isFalse() {
        assertFalse(
            "Clean lens mode must default to false",
            settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE, UtilSettings.DEFAULT_CLEAN_LENS_MODE)
        )
    }

    @Test
    fun cleanLensMode_canBePersistedAndToggled() {
        try {
            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
            assertTrue(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))

            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
            assertFalse(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
        } finally {
            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, UtilSettings.DEFAULT_CLEAN_LENS_MODE)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.CleanLensModeUnitTest`
Expected: FAIL (unresolved reference `KEY_CLEAN_LENS_MODE`)

- [ ] **Step 3: Add `KEY_CLEAN_LENS_MODE` to `UtilSettings.kt`**
Add constants to `companion object` in `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`:
```kotlin
const val DEFAULT_CLEAN_LENS_MODE = false
const val KEY_CLEAN_LENS_MODE = "clean_lens_mode"
```

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.CleanLensModeUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/CleanLensModeUnitTest.kt
git commit -m "feat(settings): add KEY_CLEAN_LENS_MODE key and unit tests"
```

---

### Task 2: Add UI Strings and Switch in Settings Screen

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsCleanLensWidgetTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.KEY_CLEAN_LENS_MODE`
- Produces: `R.id.swCleanLensMode`

- [ ] **Step 1: Write string resources in `strings.xml`**
Add in `app/src/main/res/values/strings.xml`:
```xml
    <string name="setting_clean_lens_mode">Clean the lens (Minimalist mode)</string>
    <string name="setting_clean_lens_mode_hint">Hide search bar, app text labels, notification badges, and page dots</string>
```

- [ ] **Step 2: Add switch item to `frm_settings.xml`**
In `app/src/main/res/layout/frm_settings.xml`, inside the General screen card (before or after `swKeepScreenOn` block), add:
```xml
                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="52dp"
                    app:dividerColor="?attr/colorOutlineVariant" />

                <RelativeLayout
                    android:id="@+id/rlCleanLensModeParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">

                    <ImageView
                        android:id="@+id/iconCleanLensMode"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_lens_24dp" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swCleanLensMode"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconCleanLensMode"
                        android:paddingTop="12dp"
                        android:text="@string/setting_clean_lens_mode"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />

                    <TextView
                        android:id="@+id/tvCleanLensModeHint"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_below="@id/swCleanLensMode"
                        android:layout_marginStart="12dp"
                        android:layout_marginEnd="16dp"
                        android:layout_toEndOf="@id/iconCleanLensMode"
                        android:paddingBottom="8dp"
                        android:text="@string/setting_clean_lens_mode_hint"
                        android:textColor="?android:attr/textColorTertiary"
                        android:textSize="12sp" />

                </RelativeLayout>
```

- [ ] **Step 3: Wire up `swCleanLensMode` in `FrmSettings.kt`**
Declare field:
```kotlin
private var swCleanLensMode: SwitchCompat? = null
```
In `setupViews(view)`:
```kotlin
swCleanLensMode = view.findViewById(R.id.swCleanLensMode)
swCleanLensMode?.setOnCheckedChangeListener { _, isChecked ->
    utilSettings?.save(UtilSettings.KEY_CLEAN_LENS_MODE, isChecked)
}
```
In `updateUiValues(us)`:
```kotlin
swCleanLensMode?.isChecked = us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
```
In `onDestroyView()`:
```kotlin
swCleanLensMode = null
```

- [ ] **Step 4: Write Widget Test**
Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsCleanLensWidgetTest.kt`:
```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsCleanLensWidgetTest {

    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
    }

    @After
    fun tearDown() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
    }

    @Test
    fun swCleanLensMode_reflectsAndPersistsSetting() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val switch = activity.findViewById<MaterialSwitch>(R.id.swCleanLensMode)
                assertNotNull("swCleanLensMode must exist in layout", switch)
                assertEquals(false, switch.isChecked)

                switch.isChecked = true
                assertEquals(true, settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
            }
        }
    }
}
```

- [ ] **Step 5: Run widget test on device**
Run: `ANDROID_SERIAL=2B051FDH3006MU ./gradlew installDevDebugAndroidTest && adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsCleanLensWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
Expected: PASS

- [ ] **Step 6: Commit**
```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/frm_settings.xml app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsCleanLensWidgetTest.kt
git commit -m "feat(ui): add Clean the Lens toggle switch to FrmSettings"
```

---

### Task 3: Hook Clean Mode in `ActHome` & `LensView` + Integration Test

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt`

**Interfaces:**
- Consumes: `UtilSettings.KEY_CLEAN_LENS_MODE`
- Produces: Chrome suppression in `ActHome` & label/badge suppression in `LensView`

- [ ] **Step 1: Write Integration Test**
Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt`:
```kotlin
package com.mckimquyen.ui

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeCleanLensModeIntegrationTest {

    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
    }

    @After
    fun tearDown() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
    }

    @Test
    fun cleanLensMode_whenTrue_hidesSearchBarAndPageIndicator() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<View>(R.id.searchBar)
                val indicator = activity.findViewById<TabLayout>(R.id.lensPageIndicator)

                assertEquals("SearchBar must be GONE in clean lens mode", View.GONE, searchBar.visibility)
                assertEquals("Indicator must be GONE in clean lens mode", View.GONE, indicator.visibility)
            }
        }
    }

    @Test
    fun cleanLensMode_whenFalse_restoresSearchBar() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<View>(R.id.searchBar)
                assertEquals("SearchBar must follow KEY_SHOW_SEARCH_BAR when clean lens mode is off", View.VISIBLE, searchBar.visibility)
            }
        }
    }
}
```

- [ ] **Step 2: Update `ActHome.java`**
In `updateSearchBarVisibility()`:
```java
        boolean isCleanMode = new UtilSettings(this).getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE, false);
        boolean showSearchBar = !isCleanMode && new UtilSettings(this).getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR);
        searchBar.setVisibility(showSearchBar ? View.VISIBLE : View.GONE);
        if (!showSearchBar && searchView.isShowing()) {
            searchView.hide();
        }
        updateRecentAppsPanelIconVisibility();
```
In `updateLensNavigationChrome()`:
```java
        if (lensPageIndicator == null) return;
        boolean isCleanMode = utilSettings != null && utilSettings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE, false);
        boolean isList = utilSettings != null && utilSettings.isListMode();
        boolean isSearchShowing = searchView != null && searchView.isShowing();
        boolean visible = !isCleanMode && !isList && !isSearchShowing && currentLenses != null && currentLenses.size() > 1;

        lensPageIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (tvLensName != null) {
            tvLensName.setVisibility(visible ? View.VISIBLE : View.GONE);
...
```

- [ ] **Step 3: Update `LensView.kt`**
In `onDraw()` where app labels and notification badges are rendered:
Check `val isClean = us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE, false)`.
- Skip label drawing if `isClean` is true.
- Skip notification badge drawing if `isClean` is true.

- [ ] **Step 4: Run integration test and unit tests**
Run:
`ANDROID_SERIAL=2B051FDH3006MU ./gradlew installDevDebugAndroidTest && adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.ui.ActHomeCleanLensModeIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`
`./gradlew testDevDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 5: Run Lint**
Run: `./gradlew lintDevDebug`
Expected: 0 errors.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/main/java/com/mckimquyen/views/LensView.kt app/src/androidTest/java/com/mckimquyen/ui/ActHomeCleanLensModeIntegrationTest.kt
git commit -m "feat(ui): hook Clean the Lens mode in ActHome and LensView with integration tests"
```
