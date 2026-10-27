# SEARCH-017 — T9 parity with text search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (inline) or `superpowers:subagent-driven-development` (subagents) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make T9 return at most the same 24 results as text search and rank same-tier results using the same favorite, recent-history, open-count, label, and app-key tie breakers.

**Architecture:** Keep T9 matching tiers and SEARCH-012 package-alias matching unchanged. Extract shared result limit into `AppSearchEngine.SEARCH_RESULT_LIMIT`, pass recent app keys from `ActHome` to T9, and use `AppSearchEngine.componentKey` (already user-aware through FEAT-012) for recent ordering. Preserve current two-argument T9 call compatibility with defaults and `@JvmOverloads`.

**Tech Stack:** Kotlin/JVM, JUnit 4, Android instrumentation (Espresso/AndroidJUnitRunner), Gradle.

## Global Constraints

- Search is local/offline; add no network calls or dependencies.
- T9 match-tier precedence remains label-prefix, label-word-prefix, label-substring, alias-prefix, alias-substring.
- Blank/non-digit T9 queries return an empty list.
- Recent history only breaks ties after match tier and favorite, matching text search; it never promotes a weaker match tier.
- Share one named result-limit constant with text search: `24`.
- Preserve user-aware `AppSearchEngine.componentKey(app)` so identical apps in personal/work profiles have separate history.
- Do not modify unrelated UI, ads, IAP, accessibility, or build configuration.

---

## File Structure

- Modify `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`: expose shared `SEARCH_RESULT_LIMIT` instead of private `DEFAULT_LIMIT`.
- Modify `app/src/main/java/com/mckimquyen/search/T9SearchEngine.kt`: accept history + limit; preserve match tiers and add common tie-break order and `.take(limit)`.
- Modify `app/src/main/java/com/mckimquyen/ui/ActHome.java`: pass `searchHistoryStore.recentKeys()` to the T9 branch.
- Modify `app/src/test/java/com/mckimquyen/search/T9SearchEngineTest.kt`: focused rank, limit, empty-history and work-profile regressions.
- Modify `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`: assert the shared cap remains 24 and named constant remains contract.
- Run existing device tests `ActHomeT9KeypadWidgetTest` and `AppSearchIntegrationTest` on locked TECNO BG6 only.

## Task 1: Lock shared result cap

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt`

- [ ] **Step 1: Write a failing contract test** in `AppSearchEngineTest.kt`:

```kotlin
@Test
fun `text search shared result limit is twenty four`() {
    assertEquals(24, AppSearchEngine.SEARCH_RESULT_LIMIT)
}
```

- [ ] **Step 2: Run the focused test and verify it fails because `SEARCH_RESULT_LIMIT` is not defined.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: compile failure `Unresolved reference 'SEARCH_RESULT_LIMIT'`.

- [ ] **Step 3: Replace the private constant with a public shared constant** in `AppSearchEngine`:

```kotlin
const val SEARCH_RESULT_LIMIT = 24
```

Change the `search(... limit: Int = DEFAULT_LIMIT)` default to `SEARCH_RESULT_LIMIT`; remove `DEFAULT_LIMIT`.

- [ ] **Step 4: Run the focused test and existing AppSearchEngine tests.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: PASS; existing search order unchanged.

- [ ] **Step 5: Commit this independently testable contract.**

```bash
git add app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt app/src/test/java/com/mckimquyen/search/AppSearchEngineTest.kt
git commit -m "refactor(search): share the text search result limit"
```

## Task 2: Add history ordering and result cap to T9

**Files:**
- Modify: `app/src/test/java/com/mckimquyen/search/T9SearchEngineTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/search/T9SearchEngine.kt`

**Interface produced by this task:**

```kotlin
@JvmStatic
@JvmOverloads
fun search(
    candidates: List<App>,
    query: CharSequence?,
    recentKeys: List<String> = emptyList(),
    limit: Int = AppSearchEngine.SEARCH_RESULT_LIMIT
): List<App>
```

- [ ] **Step 1: Add focused failing tests** to `T9SearchEngineTest.kt`:

```kotlin
@Test
fun `recent history orders apps within the same t9 match tier`() {
    val recent = App(label = "Chrome", packageName = "com.chrome.recent", name = "Main")
    val older = App(label = "Chrome", packageName = "com.chrome.older", name = "Main")
    val keys = listOf(AppSearchEngine.componentKey(recent), AppSearchEngine.componentKey(older))

    assertEquals(
        listOf(recent, older),
        T9SearchEngine.search(listOf(older, recent), "247", recentKeys = keys)
    )
}

@Test
fun `favorite outranks recency and stronger match tier outranks recent history`() {
    val recentSubstring = App(label = "Monochrome", packageName = "com.mono", name = "Main")
    val prefix = App(label = "Chrome", packageName = "com.chrome", name = "Main")
    val recentNonFavorite = App(label = "Chrome", packageName = "com.chrome.recent", name = "Main")
    val favorite = App(label = "Chrome", packageName = "com.chrome.favorite", name = "Main", isFavorite = true)
    val keys = listOf(AppSearchEngine.componentKey(recentSubstring), AppSearchEngine.componentKey(recentNonFavorite))

    assertEquals(
        listOf(favorite, recentNonFavorite, prefix, recentSubstring),
        T9SearchEngine.search(listOf(recentSubstring, prefix, recentNonFavorite, favorite), "247", recentKeys = keys)
    )
}

@Test
fun `empty history keeps the existing t9 ordering`() {
    val lowOpen = App(label = "Chrome", packageName = "com.chrome.low", name = "Main", openCount = 1)
    val highOpen = App(label = "Chrome", packageName = "com.chrome.high", name = "Main", openCount = 9)

    assertEquals(
        listOf(highOpen, lowOpen),
        T9SearchEngine.search(listOf(lowOpen, highOpen), "247")
    )
}

@Test
fun `results are capped after ranking and a non-positive limit returns empty`() {
    val apps = (0 until 40).map { index ->
        App(label = "Chrome", packageName = "com.chrome.app$index", name = "Main", openCount = index.toLong())
    }

    val capped = T9SearchEngine.search(apps, "247", limit = 7)
    assertEquals(7, capped.size)
    assertEquals(apps.sortedByDescending(App::openCount).take(7), capped)
    assertTrue(T9SearchEngine.search(apps, "247", limit = 0).isEmpty())
}

@Test
fun `work profile history does not rank the personal copy as recent`() {
    val personal = App(label = "Chrome", packageName = "com.chrome", name = "Main", userId = 0)
    val work = App(label = "Chrome", packageName = "com.chrome", name = "Main", userId = 10, isWorkProfile = true)
    val keys = listOf(AppSearchEngine.componentKey(work))

    assertEquals(
        listOf(work, personal),
        T9SearchEngine.search(listOf(personal, work), "247", recentKeys = keys)
    )
}
```

Also add an explicit alias-cap assertion if the existing SEARCH-012 alias test is not already covered by the shared limit test:

```kotlin
@Test
fun `alias matches are capped by the same shared result limit`() {
    val apps = (0 until 30).map { index ->
        App(label = "微信", packageName = "com.chrome.alias$index", name = "Main", openCount = index.toLong())
    }
    assertEquals(
        AppSearchEngine.SEARCH_RESULT_LIMIT,
        T9SearchEngine.search(apps, "247").size
    )
}
```

- [ ] **Step 2: Run focused T9 tests and verify the new behaviors fail before implementation.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.T9SearchEngineTest'`
Expected: compile failure for new named parameters (`recentKeys`, `limit`).

- [ ] **Step 3: Implement the shared signature and rank chain** in `T9SearchEngine.kt`:

```kotlin
@JvmStatic
@JvmOverloads
fun search(
    candidates: List<App>,
    query: CharSequence?,
    recentKeys: List<String> = emptyList(),
    limit: Int = AppSearchEngine.SEARCH_RESULT_LIMIT
): List<App> {
    if (limit <= 0) return emptyList()
    val digitsQuery = query?.toString()?.trim().orEmpty()
    if (digitsQuery.isEmpty() || digitsQuery.any { !it.isDigit() }) return emptyList()

    val recentPositions = buildMap {
        recentKeys.forEachIndexed { index, key -> putIfAbsent(key, index) }
    }
    return candidates.asSequence()
        .filter(App::isVisible)
        .mapNotNull { app ->
            val tier = matchTier(app, digitsQuery) ?: return@mapNotNull null
            app to tier
        }
        .sortedWith(
            compareBy<Pair<App, Int>> { it.second }
                .thenByDescending { it.first.isFavorite }
                .thenBy { recentPositions[AppSearchEngine.componentKey(it.first)] ?: Int.MAX_VALUE }
                .thenByDescending { it.first.openCount }
                .thenBy { AppSearchEngine.normalize(it.first.label) }
                .thenBy { AppSearchEngine.componentKey(it.first) }
        )
        .take(limit)
        .map { it.first }
        .toList()
}
```

Preserve `matchTier` unchanged, including SEARCH-012 alias fallback. Do not return alias-signature matches twice; current `mapNotNull` already emits one `(App, tier)` per app.

- [ ] **Step 4: Run focused T9 tests; require all existing mapping/tier/alias tests and new history/limit tests to pass.**

Run: `./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.search.T9SearchEngineTest' --tests 'com.mckimquyen.search.AppSearchEngineTest'`
Expected: PASS.

- [ ] **Step 5: Commit T9 engine and tests.**

```bash
git add app/src/main/java/com/mckimquyen/search/T9SearchEngine.kt app/src/test/java/com/mckimquyen/search/T9SearchEngineTest.kt
git commit -m "feat(search): rank and cap T9 results like text search"
```

## Task 3: Pass recent keys from ActHome and verify the Home path

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java:1728-1733`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeT9KeypadWidgetTest.kt`

- [ ] **Step 1: Add this widget test** to `ActHomeT9KeypadWidgetTest.kt` (it reuses the existing fixture, `awaitSearchShown`, `openSearchFromHome`, `searchResultAdapterForTest` and the `btnT9_*` keys; `SearchHistoryStore` is cleared before and after so no state leaks):

```kotlin
/** SEARCH-017: in T9 mode a recently launched app precedes an equally ranked one that was not. */
@Test
fun t9ResultsPutTheRecentlyLaunchedAppFirstWithinTheSameMatchTier() {
    val older = App(label = "Chrome", packageName = "com.test.chrome.older", name = "Main", isVisible = true)
    val recent = App(label = "Chrome", packageName = "com.test.chrome.recent", name = "Main", isVisible = true)
    RAppsSingleton.instance.apps = arrayListOf(older, recent)
    val store = com.mckimquyen.search.SearchHistoryStore(context)
    store.clear()
    store.recordLaunch(com.mckimquyen.search.AppSearchEngine.componentKey(recent))
    try {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btnT9_Clear).performClick()
                activity.findViewById<View>(R.id.btnT9_2).performClick()
                activity.findViewById<View>(R.id.btnT9_4).performClick()
                activity.findViewById<View>(R.id.btnT9_7).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals(listOf("com.test.chrome.recent", "com.test.chrome.older"), list.map { it.packageName.toString() })
            }
        }
    } finally {
        store.clear()
    }
}
```

- [ ] **Step 2: Run the focused widget test and verify it fails because ActHome does not yet pass recent keys.**

Run: `./gradlew assembleDevDebugAndroidTest`; then on the device locked for this session (`<LOCKED_SERIAL>`; ask once per session via AskUserQuestion when more than one device is attached, per R3):

```bash
adb -s <LOCKED_SERIAL> shell am instrument --user 0 -w \
  -e class com.mckimquyen.ui.ActHomeT9KeypadWidgetTest \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: new ordering assertion fails while existing T9 keypad tests pass. If the locked device differs, use only the currently authorized device serial; never let a Gradle `connected*` task fan out to multiple devices.

- [ ] **Step 3: Pass history into T9 in `ActHome.java`.** Change only the T9 expression:

```java
? T9SearchEngine.search(candidates, query, searchHistoryStore.recentKeys())
```

Keep the text-search branch unchanged; its default cap remains the shared 24.

- [ ] **Step 4: Re-run the focused widget and search integration tests on the device locked for this session (`<LOCKED_SERIAL>`; ask once per session via AskUserQuestion when more than one device is attached, per R3).**

Run:

```bash
adb -s <LOCKED_SERIAL> shell am instrument --user 0 -w \
  -e class 'com.mckimquyen.ui.ActHomeT9KeypadWidgetTest,com.mckimquyen.search.AppSearchIntegrationTest' \
  com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: all tests pass. If a screenshot shows any ad overlay, stop immediately per R4; do not continue until the user says `done`.

- [ ] **Step 5: Commit ActHome call and the test.**

```bash
git add app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/androidTest/java/com/mckimquyen/ui/ActHomeT9KeypadWidgetTest.kt
git commit -m "fix(search): apply recent-launch order in T9 mode"
```

## Task 4: Full verification, audit and story close

**Files:**
- Modify: `doc/task/todo/p3-search-search-017-t9-parity-with-text-search.md` → `doc/task/done/`
- Modify: `doc/task/README.md`, `doc/feature.md`
- Create: `doc/task/AUDIT_SEARCH-017_2026-10-27.md`

- [ ] Run full gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- [ ] Record fresh unit XML totals, lint warnings/errors, focused widget/integration result, device model/API/build SHA/time; be honest about any test not run or any R4 ad stop.
- [ ] Run `git diff --check` and `bash scripts/check-secrets.sh`.
- [ ] Audit: verify T9 still uses SEARCH-012 aliases; both engines share exactly one limit; text-search order unchanged; T9 rank chain matches spec; recent key includes user id; no extra dependency or string; score > 9.0 required before push.
- [ ] Update story acceptance criteria / verification boxes and move it from todo to done only if all gates and the score threshold pass. Otherwise leave it in progress and document the blocker.
- [ ] Update `doc/task/README.md` and `doc/feature.md`.
- [ ] Commit audit/story/tracker files, then push `origin/dev` only if the final score is > 9.0.

## Self-review

- All spec decisions have a task: shared cap (Task 1), T9 order/history/alias retention/user-aware key (Task 2), production call site (Task 3), and full evidence/audit (Task 4).
- No Room, persistence, layout, or locale changes are included.
- Existing two-argument Kotlin/Java calls continue through `@JvmOverloads`; `ActHome` is the only production caller that adds `recentKeys`.
- Limit and order tests use concrete app labels, package keys, favorites, open counts and user ids; no test depends on timing.
