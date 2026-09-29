# FISH-013 Implementation Plan — Auto-Export-Lens Process-Kill Recovery

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent silent loss of the "Share lens image" request if the process is killed before export finishes, by persisting a pending flag in `SharedPreferences` and clearing it only when the export truly resolves.

**Architecture:** Add `hasPendingAutoExportLens()` / `setPendingAutoExportLens()` / `clearPendingAutoExportLens()` to `UtilSettings`. Set the flag when `FrmLens.shareLensImage()` is tapped (and sync in `ActHome.consumeAutoExportExtra`). Resurrect in `ActHome.onCreate()` / `onNewIntent()`. Post export without clearing disk flag; only clear on genuine terminal resolve (success, error toast, or unrecoverable missing lens).

**Tech Stack:** Kotlin, Java (ActHome), Android SharedPreferences, JUnit4, Robolectric, AndroidX Test (ActivityScenario).

## Global Constraints

- Android minSdk 25, compileSdk 37
- Zero new lint errors, no hardcoded strings (use `R.string.error_lens_share_failed`)
- Preserve existing bind-race protection (`ActHome.java:570-574`)
- Target device for smoke: TECNO KJ7 (`115333744A005844`)
- Test required at all layers: Unit (Robolectric), Widget (unrecoverable/error), Integration (cold-launch resurrection from disk)

---

### Task 1: Add Pending Auto-Export API to `UtilSettings`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt:94-96,360-365`
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsPendingAutoExportTest.kt`

**Interfaces:**
- Produces:
  - `UtilSettings.KEY_PENDING_AUTO_EXPORT_LENS: String`
  - `UtilSettings.hasPendingAutoExportLens(): Boolean`
  - `UtilSettings.setPendingAutoExportLens(value: Boolean): Unit`
  - `UtilSettings.clearPendingAutoExportLens(): Unit`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsPendingAutoExportTest.kt`:

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPendingAutoExportTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `pending auto-export defaults to false when unset`() {
        val settings = freshSettings()
        assertFalse(settings.hasPendingAutoExportLens())
    }

    @Test
    fun `saving pending auto-export reads back true`() {
        val settings = freshSettings()
        settings.setPendingAutoExportLens(true)
        assertTrue(settings.hasPendingAutoExportLens())
    }

    @Test
    fun `clearing pending auto-export reverts to false`() {
        val settings = freshSettings()
        settings.setPendingAutoExportLens(true)
        assertTrue(settings.hasPendingAutoExportLens())

        settings.clearPendingAutoExportLens()
        assertFalse(settings.hasPendingAutoExportLens())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests UtilSettingsPendingAutoExportTest`
Expected: FAIL with compilation error (unresolved references `hasPendingAutoExportLens`, `setPendingAutoExportLens`, etc.).

- [ ] **Step 3: Implement minimal code in `UtilSettings.kt`**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`:
In `companion object`:
```kotlin
const val KEY_PENDING_AUTO_EXPORT_LENS = "pending_auto_export_lens"
```
In body:
```kotlin
fun hasPendingAutoExportLens(): Boolean {
    return prefs.getBoolean(KEY_PENDING_AUTO_EXPORT_LENS, false)
}

fun setPendingAutoExportLens(value: Boolean) {
    save(KEY_PENDING_AUTO_EXPORT_LENS, value)
}

fun clearPendingAutoExportLens() {
    prefs.edit { remove(KEY_PENDING_AUTO_EXPORT_LENS) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests UtilSettingsPendingAutoExportTest`
Expected: PASS (3 tests pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/UtilSettingsPendingAutoExportTest.kt
git commit -m "feat(fish-013): add pending auto-export API to UtilSettings

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Persist Flag at Trigger Sites (`FrmLens` & `ActHome.consumeAutoExportExtra`)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt:140-148`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:315-322`

**Interfaces:**
- Consumes: `UtilSettings.setPendingAutoExportLens(Boolean)` from Task 1
- Produces: Persistent disk state when share action is initiated

- [ ] **Step 1: Update `FrmLens.kt` to persist flag on Share button tap**

In `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`:
```kotlin
    private fun shareLensImage() {
        utilSettings?.setPendingAutoExportLens(true)
        val intent = Intent(requireContext(), ActHome::class.java).apply {
            putExtra(ActHome.EXTRA_AUTO_EXPORT_LENS, true)
        }
        lensExportLauncher(intent)
    }
```

- [ ] **Step 2: Update `ActHome.java` `consumeAutoExportExtra` to sync flag to disk**

In `app/src/main/java/com/mckimquyen/ui/ActHome.java`:
```java
    private void consumeAutoExportExtra(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_AUTO_EXPORT_LENS, false)) {
            intent.removeExtra(EXTRA_AUTO_EXPORT_LENS);
            pendingAutoExportLens = true;
            if (utilSettings != null) {
                utilSettings.setPendingAutoExportLens(true);
            }
        }
    }
```
*Note: `utilSettings` is instantiated in `setupViews()` which runs before `consumeAutoExportExtra(getIntent())` in `onCreate()`.*

- [ ] **Step 3: Run existing unit and widget tests to ensure no regression**

Run: `./gradlew testDevDebugUnitTest`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/FrmLens.kt app/src/main/java/com/mckimquyen/ui/ActHome.java
git commit -m "feat(fish-013): persist pending auto-export flag at launch trigger sites

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Resurrect and Resolve Auto-Export with Safe Failure Handling in `ActHome`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:270-280,305-322,520-532,585-592,730-765`

**Interfaces:**
- Consumes:
  - `UtilSettings.hasPendingAutoExportLens(): Boolean`
  - `UtilSettings.clearPendingAutoExportLens(): Unit`
- Produces: Resurrected export on startup, explicit failure toast if unrecoverable, clear disk flag on all terminal paths.

- [ ] **Step 1: Resurrect pending flag from disk in `onCreate` and `onNewIntent`**

In `ActHome.java`:
Update `consumeAutoExportExtra`:
```java
    private void consumeAutoExportExtra(Intent intent) {
        boolean fromIntent = intent != null && intent.getBooleanExtra(EXTRA_AUTO_EXPORT_LENS, false);
        if (fromIntent) {
            intent.removeExtra(EXTRA_AUTO_EXPORT_LENS);
            if (utilSettings != null) {
                utilSettings.setPendingAutoExportLens(true);
            }
        }
        boolean fromDisk = utilSettings != null && utilSettings.hasPendingAutoExportLens();
        if (fromIntent || fromDisk) {
            pendingAutoExportLens = true;
        }
    }
```

- [ ] **Step 2: Post export without clearing disk flag in trigger sites**

Keep in-memory flip `pendingAutoExportLens = false` at lines 527 and 588 so only one export task is enqueued. Do NOT clear `utilSettings` here.

- [ ] **Step 3: Handle unrecoverable states and clear disk flag on terminal outcomes in `exportActiveLensImage`**

In `ActHome.java`:
```java
    private void exportActiveLensImage() {
        LensView view = lensViews;
        if (view == null) {
            if (utilSettings != null) {
                utilSettings.clearPendingAutoExportLens();
            }
            Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String activeLensId = view.getLensId();
        LensWorkspace matched = null;
        for (LensWorkspace lens : currentLenses) {
            if (lens.getId().equals(activeLensId)) {
                matched = lens;
                break;
            }
        }
        if (matched == null) {
            if (utilSettings != null) {
                utilSettings.clearPendingAutoExportLens();
            }
            Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String lensName = matched.getName();
        PolaroidExportHelper.exportAsync(view, lensName, this, uri -> {
            if (utilSettings != null) {
                utilSettings.clearPendingAutoExportLens();
            }
            if (uri != null) {
                try {
                    lensShareLauncher.launch(Intent.createChooser(
                            PolaroidExportHelper.buildShareIntent(uri),
                            getString(R.string.share_via)));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
```

- [ ] **Step 4: Run unit tests to verify compilation and baseline logic**

Run: `./gradlew testDevDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java
git commit -m "feat(fish-013): resurrect pending auto-export on launch and clear on terminal resolve

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Add Integration Test for Process-Kill Resurrection & Unrecoverable State

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeAutoExportPersistenceIntegrationTest.kt`

**Interfaces:**
- Tests `UtilSettings.setPendingAutoExportLens(true)` surviving cold start without intent extra, and clearing after launch.

- [ ] **Step 1: Write integration test `ActHomeAutoExportPersistenceIntegrationTest.kt`**

```kotlin
package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ActHomeAutoExportPersistenceIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
    }

    @After
    fun tearDown() {
        utilSettings.clearPendingAutoExportLens()
        cleanDb()
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        Unit
    }

    @Test
    fun pendingFlagOnDisk_resurrectsExportOnColdLaunch_andClearsFlag() {
        // 1. Simulate process kill between FrmLens tap and export completion:
        // Flag is written directly to disk, with NO Intent extra passed to ActivityScenario.
        utilSettings.setPendingAutoExportLens(true)
        assertTrue(utilSettings.hasPendingAutoExportLens())

        val exportCount = AtomicInteger(0)
        val exportLatch = CountDownLatch(1)
        var capturedIntent: Intent? = null

        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            activity.lensShareLauncher = ActHome.LensShareLauncher { intent ->
                capturedIntent = intent
                exportCount.incrementAndGet()
                exportLatch.countDown()
            }
        }

        // 2. Export must fire from resurrected pending state
        assertTrue(
            "resurrected pending auto-export must trigger export shortly after launch",
            exportLatch.await(5, TimeUnit.SECONDS)
        )
        assertEquals(1, exportCount.get())
        assertNotNull(capturedIntent)

        // 3. Flag must be cleared from disk on terminal resolve
        assertFalse(
            "pending flag must be cleared from disk after export completes",
            utilSettings.hasPendingAutoExportLens()
        )

        scenario.close()
    }
}
```

- [ ] **Step 2: Run instrumented integration test on connected Tecno device**

Run: Device check & run instrumented test
```bash
adb devices
./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.ui.ActHomeAutoExportPersistenceIntegrationTest
```
Expected: PASS (1 test passes).

- [ ] **Step 3: Also run existing `ActHomeAutoExportWidgetTest` to ensure no regression**

Run:
```bash
./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.ui.ActHomeAutoExportWidgetTest
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/ui/ActHomeAutoExportPersistenceIntegrationTest.kt
git commit -m "test(fish-013): add integration test for process-kill auto-export resurrection

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Physical Device Smoke Test, Backlog Move & Delivery Audit

**Files:**
- Move: `doc/task/todo/p2-fish-fish-013-auto-export-lens-race.md` -> `doc/task/done/p2-fish-fish-013-auto-export-lens-race.md`
- Update: `doc/task/README.md`

- [ ] **Step 1: Install devDebug build on designated Tecno device**

Check locked device `115333744A005844`:
```bash
adb -s 115333744A005844 install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

- [ ] **Step 2: Smoke test manual flow**

1. Launch Launcher
2. Open Settings -> Tab Lens
3. Tap "Share lens image" button
4. Verify Android system share sheet appears with the Polaroid rendered image
5. Check logcat for no crash/exceptions

- [ ] **Step 3: Run full verification suite (unit + lint)**

```bash
./gradlew testDevDebugUnitTest lintDevDebug
```
Expected: 0 failures, 0 new errors.

- [ ] **Step 4: Move backlog story to `done` and record audit evidence**

Update story checkboxes, fill in Tecno KJ7 smoke run details, git SHA, and audit rubric. Move file to `doc/task/done/p2-fish-fish-013-auto-export-lens-race.md`.
Update `doc/task/README.md`.

- [ ] **Step 5: Commit**

```bash
git add doc/task/
git commit -m "docs(fish-013): mark auto-export-lens race fix as done

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
