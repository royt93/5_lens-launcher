# FISH-026 — Hiệu ứng đàn hồi quán tính thấu kính (Lens Elastic Overscroll Physics) Spec

## Mục tiêu

Cung cấp phản hồi xúc giác và thị giác mượt mà, đàn hồi tự nhiên (Spring/Elastic Overscroll Physics) khi người dùng kéo thấu kính fisheye chạm hoặc vượt qua mép lưới ứng dụng trên màn hình chính, tạo cảm giác thấu kính có trọng lượng và lực cản vật lý linh hoạt, tránh dừng đột ngột (hard stop) gây cảm giác thô cứng.

## Quyết định thiết kế

### 1. Thuật toán cản đàn hồi (Logarithmic Damping)

Tách rời toán học thành pure engine `LensOverscrollPhysics`:
- **Đầu vào**: Tọa độ kéo hiện tại `(x, y)`, giới hạn biên lưới `[minX, maxX, minY, maxY]`, khoảng kéo dãn tối đa `maxOverscrollPx` (mặc định tương đương `28dp`), hệ số cản `dampingFactor = 0.55f`.
- **Hàm cản logarit**:
  - Khi tọa độ `x` vượt quá `maxX`:
    $$\Delta = x - \text{maxX}$$
    $$\text{offset} = \text{maxOverscrollPx} \times \left(1 - \frac{1}{1 + \frac{\Delta \times \text{dampingFactor}}{\text{maxOverscrollPx}}}\right)$$
    Tọa độ thực tế của tâm thấu kính: $x_{\text{effective}} = \text{maxX} + \text{offset}$.
  - Tương tự cho biên trái ($x < \text{minX}$), biên trên ($y < \text{minY}$) và biên dưới ($y > \text{maxY}$).
- **0 Allocation**: Hàm toán học thuần nhận và trả về kiểu `Float`, không cấp phát object nào trên hot path `onTouchEvent / onDraw`.

### 2. Hành vi khi nhấc tay (Spring Release)

- Khi người dùng nhấc tay (`ACTION_UP`) hoặc hủy cử chỉ (`ACTION_CANCEL`):
  - Nếu tâm thấu kính đang ở trạng thái overscroll (vượt biên), khoảng overscroll này co về 0 theo animation co thấu kính (`LensAnimation(false)`).
  - Không làm lệch vị trí mở ứng dụng: việc chọn ứng dụng (`mSelectIndex`) vẫn dựa trên hit-test thực tế của ô lưới.

### 3. Điều kiện Gate & Accessibility

- **Giảm chuyển động (Reduced Motion)**:
  - Khi `LensPhysicsPolicy.shouldReduceLensMotion(context)` trả về `true` (do cài đặt Developer Options `animator_duration_scale = 0`, chế độ tiết kiệm pin hoặc máy quá nhiệt):
  - Tự động bỏ qua hiệu ứng đàn hồi, clamp cứng tọa độ tại biên `[minX, maxX]` và `[minY, maxY]`, đảm bảo khả năng tiếp cận (Accessibility) và tiết kiệm tài nguyên.

### 4. Cài đặt trong Settings

- **Toggle Switch trong `FrmSettings` (tab Lens)**:
  - Tiêu đề: "Hiệu ứng đàn hồi thấu kính" (`setting_lens_elastic_overscroll`).
  - Mô tả: "Tạo lực cản đàn hồi tự nhiên khi kéo thấu kính chạm mép viền" (`setting_lens_elastic_overscroll_desc`).
  - Key SharedPreferences: `UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL` (`lens_elastic_overscroll`), boolean, **mặc định `true`**.
  - Reset-to-defaults: Khôi phục về `true`.
- **17 Locales**: Bản dịch đầy đủ 17 ngôn ngữ cho 2 chuỗi mới.

## Verification Gate

- Unit test: `LensOverscrollPhysicsTest` (kiểm tra tính toán tọa độ trong biên không đổi, vượt biên trên/dưới/trái/phải bị hãm đúng tỷ lệ logarit, không bao giờ vượt quá `maxOverscrollPx`).
- Unit test: `UtilSettingsElasticOverscrollTest` (mặc định true, lưu và đọc lại giá trị, reset-to-defaults).
- Unit test: `LensElasticOverscrollStringsTest` (17 locales đủ 2 keys mới).
- Widget test: `LensViewElasticOverscrollWidgetTest` trên thiết bị thật (mô phỏng kéo chạm mép, xác nhận tâm thấu kính có tọa độ overscroll khi bật và clamp cứng khi tắt).
- Widget test: `FrmSettingsElasticOverscrollWidgetTest` (toggle switch trong Settings lưu SharedPreferences).
- Device testing: Xác minh trên Samsung S24 Ultra `R5CX613VZBR`.
- Smoke test tay trên thiết bị thật, bảo đảm không có quảng cáo che màn hình.
- Audit tự chấm đạt điểm >9.0/10.
