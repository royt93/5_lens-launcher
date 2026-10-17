# FISH-024 — Vòng sáng tiêu điểm thấu kính (Focus Accent Halo) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hiển thị vòng sáng viền mảnh (Accent Ring) bao quanh icon app đang được phóng to tại tiêu điểm ngón tay trên lưới thấu kính fisheye, có tùy chọn Bật/Tắt trong Cài đặt và tự ẩn khi Clean Mode bật.

**Architecture:**
- Pure policy function `LensView.shouldDrawFocusHalo(enabled, cleanMode)` xác định điều kiện vẽ.
- Khởi tạo `Paint` một lần duy nhất trong `LensView.initPaints()`, 0 allocation trên `onDraw()`.
- Persistence qua `UtilSettings.KEY_SHOW_FOCUS_HALO` trong SharedPreferences (mặc định `true`).
- Cung cấp MaterialSwitch trong `FrmSettings` với bản địa hóa 17 ngôn ngữ.

**Tech Stack:** Kotlin, Android Canvas/Paint, SharedPreferences, Material 3, JUnit4, Robolectric.

## Global Constraints

- Thiết bị khóa duy nhất: TECNO BG6 `118743744X002560`. Không dùng `connected*` tasks; dùng `adb -s 118743744X002560 shell am instrument`.
- Không thêm dependency bên ngoài, không Room migration.
- R5: Null-safety, không force-unwrap (`!`), dọn sạch view/listener ở `onDestroyView()`.
- R4: Dừng ngay nếu thấy quảng cáo che màn hình khi smoke test.
- 17 locales đầy đủ cho các chuỗi mới.

---

### Task 1: Policy logic `shouldDrawFocusHalo` & Dimension Resources

**Files:**
- Create: `app/src/test/java/com/mckimquyen/views/LensViewFocusHaloPolicyTest.kt`
- Modify: `app/src/main/res/values/dimen.xml:5-10`
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt:180-200`

**Interfaces:**
- Produces: `LensView.shouldDrawFocusHalo(enabled: Boolean, cleanMode: Boolean): Boolean`
- Produces: `R.dimen.padding_focus_halo` (4dp), `R.dimen.stroke_width_focus_halo` (2.5dp)

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/views/LensViewFocusHaloPolicyTest.kt`:
```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensViewFocusHaloPolicyTest {

    @Test
    fun shouldDrawFocusHalo_trueWhenEnabledAndCleanModeOff() {
        assertTrue(LensView.shouldDrawFocusHalo(enabled = true, cleanMode = false))
    }

    @Test
    fun shouldDrawFocusHalo_falseWhenDisabled() {
        assertFalse(LensView.shouldDrawFocusHalo(enabled = false, cleanMode = false))
        assertFalse(LensView.shouldDrawFocusHalo(enabled = false, cleanMode = true))
    }

    @Test
    fun shouldDrawFocusHalo_falseWhenCleanModeOn() {
        assertFalse(LensView.shouldDrawFocusHalo(enabled = true, cleanMode = true))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests LensViewFocusHaloPolicyTest`
Expected: FAIL (unresolved reference: `shouldDrawFocusHalo`).

- [ ] **Step 3: Implement minimal code**

In `app/src/main/res/values/dimen.xml`, add:
```xml
    <dimen name="padding_focus_halo">4dp</dimen>
    <dimen name="stroke_width_focus_halo">2.5dp</dimen>
```

In `app/src/main/java/com/mckimquyen/views/LensView.kt` companion object:
```kotlin
        @JvmStatic
        fun shouldDrawFocusHalo(enabled: Boolean, cleanMode: Boolean): Boolean =
            enabled && !cleanMode
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests LensViewFocusHaloPolicyTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mckimquyen/views/LensViewFocusHaloPolicyTest.kt app/src/main/res/values/dimen.xml app/src/main/java/com/mckimquyen/views/LensView.kt
git commit -m "feat(lens): add focus halo policy and dimensions (FISH-024)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Persistence in `UtilSettings`

**Files:**
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsFocusHaloTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt:100-150`

**Interfaces:**
- Produces: `UtilSettings.KEY_SHOW_FOCUS_HALO` ("show_focus_halo")
- Produces: `UtilSettings.showFocusHalo` (getter/setter Boolean, default `true`)

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsFocusHaloTest.kt`:
```kotlin
package com.mckimquyen.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilSettingsFocusHaloTest {

    private lateinit var settings: UtilSettings

    @Before
    fun setUp() {
        settings = UtilSettings(ApplicationProvider.getApplicationContext())
        settings.sharedPreferences.edit().clear().commit()
    }

    @Test
    fun showFocusHalo_defaultsToTrue() {
        assertTrue(settings.showFocusHalo)
    }

    @Test
    fun showFocusHalo_persistsValue() {
        settings.showFocusHalo = false
        assertEquals(false, settings.showFocusHalo)

        settings.showFocusHalo = true
        assertEquals(true, settings.showFocusHalo)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests UtilSettingsFocusHaloTest`
Expected: FAIL (unresolved reference: `showFocusHalo`).

- [ ] **Step 3: Implement minimal code**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`:
Add constant:
```kotlin
const val KEY_SHOW_FOCUS_HALO = "show_focus_halo"
```
Add property or helper:
```kotlin
var showFocusHalo: Boolean
    get() = getBoolean(KEY_SHOW_FOCUS_HALO, true)
    set(value) = save(KEY_SHOW_FOCUS_HALO, value)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests UtilSettingsFocusHaloTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mckimquyen/util/UtilSettingsFocusHaloTest.kt app/src/main/java/com/mckimquyen/util/UtilSettings.kt
git commit -m "feat(settings): persist focus halo setting (FISH-024)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Strings in 17 Locales

**Files:**
- Create: `app/src/test/java/com/mckimquyen/views/FocusHaloStringsTest.kt`
- Modify: `app/src/main/res/values*/strings.xml` (17 locales)

**Interfaces:**
- Produces: `R.string.setting_show_focus_halo`
- Produces: `R.string.setting_show_focus_halo_desc`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/mckimquyen/views/FocusHaloStringsTest.kt`:
```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FocusHaloStringsTest {

    private val locales = listOf(
        "values", "values-ar", "values-de", "values-es", "values-fr",
        "values-hi", "values-in", "values-it", "values-ja", "values-km",
        "values-ko", "values-lo", "values-pt", "values-ru", "values-th",
        "values-vi", "values-zh"
    )

    private val requiredKeys = listOf(
        "setting_show_focus_halo",
        "setting_show_focus_halo_desc"
    )

    @Test
    fun all17LocalesHaveRequiredStrings() {
        val resDir = File("src/main/res")
        for (locale in locales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("File strings.xml phải tồn tại cho $locale", stringsFile.exists())
            val content = stringsFile.readText()
            for (key in requiredKeys) {
                assertTrue(
                    "Locale $locale phải chứa key $key",
                    content.contains("name=\"$key\"")
                )
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests FocusHaloStringsTest`
Expected: FAIL (missing keys in `strings.xml`).

- [ ] **Step 3: Add localized strings in 17 locales**

Add `setting_show_focus_halo` và `setting_show_focus_halo_desc` vào 17 files:
- `values`: "Focus ring indicator" / "Show an accent ring highlighting the focused app"
- `values-vi`: "Vòng sáng tiêu điểm" / "Hiển thị vòng tròn làm nổi bật ứng dụng đang chọn"
- `values-ar`: "حلقة تركيز التطبيق" / "إظهار حلقة تمييز للتطبيق المركز"
- `values-de`: "Fokusring-Anzeige" / "Akzentring zur Hervorhebung der fokussierten App anzeigen"
- `values-es`: "Anillo de enfoque" / "Mostrar un anillo para resaltar la app enfocada"
- `values-fr`: "Anneau de mise au point" / "Afficher un anneau mettant en valeur l'application ciblée"
- `values-hi`: "फ़ोकस रिंग संकेतक" / "फ़ोकस किए गए ऐप को हाइलाइट करने वाला रिंग दिखाएं"
- `values-in`: "Cincin sorotan fokus" / "Tampilkan cincin aksen untuk menyorot aplikasi yang difokuskan"
- `values-it`: "Anello di messa a fuoco" / "Mostra un anello per evidenziare l'app focalizzata"
- `values-ja`: "フォーカスリング表示" / "選択中のアプリを強調するリングを表示します"
- `values-km`: "កងរង្វង់ផ្ដោតលើកម្មវិធី" / "បង្ហាញកងរង្វង់ដើម្បីរំលេចកម្មវិធីដែលកំពុងផ្ដោត"
- `values-ko`: "포커스 링 표시" / "포커스된 앱을 강조하는 링을 표시합니다"
- `values-lo`: "ວົງແຫວນຈຸດໂຟກັສ" / "ສະແດງວົງແຫວນເນັ້ນແອັບທີ່ກຳລັງເລືອກ"
- `values-pt`: "Anel de foco" / "Mostrar um anel de destaque no app focado"
- `values-ru`: "Кольцо фокуса" / "Показывать акцентное кольцо вокруг активного приложения"
- `values-th`: "วงแหวนโฟกัส" / "แสดงวงแหวนเน้นแอปที่กำลังเลือกอยู่"
- `values-zh`: "焦点高亮圆环" / "显示高亮圆环以突出显示聚焦的应用"

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests FocusHaloStringsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mckimquyen/views/FocusHaloStringsTest.kt app/src/main/res/values*/strings.xml
git commit -m "feat(strings): add focus halo strings for 17 locales (FISH-024)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: Canvas Drawing in `LensView`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Create: `app/src/test/java/com/mckimquyen/views/LensViewFocusHaloDrawingTest.kt`

**Interfaces:**
- Consumes: `LensView.shouldDrawFocusHalo`, `UtilSettings.KEY_SHOW_FOCUS_HALO`, `R.dimen.padding_focus_halo`, `R.dimen.stroke_width_focus_halo`
- Produces: `drawFocusHalo(canvas, rect)` drawing accent ring around hovered app

- [ ] **Step 1: Write unit / robolectric test**

Create `app/src/test/java/com/mckimquyen/views/LensViewFocusHaloDrawingTest.kt`:
```kotlin
package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class LensViewFocusHaloDrawingTest {

    private lateinit var lensView: LensView
    private lateinit var settings: UtilSettings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settings = UtilSettings(context)
        lensView = LensView(context)
    }

    @Test
    fun lensViewInitializesFocusHaloPaint() {
        assertNotNull(lensView.paintFocusHaloForTest)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests LensViewFocusHaloDrawingTest`
Expected: FAIL (unresolved reference: `paintFocusHaloForTest`).

- [ ] **Step 3: Implement `mPaintFocusHalo` and `drawFocusHalo` in `LensView`**

In `LensView.kt`:
```kotlin
    private var mPaintFocusHalo: Paint? = null
    internal val paintFocusHaloForTest: Paint? get() = mPaintFocusHalo

    // Trong initPaints:
    mPaintFocusHalo = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.stroke_width_focus_halo)
        color = mUtilSettings?.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
    }

    // Trong onDraw / drawGrid:
    if (mRectToSelect != null && mDrawType == DrawType.APPS && mApps != null && mSelectIndex >= 0) {
        drawFocusHalo(canvas, mRectToSelect)
        drawAppName(canvas, mRectToSelect)
    }

    private fun drawFocusHalo(canvas: Canvas, rect: RectF?) {
        val us = mUtilSettings ?: return
        if (!shouldDrawFocusHalo(
                us.showFocusHalo,
                us.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
            )
        ) return
        val r = rect ?: return
        val paint = mPaintFocusHalo ?: return
        paint.color = us.getString(UtilSettings.KEY_HIGHLIGHT_COLOR)?.toColorInt() ?: 0
        val radius = (r.width() / 2f) + resources.getDimension(R.dimen.padding_focus_halo)
        canvas.drawCircle(r.centerX(), r.centerY(), radius, paint)
    }
```
Sync alpha in animation:
```kotlin
mPaintFocusHalo?.alpha = (255.0f * interpolatedTime).toInt()
mPaintFocusHalo?.alpha = (255.0f * (1.0f - interpolatedTime)).toInt()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests LensViewFocusHaloDrawingTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/mckimquyen/views/LensViewFocusHaloDrawingTest.kt app/src/main/java/com/mckimquyen/views/LensView.kt
git commit -m "feat(views): draw focus halo around selected app icon (FISH-024)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: UI Toggle in `FrmSettings` & Integration

**Files:**
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsFocusHaloWidgetTest.kt`

**Interfaces:**
- Produces: `swShowFocusHalo` MaterialSwitch in `frm_settings.xml`
- Connects: switch state with `UtilSettings.showFocusHalo`

- [ ] **Step 1: Write instrumentation widget test**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsFocusHaloWidgetTest.kt`:
```kotlin
package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsFocusHaloWidgetTest {

    @Test
    fun focusHaloSwitchTogglesSetting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = UtilSettings(context)
        settings.showFocusHalo = true

        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val switch = activity.findViewById<MaterialSwitch>(R.id.swShowFocusHalo)
                assertNotNull(switch)
                assertEquals(true, switch.isChecked)

                switch.performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(false, settings.showFocusHalo)
        }
    }
}
```

- [ ] **Step 2: Add switch in `frm_settings.xml` and wire in `FrmSettings.kt`**

In `app/src/main/res/layout/frm_settings.xml`:
Add `rlSwitchShowFocusHaloParent` below `rlSwitchShowTouchSelectionParent`:
```xml
                <RelativeLayout
                    android:id="@+id/rlSwitchShowFocusHaloParent"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingVertical="8dp">

                    <ImageView
                        android:id="@+id/iconFocusHalo"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:layout_alignParentStart="true"
                        android:layout_centerVertical="true"
                        android:contentDescription="@string/app_name"
                        android:padding="8dp"
                        android:src="@drawable/ic_search_filter_this_lens" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/swShowFocusHalo"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:layout_toEndOf="@id/iconFocusHalo"
                        android:paddingTop="12dp"
                        android:paddingBottom="12dp"
                        android:text="@string/setting_show_focus_halo"
                        android:textColor="?android:attr/textColorSecondary"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:thumb="@drawable/sw_thumb_ios"
                        app:switchPadding="16dp"
                        app:track="@drawable/sw_track_ios" />

                </RelativeLayout>
```

In `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`:
- Declare `private var swShowFocusHalo: SwitchCompat? = null` (or MaterialSwitch)
- In `onViewCreated`: find `swShowFocusHalo`, set listener to update `us.showFocusHalo`.
- In `loadSettings`: `swShowFocusHalo?.isChecked = us.showFocusHalo`.
- In `onDestroyView`: `swShowFocusHalo = null`.

- [ ] **Step 3: Run targeted instrumentation test on TECNO BG6**

Run:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 118743744X002560 install -r -t app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s 118743744X002560 install -r -t app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 118743744X002560 shell am instrument -w -e class com.mckimquyen.ui.FrmSettingsFocusHaloWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/frm_settings.xml app/src/main/java/com/mckimquyen/ui/FrmSettings.kt app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsFocusHaloWidgetTest.kt
git commit -m "feat(ui): add focus halo switch in settings (FISH-024)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Full Verification, Smoke Test & Audit

**Files:**
- Create: `doc/task/inprogress/p2-fish-fish-024-focus-accent-halo.md`
- Create: `doc/task/AUDIT_FISH-024_2026-10-17.md`

- [ ] **Step 1: Run full unit test suite & lint**

Run: `./gradlew testDevDebugUnitTest lintDevDebug`
Expected: 890+ unit tests pass, 0 lint errors.

- [ ] **Step 2: Run targeted instrumentation suite on TECNO BG6**

Run targeted tests on `118743744X002560`.

- [ ] **Step 3: Smoke test tay trên TECNO BG6**

1. Mở launcher, chạm và kéo thấu kính fisheye.
2. Kiểm tra vòng viền mảnh bao quanh icon đang phóng to.
3. Chụp screenshot, kiểm tra không có quảng cáo.
4. Vào Cài đặt tắt "Vòng sáng tiêu điểm", quay lại màn hình chính kéo thấu kính xác nhận không còn vòng sáng.
5. Bật lại cài đặt, thử bật Clean Mode xác nhận vòng sáng tự ẩn.

- [ ] **Step 4: Chấm audit (>9.0/10) và lưu tài liệu**

Lập `doc/task/AUDIT_FISH-024_2026-10-17.md` đạt gate >9.0.
Không tự ý commit/push trước khi người dùng xác nhận.
