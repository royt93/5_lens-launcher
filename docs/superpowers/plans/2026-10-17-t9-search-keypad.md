# SEARCH-010 — Bàn phím số T9 trong SearchView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm bàn phím số 12 nút (T9 Dialpad) cố định ở đáy màn hình `SearchView`, hỗ trợ tìm kiếm app nhanh bằng một tay theo dãy số tương ứng chữ cái (chuẩn ITU E.161), có tùy chọn Bật/Tắt trong Cài đặt (mặc định Tắt).

**Architecture:**
- Pure engine `T9SearchEngine`: chuẩn hóa chuỗi, ánh xạ ký tự Latin/bỏ dấu tiếng Việt sang số `0-9`, đối chiếu và xếp hạng ứng viên theo độ khớp (Prefix > Word Prefix > Substring) và metadata (isFavorite, openCount).
- Giao diện `T9KeypadView`: bố cục 12 phím (4x3) Material 3 đặt ở đáy `SearchView` trong `act_home.xml`, tương tác với `appSearch` EditText, có haptic nhẹ khi nhấn.
- Cài đặt `UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD`: toggle trong `FrmSettings` (tab Settings), quản lý hiển thị T9 pad hoặc bàn phím hệ thống.

**Tech Stack:** Kotlin, Android Canvas/Views, Material 3, SharedPreferences, Robolectric, JUnit4.

## Global Constraints

- Thiết bị khóa duy nhất: TECNO KJ7 `115333744A005844`.
- Không chạy Gradle `connected*`; chạy instrumentation trực tiếp bằng `adb -s 115333744A005844 shell am instrument`.
- TDD bắt buộc: test fail trước mỗi thay đổi production.
- Tuyệt đối không xóa DB Room hay gọi `clear()` toàn bộ SharedPreferences trong test setup/teardown; luôn sao lưu và khôi phục đúng key được kiểm thử.
- R4: Dừng ngay nếu phát hiện quảng cáo che màn hình khi smoke test.
- R5: Null-safety, không force unwrap (`!`), dọn sạch view/listener ở `onDestroyView()`.
- 17 locales đầy đủ cho mọi chuỗi mới.
- Không commit/push cho đến khi có chỉ thị từ người dùng sau khi audit >9.0/10.

---

### Task 1: Pure Engine `T9SearchEngine`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/search/T9SearchEngine.kt`
- Create: `app/src/test/java/com/mckimquyen/search/T9SearchEngineTest.kt`

**Interfaces:**
- Produces: `T9SearchEngine.toT9Digits(text: CharSequence?): String`
- Produces: `T9SearchEngine.search(candidates: List<App>, query: CharSequence?): List<App>`
- Mapping:
  - `2`: a, b, c, 2
  - `3`: d, e, f, 3
  - `4`: g, h, i, 4
  - `5`: j, k, l, 5
  - `6`: m, n, o, 6
  - `7`: p, q, r, s, 7
  - `8`: t, u, v, 8
  - `9`: w, x, y, z, 9
  - `0`: khoảng trắng ' ', số 0
  - `1`: ký tự khác, dấu câu, số 1
- Ranking:
  - Prefix match (score = 1)
  - Word prefix match (score = 2)
  - Substring match (score = 3)
  - Tie-break: isFavorite desc -> openCount desc -> label asc

- [ ] **Step 1: Write failing unit tests**

Create `app/src/test/java/com/mckimquyen/search/T9SearchEngineTest.kt`:
```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SearchEngineTest {

    @Test
    fun toT9Digits_mapsStandardLatinAndVietnamese() {
        assertEquals("247663", T9SearchEngine.toT9Digits("Chrome"))
        assertEquals("3664", T9SearchEngine.toT9Digits("Đồng"))
        assertEquals("4664530247663", T9SearchEngine.toT9Digits("Google Chrome"))
        assertEquals("22233344455566677778889999", T9SearchEngine.toT9Digits("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("0", T9SearchEngine.toT9Digits(" "))
        assertEquals("1", T9SearchEngine.toT9Digits("-"))
    }

    @Test
    fun search_matchesPrefixBeforeWordPrefixBeforeSubstring() {
        val appChrome = App(label = "Chrome", packageName = "com.android.chrome", name = "Main")
        val appGoogleChrome = App(label = "Google Chrome", packageName = "com.google.chrome", name = "Main")
        val appMonochrome = App(label = "Monochrome", packageName = "com.test.mono", name = "Main")
        val appOther = App(label = "YouTube", packageName = "com.google.yt", name = "Main")

        val candidates = listOf(appOther, appMonochrome, appGoogleChrome, appChrome)

        // Query "247" maps to "chr"
        val results = T9SearchEngine.search(candidates, "247")

        assertEquals(3, results.size)
        assertEquals("Chrome", results[0].label) // Prefix match
        assertEquals("Google Chrome", results[1].label) // Word prefix match
        assertEquals("Monochrome", results[2].label) // Substring match
    }

    @Test
    fun search_blankQueryReturnsEmptyList() {
        val app = App(label = "Chrome", packageName = "com.android.chrome", name = "Main")
        assertTrue(T9SearchEngine.search(listOf(app), "").isEmpty())
        assertTrue(T9SearchEngine.search(listOf(app), null).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.T9SearchEngineTest`
Expected: FAIL (unresolved reference: `T9SearchEngine`).

- [ ] **Step 3: Implement minimal code in `T9SearchEngine.kt`**

Create `app/src/main/java/com/mckimquyen/search/T9SearchEngine.kt`:
```kotlin
package com.mckimquyen.search

import com.mckimquyen.model.App

object T9SearchEngine {

    @JvmStatic
    fun charToT9Digit(c: Char): Char = when (c) {
        in 'a'..'c', '2' -> '2'
        in 'd'..'f', '3' -> '3'
        in 'g'..'i', '4' -> '4'
        in 'j'..'l', '5' -> '5'
        in 'm'..'o', '6' -> '6'
        in 'p'..'s', '7' -> '7'
        in 't'..'v', '8' -> '8'
        in 'w'..'z', '9' -> '9'
        ' ', '0' -> '0'
        else -> '1'
    }

    @JvmStatic
    fun toT9Digits(text: CharSequence?): String {
        if (text.isNullOrEmpty()) return ""
        val normalized = AppSearchEngine.normalize(text)
        val sb = StringBuilder(normalized.length)
        for (i in 0 until normalized.length) {
            sb.append(charToT9Digit(normalized[i]))
        }
        return sb.toString()
    }

    @JvmStatic
    fun search(candidates: List<App>, query: CharSequence?): List<App> {
        val q = query?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return emptyList()

        data class MatchResult(val app: App, val matchTier: Int)

        val matches = mutableListOf<MatchResult>()
        for (app in candidates) {
            if (!app.isVisible) continue
            val digits = toT9Digits(app.label)
            val tier = when {
                digits.startsWith(q) -> 1 // Prefix
                digits.split('0').any { it.startsWith(q) } -> 2 // Word prefix
                digits.contains(q) -> 3 // Substring
                else -> 0
            }
            if (tier > 0) {
                matches.add(MatchResult(app, tier))
            }
        }

        return matches.sortedWith(
            compareBy<MatchResult> { it.matchTier }
                .thenByDescending { it.app.isFavorite }
                .thenByDescending { it.app.openCount }
                .thenBy { AppSearchEngine.normalize(it.app.label) }
                .thenBy { it.app.packageName }
        ).map { it.app }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.T9SearchEngineTest`
Expected: PASS.

---

### Task 2: Persistence in `UtilSettings`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsT9Test.kt`

**Interfaces:**
- Produces: `UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD` ("enable_t9_search_keypad")
- Produces: `UtilSettings.DEFAULT_ENABLE_T9_SEARCH_KEYPAD` (false)
- Produces: `getEnableT9SearchKeypad(): Boolean`, `saveEnableT9SearchKeypad(value: Boolean)`

- [ ] **Step 1: Write failing unit test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsT9Test.kt`:
```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsT9Test {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultEnableT9SearchKeypadIsFalse() {
        assertFalse(settings.getEnableT9SearchKeypad())
    }

    @Test
    fun saveAndRetrieveEnableT9SearchKeypad() {
        settings.saveEnableT9SearchKeypad(true)
        assertTrue(UtilSettings(context).getEnableT9SearchKeypad())

        settings.saveEnableT9SearchKeypad(false)
        assertFalse(UtilSettings(context).getEnableT9SearchKeypad())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsT9Test`
Expected: FAIL (unresolved reference: `getEnableT9SearchKeypad`).

- [ ] **Step 3: Implement minimal code in `UtilSettings.kt`**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`:
Add constants:
```kotlin
const val KEY_ENABLE_T9_SEARCH_KEYPAD = "enable_t9_search_keypad"
const val DEFAULT_ENABLE_T9_SEARCH_KEYPAD = false
```
Add methods:
```kotlin
fun getEnableT9SearchKeypad(): Boolean =
    getBoolean(KEY_ENABLE_T9_SEARCH_KEYPAD)

fun saveEnableT9SearchKeypad(value: Boolean) {
    save(KEY_ENABLE_T9_SEARCH_KEYPAD, value)
}
```
In `getBoolean(name)` when statement:
```kotlin
KEY_ENABLE_T9_SEARCH_KEYPAD -> prefs.getBoolean(name, DEFAULT_ENABLE_T9_SEARCH_KEYPAD)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsT9Test`
Expected: PASS.

---

### Task 3: Strings in 17 Locales

**Files:**
- Create: `app/src/test/java/com/mckimquyen/search/T9SearchStringsTest.kt`
- Modify: `app/src/main/res/values*/strings.xml` (17 locales)

**Interfaces:**
- Produces: `setting_enable_t9_search_keypad`
- Produces: `setting_enable_t9_search_keypad_desc`

- [ ] **Step 1: Write failing unit test**

Create `app/src/test/java/com/mckimquyen/search/T9SearchStringsTest.kt`:
```kotlin
package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class T9SearchStringsTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val expectedKeys = setOf(
        "setting_enable_t9_search_keypad",
        "setting_enable_t9_search_keypad_desc"
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
    fun all17LocalesContainT9StringsNonEmpty() {
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

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.T9SearchStringsTest`
Expected: FAIL (missing keys).

- [ ] **Step 3: Add localized strings in 17 locales**

Add `setting_enable_t9_search_keypad` & `setting_enable_t9_search_keypad_desc` across 17 locales via script.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.search.T9SearchStringsTest`
Expected: PASS.

---

### Task 4: Layout & UI Integration in `act_home.xml` & `ActHome.java`

**Files:**
- Modify: `app/src/main/res/layout/act_home.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeT9KeypadWidgetTest.kt`

**Interfaces:**
- Produces: `layoutT9Keypad` view in `act_home.xml` docked at bottom of `SearchView`.
- Produces: 12 buttons (`btnT9_1` to `btnT9_9`, `btnT9_0`, `btnT9_Clear`, `btnT9_Backspace`).
- Integrates:
  - T9 pad visibility matches `utilSettings.getEnableT9SearchKeypad()`.
  - When query is numeric only (`query.all { it.isDigit() || it == ' ' }`) and T9 is enabled, `T9SearchEngine.search` is used.
  - Buttons update `appSearch` text and trigger search.

- [ ] **Step 1: Write widget test on KJ7**

Create `app/src/androidTest/java/com/mckimquyen/ui/ActHomeT9KeypadWidgetTest.kt`:
```kotlin
package com.mckimquyen.ui

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeT9KeypadWidgetTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private var originalMode = false
    private var originalApps: ArrayList<App>? = null

    private val appChrome = App(label = "Chrome", packageName = "com.android.chrome", name = "Main", isVisible = true)
    private val appMaps = App(label = "Maps", packageName = "com.google.maps", name = "Main", isVisible = true)

    @Before
    fun setup() {
        originalMode = settings.getEnableT9SearchKeypad()
        settings.saveEnableT9SearchKeypad(true)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(appChrome, appMaps)
    }

    @After
    fun tearDown() {
        settings.saveEnableT9SearchKeypad(originalMode)
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun t9KeypadTyping247_filtersToChrome() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val keypad = activity.findViewById<View>(R.id.layoutT9Keypad)
                assertNotNull("T9 keypad layout must exist", keypad)
                assertEquals(View.VISIBLE, keypad.visibility)

                activity.findViewById<View>(R.id.btnT9_2).performClick()
                activity.findViewById<View>(R.id.btnT9_4).performClick()
                activity.findViewById<View>(R.id.btnT9_7).performClick()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val editText = activity.findViewById<EditText>(com.google.android.material.R.id.open_search_view_edit_text)
                assertEquals("247", editText.text.toString())

                val adapter = activity.searchResultAdapterForTest
                val list = adapter?.currentListForTest().orEmpty()
                assertEquals(1, list.size)
                assertEquals("Chrome", list[0].label)
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify RED on KJ7**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r -t app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk
adb -s 115333744A005844 install -r -t app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeT9KeypadWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: FAIL (unresolved layout ID `layoutT9Keypad`).

- [ ] **Step 3: Implement layout in `act_home.xml` & logic in `ActHome.java`**

- Thêm `layoutT9Keypad` (`TableLayout` / `GridLayout` 4x3) vào đáy `SearchView` trong `act_home.xml`.
- Trong `ActHome.java`:
  - Wire phím bấm thêm số vào `appSearch`.
  - Phím `btnT9_Backspace`: xóa 1 ký tự, giữ xóa hết.
  - Phím `btnT9_Clear`: xóa sạch query.
  - Trong `updateSearchResults`: nếu `utilSettings.getEnableT9SearchKeypad()` và query chỉ chứa số (`query.all { it.isDigit() || it == ' ' }`), gọi `T9SearchEngine.search(candidates, query)` thay cho `AppSearchEngine.search`.

- [ ] **Step 4: Run test to verify GREEN on KJ7**

Run test command.
Expected: PASS.

---

### Task 5: Settings Toggle in `frm_settings.xml` & `FrmSettings.kt`

**Files:**
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsT9WidgetTest.kt`

**Interfaces:**
- Produces: `swEnableT9SearchKeypad` in `frm_settings.xml`.
- Connects: switch state with `UtilSettings.getEnableT9SearchKeypad()`.

- [ ] **Step 1: Write failing widget test on KJ7**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsT9WidgetTest.kt`:
```kotlin
package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsT9WidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = UtilSettings(context)
    private var originalState = false
    private var hadKey = false

    @Before
    fun setUp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadKey = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalState = settings.getEnableT9SearchKeypad()
        settings.saveEnableT9SearchKeypad(false)
    }

    @After
    fun tearDown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().apply {
            if (hadKey) {
                putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalState)
            } else {
                remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
            }
        }.commit()
    }

    @Test
    fun switchTogglesSettingValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swEnableT9SearchKeypad)
                assertNotNull("swEnableT9SearchKeypad must exist", sw)
                assertFalse("Default must be off", sw.isChecked)

                sw.isChecked = true
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(settings.getEnableT9SearchKeypad())
        }
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run test on KJ7.
Expected: FAIL (unresolved ID).

- [ ] **Step 3: Implement switch in layout and fragment**

- In `frm_settings.xml`: Thêm row Switch "Bàn phím số T9 trong tìm kiếm" vào nhóm tìm kiếm / search bar customization card.
- In `FrmSettings.kt`: Wire `swEnableT9SearchKeypad`, save, load và nullify ở `onDestroyView`.

- [ ] **Step 4: Run test to verify GREEN**

Run test on KJ7.
Expected: PASS.

---

### Task 6: Full Verification, Smoke Test & Audit

**Files:**
- Create: `doc/task/inprogress/p2-search-search-010-t9-dialpad-search.md`
- Create: `doc/task/AUDIT_SEARCH-010_2026-10-17.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Full unit regression + lint**
Run: `./gradlew testDevDebugUnitTest lintDevDebug`
Expected: 910+ unit tests pass, 0 lint errors.

- [ ] **Step 2: Targeted instrumentation suite on TECNO KJ7**
Run all targeted tests on `115333744A005844`.

- [ ] **Step 3: Smoke test tay trên TECNO KJ7**
1. Mở Cài đặt, bật T9 Search Dialpad.
2. Mở SearchView, kiểm tra bàn phím số hiển thị đẹp mắt ở đáy.
3. Bấm `2-4-7` -> kiểm tra lọc đúng Chrome.
4. Chụp ảnh màn hình kiểm tra không có quảng cáo che UI.
5. Tắt lại cài đặt T9 để trả thiết bị về trạng thái sạch sẽ.

- [ ] **Step 4: Audit score >9.0/10 và báo cáo người dùng**
Không commit/push khi chưa có xác nhận từ người dùng.
