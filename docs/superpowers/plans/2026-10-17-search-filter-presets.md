# SEARCH-009 — Bộ lọc tìm kiếm lưu sẵn (Preset Search Filters) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm thanh bộ lọc nhanh dạng Chip cuộn ngang ngay dưới ô tìm kiếm (Tất cả, Lens này, Yêu thích, Đã ẩn) cho màn hình SearchView trong ActHome, lưu preset được chọn vào SharedPreferences và cập nhật kết quả lọc tức thì.

**Architecture:** Tạo enum pure model `SearchFilterPreset` và pure filter engine `SearchFilterEngine` nhận danh sách candidate apps lọc theo preset độc lập với View framework. Mở rộng `UtilSettings` để lưu/đọc `KEY_SEARCH_FILTER_PRESET`. Thêm `HorizontalScrollView` + `ChipGroup` chứa 4 `Chip` (Material 3 FilterChip) vào `act_home.xml`. Trong `ActHome.java`, kết nối `OnCheckedStateChangeListener` với `updateSearchResults()` và khôi phục chip đã chọn khi mở lại search.

**Tech Stack:** Kotlin, Java (ActHome), AndroidX / Material 3 Components (`ChipGroup`, `Chip`), JUnit 4, Robolectric, AndroidX Test.

## Global Constraints

- Không thêm thư viện bên ngoài (dùng Material Components 1.13 sẵn có trong dự án).
- Hoạt động 100% offline, 0 network call, không cấp phát bộ nhớ trên hot path.
- Thiết bị chỉ định kiểm thử on-device duy nhất trong phiên: **TECNO BG6 `118743744X002560`**.
- Không xóa dữ liệu ứng dụng, không đổi launcher mặc định của thiết bị.
- Đầy đủ bản dịch cho 4 nhãn bộ lọc trong 17 ngôn ngữ (`res/values*/strings.xml`).
- Không commit hay push cho đến khi hoàn tất và có chỉ thị rõ ràng từ user.

---

### Task 1: Định nghĩa model `SearchFilterPreset` và test

**Files:**
- Create: `app/src/main/java/com/mckimquyen/model/SearchFilterPreset.kt`
- Create: `app/src/test/java/com/mckimquyen/model/SearchFilterPresetTest.kt`

**Interfaces:**
- Produces: `enum class SearchFilterPreset { ALL, THIS_LENS, FAVORITES, HIDDEN }`
  - `fun fromStored(value: String?): SearchFilterPreset` (fallback `ALL` an toàn)

- [ ] **Step 1: Viết test failing `SearchFilterPresetTest.kt`**

```kotlin
package com.mckimquyen.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterPresetTest {

    @Test
    fun fromStored_mapsCorrectEnumValues() {
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("ALL"))
        assertEquals(SearchFilterPreset.THIS_LENS, SearchFilterPreset.fromStored("THIS_LENS"))
        assertEquals(SearchFilterPreset.FAVORITES, SearchFilterPreset.fromStored("FAVORITES"))
        assertEquals(SearchFilterPreset.HIDDEN, SearchFilterPreset.fromStored("HIDDEN"))
    }

    @Test
    fun fromStored_fallsBackToAllOnNullOrUnknown() {
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored(null))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored(""))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("UNKNOWN_PRESET"))
        assertEquals(SearchFilterPreset.ALL, SearchFilterPreset.fromStored("all"))
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận RED (compile error / unresolved class)**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.SearchFilterPresetTest`
Expected: FAIL do chưa có class `SearchFilterPreset`.

- [ ] **Step 3: Viết minimal implementation `SearchFilterPreset.kt`**

```kotlin
package com.mckimquyen.model

enum class SearchFilterPreset {
    ALL,
    THIS_LENS,
    FAVORITES,
    HIDDEN;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): SearchFilterPreset =
            entries.firstOrNull { it.name == value } ?: ALL
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.model.SearchFilterPresetTest`
Expected: PASS (2 tests pass).

---

### Task 2: Pure Engine lọc `SearchFilterEngine` và test

**Files:**
- Create: `app/src/main/java/com/mckimquyen/search/SearchFilterEngine.kt`
- Create: `app/src/test/java/com/mckimquyen/search/SearchFilterEngineTest.kt`

**Interfaces:**
- Consumes: `com.mckimquyen.model.App`, `com.mckimquyen.model.SearchFilterPreset`, `com.mckimquyen.util.LensAppScope.identifierOf`
- Produces: `SearchFilterEngine.filterCandidates(apps: List<App>, preset: SearchFilterPreset, lensApps: List<App>?, hiddenIds: Set<String>): List<App>`

- [ ] **Step 1: Viết test failing `SearchFilterEngineTest.kt`**

```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SearchFilterEngineTest {

    private fun app(pkg: String, name: String = "Main", isFavorite: Boolean = false) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = name, isFavorite = isFavorite, isVisible = true)

    private val appA = app("com.example.a", isFavorite = true)
    private val appB = app("com.example.b", isFavorite = false)
    private val appC = app("com.example.c", isFavorite = true)
    private val allApps = listOf(appA, appB, appC)

    @Test
    fun filterCandidates_all_returnsAllApps() {
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.ALL,
            lensApps = listOf(appA),
            hiddenIds = setOf(LensAppScope.identifierOf(appB))
        )
        assertEquals(allApps, result)
        assertNotSame(allApps, result)
    }

    @Test
    fun filterCandidates_thisLens_returnsOnlyLensApps() {
        val lensApps = listOf(appA, appB)
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = lensApps,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appA, appB), result)
    }

    @Test
    fun filterCandidates_thisLens_nullOrEmptyLensAppsReturnsEmpty() {
        val resultNull = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(emptyList<App>(), resultNull)

        val resultEmpty = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.THIS_LENS,
            lensApps = emptyList(),
            hiddenIds = emptySet()
        )
        assertEquals(emptyList<App>(), resultEmpty)
    }

    @Test
    fun filterCandidates_favorites_returnsOnlyFavoriteApps() {
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.FAVORITES,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appA, appC), result)
    }

    @Test
    fun filterCandidates_hidden_returnsOnlyHiddenIdsMatchingInstalled() {
        val hiddenIds = setOf(LensAppScope.identifierOf(appB), "com.orphan-Main")
        val result = SearchFilterEngine.filterCandidates(
            apps = allApps,
            preset = SearchFilterPreset.HIDDEN,
            lensApps = null,
            hiddenIds = hiddenIds
        )
        assertEquals(listOf(appB), result)
    }

    @Test
    fun filterCandidates_preservesOrderOfInputApps() {
        val shuffled = listOf(appC, appA, appB)
        val result = SearchFilterEngine.filterCandidates(
            apps = shuffled,
            preset = SearchFilterPreset.FAVORITES,
            lensApps = null,
            hiddenIds = emptySet()
        )
        assertEquals(listOf(appC, appA), result)
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.SearchFilterEngineTest`
Expected: FAIL do chưa có class `SearchFilterEngine`.

- [ ] **Step 3: Viết minimal implementation `SearchFilterEngine.kt`**

```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope

object SearchFilterEngine {

    @JvmStatic
    fun filterCandidates(
        apps: List<App>,
        preset: SearchFilterPreset,
        lensApps: List<App>?,
        hiddenIds: Set<String>
    ): List<App> {
        return when (preset) {
            SearchFilterPreset.ALL -> ArrayList(apps)
            SearchFilterPreset.THIS_LENS -> {
                if (lensApps.isNullOrEmpty()) return emptyList()
                val lensKeys = lensApps.mapTo(HashSet(), LensAppScope::identifierOf)
                ArrayList(apps.filter { LensAppScope.identifierOf(it) in lensKeys })
            }
            SearchFilterPreset.FAVORITES -> {
                ArrayList(apps.filter { it.isFavorite })
            }
            SearchFilterPreset.HIDDEN -> {
                if (hiddenIds.isEmpty()) return emptyList()
                ArrayList(apps.filter { LensAppScope.identifierOf(it) in hiddenIds })
            }
        }
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.SearchFilterEngineTest`
Expected: PASS (6 tests pass).

---

### Task 3: Lưu / Đọc Preset trong `UtilSettings` và test

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Test: `app/src/test/java/com/mckimquyen/util/UtilSettingsSearchFilterTest.kt`

**Interfaces:**
- Produces in `UtilSettings`:
  - `const val KEY_SEARCH_FILTER_PRESET = "search_filter_preset"`
  - `fun getSearchFilterPreset(): SearchFilterPreset`
  - `fun saveSearchFilterPreset(preset: SearchFilterPreset)`

- [ ] **Step 1: Viết test failing `UtilSettingsSearchFilterTest.kt`**

```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.SearchFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsSearchFilterTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultFilterPresetIsAll() {
        assertEquals(SearchFilterPreset.ALL, settings.getSearchFilterPreset())
    }

    @Test
    fun saveAndRetrieveSearchFilterPreset() {
        settings.saveSearchFilterPreset(SearchFilterPreset.FAVORITES)
        assertEquals(SearchFilterPreset.FAVORITES, UtilSettings(context).getSearchFilterPreset())

        settings.saveSearchFilterPreset(SearchFilterPreset.THIS_LENS)
        assertEquals(SearchFilterPreset.THIS_LENS, UtilSettings(context).getSearchFilterPreset())

        settings.saveSearchFilterPreset(SearchFilterPreset.HIDDEN)
        assertEquals(SearchFilterPreset.HIDDEN, UtilSettings(context).getSearchFilterPreset())
    }

    @Test
    fun invalidStoredValueFallsBackToAll() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(UtilSettings.KEY_SEARCH_FILTER_PRESET, "CORRUPTED_VALUE")
            .commit()
        assertEquals(SearchFilterPreset.ALL, settings.getSearchFilterPreset())
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsSearchFilterTest`
Expected: FAIL do `KEY_SEARCH_FILTER_PRESET`, `getSearchFilterPreset`, `saveSearchFilterPreset` chưa có.

- [ ] **Step 3: Mở rộng `UtilSettings.kt`**

Trong `companion object` của `UtilSettings`:
```kotlin
const val KEY_SEARCH_FILTER_PRESET = "search_filter_preset"
```

Thêm hai phương thức vào `UtilSettings`:
```kotlin
fun getSearchFilterPreset(): com.mckimquyen.model.SearchFilterPreset =
    runCatching { prefs.getString(KEY_SEARCH_FILTER_PRESET, null) }
        .getOrNull()
        .let(com.mckimquyen.model.SearchFilterPreset::fromStored)

fun saveSearchFilterPreset(preset: com.mckimquyen.model.SearchFilterPreset) {
    save(KEY_SEARCH_FILTER_PRESET, preset.name)
}
```

- [ ] **Step 4: Chạy test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsSearchFilterTest`
Expected: PASS (3 tests pass).

---

### Task 4: Thêm chuỗi ngôn ngữ cho 4 Filter Chips vào 17 locales và test

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-*/strings.xml` (tất cả 16 ngôn ngữ còn lại)
- Create: `app/src/test/java/com/mckimquyen/search/SearchFilterStringsTest.kt`

**4 chuỗi mới:**
1. `search_filter_all`: English "All", Tiếng Việt "Tất cả"
2. `search_filter_this_lens`: English "This lens", Tiếng Việt "Lens này"
3. `search_filter_favorites`: English "Favorites", Tiếng Việt "Yêu thích"
4. `search_filter_hidden`: English "Hidden", Tiếng Việt "Đã ẩn"

- [ ] **Step 1: Viết test failing `SearchFilterStringsTest.kt`**

```kotlin
package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SearchFilterStringsTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val expectedKeys = setOf(
        "search_filter_all",
        "search_filter_this_lens",
        "search_filter_favorites",
        "search_filter_hidden"
    )

    private fun resDirectory(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, "src/main/res"),
            File(workingDirectory, "app/src/main/res")
        ).first { it.isDirectory }
    }

    private fun localeDirectories(): List<File> =
        resDirectory().listFiles { file -> file.isDirectory && file.name.startsWith("values") }
            .orEmpty()
            .filter { it.name == "values" || Regex("^values-[a-z]{2}(-r[A-Z]{2})?$").matches(it.name) }
            .filter { File(it, "strings.xml").isFile }
            .sortedBy { it.name }

    @Test
    fun all17LocalesContainAllFourFilterStringsNonEmpty() {
        val dirs = localeDirectories()
        assertEquals("Phải đủ 17 values/values-* thư mục locale", 17, dirs.size)
        for (dir in dirs) {
            val file = File(dir, "strings.xml")
            val doc = documentBuilder.parse(file)
            val nodes = doc.getElementsByTagName("string")
            val found = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as org.w3c.dom.Element
                val name = elem.getAttribute("name")
                if (name in expectedKeys) {
                    found[name] = elem.textContent
                }
            }
            for (key in expectedKeys) {
                assertTrue("File ${dir.name}/strings.xml thiếu key $key", found.containsKey(key))
                assertTrue("File ${dir.name}/strings.xml key $key không được rỗng", found[key]?.isNotBlank() == true)
            }
        }
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.SearchFilterStringsTest`
Expected: FAIL do chưa có 4 keys trong các file `strings.xml`.

- [ ] **Step 3: Thêm chuỗi vào `app/src/main/res/values/strings.xml` và 16 files `values-*/strings.xml`**

Thêm 4 chuỗi vào:
- `values`: All, This lens, Favorites, Hidden
- `values-vi`: Tất cả, Thấu kính này, Yêu thích, Đã ẩn
- `values-ar`: الكل, هذه العدسة, المفضلة, مخفي
- `values-de`: Alle, Diese Linse, Favoriten, Ausgeblendet
- `values-es`: Todos, Esta lente, Favoritos, Ocultos
- `values-fr`: Tout, Cette lentille, Favoris, Masqués
- `values-hi`: सभी, यह लेंस, पसंदीदा, छिपा हुआ
- `values-in`: Semua, Lensa ini, Favorit, Tersembunyi
- `values-it`: Tutti, Questo obiettivo, Preferiti, Nascosti
- `values-ja`: すべて, このレンズ, お気に入り, 非表示
- `values-km`: ទាំងអស់, កែវភ្នែកនេះ, ចំណូលចិត្ត, បានលាក់
- `values-ko`: 전체, 이 렌즈, 즐겨찾기, 숨김
- `values-lo`: ທັງໝົດ, ເລນນີ້, ທີ່ມັກ, ເຊື່ອງໄວ້
- `values-pt`: Todos, Esta lente, Favoritos, Ocultos
- `values-ru`: Все, Этот объектив, Избранное, Скрытые
- `values-th`: ทั้งหมด, เลนส์นี้, รายการโปรด, ซ่อนอยู่
- `values-zh`: 全部, 当前镜头, 收藏, 已隐藏

- [ ] **Step 4: Chạy test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.SearchFilterStringsTest`
Expected: PASS (1 test pass).

---

### Task 5: Cập nhật giao diện `act_home.xml` với Material 3 Filter Chips

**Files:**
- Modify: `app/src/main/res/layout/act_home.xml:120-135`

- [ ] **Step 1: Thêm `HorizontalScrollView` chứa `ChipGroup` ngay dưới `searchView` content root**

Thêm khối giao diện:
```xml
                <!-- SEARCH-009: horizontal preset filter chips -->
                <HorizontalScrollView
                    android:id="@+id/hsvSearchFilterChips"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:scrollbars="none"
                    android:overScrollMode="never"
                    android:paddingStart="16dp"
                    android:paddingEnd="16dp"
                    android:paddingBottom="4dp">

                    <com.google.android.material.chip.ChipGroup
                        android:id="@+id/chipGroupSearchFilters"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        app:singleLine="true"
                        app:singleSelection="true"
                        app:selectionRequired="true">

                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipFilterAll"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/search_filter_all"
                            app:chipIcon="@drawable/ic_apps_24dp"
                            app:chipIconVisible="true" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipFilterThisLens"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/search_filter_this_lens"
                            app:chipIcon="@drawable/ic_color_lens_24dp"
                            app:chipIconVisible="true" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipFilterFavorites"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/search_filter_favorites"
                            app:chipIcon="@drawable/ic_star_24dp"
                            app:chipIconVisible="true" />

                        <com.google.android.material.chip.Chip
                            android:id="@+id/chipFilterHidden"
                            style="@style/Widget.Material3.Chip.Filter"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/search_filter_hidden"
                            app:chipIcon="@drawable/ic_visibility_off_24dp"
                            app:chipIconVisible="true" />

                    </com.google.android.material.chip.ChipGroup>
                </HorizontalScrollView>
```

- [ ] **Step 2: Xác nhận layout build thành công**

Run: `./gradlew assembleDevDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 6: Tích hợp logic lọc trong `ActHome.java` và Widget Test

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeSearchFilterWidgetTest.kt`

- [ ] **Step 1: Viết widget test failing `ActHomeSearchFilterWidgetTest.kt`**

Kiểm tra:
1. Mở search -> hiển thị 4 chip lọc, chip đã lưu (mặc định ALL) được checked.
2. Chọn chip FAVORITES -> danh sách lọc chỉ trả về app favorite.
3. Chọn chip THIS_LENS -> chỉ trả về app thuộc active lens.
4. Chọn chip HIDDEN -> chỉ trả về app đang bị ẩn khỏi lens.
5. Kiểm tra lưu lựa chọn vào `UtilSettings`.

```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeSearchFilterWidgetTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val defaultLensId = LensWorkspace.DEFAULT_LENS_ID

    private val appFav = App(label = "FavApp", packageName = "com.test.fav", name = "Main", isFavorite = true, isVisible = true)
    private val appNormal = App(label = "NormalApp", packageName = "com.test.normal", name = "Main", isFavorite = false, isVisible = true)
    private val appHidden = App(label = "HiddenApp", packageName = "com.test.hidden", name = "Main", isFavorite = false, isVisible = true)

    private var originalApps: ArrayList<App>? = null

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(appFav, appNormal, appHidden)

        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, defaultLensId)
        settings.saveLensAppScope(defaultLensId, LensAppScope.SELECTED)
        settings.saveLensAppSelection(defaultLensId, setOf(LensAppScope.identifierOf(appFav), LensAppScope.identifierOf(appNormal)))
        settings.saveLensHiddenAppIds(defaultLensId, setOf(LensAppScope.identifierOf(appHidden)))
        settings.saveSearchFilterPreset(SearchFilterPreset.ALL)
    }

    @After
    fun cleanup() {
        settings.saveSearchFilterPreset(SearchFilterPreset.ALL)
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun searchChipsRenderAndSwitchFiltering() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val chipGroup = activity.findViewById<ChipGroup>(R.id.chipGroupSearchFilters)
                assertNotNull(chipGroup)

                val chipAll = activity.findViewById<Chip>(R.id.chipFilterAll)
                val chipLens = activity.findViewById<Chip>(R.id.chipFilterThisLens)
                val chipFav = activity.findViewById<Chip>(R.id.chipFilterFavorites)
                val chipHidden = activity.findViewById<Chip>(R.id.chipFilterHidden)

                assertTrue("Mặc định All chip được chọn", chipAll.isChecked)

                // Chuyển sang Favorites
                chipFav.performClick()
            }
            instrumentation.waitForIdleSync()

            assertEquals(SearchFilterPreset.FAVORITES, settings.getSearchFilterPreset())

            scenario.onActivity { activity ->
                val searchAdapter = activity.searchResultAdapterForTest
                val currentList = searchAdapter?.currentList.orEmpty()
                assertTrue(currentList.all { it.isFavorite })
                assertEquals(1, currentList.size)
                assertEquals("FavApp", currentList.first().label)

                // Chuyển sang Hidden
                activity.findViewById<Chip>(R.id.chipFilterHidden).performClick()
            }
            instrumentation.waitForIdleSync()

            assertEquals(SearchFilterPreset.HIDDEN, settings.getSearchFilterPreset())

            scenario.onActivity { activity ->
                val searchAdapter = activity.searchResultAdapterForTest
                val currentList = searchAdapter?.currentList.orEmpty()
                assertEquals(1, currentList.size)
                assertEquals("HiddenApp", currentList.first().label)
            }
        }
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run:
```bash
./gradlew assembleDevDebugAndroidTest
adb -s 118743744X002560 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 118743744X002560 shell am instrument -w -e class com.mckimquyen.ui.ActHomeSearchFilterWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: FAIL do `ActHome` chưa wire `chipGroupSearchFilters`.

- [ ] **Step 3: Tích hợp filter trong `ActHome.java`**

1. Khai báo biến và helper trong `ActHome.java`:
   - `private ChipGroup chipGroupSearchFilters;`
   - Expose `@VisibleForTesting SearchResultAdapter searchResultAdapterForTest;`
2. Trong `setupSearch()`:
   - Ánh xạ `chipGroupSearchFilters = findViewById(R.id.chipGroupSearchFilters);`
   - Khôi phục checked chip từ `utilSettings.getSearchFilterPreset()`:
     - `ALL` -> `chipFilterAll`
     - `THIS_LENS` -> `chipFilterThisLens`
     - `FAVORITES` -> `chipFilterFavorites`
     - `HIDDEN` -> `chipFilterHidden`
   - Đặt listener `setOnCheckedStateChangeListener`:
     ```java
     chipGroupSearchFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
         if (checkedIds.isEmpty()) return;
         int checkedId = checkedIds.get(0);
         SearchFilterPreset preset = SearchFilterPreset.ALL;
         if (checkedId == R.id.chipFilterThisLens) preset = SearchFilterPreset.THIS_LENS;
         else if (checkedId == R.id.chipFilterFavorites) preset = SearchFilterPreset.FAVORITES;
         else if (checkedId == R.id.chipFilterHidden) preset = SearchFilterPreset.HIDDEN;
         utilSettings.saveSearchFilterPreset(preset);
         updateSearchResults(appSearch.getText());
     });
     ```
3. Trong `updateSearchResults(CharSequence query)`:
   - Lọc candidate apps trước khi truyền vào `AppSearchEngine`:
     ```java
     ArrayList<App> allVisible = listApp == null ? new ArrayList<>() : listApp;
     SearchFilterPreset preset = utilSettings.getSearchFilterPreset();
     String currentLensId = currentLenses != null && !currentLenses.isEmpty()
             ? currentLenses.get(Math.min(lensPager.getCurrentItem(), currentLenses.size() - 1)).getId()
             : LensWorkspace.DEFAULT_LENS_ID;
     List<App> lensApps = scopedApps(currentLensId);
     Set<String> hiddenIds = utilSettings.getLensHiddenAppIds(currentLensId);
     List<App> filteredCandidates = SearchFilterEngine.filterCandidates(allVisible, preset, lensApps, hiddenIds);
     List<App> engineResults = AppSearchEngine.search(filteredCandidates, query, searchHistoryStore.recentKeys());
     ```
4. Khi query rỗng và fallback all apps:
   - Dùng `sortedByLabel(filteredCandidates)` thay vì toàn bộ `listApp`.

- [ ] **Step 4: Chạy widget test để xác nhận GREEN**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 118743744X002560 install -r app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk
adb -s 118743744X002560 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 118743744X002560 shell am instrument -w -e class com.mckimquyen.ui.ActHomeSearchFilterWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS (`OK (1 test)`).

---

### Task 7: Full Regression, On-Device Smoke và Audit

**Scope:**
- Chạy toàn bộ Unit Tests + Lint.
- Chạy các test instrumentation liên quan và full regression trên **TECNO BG6 `118743744X002560`**.
- Smoke tay bằng UI thật (kiểm tra không có ad che UI).
- Cập nhật tài liệu `doc/task/README.md`, `doc/feature.md`, và tạo file audit score.

- [ ] **Step 1: Chạy Full JVM Unit Tests + Android Lint**

Run: `./gradlew testDevDebugUnitTest lintDevDebug`
Expected: 880+ unit tests pass, 0 lint errors, warnings <= 27.

- [ ] **Step 2: Chạy Instrumentation Tests trên TECNO BG6 `118743744X002560`**

Run:
```bash
adb -s 118743744X002560 shell am instrument -w -e class com.mckimquyen.ui.ActHomeSearchFilterWidgetTest,com.mckimquyen.ui.ActHomeTemporaryHidingWidgetTest,com.mckimquyen.ui.AppSearchWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: Tất cả tests pass.

- [ ] **Step 3: Smoke test tay trên thiết bị thật**

1. Mở app trên TECNO BG6: `adb -s 118743744X002560 shell am start -W -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome`
2. Mở Search bằng cách vuốt xuống từ đỉnh hoặc chạm search bar.
3. Chụp màn hình kiểm tra không có quảng cáo.
4. Kiểm tra thanh cuộn ngang chứa 4 Chip: `All`, `This lens`, `Favorites`, `Hidden`.
5. Bấm chọn `Favorites` -> kiểm tra danh sách app thay đổi tương ứng.
6. Đóng search và mở lại -> xác nhận chip `Favorites` vẫn được chọn.
7. Chọn lại `All`, đóng search.

- [ ] **Step 4: Chạy kiểm tra secrets và diff**

Run:
```bash
bash scripts/check-secrets.sh
git diff --check
```
Expected: Secret check passed, không có lỗi whitespace/trailing newline.

- [ ] **Step 5: Viết audit doc `doc/task/AUDIT_SEARCH-009_YYYY-MM-DD.md` và cập nhật `doc/feature.md`**

Đánh giá chấm điểm theo rubric 10 điểm (yêu cầu > 9.0/10).
Chưa commit/push cho đến khi có xác nhận từ user.
