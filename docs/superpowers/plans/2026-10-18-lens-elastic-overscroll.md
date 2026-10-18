# FISH-026 — Lens Elastic Overscroll Physics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm hiệu ứng đàn hồi vật lý (Elastic Overscroll Physics) mượt mà khi kéo thấu kính fisheye chạm hoặc vượt qua mép lưới ứng dụng, hỗ trợ bật/tắt trong Settings (mặc định Bật), tự động vô hiệu hóa khi bật Giảm chuyển động (Reduced Motion).

**Architecture:**
- Pure engine `LensOverscrollPhysics`: hàm toán học thuần tính toán tọa độ tâm thấu kính bị hãm theo hàm logarit/bậc hai khi vượt khỏi giới hạn biên lưới `[minX, maxX, minY, maxY]`, 0 allocation per frame.
- Tích hợp `LensView`: trong `ACTION_MOVE`, thay vì clamp cứng, tính toán tọa độ hiệu dụng qua `LensOverscrollPhysics`.
- Cài đặt `UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL`: boolean trong SharedPreferences (mặc định `true`), khôi phục về `true` khi reset-to-defaults.
- Toggle switch trong `FrmSettings` (tab Lens): "Hiệu ứng đàn hồi thấu kính".

**Tech Stack:** Kotlin, Android Canvas/Touch, Material 3, SharedPreferences, JUnit4, Robolectric.

## Global Constraints

- Thiết bị khóa duy nhất: Samsung S24 Ultra `R5CX613VZBR`.
- Không dùng Gradle `connected*`; chạy instrumentation trực tiếp bằng `adb -s R5CX613VZBR shell am instrument`.
- TDD bắt buộc: test fail trước mỗi thay đổi production.
- Không xóa DB Room hay gọi `clear()` toàn bộ SharedPreferences trong test setup/teardown; luôn snapshot và khôi phục đúng key được kiểm thử.
- R4: Dừng ngay nếu phát hiện quảng cáo che màn hình khi smoke test.
- R5: Null-safety, không force unwrap (`!`), dọn sạch view/listener ở `onDestroyView()`.
- 17 locales đầy đủ cho 2 chuỗi mới (`setting_lens_elastic_overscroll`, `setting_lens_elastic_overscroll_desc`).
- Không commit/push khi chưa có chỉ thị từ người dùng sau audit >9.0/10.

---

### Task 1: Pure Engine `LensOverscrollPhysics`

**Files:**
- Create: `app/src/main/java/com/mckimquyen/util/LensOverscrollPhysics.kt`
- Create: `app/src/test/java/com/mckimquyen/util/LensOverscrollPhysicsTest.kt`

**Interfaces:**
- Produces: `LensOverscrollPhysics.damp(delta: Float, maxOverscroll: Float, dampingFactor: Float): Float`
- Produces: `LensOverscrollPhysics.calculateEffectiveCoordinate(raw: Float, min: Float, max: Float, maxOverscroll: Float, enabled: Boolean): Float`

- [ ] **Step 1: Write failing unit test**

Create `app/src/test/java/com/mckimquyen/util/LensOverscrollPhysicsTest.kt`:
```kotlin
package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensOverscrollPhysicsTest {

    private companion object {
        const val MIN = 100f
        const val MAX = 900f
        const val MAX_OVERSCROLL = 80f // ~28dp on 480dpi
    }

    @Test
    fun calculate_withinBounds_returnsRawCoordinate() {
        val x = 500f
        assertEquals(x, LensOverscrollPhysics.calculateEffectiveCoordinate(x, MIN, MAX, MAX_OVERSCROLL, true), 0.001f)
    }

    @Test
    fun calculate_disabled_clampsHardToBounds() {
        val beyondMax = 1200f
        val beyondMin = -200f
        assertEquals(MAX, LensOverscrollPhysics.calculateEffectiveCoordinate(beyondMax, MIN, MAX, MAX_OVERSCROLL, false), 0.001f)
        assertEquals(MIN, LensOverscrollPhysics.calculateEffectiveCoordinate(beyondMin, MIN, MAX, MAX_OVERSCROLL, false), 0.001f)
    }

    @Test
    fun calculate_beyondMax_appliesDampingAndNeverExceedsMaxOverscroll() {
        val raw1 = MAX + 50f
        val raw2 = MAX + 500f
        val rawExtreme = MAX + 100_000f

        val effective1 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw1, MIN, MAX, MAX_OVERSCROLL, true)
        val effective2 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw2, MIN, MAX, MAX_OVERSCROLL, true)
        val effectiveExtreme = LensOverscrollPhysics.calculateEffectiveCoordinate(rawExtreme, MIN, MAX, MAX_OVERSCROLL, true)

        assertTrue("Overscroll phải lớn hơn MAX", effective1 > MAX)
        assertTrue("Kéo xa hơn thì overscroll lớn hơn", effective2 > effective1)
        assertTrue("Không bao giờ vượt quá MAX + MAX_OVERSCROLL", effectiveExtreme <= MAX + MAX_OVERSCROLL)
    }

    @Test
    fun calculate_beyondMin_appliesDampingAndNeverExceedsMaxOverscroll() {
        val raw1 = MIN - 50f
        val rawExtreme = MIN - 100_000f

        val effective1 = LensOverscrollPhysics.calculateEffectiveCoordinate(raw1, MIN, MAX, MAX_OVERSCROLL, true)
        val effectiveExtreme = LensOverscrollPhysics.calculateEffectiveCoordinate(rawExtreme, MIN, MAX, MAX_OVERSCROLL, true)

        assertTrue("Overscroll phải nhỏ hơn MIN", effective1 < MIN)
        assertTrue("Không bao giờ vượt quá MIN - MAX_OVERSCROLL", effectiveExtreme >= MIN - MAX_OVERSCROLL)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensOverscrollPhysicsTest`
Expected: FAIL (unresolved reference: `LensOverscrollPhysics`).

- [ ] **Step 3: Implement `LensOverscrollPhysics.kt`**

Create `app/src/main/java/com/mckimquyen/util/LensOverscrollPhysics.kt`:
```kotlin
package com.mckimquyen.util

import kotlin.math.abs

object LensOverscrollPhysics {

    const val DEFAULT_DAMPING_FACTOR = 0.55f

    @JvmStatic
    fun damp(delta: Float, maxOverscroll: Float, dampingFactor: Float = DEFAULT_DAMPING_FACTOR): Float {
        if (delta <= 0f || maxOverscroll <= 0f) return 0f
        return maxOverscroll * (1f - 1f / (1f + (delta * dampingFactor) / maxOverscroll))
    }

    @JvmStatic
    fun calculateEffectiveCoordinate(
        raw: Float,
        min: Float,
        max: Float,
        maxOverscroll: Float,
        enabled: Boolean
    ): Float {
        if (!enabled || min >= max || maxOverscroll <= 0f) {
            return raw.coerceIn(min, max)
        }
        return when {
            raw < min -> {
                val delta = min - raw
                min - damp(delta, maxOverscroll)
            }
            raw > max -> {
                val delta = raw - max
                max + damp(delta, maxOverscroll)
            }
            else -> raw
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.LensOverscrollPhysicsTest`
Expected: PASS.

---

### Task 2: Persistence in `UtilSettings`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsElasticOverscrollTest.kt`

**Interfaces:**
- Produces: `UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL` ("lens_elastic_overscroll")
- Produces: `UtilSettings.DEFAULT_LENS_ELASTIC_OVERSCROLL` (true)
- Produces: `getLensElasticOverscroll(): Boolean`, `saveLensElasticOverscroll(value: Boolean)`

- [ ] **Step 1: Write failing unit test**

Create `app/src/test/java/com/mckimquyen/util/UtilSettingsElasticOverscrollTest.kt`:
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
class UtilSettingsElasticOverscrollTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultElasticOverscrollIsTrue() {
        assertTrue(settings.getLensElasticOverscroll())
    }

    @Test
    fun saveAndRetrieveElasticOverscroll() {
        settings.saveLensElasticOverscroll(false)
        assertFalse(UtilSettings(context).getLensElasticOverscroll())

        settings.saveLensElasticOverscroll(true)
        assertTrue(UtilSettings(context).getLensElasticOverscroll())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsElasticOverscrollTest`
Expected: FAIL (unresolved reference: `getLensElasticOverscroll`).

- [ ] **Step 3: Implement minimal code in `UtilSettings.kt`**

In `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`:
```kotlin
const val KEY_LENS_ELASTIC_OVERSCROLL = "lens_elastic_overscroll"
const val DEFAULT_LENS_ELASTIC_OVERSCROLL = true

fun getLensElasticOverscroll(): Boolean =
    getBoolean(KEY_LENS_ELASTIC_OVERSCROLL)

fun saveLensElasticOverscroll(value: Boolean) {
    save(KEY_LENS_ELASTIC_OVERSCROLL, value)
}
```
And in `getBoolean(name)`:
```kotlin
KEY_LENS_ELASTIC_OVERSCROLL -> prefs.getBoolean(name, DEFAULT_LENS_ELASTIC_OVERSCROLL)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsElasticOverscrollTest`
Expected: PASS.

---

### Task 3: Strings in 17 Locales

**Files:**
- Create: `app/src/test/java/com/mckimquyen/views/LensElasticOverscrollStringsTest.kt`
- Modify: `app/src/main/res/values*/strings.xml` (17 locales)

**Interfaces:**
- Produces: `setting_lens_elastic_overscroll`
- Produces: `setting_lens_elastic_overscroll_desc`

- [ ] **Step 1: Write failing unit test**

Create `app/src/test/java/com/mckimquyen/views/LensElasticOverscrollStringsTest.kt`:
```kotlin
package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LensElasticOverscrollStringsTest {

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val expectedKeys = setOf(
        "setting_lens_elastic_overscroll",
        "setting_lens_elastic_overscroll_desc"
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
    fun all17LocalesContainElasticOverscrollStringsNonEmpty() {
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

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.views.LensElasticOverscrollStringsTest`
Expected: FAIL (missing keys).

- [ ] **Step 3: Add localized strings in 17 locales**

Add `setting_lens_elastic_overscroll` & `setting_lens_elastic_overscroll_desc` to all 17 locales via python script (escape `'` properly with `\'`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.views.LensElasticOverscrollStringsTest`
Expected: PASS.

---

### Task 4: Integrate Overscroll Physics in `LensView`

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/views/LensViewElasticOverscrollWidgetTest.kt`

**Interfaces:**
- In `LensView.onTouchEvent`:
  - Trong `ACTION_MOVE`: Khi `mMoving == true`, tính toán `effectiveX, effectiveY` qua `LensOverscrollPhysics.calculateEffectiveCoordinate`.
  - Giới hạn biên lưới: `minX = mInsets.left.toFloat()`, `maxX = (width - mInsets.right).toFloat()`, `minY = mInsets.top.toFloat()`, `maxY = (height - mInsets.bottom).toFloat()`.
  - `enabled = (mUtilSettings?.getLensElasticOverscroll() ?: true) && !reduceMotion`.
  - Gán `mTouchX = effectiveX`, `mTouchY = effectiveY`.
- Thêm getter `@VisibleForTesting fun touchCoordinatesForTest(): Pair<Float, Float>` để test kiểm tra tọa độ tâm thấu kính.

- [ ] **Step 1: Write failing widget test on S24 Ultra**

Create `app/src/androidTest/java/com/mckimquyen/views/LensViewElasticOverscrollWidgetTest.kt`:
```kotlin
package com.mckimquyen.views

import android.os.SystemClock
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewElasticOverscrollWidgetTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private var hadSetting = false
    private var originalSetting = true

    @Before
    fun setup() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadSetting = prefs.contains(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        originalSetting = settings.getLensElasticOverscroll()
        settings.saveLensElasticOverscroll(true)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadSetting) {
                putBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, originalSetting)
            } else {
                remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
            }
        }.commit()
    }

    @Test
    fun draggingBeyondRightEdge_allowsElasticOverscrollWhenEnabled() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var lensView: LensView? = null
            scenario.onActivity { activity ->
                lensView = activity.findViewById(R.id.lensViews)
            }
            val view = requireNotNull(lensView)
            val w = view.width.toFloat()
            val h = view.height.toFloat()

            instrumentation.runOnMainSync {
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, w * 0.5f, h * 0.5f, 0)
                view.dispatchTouchEvent(down)
                down.recycle()

                // Drag far past right edge: w + 200px
                val move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, w + 200f, h * 0.5f, 0)
                view.dispatchTouchEvent(move)
                move.recycle()

                val (touchX, _) = view.touchCoordinatesForTest()
                assertTrue("Tọa độ thấu kính phải vượt qua w khi có đàn hồi: touchX=$touchX, w=$w", touchX > w)

                val cancel = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_CANCEL, w + 200f, h * 0.5f, 0)
                view.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run test on S24 Ultra:
Expected: FAIL (unresolved reference: `touchCoordinatesForTest`).

- [ ] **Step 3: Implement overscroll in `LensView.kt`**

- In `LensView.kt`:
  ```kotlin
  @VisibleForTesting
  internal fun touchCoordinatesForTest(): Pair<Float, Float> = Pair(mTouchX, mTouchY)
  ```
  In `ACTION_MOVE`:
  ```kotlin
  val maxOverscrollPx = 28f * resources.displayMetrics.density
  val elasticEnabled = (mUtilSettings?.getLensElasticOverscroll() ?: true) && !reduceMotion
  val effectiveX = LensOverscrollPhysics.calculateEffectiveCoordinate(
      event.x, 0f, width.toFloat(), maxOverscrollPx, elasticEnabled
  )
  val effectiveY = LensOverscrollPhysics.calculateEffectiveCoordinate(
      event.y, 0f, height.toFloat(), maxOverscrollPx, elasticEnabled
  )
  mTouchX = effectiveX
  mTouchY = effectiveY
  ```

- [ ] **Step 4: Run test to verify it passes**

Run test on S24 Ultra:
Expected: PASS.

---

### Task 5: Settings Switch in `frm_lens.xml` & `FrmLens.kt`

**Files:**
- Modify: `app/src/main/res/layout/frm_lens.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/FrmLensElasticOverscrollWidgetTest.kt`

**Interfaces:**
- Produces: `swLensElasticOverscroll` MaterialSwitch in `frm_lens.xml`.
- Connects: switch state with `UtilSettings.getLensElasticOverscroll()`.

- [ ] **Step 1: Write failing widget test on S24 Ultra**

Create `app/src/androidTest/java/com/mckimquyen/ui/FrmLensElasticOverscrollWidgetTest.kt`:
```kotlin
package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmLensElasticOverscrollWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = UtilSettings(context)
    private var hadSetting = false
    private var originalSetting = true

    @Before
    fun setUp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadSetting = prefs.contains(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        originalSetting = settings.getLensElasticOverscroll()
        settings.saveLensElasticOverscroll(true)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadSetting) {
                putBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, originalSetting)
            } else {
                remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
            }
        }.commit()
    }

    @Test
    fun switchTogglesSettingValue() {
        launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<MaterialSwitch>(R.id.swLensElasticOverscroll)
                assertNotNull("swLensElasticOverscroll must exist", sw)
                assertTrue("Default must be on", sw.isChecked)

                sw.isChecked = false
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue("Toggling must persist false", !settings.getLensElasticOverscroll())
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run test on S24 Ultra:
Expected: FAIL (unresolved ID).

- [ ] **Step 3: Implement switch in layout and fragment**

- Add switch card into `frm_lens.xml` below the presets card.
- In `FrmLens.kt`: Wire `swLensElasticOverscroll`, bind in `assignValues()`, save in listener, reset in `resetToDefault()`, null in `onDestroyView()`.

- [ ] **Step 4: Run test to verify it passes**

Run test on S24 Ultra:
Expected: PASS.

---

### Task 6: Full Verification, Smoke Test & Audit

**Files:**
- Create: `doc/task/inprogress/p2-fish-fish-026-lens-elastic-overscroll.md`
- Create: `doc/task/AUDIT_FISH-026_2026-10-18.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Full unit regression + lint**
Run: `./gradlew testDevDebugUnitTest lintDevDebug`
Expected: 915+ unit tests pass, 0 lint errors.

- [ ] **Step 2: Targeted instrumentation suite on Samsung S24 Ultra**
Run all targeted tests on `R5CX613VZBR`.

- [ ] **Step 3: Smoke test tay trên Samsung S24 Ultra**
1. Mở Cài đặt > tab Lens, kiểm tra switch "Hiệu ứng đàn hồi thấu kính" (mặc định Bật).
2. Quay lại Home, kéo thấu kính chạm mép viền, cảm nhận lực cản đàn hồi mượt mà.
3. Chụp ảnh màn hình kiểm tra không có quảng cáo che UI.
4. Trả thiết bị về trạng thái sạch sẽ ban đầu.

- [ ] **Step 4: Audit score >9.0/10 và báo cáo người dùng**
Không commit/push khi chưa có chỉ thị từ người dùng.
