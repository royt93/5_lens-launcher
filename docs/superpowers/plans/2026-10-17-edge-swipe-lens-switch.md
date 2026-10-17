# FISH-025 — Edge Swipe Lens Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép chuyển qua lại giữa các Lens bằng vuốt ngang từ mép màn hình, cấu hình được OFF/BOTH/RIGHT_ONLY/LEFT_ONLY, không làm hỏng lens pan, pinch, pull-down search hoặc Android Back.

**Architecture:** Pure Kotlin `EdgeSwipePolicy` tính hướng vuốt từ tọa độ down/move, kích thước view, touch slop và chế độ mép. `LensView` nhận diện cử chỉ trước khi bắt đầu lens pan, rồi gọi listener để `ActHome` cập nhật `ViewPager2` và haptic; `UtilSettings` lưu chế độ và `FrmSettings` cung cấp single-choice dialog.

**Tech Stack:** Kotlin/Java, Android `MotionEvent`/Canvas View touch dispatch, AndroidX `ViewPager2`, SharedPreferences, Material 3, JUnit4, Android instrumentation.

## Global Constraints

- Thiết bị duy nhất: TECNO KJ7 `115333744A005844`; nếu không còn kết nối, dừng và hỏi owner. Không dùng emulator hoặc thiết bị khác.
- Không chạy Gradle `connected*`; instrumentation bằng `adb -s 115333744A005844 shell am instrument`.
- Không thêm dependency, Room migration hoặc đổi schema.
- TDD bắt buộc: test fail đúng trước mỗi production change.
- FISH-016 đang dành vùng 20% phía trên để pull-down search; cạnh-swipe phải bỏ qua vùng đó.
- Android system Back gesture có ưu tiên ở cạnh trái; chỉ giữ interception sau khi hướng Lens switch được xác nhận, tránh giữ mọi cử chỉ cạnh.
- Chuyển Lens ở biên đầu/cuối phải clamp; không wrap-around, không rung ở biên nếu trang không đổi.
- R4: dừng screenshot smoke ngay nếu có quảng cáo che UI; không tự chấp thuận consent.
- R5: không `late`/force unwrap mới; xóa view/listener tham chiếu ở `onDestroyView()`.
- 17 locale đầy đủ cho mọi string/resource mới.
- Không commit/push trong các task triển khai; owner quyết định riêng sau audit.

---

## File Map

- `app/src/main/java/com/mckimquyen/model/EdgeSwipeMode.kt`: enum với parse an toàn.
- `app/src/main/java/com/mckimquyen/gesture/EdgeSwipePolicy.kt`: pure direction policy; không phụ thuộc Android.
- `app/src/main/java/com/mckimquyen/views/LensView.kt`: theo dõi down point và chuyển gesture khỏi pan khi edge-swipe được xác nhận; listener callback.
- `app/src/main/java/com/mckimquyen/ui/ActHome.java`: listener gọi `lensPager.setCurrentItem()` trong giới hạn trang và phát haptic khi trang thực sự đổi.
- `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`: key, default, parse/store mode.
- `app/src/main/res/layout/frm_settings.xml`, `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`: hàng chế độ vuốt cạnh và dialog single-choice.
- `app/src/main/res/values*/strings.xml`: label/title/summary 17 locale.
- Tests trong `app/src/test/java/...` và `app/src/androidTest/java/...` cho pure policy, persistence, localization, Settings UI, gesture/page integration.
- `doc/task/inprogress/p2-fish-fish-025-edge-swipe-lens-switch.md`, `doc/task/AUDIT_FISH-025_2026-10-17.md`, `doc/feature.md` cập nhật theo cổng hoàn tất.

---

### Task 1: Enum và pure gesture policy

**Files:**
- Create: `app/src/main/java/com/mckimquyen/model/EdgeSwipeMode.kt`
- Create: `app/src/main/java/com/mckimquyen/gesture/EdgeSwipePolicy.kt`
- Create: `app/src/test/java/com/mckimquyen/gesture/EdgeSwipePolicyTest.kt`

**Interfaces:**
- `enum class EdgeSwipeMode { OFF, BOTH, RIGHT_ONLY, LEFT_ONLY }`
- `enum class EdgeSwipeDirection { NONE, PREVIOUS, NEXT }`
- `EdgeSwipePolicy.resolveIntent(downX, downY, currentX, currentY, viewWidth, viewHeight, edgeThresholdPx, touchSlopPx, mode, isSearchActivationZone): EdgeSwipeDirection`
- Mép trái: chỉ mode BOTH/LEFT_ONLY, dx dương, vượt `touchSlopPx * 2`, `dx > abs(dy) * 1.5f`.
- Mép phải: chỉ mode BOTH/RIGHT_ONLY, dx âm, `-dx > touchSlopPx * 2`, `-dx > abs(dy) * 1.5f`.
- Cả hai mép bỏ qua top 20%, tọa độ ngoài view, mode OFF, view width/height không dương.

- [ ] **Step 1: Viết policy tests trước**

Test cases tối thiểu:
```kotlin
@Test fun leftEdgeSwipeRight_returnsPrevious()
@Test fun rightEdgeSwipeLeft_returnsNext()
@Test fun centerSwipe_returnsNone()
@Test fun verticalOrDiagonalSwipe_returnsNone()
@Test fun movementAtThreshold_returnsNone()
@Test fun disabledOrWrongEdgeMode_returnsNone()
@Test fun topSearchActivationZone_returnsNone()
@Test fun outOfBoundsAndZeroSize_returnsNone()
```
Assert `PREVIOUS`/`NEXT`/`NONE` bằng giá trị cụ thể; test boundary equality để khóa strict `>` behavior.

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.gesture.EdgeSwipePolicyTest`
Expected: compile failure do các type/function chưa tồn tại.

- [ ] **Step 3: Viết enum/parser và pure policy tối thiểu**

```kotlin
enum class EdgeSwipeMode {
    OFF, BOTH, RIGHT_ONLY, LEFT_ONLY;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): EdgeSwipeMode =
            entries.firstOrNull { it.name == value } ?: BOTH
    }
}

enum class EdgeSwipeDirection { NONE, PREVIOUS, NEXT }

object EdgeSwipePolicy {
    private const val TOP_SEARCH_ZONE_RATIO = 0.20f
    private const val HORIZONTAL_DOMINANCE_RATIO = 1.5f
    private const val TOUCH_SLOP_MULTIPLIER = 2f

    @JvmStatic
    fun resolveIntent(
        downX: Float, downY: Float, currentX: Float, currentY: Float,
        viewWidth: Float, viewHeight: Float, edgeThresholdPx: Float, touchSlopPx: Float,
        mode: EdgeSwipeMode, isSearchActivationZone: Boolean
    ): EdgeSwipeDirection {
        if (mode == EdgeSwipeMode.OFF || viewWidth <= 0f || viewHeight <= 0f ||
            edgeThresholdPx <= 0f || touchSlopPx < 0f || isSearchActivationZone ||
            downX < 0f || downX > viewWidth || downY < 0f || downY > viewHeight ||
            downY <= viewHeight * TOP_SEARCH_ZONE_RATIO
        ) return EdgeSwipeDirection.NONE

        val fromLeft = downX <= edgeThresholdPx
        val fromRight = downX >= viewWidth - edgeThresholdPx
        val dx = currentX - downX
        val dy = currentY - downY
        if (abs(dx) <= touchSlopPx * TOUCH_SLOP_MULTIPLIER ||
            abs(dx) <= abs(dy) * HORIZONTAL_DOMINANCE_RATIO
        ) return EdgeSwipeDirection.NONE

        if (fromLeft && dx > 0f && mode in setOf(EdgeSwipeMode.BOTH, EdgeSwipeMode.LEFT_ONLY)) {
            return EdgeSwipeDirection.PREVIOUS
        }
        if (fromRight && dx < 0f && mode in setOf(EdgeSwipeMode.BOTH, EdgeSwipeMode.RIGHT_ONLY)) {
            return EdgeSwipeDirection.NEXT
        }
        return EdgeSwipeDirection.NONE
    }
}
```

Implementation must avoid allocating `setOf(...)` per move; prefer `when (mode)` or named pure predicates. The snippet above defines contract, not an allocation instruction.

- [ ] **Step 4: Chạy focused tests để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.gesture.EdgeSwipePolicyTest`
Expected: all policy cases pass.

- [ ] **Step 5: Commit**

Không commit trong phiên này; giữ checkpoint diff để owner review.

---

### Task 2: SharedPreferences mode persistence

**Files:**
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsEdgeSwipeTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`

**Interfaces:**
- `UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH = "edge_swipe_lens_switch"`
- `UtilSettings.DEFAULT_EDGE_SWIPE_LENS_SWITCH = EdgeSwipeMode.BOTH`
- `getEdgeSwipeMode(): EdgeSwipeMode`, `saveEdgeSwipeMode(mode: EdgeSwipeMode)`

- [ ] **Step 1: RED tests**

Test default `BOTH`, save/read all 4 modes through a new `UtilSettings(context)`, and corrupt stored values falling safely back to `BOTH`.

- [ ] **Step 2: Chạy test RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsEdgeSwipeTest`
Expected: unresolved references.

- [ ] **Step 3: Minimal implementation**

Use established `.name` stable-enum persistence; catch wrong preference type using `runCatching` like sibling enum prefs. Do not make a per-lens preference: gesture mode is global launcher navigation setting.

- [ ] **Step 4: Chạy test GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.UtilSettingsEdgeSwipeTest`
Expected: all persistence tests pass.

- [ ] **Step 5: No commit**

---

### Task 3: Wire edge gesture into LensView and ActHome pager

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/views/LensView.kt`
- Modify: `app/src/main/java/com/mckimquyen/ui/ActHome.java`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/ActHomeEdgeSwipeWidgetTest.kt`

**Interfaces:**
- `LensView.OnEdgeSwipeListener.onEdgeSwipe(direction: EdgeSwipeDirection)`
- `LensView.onEdgeSwipeListener: OnEdgeSwipeListener?`
- `LensView` reads `EdgeSwipeMode` via its existing `mUtilSettings` dependency and calls pure policy on `ACTION_MOVE` only while candidate began at `ACTION_DOWN`.
- `ActHome` maps `PREVIOUS` -> current page - 1, `NEXT` -> current page + 1; clamp to `[0, itemCount-1]`.

- [ ] **Step 1: Add instrumentation test for pager behavior**

Open actual multi-lens Home; ensure at least two lenses in test setup via existing Room workspace fixture, and dispatch `MotionEvent` from valid left/right edge through the active `LensView`. Assert `lensPager.currentItem` changes in correct direction; assert center pan and blocked mode do not page; assert one-lens and edge-boundary attempts do not wrap.

- [ ] **Step 2: Run targeted test to confirm RED**

Build and run only on locked TECNO KJ7 using explicit serial:
```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s 115333744A005844 install -r -t app/build/outputs/apk/dev/debug/com.mckimquyen.lenslauncherdebug_2026.10.07_20261007.apk
adb -s 115333744A005844 install -r -t app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.ui.ActHomeEdgeSwipeWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: tests fail because `LensView` has no edge listener/wiring and pager stays unchanged.

- [ ] **Step 3: Add testable gesture state and callback**

In `LensView.ACTION_DOWN`, save downX/downY and initialize edge candidate before normal pan. In `ACTION_MOVE`, call `EdgeSwipePolicy.resolveIntent(...)`. Once direction is non-NONE, mark gesture consumed, cancel long-press, request parent interception disallowed only for this confirmed lens-switch gesture, invoke listener once, and prevent `mMoving`/pan start. If candidate fails because movement is vertical/diagonal or began away from edge, continue existing `LensView` / ViewPager2 behavior unchanged. Keep FISH-016 top search priority by policy exclusion.

In `ActHome.bindLensView` / existing listener-wiring point, set listener. Use the already existing pager. If direction points beyond first/last, leave page unchanged and do not haptic. If page changes, call `lensPager.setCurrentItem(target, true)` once.

Use existing haptic settings (`KEY_VIBRATE_APP_HOVER`, `KEY_HAPTIC_INTENSITY`) and existing `LensView.performHapticFeedback` conventions; haptic only after target index is valid and differs from current.

- [ ] **Step 4: Run widget integration tests GREEN**

Run the same direct instrumentation command; verify all direction, no-wrap, disabled-mode, vertical-pan and single-lens tests pass.

- [ ] **Step 5: No commit**

---

### Task 4: Localized Settings mode picker

**Files:**
- Create: `app/src/test/java/com/mckimquyen/util/EdgeSwipeStringsTest.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/ui/FrmSettingsEdgeSwipeWidgetTest.kt`
- Modify: `app/src/main/res/layout/frm_settings.xml`
- Modify: `app/src/main/java/com/mckimquyen/ui/FrmSettings.kt`
- Modify: all 17 `app/src/main/res/values*/strings.xml`
- Add or reuse a vector icon only after checking for existing horizontal-swap/navigation icon; no new dependency.

**Interfaces:**
- String resources: `setting_edge_swipe_lens_switch`, `edge_swipe_mode_off`, `edge_swipe_mode_both`, `edge_swipe_mode_right`, `edge_swipe_mode_left`.
- Single-choice dialog shows 4 translated labels and current mode as checked row; selection saves and immediately updates summary.

- [ ] **Step 1: Add string coverage test first**

Mirror `FocusHaloStringsTest`/`SearchFilterStringsTest`: discover only supported locale dirs and assert five keys exist and are non-empty in all 17.

- [ ] **Step 2: Run test to confirm RED**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.EdgeSwipeStringsTest`
Expected: missing key failure.

- [ ] **Step 3: Add localized strings to all 17 locales**

Add meaningful native translations for title and four modes. Avoid adding unused strings; test exactly every referenced resource key.

- [ ] **Step 4: Run string test GREEN**

Run: `./gradlew testDevDebugUnitTest --tests com.mckimquyen.util.EdgeSwipeStringsTest`
Expected: all 17 locales valid.

- [ ] **Step 5: Widget RED, then wire UI**

Write `FrmSettingsEdgeSwipeWidgetTest` to assert default summary is BOTH, dialog opens with four choices, choosing each mode updates `UtilSettings`, summary, and re-opening selects saved row. Prove RED first on KJ7, then add row using existing settings-card style and use `MaterialAlertDialogBuilder.setSingleChoiceItems(...)`. Store/null dialog and view references using established lifecycle cleanup; no new persistent listener leak.

- [ ] **Step 6: Run Settings widget test GREEN on KJ7**

Use direct `adb -s 115333744A005844 shell am instrument ...` only.

- [ ] **Step 7: No commit**

---

### Task 5: Full regression, smoke, documentation and audit

**Files:**
- Create: `doc/task/inprogress/p2-fish-fish-025-edge-swipe-lens-switch.md`
- Create: `doc/task/AUDIT_FISH-025_2026-10-17.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Full JVM unit regression + lint**

Run: `./gradlew testDevDebugUnitTest lintDevDebug`.
Expected: all JVM tests pass, 0 lint errors, warnings count compared to pre-change baseline.

- [ ] **Step 2: Full affected instrumentation on TECNO KJ7**

Build both APKs sequentially, install only with `adb -s 115333744A005844`, execute affected classes directly via `am instrument`. Do not use Gradle `connected*`.

- [ ] **Step 3: Verify test-device availability before install/run**

Run `adb devices -l` before the device phase. Verify KJ7 `115333744A005844` remains attached; ignore all other devices. If absent, stop without attempting any other target.

- [ ] **Step 4: Manual smoke on TECNO KJ7**

Test all four modes (BOTH, RIGHT_ONLY, LEFT_ONLY, OFF), both directions where enabled, single-lens no-op, first/last lens no-wrap, vertical drag still pans lens, top pull-down still opens search, clean mode still permits edge switching, and haptic occurs only on successful page change. Inspect screenshot before every next smoke action; if an ad overlays UI, stop and ask owner. Do not alter/accept consent without owner.

- [ ] **Step 5: Restore device state**

Restore original lens count/active lens, mode choice, Clean Mode, language and any preferences changed during smoke; confirm via device UI/settings or app prefs.

- [ ] **Step 6: Run secret and diff checks**

Run: `bash scripts/check-secrets.sh && git diff --check`.
Expected: secret scan exit 0; diff check clean.

- [ ] **Step 7: Write audit and update feature tracker**

Only claim passed checks backed by current output. Audit score must be strictly >9.0 to pass the project gate. If device smoke is blocked, write the exact limitation and do not claim completion or recommend push.

- [ ] **Step 8: No commit/push**

Wait for explicit owner instruction after the audit.
