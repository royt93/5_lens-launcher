# FISH-024 — Vòng sáng tiêu điểm thấu kính (Focus Accent Halo / Ring) Spec

## Mục tiêu

Cung cấp phản hồi thị giác trực quan, thanh lịch và chính xác khi người dùng chạm hoặc kéo thấu kính fisheye trên màn hình chính: một vòng tròn viền mảnh (Accent Ring) bao quanh icon app đang được tiêu điểm hóa (phóng to cực đại) tại vị trí ngón tay, giúp định vị tức thì ứng dụng sẽ được mở khi nhấc tay.

## Quyết định thiết kế

1. **Kiểu dáng & Vị trí:**
   - Vẽ vòng tròn đồng tâm tại `(mRectToSelect.centerX(), mRectToSelect.centerY())`.
   - Bán kính: `radius = (mRectToSelect.width() / 2f) + padding` (với `padding = 4dp`, `strokeWidth = 2.5dp`).
   - Style: `Paint.Style.STROKE`, `isAntiAlias = true`.

2. **Màu sắc & Độ trong suốt:**
   - Màu viền lấy từ `UtilSettings.KEY_HIGHLIGHT_COLOR` (đồng bộ với màu người dùng chọn hoặc Material You dynamic theme).
   - Độ trong suốt (alpha) đồng bộ theo animation phóng to/thu nhỏ của thấu kính (`mAnimationMultiplier` và trạng thái `mAnimationHiding`).

3. **Cài đặt & Hành vi Clean Mode:**
   - Key SharedPreferences mới: `UtilSettings.KEY_SHOW_FOCUS_HALO` (`show_focus_halo`), boolean, mặc định `true`.
   - Thêm switch trong màn hình Cài đặt (`FrmLens`) tại tab Lens: "Vòng sáng tiêu điểm" ("Focus ring indicator") kèm phụ đề mô tả.
   - Hỗ trợ Clean Mode: tự động ẩn vòng sáng khi `KEY_CLEAN_LENS_MODE` được bật.
   - Pure policy: `LensView.shouldDrawFocusHalo(enabled: Boolean, cleanMode: Boolean): Boolean = enabled && !cleanMode`.

4. **Hiệu năng & Bất biến hình học:**
   - `mPaintFocusHalo` được khởi tạo 1 lần duy nhất trong `LensView.initPaints()`.
   - 0 allocation trên hot path `onDraw()` khi người dùng rê ngón tay liên tục.
   - Không thay đổi toán học thấu kính, không sửa đổi `LensGridCache` hay hit-test logic.

5. **Đa ngôn ngữ (I18n):**
   - Đầy đủ bản dịch cho 2 chuỗi mới (`title_show_focus_halo`, `desc_show_focus_halo`) trên toàn bộ 17 locales của ứng dụng.

## Verification Gate

- Unit test: `shouldDrawFocusHalo` policy test.
- Unit test: `UtilSettings` getter/setter và default value test.
- Unit test: 17 locales string completeness test.
- Widget test: `LensView` render vòng sáng khi hover app, không vẽ khi tắt toggle hoặc khi Clean Mode bật.
- Widget test: `FrmLens` switch tương tác và lưu cài đặt.
- Device testing: chạy trên TECNO BG6 `118743744X002560`.
- Smoke test tay trên thiết bị thật, kiểm tra giao diện trực quan và bảo đảm không có quảng cáo che màn hình.
