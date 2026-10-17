# FISH-025 — Vuốt cạnh chuyển nhanh Lens (Edge Swipe Lens Switch) Spec

## Mục tiêu

Cung cấp cử chỉ chuyển đổi tức thì giữa các không gian Lens trên màn hình chính bằng thao tác vuốt 1 tay từ mép màn hình (Edge Swipe), tiện lợi cho người dùng sử dụng điện thoại màn hình lớn mà không cần với ngón tay lên thanh chỉ báo (dots indicator) hay mở menu popup.

## Quyết định thiết kế

### 1. Phân loại cử chỉ & Vùng kích hoạt mép

- **Enum trạng thái chế độ mép `EdgeSwipeMode`**:
  ```kotlin
  enum class EdgeSwipeMode {
      OFF,
      BOTH,        // Mặc định: kích hoạt cả 2 mép
      RIGHT_ONLY,  // Chỉ mép phải
      LEFT_ONLY    // Chỉ mép trái
  }
  ```
- **Vùng kích hoạt cạnh (Activation Zone)**:
  - Bắt đầu khi ngón tay chạm xuống (`ACTION_DOWN`) tại vùng sát mép màn hình:
    - Mép trái: `0 <= downX <= edgeThreshold` (với `edgeThreshold = 32dp`).
    - Mép phải: `viewWidth - edgeThreshold <= downX <= viewWidth`.
    - Trục dọc: Không nằm trong vùng 20% trên cùng (tránh tranh chấp với cử chỉ vuốt kéo tìm kiếm iOS pull-down search của FISH-016).
- **Phân định cử chỉ ngang (Swipe Intent)**:
  - Cử chỉ được xác nhận khi:
    - Vuốt từ mép trái sang phải: `dx > touchSlop * 2` và `dx > abs(dy) * 1.5f`.
    - Vuốt từ mép phải sang trái: `-dx > touchSlop * 2` và `-dx > abs(dy) * 1.5f`.
  - Khi xác nhận, giải phóng quyền kiểm soát (hoặc điều khiển `ViewPager2.setCurrentItem(nextIndex, true)`) và ngăn `LensView` pan phóng to icon.

### 2. Pure Policy Logic

Tách rời toán học và điều kiện cử chỉ thành pure engine `EdgeSwipePolicy` hoàn toàn không phụ thuộc Android Context:
```kotlin
object EdgeSwipePolicy {
    enum class Direction { NONE, PREVIOUS, NEXT }

    @JvmStatic
    fun resolveIntent(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        viewWidth: Float,
        viewHeight: Float,
        edgeThresholdPx: Float,
        touchSlopPx: Float,
        mode: EdgeSwipeMode,
        isSearchActivationZone: Boolean
    ): Direction
}
```

### 3. Phản hồi xúc giác (Haptics)

- Khi cử chỉ chuyển Lens được kích hoạt thành công, phát một rung phản hồi xúc giác nhẹ (Haptic feedback) thông qua `Vibrator` / `HapticIntensity` để người dùng cảm nhận rõ chuyển trang đã thực hiện.

### 4. Cài đặt trong Settings

- Key SharedPreferences: `UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH` (`edge_swipe_lens_switch`), mặc định `"BOTH"`.
- UI trong `FrmSettings` (tab Settings):
  - Hàng cài đặt "Vuốt cạnh chuyển Lens" với icon điều hướng hai chiều `ic_swap_horiz_24dp`.
  - Nhấp vào mở Dialog chọn 1 trong 4 lựa chọn (Single choice):
    - Tắt (`OFF`)
    - Cả hai mép (`BOTH` - Mặc định)
    - Chỉ mép phải (`RIGHT_ONLY`)
    - Chỉ mép trái (`LEFT_ONLY`)
  - Nhãn phụ (subtitle) phản ánh lựa chọn hiện tại.
  - Hỗ trợ đầy đủ 17 locales cho tiêu đề và 4 nhãn lựa chọn.

### 5. Tương thích & An toàn

- Khi chỉ có 1 Lens duy nhất (`lensCount <= 1`): Cử chỉ tự động bỏ qua để không gây giật màn hình hoặc vô hiệu hóa pan fisheye.
- Khi đang ở Clean Mode: Cử chỉ vuốt cạnh vẫn hoạt động bình thường, giúp người dùng chuyển đổi các workspace ngay cả khi giao diện đã ẩn hết mọi thanh điều hướng.

## Verification Gate

- Unit test: `EdgeSwipePolicyTest` (bao phủ mọi góc, biên, hướng, độ ưu tiên).
- Unit test: `UtilSettingsEdgeSwipeTest` (default BOTH, get/save persistence, fallback an toàn).
- Unit test: `EdgeSwipeStringsTest` (đủ 17 locales cho 5 chuỗi mới).
- Widget test: `ActHomeEdgeSwipeWidgetTest` trên thiết bị thật (mô phỏng chạm mép, xác nhận `ViewPager2.currentItem` thay đổi đúng trang).
- Widget test: `FrmSettingsEdgeSwipeWidgetTest` (mở dialog chọn chế độ, lưu SharedPreferences).
- Device testing: Xác minh trên TECNO KJ7 `115333744A005844`.
- Smoke test tay trên thiết bị thật, bảo đảm không có quảng cáo che màn hình.
- Audit tự chấm đạt điểm >9.0/10.
