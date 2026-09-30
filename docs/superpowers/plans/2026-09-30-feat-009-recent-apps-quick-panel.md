# FEAT-009 — Recently Used Apps Quick Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `BottomSheetDialogFragment` lists up to 16 recently-launched apps, reachable both from a
new icon on the always-visible home-screen `SearchBar` and from the existing lens-management
long-press menu; tap launches, long-press offers App info / Remove from recent.

**Architecture:** `SearchHistoryStore`'s existing `SharedPreferences`-backed MRU list (raised from
8 to 16) stays the single source of truth for both the pre-existing search-overlay `recentHeader`
and the new panel. The panel's `RecyclerView` reuses `SearchResultAdapter` as-is (icon loading,
shortcuts, `DiffUtil`, long-press menu) via one new optional constructor callback — no new adapter.
A pure `RecentAppsPanelResolver` maps stored component keys back to live `App` objects. A new
`UtilSettings` boolean toggles the `SearchBar` icon only; the lens-menu entry is untoggleable.

**Tech Stack:** Kotlin, `SharedPreferences` (via `UtilSettings`/`SearchHistoryStore`), Material3
`BottomSheetDialogFragment` + `SearchBar.inflateMenu`, JUnit4 + Robolectric (unit), AndroidJUnit4
instrumented tests (widget/integration, no Espresso — this project's device cannot build the
Espresso event injector, see existing test docstrings).

## Global Constraints

- `MAX_RECENT` changes from 8 to 16 for **both** `recentHeader` (search overlay) and the new panel
  — one shared constant, not two diverging lists.
- Recent apps stay global across all lenses (`FISH-008`) — no per-lens key suffix, matching
  `App.openCount`'s existing "by design" global scope.
- No new adapter class. The panel's `RecyclerView` reuses `SearchResultAdapter`
  (`app/src/main/java/com/mckimquyen/search/SearchResultAdapter.kt`) via one new optional
  constructor parameter `onRemoveFromRecent: ((App) -> Unit)? = null`. Every existing call site
  (`SearchResultAdapter { app, source -> ... }` / `SearchResultAdapter { _, _ -> }`) must keep
  compiling unchanged.
- No new drawable icons: reuse `@drawable/ic_history_24dp` (SearchBar action icon, already used by
  `recentHeader`) and leave "Remove from recent" icon-less, matching the existing icon-less
  `menuItemPinStart`/`menuItemPinEnd`/`menuItemUnpin` items in the same menu.
- Espresso cannot be used on this project's test device (API 37 removed
  `InputManager.getInstance`, breaking even a non-clicking `onView()`). Every menu-item-click
  behavior under test must go through a `@VisibleForTesting` handler method called directly, the
  same convention `ActHome.onLensMenuItemSelected(int, int)` already established — never a
  simulated real popup click.
- Every new/changed translatable string must exist, with matching placeholders and real
  (non-English) text, in all 16 non-English locales
  (`values-ar/de/es/fr/hi/in/it/ja/km/ko/lo/pt/ru/th/vi/zh`). This project's existing
  `app/src/test/java/com/mckimquyen/a11y/AllStringsTranslationTest.kt` already scans every
  translatable `<string>`/`<plurals>` in `values/strings.xml` against all 16 locales — no new or
  modified test is needed for string coverage, only the string entries themselves.
- Every implementation case needs unit, widget, and integration coverage per this repo's
  `doc/task/README.md` test-layer rule; a Tecno smoke pass on the session-locked device closes the
  loop.
- Device policy (current owner decision): **TECNO only** for build/run/install/smoke — TECNO KJ7
  (`115333744A005844`) preferred, TECNO BG6 (`118743744X002560`) standing fallback if KJ7 drops.
  Never fall back to Pixel/S24U even if attached and idle. If no TECNO is attached, stop and ask.
- Spec: `docs/superpowers/specs/2026-09-30-feat-009-recent-apps-quick-panel-design.md`.

---

### Task 1: `SearchHistoryStore` — raise MAX_RECENT to 16, add `removeKey`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/search/SearchHistoryStore.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/search/SearchHistoryStoreTest.kt` (existing file
  — this store is only ever tested instrumented, via a real device `SharedPreferences`, not JVM)

**Interfaces:**
- Produces: `SearchHistoryStore.removeKey(componentKey: String): Unit`; `MAX_RECENT` raised to 16
  (private constant, no signature change to existing public methods).

- [ ] **Step 1: Write the failing tests**

Append to `app/src/androidTest/java/com/mckimquyen/search/SearchHistoryStoreTest.kt` (inside the
existing `SearchHistoryStoreTest` class, using its existing `store`/`setUp`/`tearDown`):

```kotlin
    @Test
    fun recentListTrimsAtSixteenNotEight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (1..17).forEach { store.recordLaunch("pkg/App$it") }

        val recent = SearchHistoryStore(context).recentKeys()
        assertEquals(16, recent.size)
        // Most-recent-first: App17 was recorded last, App1 fell off the sixteen-item window.
        assertEquals("pkg/App17", recent.first())
        assertTrue("pkg/App1" !in recent)
    }

    @Test
    fun removeKeyDropsExactlyThatEntryAndPreservesOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store.recordLaunch("pkg/First")
        store.recordLaunch("pkg/Second")
        store.recordLaunch("pkg/Third")

        store.removeKey("pkg/Second")

        assertEquals(listOf("pkg/Third", "pkg/First"), SearchHistoryStore(context).recentKeys())
    }

    @Test
    fun removeKeyOnAbsentKeyIsANoOp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store.recordLaunch("pkg/First")

        store.removeKey("pkg/DoesNotExist")

        assertEquals(listOf("pkg/First"), SearchHistoryStore(context).recentKeys())
    }
```

- [ ] **Step 2: Run the new tests to verify they fail**

Build and install the debug + androidTest APKs, then run just these three tests on the
session-locked device (replace `<locked-device-serial>` with the actual locked serial):

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.search.SearchHistoryStoreTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `recentListTrimsAtSixteenNotEight` FAILS (list size is 8, not 16); the other two FAIL
with an unresolved-reference compile error (`removeKey` doesn't exist yet) — confirm the module
fails to compile before implementing, then proceed.

- [ ] **Step 3: Implement**

In `SearchHistoryStore.kt`, change the constant and add the method:

```kotlin
    fun removeKey(componentKey: String) {
        val updated = recentKeys().filterNot { it == componentKey }
        preferences.edit { putString(KEY_RECENT, updated.joinToString(SEPARATOR)) }
    }
```

(placed directly below `recordLaunch`, above `clear`), and in the `private companion object`:

```kotlin
        const val MAX_RECENT = 16 // FEAT-009: was 8, shared by recentHeader and the new panel
```

- [ ] **Step 4: Run the tests again to verify they pass**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.search.SearchHistoryStoreTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (5 tests)` (the 2 pre-existing tests plus the 3 new ones), all passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/search/SearchHistoryStore.kt app/src/androidTest/java/com/mckimquyen/search/SearchHistoryStoreTest.kt
git commit -m "feat(feat-009): raise recent-apps MAX_RECENT to 16, add removeKey"
```

---

### Task 2: `UtilSettings` — recent-apps quick-panel-icon toggle key

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Test: `app/src/test/java/com/mckimquyen/util/UtilSettingsRecentAppsPanelToggleTest.kt` (new)

**Interfaces:**
- Produces: `UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED: String`,
  `UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED: Boolean = true`; readable via the
  existing `getBoolean(name)` dispatcher, writable via the existing `save(name, Boolean)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsRecentAppsPanelToggleTest.kt`:

```kotlin
package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FEAT-009: the SearchBar's recent-apps-panel icon can be hidden independently of the panel
 * itself (the lens-menu entry point always stays available). Defaults to shown, same generic
 * UtilSettings.save(name, Boolean)/getBoolean() pattern as every other boolean setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsRecentAppsPanelToggleTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `recent apps panel icon is shown by default`() {
        assertTrue(freshSettings().getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
        assertTrue(UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
    }

    @Test
    fun `disabling the icon persists and reads back false`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }

    @Test
    fun `re-enabling the icon persists and reads back true`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }

    @Test
    fun `the key is stable and distinct from other boolean settings`() {
        assertEquals("recent_apps_quick_panel_enabled", UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.UtilSettingsRecentAppsPanelToggleTest"
```

Expected: FAIL to compile — `KEY_RECENT_APPS_QUICK_PANEL_ENABLED`/`DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED` unresolved.

- [ ] **Step 3: Implement**

In `UtilSettings.kt`'s companion object, next to `KEY_SMART_FOCUS_BIAS`/`KEY_DEPTH_OF_FIELD`:

```kotlin
        const val KEY_RECENT_APPS_QUICK_PANEL_ENABLED = "recent_apps_quick_panel_enabled"
        const val DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED = true
```

In the `getBoolean(name)` when-block, next to the `KEY_SMART_FOCUS_BIAS` case:

```kotlin
        KEY_RECENT_APPS_QUICK_PANEL_ENABLED -> prefs.getBoolean(name, DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.util.UtilSettingsRecentAppsPanelToggleTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/util/UtilSettings.kt app/src/test/java/com/mckimquyen/util/UtilSettingsRecentAppsPanelToggleTest.kt
git commit -m "feat(feat-009): add recent-apps quick-panel-icon toggle setting"
```

---

### Task 3: `RecentAppsPanelResolver` — pure key-to-App resolution

**Files:**
- Create: `app/src/main/java/com/mckimquyen/search/RecentAppsPanelResolver.kt`
- Test: `app/src/test/java/com/mckimquyen/search/RecentAppsPanelResolverTest.kt`

**Interfaces:**
- Consumes: `AppSearchEngine.componentKey(app: App): String` (existing,
  `app/src/main/java/com/mckimquyen/search/AppSearchEngine.kt:12`).
- Produces: `RecentAppsPanelResolver.resolve(recentKeys: List<String>, snapshot: List<App>): List<App>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/mckimquyen/search/RecentAppsPanelResolverTest.kt`:

```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentAppsPanelResolverTest {

    private val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
    private val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")

    @Test
    fun emptyRecentKeys_returnsEmptyList() {
        assertTrue(RecentAppsPanelResolver.resolve(emptyList(), listOf(camera, notes)).isEmpty())
    }

    @Test
    fun resolvesInRecentKeysOrder_notSnapshotOrder() {
        val keys = listOf(AppSearchEngine.componentKey(notes), AppSearchEngine.componentKey(camera))
        assertEquals(listOf(notes, camera), RecentAppsPanelResolver.resolve(keys, listOf(camera, notes)))
    }

    @Test
    fun keyMissingFromSnapshot_isDroppedNotCrashed() {
        val keys = listOf(
            AppSearchEngine.componentKey(camera),
            "pkg.uninstalled/UninstalledActivity",
            AppSearchEngine.componentKey(notes)
        )
        assertEquals(listOf(camera, notes), RecentAppsPanelResolver.resolve(keys, listOf(camera, notes)))
    }

    @Test
    fun emptySnapshot_returnsEmptyListRegardlessOfKeys() {
        assertTrue(
            RecentAppsPanelResolver.resolve(listOf(AppSearchEngine.componentKey(camera)), emptyList()).isEmpty()
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.search.RecentAppsPanelResolverTest"
```

Expected: FAIL to compile — `RecentAppsPanelResolver` doesn't exist yet.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/mckimquyen/search/RecentAppsPanelResolver.kt`:

```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App

/**
 * FEAT-009: resolves the recent-apps panel's row list from SearchHistoryStore's stored component
 * keys against the live app snapshot. Pure logic, independent of Android framework/Context, fully
 * unit-testable - same shape as util.LensLabelResolver. Keys for an uninstalled/updated app (no
 * longer in the snapshot) are silently dropped rather than shown as a "ghost" row.
 */
object RecentAppsPanelResolver {

    @JvmStatic
    fun resolve(recentKeys: List<String>, snapshot: List<App>): List<App> {
        val byKey = snapshot.associateBy { AppSearchEngine.componentKey(it) }
        return recentKeys.mapNotNull { byKey[it] }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.search.RecentAppsPanelResolverTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/search/RecentAppsPanelResolver.kt app/src/test/java/com/mckimquyen/search/RecentAppsPanelResolverTest.kt
git commit -m "feat(feat-009): add pure recent-apps-panel key-to-App resolver"
```

---

### Task 4: `SearchResultAdapter` — optional "Remove from recent" menu action

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/search/SearchResultAdapter.kt`
- Modify: `app/src/main/res/menu/menu_search_result.xml`
- Modify: `app/src/main/res/values/strings.xml` (+ all 16 locale `strings.xml` files)
- Modify: `app/src/androidTest/java/com/mckimquyen/search/SearchResultAdapterWidgetTest.kt`

**Interfaces:**
- Produces: `SearchResultAdapter(onAppClick: SearchResultClickListener, onRemoveFromRecent: ((App) -> Unit)? = null)`;
  `ResultViewHolder.handleMenuAction(itemId: Int, app: App, anchor: View): Boolean` (`@VisibleForTesting`);
  `ResultViewHolder.lastActionMenu: PopupMenu?` (`@VisibleForTesting`, read-only); `R.id.menuItemRemoveFromRecent`.
- Consumes (Task 1): none new here.

- [ ] **Step 1: Write the failing tests**

Add the new string first — `app/src/main/res/values/strings.xml`, next to `menu_app_info`:

```xml
    <string name="remove_from_recent">Remove from recent</string>
```

And in every locale file (same `name`, translated `text`), one line each:

```
values-ar/strings.xml:   <string name="remove_from_recent">إزالة من الأخيرة</string>
values-de/strings.xml:   <string name="remove_from_recent">Aus „Zuletzt" entfernen</string>
values-es/strings.xml:   <string name="remove_from_recent">Quitar de recientes</string>
values-fr/strings.xml:   <string name="remove_from_recent">Retirer des récents</string>
values-hi/strings.xml:   <string name="remove_from_recent">हाल ही से हटाएं</string>
values-in/strings.xml:   <string name="remove_from_recent">Hapus dari terbaru</string>
values-it/strings.xml:   <string name="remove_from_recent">Rimuovi dai recenti</string>
values-ja/strings.xml:   <string name="remove_from_recent">最近から削除</string>
values-km/strings.xml:   <string name="remove_from_recent">លុបចេញពីថ្មីៗ</string>
values-ko/strings.xml:   <string name="remove_from_recent">최근 항목에서 삭제</string>
values-lo/strings.xml:   <string name="remove_from_recent">ລຶບອອກຈາກລ່າສຸດ</string>
values-pt/strings.xml:   <string name="remove_from_recent">Remover dos recentes</string>
values-ru/strings.xml:   <string name="remove_from_recent">Удалить из недавних</string>
values-th/strings.xml:   <string name="remove_from_recent">ลบออกจากล่าสุด</string>
values-vi/strings.xml:   <string name="remove_from_recent">Xóa khỏi gần đây</string>
values-zh/strings.xml:   <string name="remove_from_recent">从最近使用中移除</string>
```

(Insert each line in its file next to that locale's existing `menu_app_info` translation, so the
menu-string block stays together — exact insertion point doesn't affect correctness.)

Add the menu item — `app/src/main/res/menu/menu_search_result.xml`, as the last item:

```xml
    <item
        android:id="@+id/menuItemRemoveFromRecent"
        android:title="@string/remove_from_recent" />
```

Update the existing menu-shape test (`app/src/androidTest/java/com/mckimquyen/search/SearchResultAdapterWidgetTest.kt`,
`searchResultMenuResourceHasExactlyTheExpectedActions`) — the ids list now has a 6th entry:

```kotlin
                assertEquals(
                    listOf(
                        R.id.menuItemElementAppInfo,
                        R.id.menuItemPinStart,
                        R.id.menuItemPinEnd,
                        R.id.menuItemUnpin,
                        R.id.menuItemElementUninstall,
                        R.id.menuItemRemoveFromRecent
                    ),
                    ids
                )
```

Append two new test methods to the same class:

```kotlin
    /** FEAT-009: the panel passes a callback; the search overlay (existing usage) does not. */
    @Test
    fun removeFromRecentItemIsHiddenWhenNoCallbackIsProvided() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val adapter = SearchResultAdapter(onAppClick = { _, _ -> })
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menuItem = holder.lastActionMenu?.menu?.findItem(R.id.menuItemRemoveFromRecent)
                assertEquals(false, menuItem?.isVisible)
            }
        }
    }

    /** FEAT-009: the panel usage shows the item and tapping it invokes the callback. */
    @Test
    fun removeFromRecentItemIsVisibleAndInvokesCallbackWhenProvided() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                var removed: App? = null
                val adapter = SearchResultAdapter(
                    onAppClick = { _, _ -> },
                    onRemoveFromRecent = { removed = it }
                )
                adapter.submitList(listOf(app))

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()

                val menuItem = holder.lastActionMenu?.menu?.findItem(R.id.menuItemRemoveFromRecent)
                assertEquals(true, menuItem?.isVisible)

                // API 37 cannot inject a real popup-item click (see class docstring); call the
                // exact handler the popup's own click listener dispatches to, same convention as
                // ActHome.onLensMenuItemSelected.
                val handled = holder.handleMenuAction(R.id.menuItemRemoveFromRecent, app, holder.itemView)
                assertEquals(true, handled)
                assertSame(app, removed)
            }
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.search.SearchResultAdapterWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL to compile (`onRemoveFromRecent`, `lastActionMenu`, `handleMenuAction` don't exist
yet) and `searchResultMenuResourceHasExactlyTheExpectedActions` would fail on item count once it
does compile.

- [ ] **Step 3: Implement**

In `SearchResultAdapter.kt`, change the class header and add the new field/methods:

```kotlin
class SearchResultAdapter(
    private val onAppClick: SearchResultClickListener,
    private val onRemoveFromRecent: ((App) -> Unit)? = null
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {
```

(everything else in the class body above `inner class ResultViewHolder` is unchanged). Inside
`inner class ResultViewHolder`, replace `showActionMenu` and extend it with the new field/method,
directly below `bindShortcuts`:

```kotlin
        /** Exposed for tests only - the exact popup the last long-press built, unshown state
         *  included, mirroring ActHome.lensDialog's existing test-inspection convention. */
        @androidx.annotation.VisibleForTesting
        var lastActionMenu: PopupMenu? = null
            private set

        private fun showActionMenu(app: App, anchor: View) {
            val wrapper = ContextThemeWrapper(anchor.context, R.style.PopupMenuTheme)
            val popupMenu = PopupMenu(wrapper, anchor, Gravity.END)
            popupMenu.inflate(R.menu.menu_search_result)
            popupMenu.menu.findItem(R.id.menuItemUnpin).isVisible = app.pinnedZone != PinnedZone.NONE
            popupMenu.menu.findItem(R.id.menuItemRemoveFromRecent).isVisible = onRemoveFromRecent != null
            popupMenu.setForceShowIcon(true)
            popupMenu.setOnMenuItemClickListener { item -> handleMenuAction(item.itemId, app, anchor) }
            lastActionMenu = popupMenu
            popupMenu.show()
        }

        /** Split out from the popup's click listener so tests can invoke it directly instead of
         *  simulating a real popup-item click (this project's test device cannot build Espresso's
         *  event injector) - same convention as ActHome.onLensMenuItemSelected. */
        @androidx.annotation.VisibleForTesting
        fun handleMenuAction(itemId: Int, app: App, anchor: View): Boolean = when (itemId) {
            R.id.menuItemElementAppInfo -> {
                startActionIntent(anchor, UtilApp.appInfoIntent(app.packageName.toString()))
                true
            }
            R.id.menuItemElementUninstall -> {
                startActionIntent(anchor, UtilApp.uninstallIntent(app.packageName.toString()))
                true
            }
            R.id.menuItemPinStart -> {
                pin(app, anchor, PinnedZone.START)
                true
            }
            R.id.menuItemPinEnd -> {
                pin(app, anchor, PinnedZone.END)
                true
            }
            R.id.menuItemUnpin -> {
                pin(app, anchor, PinnedZone.NONE)
                true
            }
            R.id.menuItemRemoveFromRecent -> {
                onRemoveFromRecent?.invoke(app)
                true
            }
            else -> false
        }
```

(remove the old inline `setOnMenuItemClickListener { item -> when (item.itemId) { ... } }` block —
its body moved verbatim into `handleMenuAction` above).

- [ ] **Step 4: Run the tests again to verify they pass**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.search.SearchResultAdapterWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (8 tests)` (6 pre-existing + 2 new), all passing.

- [ ] **Step 5: Run the localization unit test**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.a11y.AllStringsTranslationTest"
```

Expected: `BUILD SUCCESSFUL` — confirms `remove_from_recent` is present, non-English, and
placeholder-consistent across all 16 locales.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/search/SearchResultAdapter.kt app/src/main/res/menu/menu_search_result.xml app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/search/SearchResultAdapterWidgetTest.kt
git commit -m "feat(feat-009): let SearchResultAdapter offer an optional Remove-from-recent action"
```

---

### Task 5: `RecentAppsPanelFragment` — the bottom sheet itself

**Files:**
- Create: `app/src/main/java/com/mckimquyen/ui/RecentAppsPanelFragment.kt`
- Create: `app/src/main/res/layout/bottom_sheet_recent_apps.xml`
- Modify: `app/src/main/res/values/strings.xml` (+ all 16 locales)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/RecentAppsPanelWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Tasks 1, 3, 4): `SearchHistoryStore.recentKeys()/removeKey()`,
  `RecentAppsPanelResolver.resolve(...)`, `SearchResultAdapter(onAppClick, onRemoveFromRecent)`,
  `RAppsSingleton.instance.apps`, `AppSearchEngine.componentKey(app)`,
  `UtilApp.launchComponent(...)`.
- Produces: `RecentAppsPanelFragment` (public no-arg constructor, `BottomSheetDialogFragment`),
  `RecentAppsPanelFragment.TAG: String`.

- [ ] **Step 1: Add the empty-state string**

`app/src/main/res/values/strings.xml`, next to `recent_apps_cleared`:

```xml
    <string name="recent_apps_panel_empty">No recent apps yet</string>
```

All 16 locales:

```
values-ar/strings.xml:   <string name="recent_apps_panel_empty">لا توجد تطبيقات حديثة بعد</string>
values-de/strings.xml:   <string name="recent_apps_panel_empty">Noch keine zuletzt verwendeten Apps</string>
values-es/strings.xml:   <string name="recent_apps_panel_empty">Aún no hay aplicaciones recientes</string>
values-fr/strings.xml:   <string name="recent_apps_panel_empty">Aucune application récente pour l'instant</string>
values-hi/strings.xml:   <string name="recent_apps_panel_empty">अभी तक कोई हालिया ऐप नहीं</string>
values-in/strings.xml:   <string name="recent_apps_panel_empty">Belum ada aplikasi terbaru</string>
values-it/strings.xml:   <string name="recent_apps_panel_empty">Nessuna app recente</string>
values-ja/strings.xml:   <string name="recent_apps_panel_empty">最近使用したアプリはまだありません</string>
values-km/strings.xml:   <string name="recent_apps_panel_empty">មិនទាន់មានកម្មវិធីថ្មីៗនៅឡើយទេ</string>
values-ko/strings.xml:   <string name="recent_apps_panel_empty">아직 최근 사용한 앱이 없습니다</string>
values-lo/strings.xml:   <string name="recent_apps_panel_empty">ຍັງບໍ່ມີແອັບລ່າສຸດ</string>
values-pt/strings.xml:   <string name="recent_apps_panel_empty">Ainda não há aplicativos recentes</string>
values-ru/strings.xml:   <string name="recent_apps_panel_empty">Пока нет недавних приложений</string>
values-th/strings.xml:   <string name="recent_apps_panel_empty">ยังไม่มีแอปล่าสุด</string>
values-vi/strings.xml:   <string name="recent_apps_panel_empty">Chưa có ứng dụng nào gần đây</string>
values-zh/strings.xml:   <string name="recent_apps_panel_empty">暂无最近使用的应用</string>
```

- [ ] **Step 2: Write the layout**

Create `app/src/main/res/layout/bottom_sheet_recent_apps.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingBottom="16dp"
    android:background="@drawable/bg_bottom_sheet_rounded">

    <View
        android:layout_width="40dp"
        android:layout_height="4dp"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="12dp"
        android:layout_marginBottom="16dp"
        android:background="@drawable/bg_drag_handle" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:layout_marginBottom="8dp"
        android:text="@string/recent_apps"
        android:textAppearance="?attr/textAppearanceTitleLarge"
        android:textColor="?attr/colorOnSurface"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvRecentAppsEmpty"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:layout_marginBottom="24dp"
        android:gravity="center"
        android:text="@string/recent_apps_panel_empty"
        android:textAppearance="?attr/textAppearanceBodyMedium"
        android:textColor="?attr/colorOnSurfaceVariant"
        android:visibility="gone"
        tools:visibility="visible" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvRecentApps"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="480dp"
        android:clipToPadding="false" />

</LinearLayout>
```

- [ ] **Step 3: Write the failing widget test**

Create `app/src/androidTest/java/com/mckimquyen/ui/RecentAppsPanelWidgetTest.kt`:

```kotlin
package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: proves the panel resolves the real recent list against the real app snapshot, shows
 * the empty state when there's nothing to show, launches on tap, and removes-in-place on the
 * "Remove from recent" action - all against real ActHome/RAppsSingleton/SearchHistoryStore state,
 * not fakes. No Espresso (see SearchResultAdapterWidgetTest's class docstring for why).
 */
@RunWith(AndroidJUnit4::class)
class RecentAppsPanelWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = SearchHistoryStore(context)

    @Before
    fun setUp() {
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    @After
    fun tearDown() {
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    @Test
    fun emptyRecentList_showsEmptyStateNotTheList() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = RecentAppsPanelFragment()
                fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                val view = requireNotNull(fragment.view)
                assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility)
                assertEquals(View.GONE, view.findViewById<RecyclerView>(R.id.rvRecentApps).visibility)
            }
        }
    }

    @Test
    fun populatedRecentList_showsRealAppsInMostRecentFirstOrder() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera, notes)
        store.recordLaunch(AppSearchEngine.componentKey(camera))
        store.recordLaunch(AppSearchEngine.componentKey(notes))

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = RecentAppsPanelFragment()
                fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                val view = requireNotNull(fragment.view)
                assertEquals(View.GONE, view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility)
                val recyclerView = view.findViewById<RecyclerView>(R.id.rvRecentApps)
                assertEquals(View.VISIBLE, recyclerView.visibility)
                assertEquals(2, recyclerView.adapter?.itemCount)

                val firstRow = recyclerView.findViewHolderForAdapterPosition(0)!!.itemView
                assertEquals(
                    "Notes",
                    firstRow.findViewById<TextView>(R.id.tvSearchResultLabel).text.toString()
                )
            }
        }
    }

    @Test
    fun removingAnEntry_updatesTheListLiveWithoutClosingTheSheet() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera, notes)
        store.recordLaunch(AppSearchEngine.componentKey(camera))
        store.recordLaunch(AppSearchEngine.componentKey(notes))

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = RecentAppsPanelFragment()
                fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                val recyclerView = requireNotNull(fragment.view).findViewById<RecyclerView>(R.id.rvRecentApps)
                val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()
                val resultHolder = holder as com.mckimquyen.search.SearchResultAdapter.ResultViewHolder
                val handled = resultHolder.handleMenuAction(R.id.menuItemRemoveFromRecent, notes, resultHolder.itemView)
                assertEquals(true, handled)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                assertEquals(1, recyclerView.adapter?.itemCount)
                assertEquals(false, fragment.isRemoving)
                assertEquals(listOf(AppSearchEngine.componentKey(camera)), store.recentKeys())
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.RecentAppsPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL to compile — `RecentAppsPanelFragment` doesn't exist yet.

- [ ] **Step 5: Implement the fragment**

Create `app/src/main/java/com/mckimquyen/ui/RecentAppsPanelFragment.kt`:

```kotlin
package com.mckimquyen.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.RecentAppsPanelResolver
import com.mckimquyen.search.SearchHistoryStore
import com.mckimquyen.search.SearchResultAdapter
import com.mckimquyen.util.UtilApp

/**
 * FEAT-009: quick access to recently-launched apps, reachable from a SearchBar icon and from the
 * lens-management menu. Reuses SearchResultAdapter as-is (icon loading, shortcuts, DiffUtil, the
 * long-press action menu) - the only new wiring is the Remove-from-recent callback.
 */
class RecentAppsPanelFragment : BottomSheetDialogFragment() {

    private var adapter: SearchResultAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var emptyState: TextView? = null
    private var historyStore: SearchHistoryStore? = null

    override fun getTheme(): Int = R.style.TransBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_recent_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val store = SearchHistoryStore(context)
        historyStore = store

        val rv = view.findViewById<RecyclerView>(R.id.rvRecentApps)
        val empty = view.findViewById<TextView>(R.id.tvRecentAppsEmpty)
        recyclerView = rv
        emptyState = empty

        val newAdapter = SearchResultAdapter(
            onAppClick = { app, source -> launchAndDismiss(app, source, store) },
            onRemoveFromRecent = { app ->
                store.removeKey(AppSearchEngine.componentKey(app))
                refreshList()
            }
        )
        adapter = newAdapter
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = newAdapter

        refreshList()
    }

    private fun launchAndDismiss(app: App, source: View, store: SearchHistoryStore) {
        store.recordLaunch(AppSearchEngine.componentKey(app))
        UtilApp.launchComponent(
            requireContext(),
            app.packageName.toString(),
            app.label.toString(),
            app.name.toString(),
            source,
            Rect(0, 0, source.width, source.height)
        )
        dismiss()
    }

    private fun refreshList() {
        val store = historyStore ?: return
        val resolved = RecentAppsPanelResolver.resolve(store.recentKeys(), RAppsSingleton.instance.apps.orEmpty())
        adapter?.submitList(resolved)
        recyclerView?.visibility = if (resolved.isEmpty()) View.GONE else View.VISIBLE
        emptyState?.visibility = if (resolved.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        adapter = null
        recyclerView = null
        emptyState = null
        historyStore = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "RecentAppsPanelFragment"
    }
}
```

- [ ] **Step 6: Run the tests again to verify they pass**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.RecentAppsPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (3 tests)`, all passing.

- [ ] **Step 7: Run the localization unit test**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.a11y.AllStringsTranslationTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/mckimquyen/ui/RecentAppsPanelFragment.kt app/src/main/res/layout/bottom_sheet_recent_apps.xml app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/ui/RecentAppsPanelWidgetTest.kt
git commit -m "feat(feat-009): add RecentAppsPanelFragment bottom sheet"
```

---

### Task 6: `ActHome` — SearchBar icon + lens-menu entry point

**Files:**
- Create: `app/src/main/res/menu/menu_search_bar.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Modify: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt`
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeSearchBarRecentPanelWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 5): `RecentAppsPanelFragment`, `RecentAppsPanelFragment.TAG`.
- Consumes (Task 2): `UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED`.
- Produces: `ActHome.onLensMenuItemSelected(int, int)` handles a new item id `6`;
  `ActHome.updateRecentAppsPanelIconVisibility()` (`@VisibleForTesting`); `R.id.menuItemRecentAppsPanel`.

- [ ] **Step 1: Write the menu resource**

Create `app/src/main/res/menu/menu_search_bar.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/menuItemRecentAppsPanel"
        android:icon="@drawable/ic_history_24dp"
        android:title="@string/recent_apps"
        app:showAsAction="always" />

</menu>
```

- [ ] **Step 2: Write the failing tests**

Add to `app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt` — a new
item id constant next to the existing ones, and a new test:

```kotlin
    private val itemRecentAppsPanel = 6
```

```kotlin
    @Test
    fun selectingRecentAppsPanelItem_showsThePanel() {
        seedSecondLens()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            openMenuAndSelect(scenario, itemRecentAppsPanel, 0)

            scenario.onActivity { activity ->
                assertNotNull(
                    "The lens menu's Recent apps entry must open the panel",
                    activity.supportFragmentManager.findFragmentByTag(RecentAppsPanelFragment.TAG)
                )
            }
        }
    }
```

Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeSearchBarRecentPanelWidgetTest.kt`:

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: the SearchBar's recent-apps icon exists, is tap-reachable, and honors its own
 * settings toggle - independent of whether the whole SearchBar itself is shown (KEY_SHOW_SEARCH_BAR
 * is a separate, coarser toggle covered by existing tests).
 */
@RunWith(AndroidJUnit4::class)
class ActHomeSearchBarRecentPanelWidgetTest {

    private val settings get() = UtilSettings(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before
    fun setUp() {
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, true)
    }

    @After
    fun tearDown() {
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
    }

    @Test
    fun iconIsVisibleByDefaultAndOpensThePanelOnTap() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<com.google.android.material.search.SearchBar>(R.id.searchBar)
                val item = searchBar.menu.findItem(R.id.menuItemRecentAppsPanel)
                assertEquals(true, item.isVisible)

                searchBar.menu.performIdentifierAction(R.id.menuItemRecentAppsPanel, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotNull(
                    "Tapping the SearchBar icon must open the panel",
                    activity.supportFragmentManager.findFragmentByTag(RecentAppsPanelFragment.TAG)
                )
            }
        }
    }

    @Test
    fun disablingTheSettingHidesTheIconOnNextResume() {
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<com.google.android.material.search.SearchBar>(R.id.searchBar)
                val item = searchBar.menu.findItem(R.id.menuItemRecentAppsPanel)
                assertEquals(false, item.isVisible)
            }
        }
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeSearchBarRecentPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL — `R.id.menuItemRecentAppsPanel` unresolved, item id `6` not handled by
`onLensMenuItemSelected` (returns `false`, no fragment shown).

- [ ] **Step 4: Implement**

In `ActHome.java`, add the import and a field-less reuse of `RecentAppsPanelFragment` via a new
private method. Add near `private void showLensManagementMenu(View anchor)`:

```java
    private void showRecentAppsPanel() {
        new RecentAppsPanelFragment().show(getSupportFragmentManager(), RecentAppsPanelFragment.TAG);
    }
```

Extend `showLensManagementMenu` (add the 6th item, right after the `share_image` line):

```java
        menu.getMenu().add(0, 5, 0, R.string.lens_share_image);
        menu.getMenu().add(0, 6, 0, R.string.recent_apps);
        menu.setOnMenuItemClickListener(item -> onLensMenuItemSelected(item.getItemId(), position));
```

Extend `onLensMenuItemSelected` (add the new branch before the final `return false;`):

```java
        } else if (itemId == 6) {
            showRecentAppsPanel();
            return true;
        }
```

In `setupViews()`, directly below `searchView.setupWithSearchBar(searchBar);`:

```java
        searchBar.inflateMenu(R.menu.menu_search_bar);
        searchBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menuItemRecentAppsPanel) {
                showRecentAppsPanel();
                return true;
            }
            return false;
        });
```

Extend `updateSearchBarVisibility()`:

```java
    private void updateSearchBarVisibility() {
        boolean showSearchBar = new UtilSettings(this).getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR);
        searchBar.setVisibility(showSearchBar ? View.VISIBLE : View.GONE);
        if (!showSearchBar && searchView.isShowing()) {
            searchView.hide();
        }
        updateRecentAppsPanelIconVisibility();
    }

    /** FEAT-009: the icon has its own, finer-grained toggle than the whole SearchBar. */
    @androidx.annotation.VisibleForTesting
    void updateRecentAppsPanelIconVisibility() {
        boolean enabled = new UtilSettings(this).getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED);
        android.view.MenuItem item = searchBar.getMenu().findItem(R.id.menuItemRecentAppsPanel);
        if (item != null) {
            item.setVisible(enabled);
        }
    }
```

Add the import near the other `com.mckimquyen.ui`-adjacent imports (or use the fully-qualified
name inline as done above for `RecentAppsPanelFragment` — both files are already in package
`com.mckimquyen.ui`, so **no import is actually needed**; skip this line, `RecentAppsPanelFragment`
resolves directly).

- [ ] **Step 5: Run the tests again to verify they pass**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeSearchBarRecentPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeLensManagementWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: both `OK`, including all pre-existing `ActHomeLensManagementWidgetTest` cases (no
regression on items 1-5).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/menu/menu_search_bar.xml app/src/main/java/com/mckimquyen/ui/ActHome.java app/src/androidTest/java/com/mckimquyen/ui/ActHomeLensManagementWidgetTest.kt app/src/androidTest/java/com/mckimquyen/ui/ActHomeSearchBarRecentPanelWidgetTest.kt
git commit -m "feat(feat-009): add SearchBar recent-apps icon and lens-menu entry point"
```

---

### Task 7: `FrmSettings` — toggle for the SearchBar icon

**Files:**
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Modify: `app/src/main/res/values/strings.xml` (+ all 16 locales)
- Test: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsRecentAppsPanelWidgetTest.kt` (new)

**Interfaces:**
- Consumes (Task 2): `UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED`,
  `UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED`.
- Produces: `R.id.swRecentAppsPanel`, `R.id.rlSwitchRecentAppsPanelParent`.

- [ ] **Step 1: Add the setting-label string**

`app/src/main/res/values/strings.xml`, next to `setting_show_search_bar`:

```xml
    <string name="setting_show_recent_apps_icon">Show recent apps icon</string>
```

All 16 locales:

```
values-ar/strings.xml:   <string name="setting_show_recent_apps_icon">إظهار أيقونة التطبيقات الأخيرة</string>
values-de/strings.xml:   <string name="setting_show_recent_apps_icon">Symbol für zuletzt verwendete Apps anzeigen</string>
values-es/strings.xml:   <string name="setting_show_recent_apps_icon">Mostrar icono de apps recientes</string>
values-fr/strings.xml:   <string name="setting_show_recent_apps_icon">Afficher l'icône des applications récentes</string>
values-hi/strings.xml:   <string name="setting_show_recent_apps_icon">हाल के ऐप्स आइकन दिखाएं</string>
values-in/strings.xml:   <string name="setting_show_recent_apps_icon">Tampilkan ikon aplikasi terbaru</string>
values-it/strings.xml:   <string name="setting_show_recent_apps_icon">Mostra icona app recenti</string>
values-ja/strings.xml:   <string name="setting_show_recent_apps_icon">最近使用したアプリのアイコンを表示</string>
values-km/strings.xml:   <string name="setting_show_recent_apps_icon">បង្ហាញរូបតំណាងកម្មវិធីថ្មីៗ</string>
values-ko/strings.xml:   <string name="setting_show_recent_apps_icon">최근 앱 아이콘 표시</string>
values-lo/strings.xml:   <string name="setting_show_recent_apps_icon">ສະແດງໄອຄອນແອັບລ່າສຸດ</string>
values-pt/strings.xml:   <string name="setting_show_recent_apps_icon">Mostrar ícone de apps recentes</string>
values-ru/strings.xml:   <string name="setting_show_recent_apps_icon">Показывать значок недавних приложений</string>
values-th/strings.xml:   <string name="setting_show_recent_apps_icon">แสดงไอคอนแอปล่าสุด</string>
values-vi/strings.xml:   <string name="setting_show_recent_apps_icon">Hiện biểu tượng ứng dụng gần đây</string>
values-zh/strings.xml:   <string name="setting_show_recent_apps_icon">显示最近使用应用图标</string>
```

- [ ] **Step 2: Write the failing widget test**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsRecentAppsPanelWidgetTest.kt`
(copying `FrmSettingsDepthOfFieldWidgetTest`'s exact structure, default `true` instead of `false`):

```kotlin
package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009 widget proof for the SearchBar recent-apps-icon toggle:
 * 1. Switch is on by default.
 * 2. Pre-existing false state is reflected in the switch.
 * 3. Toggling the switch persists the new value.
 * 4. Reset to defaults turns it back on.
 */
@RunWith(AndroidJUnit4::class)
class FrmSettingsRecentAppsPanelWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() {
        rawPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun switchIsOn_whenSettingHasNeverBeenSaved() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swRecentAppsPanel)
                assertTrue(sw.isChecked)
            }
        }
    }

    @Test
    fun switchIsOff_whenSettingWasPreviouslySavedFalse() {
        UtilSettings(context).save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swRecentAppsPanel)
                assertFalse(sw.isChecked)
            }
        }
    }

    @Test
    fun togglingTheSwitch_persistsTheNewValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swRecentAppsPanel)
                sw.isChecked = false
            }
        }
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }

    @Test
    fun resetToDefaults_turnsItBackOn() {
        UtilSettings(context).save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                (fragment as com.mckimquyen.itf.SettingsInterface).onDefaultsReset()
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swRecentAppsPanel)
                assertTrue(sw.isChecked)
            }
        }
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsRecentAppsPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL to compile — `R.id.swRecentAppsPanel` doesn't exist yet.

- [ ] **Step 4: Implement**

In `app/src/main/res/layout/frm_settings.xml`, directly below the closing
`</RelativeLayout>` of `rlSwitchShowSearchBarParent` and its following `MaterialDivider` (copy
that exact divider + a new row, following the exact same structure):

```xml
                <com.google.android.material.divider.MaterialDivider
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="52dp"
                    app:dividerColor="?attr/colorOutlineVariant" />

                <!-- FEAT-009: Show Recent Apps Icon on the home SearchBar -->
                <RelativeLayout
                    android:id="@+id/rlSwitchRecentAppsPanelParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">

                    <ImageView
                        android:id="@+id/iconRecentAppsPanel"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_history_24dp" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swRecentAppsPanel"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconRecentAppsPanel"
                        android:paddingTop="12dp"
                        android:paddingBottom="12dp"
                        android:text="@string/setting_show_recent_apps_icon"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />

                </RelativeLayout>
```

In `FrmSettings.kt`, next to `swShowSearchBar`:

```kotlin
    private var swRecentAppsPanel: SwitchCompat? = null
```

Next to `swShowSearchBar = view.findViewById(R.id.swShowSearchBar)`:

```kotlin
        swRecentAppsPanel = view.findViewById(R.id.swRecentAppsPanel)
```

Next to `view.findViewById<View>(R.id.rlSwitchShowSearchBarParent).setOnClickListener(null)`:

```kotlin
        view.findViewById<View>(R.id.rlSwitchRecentAppsPanelParent).setOnClickListener(null)
```

Next to the `swShowSearchBar` check-changed block:

```kotlin
        swRecentAppsPanel?.setOnCheckedChangeListener { _, isChecked ->
            utilSettings?.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, isChecked)
        }
```

Next to the `swShowSearchBar` read-back line (in `assignValues()`/`onResume`):

```kotlin
            swRecentAppsPanel?.isChecked = us.getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED)
```

Next to the `KEY_SHOW_SEARCH_BAR` line in `resetToDefault()`:

```kotlin
            us.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
```

Next to `swShowSearchBar = null` (the nulling-out in `onDestroyView()`):

```kotlin
        swRecentAppsPanel = null
```

- [ ] **Step 5: Run the test again to verify it passes**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsRecentAppsPanelWidgetTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK (4 tests)`.

- [ ] **Step 6: Run the localization unit test**

```bash
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.a11y.AllStringsTranslationTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/frm_settings.xml app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/main/res/values*/strings.xml app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsRecentAppsPanelWidgetTest.kt
git commit -m "feat(feat-009): add Settings toggle for the SearchBar recent-apps icon"
```

---

### Task 8: Cross-lens integration test, full regression, lint, device smoke

**Files:**
- Test: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeMultiLensRecentAppsIntegrationTest.kt` (new)

**Interfaces:**
- Consumes (all prior tasks): the complete feature, exercised end-to-end across two lenses.

- [ ] **Step 1: Write the failing integration test**

Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeMultiLensRecentAppsIntegrationTest.kt`:

```kotlin
package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: proves the recent-apps list is genuinely global across lenses at runtime, not merely
 * by SharedPreferences-key-naming convention - a launch recorded while lens A is active must show
 * up in a panel opened from lens B.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeMultiLensRecentAppsIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = SearchHistoryStore(context)

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertOrUpdate(LensWorkspace(id = "second", name = "Second Lens", orderIndex = 1))
        store.clear()
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    @Test
    fun launchRecordedOnOneLensAppearsInThePanelOpenedFromAnotherLens() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // Lens A (default, page 0) is active on cold launch. Record a launch as if it
            // happened from lens A - SearchHistoryStore has no lensId parameter, matching its
            // global-by-design scope.
            store.recordLaunch(AppSearchEngine.componentKey(camera))

            scenario.onActivity { activity ->
                activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager).currentItem = 1
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val fragment = RecentAppsPanelFragment()
                fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager
                    .findFragmentByTag(RecentAppsPanelFragment.TAG) as RecentAppsPanelFragment
                val recyclerView = requireNotNull(fragment.view).findViewById<RecyclerView>(R.id.rvRecentApps)
                assertEquals(1, recyclerView.adapter?.itemCount)
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s <locked-device-serial> install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <locked-device-serial> install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s <locked-device-serial> shell am instrument -w -e class com.mckimquyen.ui.ActHomeMultiLensRecentAppsIntegrationTest com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected at this point in the plan: this should already PASS, since Tasks 1-6 already implement
everything it exercises — if it fails, that's a real regression to fix before continuing (most
likely cause: `RAppsSingleton.instance.apps` not seeded before `ActHome`'s own background scan
overwrites it — if so, mirror the existing `TEST-003`/`AppSearchIntegrationTest` fix already in
this codebase: re-set `RAppsSingleton.instance.apps` immediately before the final assertion).

- [ ] **Step 3: Full regression pass**

```bash
./gradlew testDevDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, all unit tests pass (no failures caused by this feature).

```bash
adb -s <locked-device-serial> shell am instrument -w com.mckimquyen.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: full instrumented suite passes (aside from any already-disclosed, pre-existing,
unrelated flake documented in `doc/task/README.md` — do not treat that as new).

```bash
./gradlew lintDevDebug
```

Expected: 0 errors; warning count unchanged or lower (no new `MissingTranslation`, no new
suppressions).

- [ ] **Step 4: Device smoke test**

On the session-locked TECNO device, with the freshly-built debug APK installed:

1. Tap the new home-screen `SearchBar` icon (top-right area) — the panel opens, showing real
   recently-launched apps (most-recent-first).
2. Tap a row — the real app launches, the panel dismisses.
3. Long-press a row — the App info / Remove-from-recent menu appears; tap Remove from recent — the
   row disappears live, panel stays open; reopen the panel from scratch and confirm it stays gone.
4. Open Settings, toggle "Show recent apps icon" off — return Home, confirm the `SearchBar` icon
   is gone.
5. Long-press the empty area of the lens grid (or the dot indicator / lens name) — confirm the
   lens-management menu still opens the panel even with the icon hidden.
6. Switch to a second lens (create one if needed via the same menu), launch an app there, switch
   back to the first lens, open the panel — confirm the just-launched app appears (global, not
   per-lens).
7. Re-enable "Show recent apps icon" in Settings, confirm it reappears.

Record: device model, Android/HiOS version, build SHA, timestamp, and a short note per step
(matching this repo's existing smoke-test evidence format in `doc/task/README.md`).

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/ui/ActHomeMultiLensRecentAppsIntegrationTest.kt
git commit -m "test(feat-009): add cross-lens recent-apps integration coverage"
```

- [ ] **Step 6: Update the backlog**

Move/create `doc/task/done/p2-feat-feat-009-recent-apps-quick-panel.md` (following this repo's
existing done-story format — acceptance criteria, evidence, test counts, self-audit score) and
update `doc/task/README.md`'s Picked table (FEAT-009 row 1 → done, next up UI-024) and Done log,
per this repo's working agreement (`doc/task/README.md`'s own rules section). Commit separately:

```bash
git add doc/task/README.md doc/task/done/p2-feat-feat-009-recent-apps-quick-panel.md
git commit -m "docs(feat-009): mark recent apps quick panel done"
```
