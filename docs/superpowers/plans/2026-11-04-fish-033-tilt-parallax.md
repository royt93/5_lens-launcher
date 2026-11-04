# FISH-033 Tilt Parallax Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khi không chạm, tâm méo fisheye trôi nhẹ theo góc nghiêng máy; chạm thì ngón tay thắng; công tắc chung mặc định tắt.

**Architecture:** `TiltParallax` (toán thuần) biến quaternion thành offset pixel đã clamp/làm mịn. `TiltSensorSource` đăng ký `TYPE_GAME_ROTATION_VECTOR`. `ActHome` sở hữu sensor một lần theo vòng đời và đẩy offset vào `LensView` hiện tại; `LensView` chỉ dùng offset khi không có chạm thật.

**Tech Stack:** Kotlin + Java, Android `SensorManager`, SharedPreferences (`UtilSettings`), JUnit4/Robolectric, AndroidJUnitRunner.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-04-fish-033-tilt-parallax-design.md` (commit `4786e273`).
- **Chỉ TECNO KJ7 `115333744A005844`** cho mọi build/install/test/smoke (owner 2026-11-04). Không chạm Pixel/Samsung/OPPO dù thấy trong `adb devices`. Mọi lệnh dùng `adb -s 115333744A005844`; không gọi tool MCP thiết bị ngầm định; không chạy Gradle `connected*`.
- Công tắc **toàn cục** `KEY_TILT_PARALLAX`, mặc định **tắt**. Không theo lens, không backup, không slider.
- Biên độ cố định nhỏ, hằng số có tên. Không magic number.
- Tự tắt bằng `LensPhysicsPolicy.shouldReduceLensMotion(context)` + Activity không resumed + search đang mở + không có sensor + chế độ list.
- Không đổi `LensGridCache`, hit-test lúc chạm, schema, quyền, dependency. Khi tắt phải vẽ lưới gốc y hệt hiện tại.
- Không Ads/IAP/TalkBack. Không push khi chưa có yêu cầu hiện hành. Không stage `.idea/`.
- Trước test thiết bị: backup APK + data + hash từng file; sau mỗi nhóm test khôi phục và so lại. Không dùng `replaceWorkspace` để dọn.
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.

---

## Kiểm chứng code thật (2026-11-04)

| Điểm nối | Thực tế |
|---|---|
| Tâm méo | `LensView.mTouchX/mTouchY` (private, `-Float.MAX_VALUE` = không chạm). `drawGrid`: `hasActiveTouch = mTouchX != -MAX && mTouchY != -MAX`; chỉ khi đúng mới áp `lensProfile.shiftX/shiftY/scaleX/scaleY` với `animationMultiplier`. Hit-test `UtilCalculator.isInsideRect(mTouchX, mTouchY, rect)` ở cùng nhánh, và `mSelectIndex`/`mRectToSelect` suy ra từ đó. |
| Hằng nhiều nơi | `drawGrid` còn dùng `mTouchX/Y` cho `DepthOfField.focusDistance`; `drawTouchSelection` dùng `mTouchX/Y`; `DRAW CIRCLES` ghi đè `mTouchX/Y`. Phải **không** để tâm ảo rò vào các chỗ này. |
| Chạm | `handleAppTouch` ACTION_DOWN gán `mTouchX/Y = event.x/y`; ACTION_UP/CANCEL đặt lại `-MAX_VALUE`. |
| Gate chuyển động | `LensPhysicsPolicy.shouldReduceLensMotion(context: Context): Boolean` (public) đọc `ANIMATOR_DURATION_SCALE`, battery saver, thermal. |
| Reset khi detach | `resetTouchState()` (private) gọi trong `onDetachedFromWindow` và `resetToIdleForExport`. |
| Trang hiện tại | `ActHome.lensViews` (package-private `LensView`), host `activeLensView()`; ViewPager2 tái dùng trang, nên mọi trang có thể được đổi `lensViews`. |
| `ActHome` | Không override `onPause`; `onResume` có ở dòng ~712; `onDestroy` ~1077 gọi `lensHolder/lensMenu/searchController/quickActions.onDestroy()`. `BaseActivity` có `onPause`. `searchView` là `SearchView` Material (`isShowing()`). |
| Công tắc mẫu | `KEY_LENS_ELASTIC_OVERSCROLL`: `UtilSettings` key/default (dòng ~86,149), nhánh `getBoolean` (~355), getter/setter (~436), `FrmLens` `swLensElasticOverscroll` (find ~106, listener ~178, bind ~259, null ~298), `frm_lens.xml` ~495-520, `ResetEntries.lens` + `ResetEntriesTest` kiểm tập key cố định. |
| Sensor TECNO KJ7 | `Game Rotation Vector Sensor` (AOSP, type 15) có mặt trong `dumpsys sensorservice`. |

### Quyết định kỹ thuật bổ sung (không trái spec)

- **Tâm ảo không ghi vào `mTouchX/mTouchY`.** Thay vào đó `drawGrid` tính hai biến cục bộ `centerX/centerY` và cờ `hasLensCenter`: `centerX = if (hasActiveTouch) mTouchX else tiltCenterX`. Hit-test, `DepthOfField`, `drawTouchSelection` vẫn dùng `mTouchX/Y` thật; vì vậy tilt không tạo "icon đang chạm", không chọn icon, không rung hover.
- Khi rảnh có tilt, dùng `animationMultiplier = TILT_ANIMATION_MULTIPLIER` (hằng số có tên); khi chạm giữ `mAnimationMultiplier` hiện có.
- Xóa offset trong `resetTouchState()` để detach/export không bake tilt vào ảnh Polaroid và không giữ trạng thái cũ ở trang tái dùng.
- `LensView` giữ offset ở hai trường `Float` và một cờ, không cấp phát đối tượng mỗi mẫu.

## File map

- Thêm `app/src/main/java/com/mckimquyen/util/TiltParallax.kt` + `app/src/test/java/com/mckimquyen/util/TiltParallaxTest.kt`.
- Thêm `app/src/main/java/com/mckimquyen/util/TiltSensorSource.kt` + `app/src/test/java/com/mckimquyen/util/TiltSensorSourceTest.kt`.
- Thêm `app/src/main/java/com/mckimquyen/util/TiltGate.kt` (hàm thuần quyết định bật/tắt) + `app/src/test/java/com/mckimquyen/util/TiltGateTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`; thêm `app/src/test/java/com/mckimquyen/util/UtilSettingsTiltParallaxTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/ui/settings/ResetEntries.kt`, `app/src/test/java/com/mckimquyen/ui/settings/ResetEntriesTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/views/LensView.kt`; thêm `app/src/androidTest/java/com/mckimquyen/views/LensViewTiltIntegrationTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/ui/ActHome.java`; thêm `app/src/androidTest/java/com/mckimquyen/ui/ActHomeTiltWidgetTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/ui/FrmLens.kt`, `app/src/main/res/layout/frm_lens.xml`, chuỗi mới ở đủ 17 locale `app/src/main/res/values*/strings.xml`; thêm `app/src/androidTest/java/com/mckimquyen/ui/FrmLensTiltWidgetTest.kt`; sửa `app/src/test/java/com/mckimquyen/a11y/LensStringTranslationTest.kt` nếu cần.

## Interfaces cố định

```kotlin
// util/TiltParallax.kt
object TiltParallax {
    const val MAX_ANGLE_RADIANS = 0.35f           // ~20°, góc nghiêng tối đa được tính
    const val MAX_OFFSET_FRACTION = 0.12f         // biên độ tối đa so với cạnh ngắn vùng lưới
    const val DEAD_ZONE_RADIANS = 0.02f           // ~1°, rung tay không làm trôi
    const val SMOOTHING_FACTOR = 0.15f            // bộ lọc thông thấp một cực, 0..1
    const val ANIMATION_MULTIPLIER = 0.6f         // độ méo khi rảnh có tilt
    const val INVALIDATE_THRESHOLD_PX = 0.5f

    /** Bộ lọc có trạng thái; không cấp phát trong update. */
    class Filter {
        var offsetX: Float; private set
        var offsetY: Float; private set
        fun reset()
        /** Trả true nếu offset đổi quá INVALIDATE_THRESHOLD_PX. */
        fun update(qx: Float, qy: Float, qz: Float, qw: Float, rotation: Int, areaPx: Float): Boolean
    }
    fun anglesOf(qx: Float, qy: Float, qz: Float, qw: Float, rotation: Int, out: FloatArray)  // out[0]=nghiêng ngang, out[1]=nghiêng dọc (rad)
}

// util/TiltGate.kt
object TiltGate {
    fun shouldRun(enabled: Boolean, reduceMotion: Boolean, resumed: Boolean,
                  searchShowing: Boolean, listMode: Boolean, sensorAvailable: Boolean): Boolean
}

// util/TiltSensorSource.kt
class TiltSensorSource(private val manager: SensorManager?) {
    interface Listener { fun onRotation(qx: Float, qy: Float, qz: Float, qw: Float) }
    val available: Boolean
    val isRunning: Boolean
    fun start(listener: Listener): Boolean   // idempotent, trả false nếu không có sensor
    fun stop()                                // idempotent
}

// UtilSettings
const val KEY_TILT_PARALLAX = "tilt_parallax"
const val DEFAULT_TILT_PARALLAX = false
fun getTiltParallax(): Boolean
fun saveTiltParallax(value: Boolean)

// LensView
internal fun setTiltOffset(x: Float, y: Float)   // main thread
internal fun clearTiltOffset()
```

---

## Task 1 — Toán TiltParallax (TDD)

**Files:** `util/TiltParallax.kt`, `TiltParallaxTest.kt`. **Produces:** `TiltParallax.Filter`, `TiltParallax.anglesOf`.

- [ ] **1. RED:** viết test trước.

```kotlin
class TiltParallaxTest {
    private val area = 1000f
    private val maxPx = TiltParallax.MAX_OFFSET_FRACTION * area

    /** Quaternion xoay quanh trục X (nghiêng dọc) hoặc Y (nghiêng ngang) của hệ máy. */
    private fun aroundX(rad: Float) = floatArrayOf(sin(rad / 2), 0f, 0f, cos(rad / 2))
    private fun aroundY(rad: Float) = floatArrayOf(0f, sin(rad / 2), 0f, cos(rad / 2))

    private fun TiltParallax.Filter.feed(q: FloatArray, times: Int, rotation: Int = Surface.ROTATION_0) =
        repeat(times) { update(q[0], q[1], q[2], q[3], rotation, area) }

    @Test fun identityQuaternionGivesZeroOffset() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 50)
        assertEquals(0f, f.offsetX, 0.001f)
        assertEquals(0f, f.offsetY, 0.001f)
    }

    @Test fun firstSampleBecomesTheReferencePose() {
        val f = TiltParallax.Filter()
        f.feed(aroundY(0.3f), 100)
        assertEquals("holding the first pose stays centered", 0f, f.offsetX, 0.5f)
        assertEquals(0f, f.offsetY, 0.5f)
    }

    @Test fun tiltBeyondMaxAngleClampsAtMaxOffset() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 1)
        f.feed(aroundY(TiltParallax.MAX_ANGLE_RADIANS * 3f), 300)
        assertEquals(maxPx, abs(f.offsetX), 0.5f)
        assertEquals(0f, f.offsetY, 0.5f)
    }

    @Test fun oppositeTiltMovesTheOffsetTheOtherWay() {
        val left = TiltParallax.Filter().also { it.feed(floatArrayOf(0f, 0f, 0f, 1f), 1); it.feed(aroundY(0.2f), 300) }
        val right = TiltParallax.Filter().also { it.feed(floatArrayOf(0f, 0f, 0f, 1f), 1); it.feed(aroundY(-0.2f), 300) }
        assertTrue(left.offsetX * right.offsetX < 0f)
        assertEquals(abs(left.offsetX), abs(right.offsetX), 0.5f)
    }

    @Test fun tinyShakeInsideTheDeadZoneDoesNotMove() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 1)
        f.feed(aroundY(TiltParallax.DEAD_ZONE_RADIANS * 0.5f), 300)
        assertEquals(0f, f.offsetX, 0.001f)
    }

    @Test fun smoothingApproachesTheTargetWithoutOvershoot() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 1)
        val q = aroundY(0.2f)
        var previous = 0f
        repeat(100) {
            f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area)
            assertTrue("never decreases while approaching", abs(f.offsetX) >= previous - 0.0001f)
            previous = abs(f.offsetX)
        }
        val target = ((0.2f - TiltParallax.DEAD_ZONE_RADIANS).coerceAtLeast(0f) / TiltParallax.MAX_ANGLE_RADIANS)
            .coerceAtMost(1f) * maxPx
        assertTrue("never passes the target", previous <= target + 0.5f)
        assertEquals(target, previous, target * 0.05f)
    }

    @Test fun nonFiniteInputKeepsStateAndNeverProducesNaN() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 1)
        f.feed(aroundY(0.2f), 40)
        val x = f.offsetX
        val y = f.offsetY
        assertFalse(f.update(Float.NaN, 0f, 0f, 1f, Surface.ROTATION_0, area))
        assertFalse(f.update(0f, Float.POSITIVE_INFINITY, 0f, 1f, Surface.ROTATION_0, area))
        assertFalse(f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, 0f))
        assertFalse(f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, -5f))
        assertEquals(x, f.offsetX, 0f)
        assertEquals(y, f.offsetY, 0f)
        assertFalse(f.offsetX.isNaN() || f.offsetY.isNaN())
    }

    @Test fun screenRotationSwapsTheDominantAxis() {
        val upright = TiltParallax.Filter().also { it.feed(floatArrayOf(0f, 0f, 0f, 1f), 1); it.feed(aroundY(0.25f), 300) }
        val sideways = TiltParallax.Filter().also {
            it.feed(floatArrayOf(0f, 0f, 0f, 1f), 1, Surface.ROTATION_90)
            it.feed(aroundY(0.25f), 300, Surface.ROTATION_90)
        }
        assertTrue("upright tilts horizontally", abs(upright.offsetX) > abs(upright.offsetY) * 5f)
        assertTrue("rotated 90 tilts vertically", abs(sideways.offsetY) > abs(sideways.offsetX) * 5f)
    }

    @Test fun resetReturnsToCenterAndTakesANewReference() {
        val f = TiltParallax.Filter()
        f.feed(floatArrayOf(0f, 0f, 0f, 1f), 1)
        f.feed(aroundY(0.3f), 100)
        assertTrue(abs(f.offsetX) > 1f)
        f.reset()
        assertEquals(0f, f.offsetX, 0f)
        assertEquals(0f, f.offsetY, 0f)
        f.feed(aroundY(0.3f), 100)
        assertEquals("the pose after reset is the new reference", 0f, f.offsetX, 0.5f)
    }

    @Test fun updateReportsAChangeOnlyBeyondTheThreshold() {
        val f = TiltParallax.Filter()
        assertFalse("first sample only sets the reference", f.update(0f, 0f, 0f, 1f, Surface.ROTATION_0, area))
        val q = aroundY(0.2f)
        assertTrue(f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area))
        repeat(500) { f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area) }
        assertFalse("settled offset no longer reports changes", f.update(q[0], q[1], q[2], q[3], Surface.ROTATION_0, area))
    }
}
```

`TiltParallaxTest` cần `import android.view.Surface`, `import kotlin.math.*` và chạy với `@RunWith(RobolectricTestRunner::class) @Config(manifest = Config.NONE, sdk = [28])` (hằng `Surface.ROTATION_*` là số nguyên trong stub android.jar nên Robolectric là cách an toàn). Hai test `oppositeTilt` và `screenRotation` dựa trên quy ước dấu cụ thể: khi triển khai phải chọn một quy ước (nghiêng máy sang phải → tâm méo trôi sang phải) và ghi ở đầu `TiltParallax`.

- [ ] **2. Verify RED:** `./gradlew testDevDebugUnitTest --tests '*TiltParallaxTest'` → lỗi biên dịch `Unresolved reference 'TiltParallax'`.
- [ ] **3. Implement:** quaternion → góc nghiêng ngang/dọc bằng `atan2` công thức roll/pitch chuẩn, hoán đổi/đảo dấu theo `rotation` (`Surface.ROTATION_0/90/180/270`); góc tham chiếu đặt ở mẫu hợp lệ đầu tiên sau `reset()`; hiệu góc trừ tham chiếu, vùng chết, clamp `±MAX_ANGLE_RADIANS`, chuẩn hóa `[-1,1]`, nhân `MAX_OFFSET_FRACTION * areaPx`, lọc `offset += (target - offset) * SMOOTHING_FACTOR`. Đầu vào không hữu hạn hoặc `areaPx <= 0` trả `false` và giữ nguyên trạng thái. Không cấp phát trong `update`.
- [ ] **4. Verify GREEN:** cùng lệnh, 0 fail.
- [ ] **5. Commit:** `feat(tilt): add pure tilt parallax math (FISH-033)`.

## Task 2 — Công tắc, gate và reset (TDD)

**Files:** `UtilSettings.kt`, `TiltGate.kt`, `ResetEntries.kt`, tests `UtilSettingsTiltParallaxTest.kt`, `TiltGateTest.kt`, `ResetEntriesTest.kt`.

- [ ] **1. RED:** test `UtilSettings` (mặc định false; `saveTiltParallax(true)` đọc lại true; không động key khác), `TiltGate.shouldRun` đủ bảng 2^6 tổ hợp chỉ `true` khi `enabled && !reduceMotion && resumed && !searchShowing && !listMode && sensorAvailable`, và `ResetEntriesTest` mong đợi tập key lens có thêm `tilt_parallax`.

```kotlin
@Test fun runsOnlyWhenEveryConditionHolds() {
    for (mask in 0 until 64) {
        val e = mask and 1 != 0; val r = mask and 2 != 0; val u = mask and 4 != 0
        val s = mask and 8 != 0; val l = mask and 16 != 0; val a = mask and 32 != 0
        assertEquals("mask=$mask", e && !r && u && !s && !l && a, TiltGate.shouldRun(e, r, u, s, l, a))
    }
}
```

- [ ] **2. Verify RED:** chạy ba lớp test; lỗi thiếu API/assert tập key.
- [ ] **3. Implement:** `KEY_TILT_PARALLAX`, `DEFAULT_TILT_PARALLAX = false`, nhánh `getBoolean`, `getTiltParallax/saveTiltParallax`; `TiltGate`; `ResetEntries.lens` thêm entry `UtilSettings.KEY_TILT_PARALLAX, R.string.setting_tilt_parallax, DEFAULT_TILT_PARALLAX, us.getTiltParallax()` (chuỗi `setting_tilt_parallax` thêm ở Task 6; để biên dịch được, **Task 2 thêm tạm chuỗi gốc tiếng Anh vào `values/strings.xml` và Task 6 hoàn thiện 16 locale + mô tả**; `AllStringsTranslationTest` sẽ đỏ đến Task 6, không commit ở trạng thái đỏ nên gộp Task 2 và Task 6 chuỗi vào cùng commit nếu cần).
- [ ] **4. Verify GREEN:** ba lớp test + `UtilSettings*Test` hiện có.
- [ ] **5. Commit:** `feat(tilt): add opt-in setting, gate and reset entry (FISH-033)`.

## Task 3 — TiltSensorSource (TDD)

**Files:** `TiltSensorSource.kt`, `TiltSensorSourceTest.kt` (Mockito `SensorManager`/`Sensor`).

- [ ] **1. RED:** test: `available` false khi `manager == null` hoặc `getDefaultSensor(TYPE_GAME_ROTATION_VECTOR)` null; `start` đăng ký đúng một lần với `SENSOR_DELAY_UI` dù gọi hai lần; `stop` gọi `unregisterListener` đúng một lần và idempotent; `start` sau `stop` đăng ký lại; mẫu có `values.size < 4` suy `w` bằng `sqrt(1 - x²-y²-z²)` an toàn (clamp âm về 0) hoặc bỏ nếu không hữu hạn; mẫu `accuracy == SENSOR_STATUS_UNRELIABLE` bị bỏ; không gọi listener sau `stop`; `registerListener` trả false thì `isRunning` false.
- [ ] **2. Verify RED, 3. Implement, 4. Verify GREEN:** `./gradlew testDevDebugUnitTest --tests '*TiltSensorSourceTest'`. `SensorEventListener` nội bộ không giữ tham chiếu `Activity`; xóa tham chiếu `Listener` trong `stop`.
- [ ] **5. Commit:** `feat(tilt): add lifecycle-safe tilt sensor source (FISH-033)`.

## Task 4 — LensView dùng tâm ảo khi rảnh (TDD, thiết bị)

**Files:** `LensView.kt`, `LensViewTiltIntegrationTest.kt`.

- [ ] **1. RED (androidTest, dùng mẫu `LensViewProfileIntegrationTest` + `setLensStateForTest`/`drawnRectsForTest`):**
  - tắt (không offset) vẽ đúng lưới gốc, bằng rect khi `multiplier = 0`;
  - `setTiltOffset(dx, dy)` khi rảnh làm ít nhất một icon lệch khỏi lưới gốc theo hướng của offset;
  - có chạm thật thì kết quả giống hệt khi không có tilt (tilt bị bỏ qua);
  - `mSelectIndex`/`touchCoordinatesForTest()` không đổi khi chỉ có tilt (không chọn, không rung);
  - `clearTiltOffset` và detach đưa về lưới gốc; `resetToIdleForExport` xóa offset;
  - `LensGridCache.recomputeCount` không tăng khi offset đổi.
- [ ] **2. Verify RED:** `./gradlew assembleDevDebugAndroidTest` → thiếu `setTiltOffset`.
- [ ] **3. Implement:** thêm `private var mTiltX = 0f; mTiltY = 0f; mTiltActive = false`; `setTiltOffset` lưu và `invalidate()`; `clearTiltOffset` đặt cờ false và `invalidate()`; `resetTouchState()` gọi xóa. Trong `drawGrid`: `val useTilt = !hasActiveTouch && mTiltActive && mDrawType == DrawType.APPS`; `val centerX = if (hasActiveTouch) mTouchX else if (useTilt) width / 2f + mTiltX else -Float.MAX_VALUE` (tương tự Y, dùng tâm vùng lưới trừ insets); biến điều kiện áp công thức méo thành `hasLensCenter = hasActiveTouch || useTilt`; `val multiplier = if (hasActiveTouch) animationMultiplier else TiltParallax.ANIMATION_MULTIPLIER`; thay `mTouchX/mTouchY` bằng `centerX/centerY` **chỉ** trong bốn lời gọi `lensProfile.shiftX/shiftY/scaleX/scaleY`; hit-test `isInsideRect`, `DepthOfField.focusDistance` và `dofMode` giữ `mTouchX/Y` và `hasActiveTouch`.
- [ ] **4. Cài APK và chạy lớp** (xem Task 7 bước backup trước; chạy từng lớp bằng `adb -s 115333744A005844 shell am instrument -w -e timeout_msec 60000 -e class com.mckimquyen.views.LensViewTiltIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`, đọc `OK (N tests)`; chạy regression `LensViewProfileIntegrationTest`, `LensViewPinchIntegrationTest`, `LensViewIconSizeIntegrationTest`).
- [ ] **5. Commit:** `feat(tilt): draw idle lens center from tilt offset (FISH-033)`.

## Task 5 — ActHome: đăng ký sensor đúng vòng đời (TDD, thiết bị)

**Files:** `ActHome.java`, `ActHomeTiltWidgetTest.kt`.

- [ ] **1. RED (androidTest):** thêm seam kiểm thử `internal`/`@VisibleForTesting` trên `ActHome`: `tiltSensorForTest(): TiltSensorSource?` và `tiltSensorFactoryForTest` (cho phép tiêm `SensorManager` giả). Test: công tắc tắt thì `isRunning` false sau resume; công tắc bật + resume thì đăng ký đúng một lần; pause thì hủy; recreate không để listener thừa (đếm register − unregister = số đang chạy ≤ 1); search mở thì hủy; reduced-motion (`animator_duration_scale=0` qua `uiAutomation`, khôi phục) thì không đăng ký; chế độ list thì không đăng ký; destroy hủy hoàn toàn.
- [ ] **2. Verify RED:** build androidTest thiếu seam.
- [ ] **3. Implement:** trường `TiltSensorSource tiltSensor` tạo trong `onCreate` từ `getSystemService(SensorManager.class)`; hàm `updateTiltParallax()` tính `TiltGate.shouldRun(utilSettings.getTiltParallax(), LensPhysicsPolicy.shouldReduceLensMotion(this), resumedFlag, searchView.isShowing(), utilSettings.isListMode(), tiltSensor.getAvailable())` rồi `start`/`stop` và `lensViews.clearTiltOffset()` khi dừng; gọi từ `onResume` (sau khi đặt `resumedFlag = true`), từ `onPause` (đặt false, `stop`), từ `onDestroy` (`stop`, bỏ tham chiếu), khi search đổi trạng thái (vị trí hiện có gọi `updateModeVisibility`/hide-show), và khi đổi trang (`onPageSelected` hiện có → xóa offset trang cũ). Listener tính `TiltParallax.Filter.update(..., rotation, areaPx)` với `rotation = getDisplay().getRotation()` và `areaPx = min(lensViews.width, lensViews.height)`; nếu đổi quá ngưỡng thì `lensViews.setTiltOffset(filter.offsetX, filter.offsetY)`. `Filter.reset()` mỗi lần `start`. Thêm `onPause` gọi `super.onPause()`.
- [ ] **4. Chạy lớp mới trên TECNO** + regression `ActHomeLensManagementWidgetTest` (29), `ActHomeMultiLensWidgetTest` (4).
- [ ] **5. Commit:** `feat(tilt): drive tilt offset from ActHome lifecycle (FISH-033)`.

## Task 6 — UI công tắc + 17 locale (TDD)

**Files:** `FrmLens.kt`, `frm_lens.xml`, 17 `strings.xml`, `FrmLensTiltWidgetTest.kt`, `LensStringTranslationTest.kt` nếu cần.

- [ ] **1. RED:** widget test: công tắc `swLensTiltParallax` tồn tại, mặc định tắt, bật ghi `tilt_parallax=true`, `resetToDefaults` đưa về tắt, mô tả `tvLensTiltParallaxDesc` chứa cảnh báo (kiểm bằng so khớp chuỗi tài nguyên `setting_tilt_parallax_desc`), bind không ghi prefs.
- [ ] **2. Implement:** thêm khối `MaterialSwitch` + `TextView` mô tả ngay dưới khối elastic overscroll trong `frm_lens.xml` (cùng style/padding); `FrmLens` find/listener/bind/null như `swLensElasticOverscroll`. Chuỗi `setting_tilt_parallax` và `setting_tilt_parallax_desc` (mô tả nêu rõ: lens nghiêng nhẹ theo máy khi không chạm; có thể gây chóng mặt hoặc buồn nôn với người nhạy cảm với chuyển động; tăng nhẹ tiêu thụ pin) ở đủ 17 locale; chạy `AllStringsTranslationTest` và `LensStringTranslationTest`.
- [ ] **3. Verify GREEN:** unit liên quan + widget trên TECNO + `FrmLensWidgetTest` hiện có.
- [ ] **4. Commit:** `feat(tilt): add opt-in tilt parallax setting UI (FISH-033)`.

## Task 7 — Backup TECNO, mutation, gate, smoke, audit

- [ ] **1. Gate máy chủ:** chạy đúng lệnh trong Global Constraints; ghi tổng unit/fail/skip, lint, SHA APK.
- [ ] **2. Khóa & backup TECNO:** `adb devices -l`, xác nhận serial; `force-stop`; kéo APK chính + test; `run-as tar` data (loại `./lib`); ghi hash từng file ra `data-hashes.json`; ghi font/locale/HOME role/animator scale; lưu `/tmp/f033-tecno-<timestamp>/`, không ghi đè thư mục cũ.
- [ ] **3. Mutation (từng cái một, hoàn nguyên ngay, đo bằng assertion không phải lỗi biên dịch):** bỏ vùng chết; bỏ clamp; đảo dấu trục ngang; bỏ gate reduced-motion; `TiltGate` coi `searchShowing` như false; bỏ `stop()` ở `onPause`; đăng ký trùng (bỏ idempotent); tilt vẫn áp khi `hasActiveTouch`; để tâm ảo rò vào `mTouchX`; không xóa offset khi detach; mặc định bật. Mutant sống phải được giải thích (tương đương) hoặc thêm test.
- [ ] **4. Smoke tay (chỉ TECNO KJ7, serial tường minh):** cài APK cuối; bật công tắc (Settings → Lens); nghiêng máy trái/phải/trước/sau và chụp ảnh từng trạng thái; chạm lens (tilt nhường); mở search (dừng); quay màn hình; tắt công tắc (về lưới gốc). Đếm listener sensor bằng `adb -s ... shell dumpsys sensorservice` trước/bật/pause/tắt để chứng minh không rò. Đo số mẫu/frame nếu có thể (`dumpsys gfxinfo`), nếu không đo được pin thật thì ghi rõ "chưa đo". Gặp quảng cáo che UI thì dừng chờ owner `done`.
- [ ] **5. Khôi phục:** cài lại APK gốc, khôi phục data từ backup, so hash từng file 1:1, đối chiếu font/locale/HOME/animator scale. Không dùng `rm` với glob qua `run-as`; dùng `find ... -delete` hoặc đường dẫn đầy đủ có dấu ngoặc kép.
- [ ] **6. Audit:** `doc/task/AUDIT_FISH-033_<ngày>.md` với số thật, mutation, smoke, giới hạn chưa đo (pin, thiết bị khác), điểm trung thực; chỉ đóng nếu > 9.0.
- [ ] **7. Trạng thái:** `todo/` → `inprogress/` khi bắt đầu, → `done/` khi đóng; cập nhật `doc/feature.md` và `doc/task/README.md` ngay sau mỗi quyết định. Commit cục bộ, không push, không stage `.idea/`.

## Tự rà plan

- [x] Spec: gate (Task 2/5), sensor (Task 3), toán (Task 1), LensView (Task 4), UI + 17 locale + cảnh báo (Task 6), reset (Task 2), mutation/smoke/đo (Task 7).
- [x] Chữ ký nhất quán giữa Interface và các task (`Filter.update(qx,qy,qz,qw,rotation,areaPx)`, `TiltGate.shouldRun` 6 tham số, `TiltSensorSource.start(Listener)`).
- [x] Tâm ảo không rò vào hit-test/DoF/`drawTouchSelection` (quyết định kỹ thuật bổ sung).
- [x] Một thiết bị duy nhất, ghi rõ trong audit; không khẳng định cho máy khác.
- [x] Chuỗi Task 2/6 phụ thuộc nhau đã được nêu cách gộp commit.
- [x] Test Task 1 viết đủ mã. Các task khác mô tả hành vi test theo bảng và hàm mẫu; mã đầy đủ viết khi thực hiện theo đúng bảng đó.
