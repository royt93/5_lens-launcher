# FEAT-013 — Recent installs in search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (inline) or `superpowers:subagent-driven-development` (subagents) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Typing exactly `new`, `recent`, `moi cai` or `vua cai` in Home search lists the visible apps installed in the last 7 days, newest first, capped at the shared result limit.

**Architecture:** Add the window, keywords and a pure `recentInstalls` ranking to `AppSearchEngine`, and branch at the top of `AppSearchEngine.search` when the whole normalised query is one keyword. Work-profile apps never had an `installDate`; fill it from `LauncherActivityInfo.getFirstInstallTime()` through `ProfileAppEntry` into `ProfileAppSource.toShells`. No UI, string, permission or dependency changes.

**Tech Stack:** Kotlin/JVM, JUnit 4, Robolectric (only where Android types are needed), Android instrumentation on one locked device.

## Global Constraints

- Window is a separate named constant, `RECENT_INSTALL_DAYS = 7`, and counts apps that were already opened. The grid "NEW" tag (`UtilSettings.SHOW_NEW_APP_TAG_DURATION`, 12 hours, unopened only) is NOT touched.
- Keywords are exactly `new`, `recent`, `moi cai`, `vua cai`, compared after `AppSearchEngine.normalize`.
- Only a query whose entire normalised text equals one keyword triggers the list; any other query (including `new york`) takes the existing path unchanged.
- Hidden apps (`isVisible == false`) are excluded; `installDate <= 0` is excluded; a future `installDate` counts as recent.
- Result cap is `AppSearchEngine.SEARCH_RESULT_LIMIT` (24). Ties after "newest first" break by normalised label, then `componentKey`.
- T9 mode (digits only) is unaffected.
- No new permission, dependency, string or layout. minSdk is 25: use only APIs available there.
- Device policy: use ONLY TECNO KJ7 `115333744A005844` (`adb -s 115333744A005844`); never a Gradle `connected*` task; stop on any ad overlay (R4); back up and byte-verify the app data around device tests.

---

## File Structure

- Modify `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`: constants, `recentInstalls`, branch in `search`.
- Modify `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`: all engine tests.
- Modify `app/src/main/java/com/mckimquyen/util/ProfileAppSource.kt`: `ProfileAppEntry.installDate`, copy into shell.
- Modify `app/src/main/java/com/mckimquyen/util/LauncherAppsDirectory.kt`: fill `installDate` from `getFirstInstallTime()`.
- Modify `app/src/test/java/com/mckimquyen/util/ProfileAppSourceTest.kt`: shell carries install date.
- Create `app/src/androidTest/java/com/mckimquyen/search/RecentInstallSearchWidgetTest.kt`: Home search test on device.

---

## Task 1: Pure `recentInstalls` ranking and constants

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`
- Test: `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`

**Interfaces produced:**

```kotlin
const val RECENT_INSTALL_DAYS = 7
val RECENT_INSTALL_KEYWORDS: Set<String> = setOf("new", "recent", "moi cai", "vua cai")

@JvmStatic
@JvmOverloads
fun recentInstalls(
    apps: List<App>,
    nowMillis: Long = System.currentTimeMillis(),
    limit: Int = SEARCH_RESULT_LIMIT
): List<App>
```

- [ ] **Step 1: Add failing tests** to `AppSearchEngineTest.kt` (inside the class, before its closing brace; `App` is already imported):

```kotlin
// ---- FEAT-013: recent installs ----

private val now = 1_800_000_000_000L
private val day = 24L * 60 * 60 * 1000
private fun installed(pkg: String, ago: Long, label: String = pkg, visible: Boolean = true, name: String = "Main") =
    App(label = label, packageName = pkg, name = name, installDate = now - ago, isVisible = visible)

@Test
fun `recent install window is seven days`() {
    assertEquals(7, AppSearchEngine.RECENT_INSTALL_DAYS)
}

@Test
fun `an app installed exactly seven days ago is recent and one millisecond older is not`() {
    val onEdge = installed("com.edge", 7 * day)
    val tooOld = installed("com.old", 7 * day + 1)

    assertEquals(listOf(onEdge), AppSearchEngine.recentInstalls(listOf(tooOld, onEdge), now))
}

@Test
fun `recent installs are newest first with deterministic ties`() {
    val newest = installed("com.newest", 1 * day)
    val middle = installed("com.middle", 3 * day)
    val tieB = installed("com.tie.b", 5 * day, label = "Beta")
    val tieA = installed("com.tie.a", 5 * day, label = "Alpha")

    assertEquals(
        listOf(newest, middle, tieA, tieB),
        AppSearchEngine.recentInstalls(listOf(tieB, middle, tieA, newest), now)
    )
}

@Test
fun `hidden apps and apps without an install date are excluded`() {
    val visible = installed("com.visible", 1 * day)
    val hidden = installed("com.hidden", 1 * day, visible = false)
    val unknown = App(label = "Unknown", packageName = "com.unknown", name = "Main", installDate = 0L)

    assertEquals(listOf(visible), AppSearchEngine.recentInstalls(listOf(hidden, unknown, visible), now))
}

@Test
fun `an install date in the future counts as recent`() {
    val skewed = App(label = "Skewed", packageName = "com.skew", name = "Main", installDate = now + day)

    assertEquals(listOf(skewed), AppSearchEngine.recentInstalls(listOf(skewed), now))
}

@Test
fun `recent installs are capped and a non-positive limit is empty`() {
    val apps = (1..40).map { installed("com.app$it", it * 60_000L) }

    assertEquals(AppSearchEngine.SEARCH_RESULT_LIMIT, AppSearchEngine.recentInstalls(apps, now).size)
    assertEquals(5, AppSearchEngine.recentInstalls(apps, now, limit = 5).size)
    assertEquals(apps.take(5), AppSearchEngine.recentInstalls(apps, now, limit = 5))
    assertTrue(AppSearchEngine.recentInstalls(apps, now, limit = 0).isEmpty())
}

@Test
fun `a work profile copy installed recently is listed separately from the personal copy`() {
    val personal = installed("com.chat", 10 * day)
    val work = App(
        label = "Chat", packageName = "com.chat", name = "Main", installDate = now - 1 * day,
        userId = 10, isWorkProfile = true
    )

    assertEquals(listOf(work), AppSearchEngine.recentInstalls(listOf(personal, work), now))
}
```

- [ ] **Step 2: Run and confirm it fails.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: compile errors `Unresolved reference 'RECENT_INSTALL_DAYS'` / `'recentInstalls'`.

- [ ] **Step 3: Implement** in `AppSearchEngine.kt`. Add the constants next to `SEARCH_RESULT_LIMIT`, and the function after `search`:

```kotlin
/** FEAT-013: an app counts as "recently installed" for this many days, whether or not it has been opened. */
const val RECENT_INSTALL_DAYS = 7

/** FEAT-013: queries that list recent installs. Compared after [normalize], so accents and case do not matter. */
val RECENT_INSTALL_KEYWORDS: Set<String> = setOf("new", "recent", "moi cai", "vua cai")

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * FEAT-013: visible apps installed within [RECENT_INSTALL_DAYS] of [nowMillis], newest first. An app with no known
 * install date is left out; a date in the future (clock skew) counts as recent.
 */
@JvmStatic
@JvmOverloads
fun recentInstalls(
    apps: List<App>,
    nowMillis: Long = System.currentTimeMillis(),
    limit: Int = SEARCH_RESULT_LIMIT
): List<App> {
    if (limit <= 0) return emptyList()
    val windowMillis = RECENT_INSTALL_DAYS * MILLIS_PER_DAY
    return apps
        .filter { it.isVisible && it.installDate > 0L && nowMillis - it.installDate <= windowMillis }
        .sortedWith(
            compareByDescending<App> { it.installDate }
                .thenBy { normalize(it.label) }
                .thenBy { componentKey(it) }
        )
        .take(limit)
}
```

- [ ] **Step 4: Run the tests and confirm they pass.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: PASS (existing 20 + 7 new).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt
git commit -m "feat(search): rank apps installed in the last seven days

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

## Task 2: Trigger the list from the search query

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt` (`search`)
- Test: `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`

**Interfaces consumed:** `RECENT_INSTALL_KEYWORDS`, `recentInstalls(apps, nowMillis, limit)` from Task 1.
**Interface produced:** `search(apps, query, recentKeys, favoriteKeys, limit, nowMillis: Long = System.currentTimeMillis())` (still `@JvmOverloads`; existing callers compile unchanged).

- [ ] **Step 1: Add failing tests** (same test class; reuses `now`, `day`, `installed` from Task 1):

```kotlin
@Test
fun `each keyword and its accent or case variants list the recent installs`() {
    val recent = installed("com.recent", 1 * day, label = "Zeta")
    val old = installed("com.old", 30 * day, label = "Alpha")
    val apps = listOf(old, recent)

    listOf("new", "NEW", " recent ", "mới cài", "MOI CAI", "vừa cài", "vua cai").forEach { query ->
        assertEquals(query, listOf(recent), AppSearchEngine.search(apps, query, nowMillis = now))
    }
}

@Test
fun `a longer query that merely contains a keyword uses normal search`() {
    val newYork = App(label = "New York Times", packageName = "com.nyt", name = "Main", installDate = now - 40 * day)
    val recent = installed("com.recent", 1 * day, label = "Zeta")

    val result = AppSearchEngine.search(listOf(newYork, recent), "new york", nowMillis = now)

    assertEquals(listOf(newYork), result)
}

@Test
fun `a keyword query does not return old apps and ignores history and favorites`() {
    val old = installed("com.old", 30 * day, label = "New Notes")
    val recent = installed("com.recent", 2 * day, label = "Zeta")

    val result = AppSearchEngine.search(
        listOf(old, recent), "new",
        recentKeys = listOf(AppSearchEngine.componentKey(old)),
        favoriteKeys = setOf(AppSearchEngine.componentKey(old)),
        nowMillis = now
    )

    assertEquals(listOf(recent), result)
}

@Test
fun `keyword queries respect hidden apps and the explicit limit`() {
    val hidden = installed("com.hidden", 1 * day, visible = false)
    val apps = (1..10).map { installed("com.app$it", it * 60_000L) } + hidden

    assertEquals(3, AppSearchEngine.search(apps, "new", limit = 3, nowMillis = now).size)
    assertTrue(AppSearchEngine.search(apps, "new", nowMillis = now).none { it.packageName == "com.hidden" })
    assertTrue(AppSearchEngine.search(apps, "new", limit = 0, nowMillis = now).isEmpty())
}

@Test
fun `blank and unrelated queries behave exactly as before`() {
    val recent = installed("com.recent", 1 * day, label = "Zeta")

    assertTrue(AppSearchEngine.search(listOf(recent), "", nowMillis = now).isEmpty())
    assertEquals(listOf(recent), AppSearchEngine.search(listOf(recent), "zeta", nowMillis = now))
    assertTrue(AppSearchEngine.search(listOf(recent), "newer", nowMillis = now).isEmpty())
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: compile error `No parameter with name 'nowMillis' found`.

- [ ] **Step 3: Implement.** Change the signature and add the branch immediately after `normalizedQuery` is computed:

```kotlin
@JvmStatic
@JvmOverloads
fun search(
    apps: List<App>,
    query: CharSequence?,
    recentKeys: List<String> = emptyList(),
    favoriteKeys: Set<String> = emptySet(),
    limit: Int = SEARCH_RESULT_LIMIT,
    nowMillis: Long = System.currentTimeMillis()
): List<App> {
    if (limit <= 0) return emptyList()

    val visibleApps = apps.filter(App::isVisible)
    val normalizedQuery = normalize(query)
    // FEAT-013: the whole query is a recent-installs keyword (e.g. "new") - list recent installs instead.
    if (normalizedQuery in RECENT_INSTALL_KEYWORDS) return recentInstalls(visibleApps, nowMillis, limit)
    // ... existing body unchanged from here ...
```

Do not change anything below that line.

- [ ] **Step 4: Run all search unit tests** (guards against regressions in the existing ranking, T9 and recent-history tests):

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.*'`
Expected: PASS.

- [ ] **Step 5: Mutation check** (each must turn a test red, then restore): remove the `if (normalizedQuery in RECENT_INSTALL_KEYWORDS)` line; change `<=` to `<` in `recentInstalls`; remove `it.installDate > 0L`. Run with `--rerun` so a cached result is not mistaken for a pass.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt
git commit -m "feat(search): list recent installs when the query is a recent-install keyword

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

## Task 3: Give work-profile apps an install date

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/ProfileAppSource.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/LauncherAppsDirectory.kt`
- Test: `app/src/test/java/com/mckimquyen/util/ProfileAppSourceTest.kt`

**Interfaces produced:** `ProfileAppEntry(packageName, name, label, userId, isWorkProfile, installDate: Long = 0L)`.

- [ ] **Step 1: Add failing tests** to `ProfileAppSourceTest.kt` (the class is Robolectric-annotated and already has the `entry(...)` helper and `icon()`):

```kotlin
@Test
fun `a work app's install date reaches its shell so it can be listed as recently installed`() {
    val installed = 1_800_000_000_000L
    val shells = ProfileAppSource.toShells(
        entries = listOf(ProfileAppEntry("com.chat", "Main", "Chat", 10, true, installDate = installed)),
        firstId = 0,
        iconFor = { null },
        cacheKeyFor = { "k" },
        paletteFor = { _, _ -> 0 }
    )

    assertEquals(installed, shells.single().installDate)
}

@Test
fun `an entry without an install date keeps the unknown value zero`() {
    val shells = ProfileAppSource.toShells(
        entries = listOf(entry("com.w", "W", 10)),
        firstId = 0,
        iconFor = { null },
        cacheKeyFor = { "k" },
        paletteFor = { _, _ -> 0 }
    )

    assertEquals(0L, shells.single().installDate)
}
```

- [ ] **Step 2: Run and confirm failure.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.ProfileAppSourceTest'`
Expected: compile error `No parameter with name 'installDate'`.

- [ ] **Step 3: Implement.** In `ProfileAppSource.kt` add the field with a default, and copy it:

```kotlin
data class ProfileAppEntry(
    val packageName: String,
    val name: String,
    val label: CharSequence,
    val userId: Int,
    val isWorkProfile: Boolean,
    /** First-install time in ms, or 0 when unknown. Filled by [LauncherAppsDirectory]. */
    val installDate: Long = 0L
) {
```

and in `toShells` add `installDate = entry.installDate,` to the `App(...)` call (after `isWorkProfile = entry.isWorkProfile`). `ProfileAppSource.collect` already uses `copy(userId = ..., isWorkProfile = ...)`, which keeps `installDate`.

In `LauncherAppsDirectory.appsOf`, add to the `ProfileAppEntry(...)` call:

```kotlin
installDate = info.firstInstallTime,
```

`LauncherActivityInfo.getFirstInstallTime()` is per-profile, unlike `PackageManager`, which only sees the current user. It is available on every API level this app supports (it already uses `LauncherApps.getActivityList`).

- [ ] **Step 4: Run tests.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.util.ProfileAppSourceTest' --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/util/ProfileAppSource.kt app/src/main/java/com/mckimquyen/util/LauncherAppsDirectory.kt app/src/test/java/com/mckimquyen/util/ProfileAppSourceTest.kt
git commit -m "fix(profile): carry work-profile install dates into app shells

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

## Task 4: Home search on the locked device

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/search/RecentInstallSearchWidgetTest.kt`

Reuse the pattern of `ActHomeT9KeypadWidgetTest`: `RAppsSingleton` seeding, `ActivityScenario.launch(ActHome::class.java)`, `activity.openSearchFromHome()`, an await helper for `SearchView.TransitionState.SHOWN`, and `activity.searchResultAdapterForTest?.currentListForTest()`. Read that test first for the exact fixture and copy its `awaitSearchShown`.

- [ ] **Step 1: Write the test** (red first: write it before the engine is wired, or confirm it fails by temporarily running it on the commit before Task 2 if convenient; otherwise rely on the unit mutation check):

```kotlin
package com.mckimquyen.search

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FEAT-013: typing "new" in the real Home search lists recently installed apps, not old ones or hidden ones. */
@RunWith(AndroidJUnit4::class)
class RecentInstallSearchWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalApps: ArrayList<App>? = null
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        (instrumentation.targetContext.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun typingNewListsOnlyVisibleRecentInstallsNewestFirst() {
        val now = System.currentTimeMillis()
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Old Tool", packageName = "com.test.old", name = "Main", isVisible = true, installDate = now - 30 * day),
            App(label = "Fresh B", packageName = "com.test.freshb", name = "Main", isVisible = true, installDate = now - 3 * day),
            App(label = "Fresh A", packageName = "com.test.fresha", name = "Main", isVisible = true, installDate = now - 1 * day),
            App(label = "Hidden New", packageName = "com.test.hidden", name = "Main", isVisible = false, installDate = now - 1 * day)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.search.SearchView>(com.mckimquyen.R.id.searchView)
                    .setText("new")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals(listOf("com.test.fresha", "com.test.freshb"), list.map { it.packageName.toString() })
            }
        }
    }

    private fun awaitSearchShown(scenario: ActivityScenario<ActHome>) {
        val deadline = android.os.SystemClock.uptimeMillis() + 6_000L
        var shown = false
        while (!shown && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                shown = activity.findViewById<com.google.android.material.search.SearchView>(com.mckimquyen.R.id.searchView)
                    .currentTransitionState == com.google.android.material.search.SearchView.TransitionState.SHOWN
            }
            if (!shown) Thread.sleep(50L)
        }
        org.junit.Assert.assertTrue("SearchView must finish opening", shown)
    }
}
```

- [ ] **Step 2: Back up the device, install, run.**

```bash
K=115333744A005844
mkdir -p /tmp/b004/state
adb -s $K exec-out run-as com.mckimquyen.lenslauncher tar cf - databases shared_prefs > /tmp/b004/state/f13_before.tar
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s $K install -r app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk
adb -s $K install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s $K shell am instrument --user 0 -w -e class com.mckimquyen.search.RecentInstallSearchWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (1 test)`. Check `ls -l /tmp/b004/state/f13_before.tar` is not 0 bytes BEFORE installing; if it is, stop. If any screenshot shows an ad over the UI, stop and wait for the user (R4).

- [ ] **Step 3: Regression on the same device**, one class at a time with a timeout (a whole-package run can hang on this ROM): `AppSearchIntegrationTest`, `ActHomeT9KeypadWidgetTest`, `SearchHistoryStoreTest`.

- [ ] **Step 4: Restore and verify the device.**

```bash
adb -s $K shell am force-stop com.mckimquyen.lenslauncher
adb -s $K push /tmp/b004/state/f13_before.tar /data/local/tmp/f13.tar
adb -s $K shell "chmod 644 /data/local/tmp/f13.tar && run-as com.mckimquyen.lenslauncher sh -c 'rm -rf databases shared_prefs && tar xf /data/local/tmp/f13.tar databases shared_prefs'"
adb -s $K shell rm -f /data/local/tmp/f13.tar
```

Then re-export `databases shared_prefs` to a second tar, extract both and run `diff -rq`. Expected: no differences. Also confirm `cmd locale get-app-locales`, `settings get system font_scale` and the Contacts grant match the values captured before.

- [ ] **Step 5: Commit.**

```bash
git add app/src/androidTest/java/com/mckimquyen/search/RecentInstallSearchWidgetTest.kt
git commit -m "test(search): cover the recent-installs list in the real Home search

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

## Task 5: Gate, audit, close the story

**Files:**
- Create: `doc/task/AUDIT_FEAT-013_2026-10-27.md`
- Modify: `doc/task/todo/p3-feat-feat-013-recent-install-quick-action.md` → `doc/task/done/`, `doc/task/README.md`, `doc/feature.md`

- [ ] Run `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun`; record unit XML totals and lint counts; run `git diff --check` and `bash scripts/check-secrets.sh`.
- [ ] Write the audit: evidence check (the story's "use the `QuickActionEngine` pipeline" does not fit because that engine returns a single row; work-profile apps had no `installDate`); decisions (list in results, 7-day window including opened apps, two intentional definitions of "new"); the accepted trade-off that the exact query `new` no longer ranks an app named "New ..." by label; results, mutation checks, device model/API/serial, restore proof; limitations (keywords are only the four listed, no setting, no header row, work-profile install date proven by unit test not by a real work profile). Score must be > 9.0 to push.
- [ ] Tick the story's acceptance criteria and Definition of Done boxes, move it to `done/`, mark FEAT-013 done in `doc/task/README.md`, add one entry to `doc/feature.md` under Implemented (keep exactly one `## 🟡 In progress` heading; build the file from `HEAD` plus the one new line and stage it with `git hash-object` + `git update-index --cacheinfo` so no unrelated edits slip in).
- [ ] Commit docs, then `git push origin dev` only if the score is > 9.0. Do not stage `.idea/`.

## Self-review

- Spec coverage: window and keywords and tie rules (Task 1), whole-query trigger and unchanged other queries (Task 2), hidden/zero/future dates (Task 1), work-profile install date (Task 3), device check (Task 4), audit and trade-off record (Task 5).
- The spec proposed reading the work-profile install date through `PackageManager`; this plan uses `LauncherActivityInfo.getFirstInstallTime()` instead, because it is profile-aware and needs no extra lookup. Spec intent is unchanged.
- Types are consistent across tasks: `recentInstalls(apps, nowMillis, limit)`, `RECENT_INSTALL_KEYWORDS`, `search(..., limit, nowMillis)`, `ProfileAppEntry.installDate`.
- No placeholders; every code step shows the code.
