# SEARCH-010 — Bàn phím số T9 trong SearchView (T9 Dialpad Search) Spec

## Mục tiêu

Cung cấp bàn phím số 9-12 nút (T9 Dialpad) gắn cố định ở đáy màn hình `SearchView`, hỗ trợ người dùng tìm kiếm và khởi chạy ứng dụng nhanh chóng bằng một tay thông qua dãy số tương ứng với chữ cái tên ứng dụng (chuẩn ITU E.161), tối ưu cho thiết bị màn hình lớn và khi đang di chuyển.

## Quyết định thiết kế

### 1. Bố cục & Vị trí Bàn phím T9

- **Vị trí**: Đặt cố định ở đáy màn hình `SearchView` (`act_home.xml`), nằm dưới `searchResults` (RecyclerView) và danh sách kết quả. Danh sách kết quả tự động co giãn kích thước bằng `layout_weight="1"` để không bị bàn phím che khuất.
- **Bố cục 12 phím (4 hàng x 3 cột)**:
  - Hàng 1: `1` (.,-), `2` (ABC), `3` (DEF)
  - Hàng 2: `4` (GHI), `5` (JKL), `6` (MNO)
  - Hàng 3: `7` (PQRS), `8` (TUV), `9` (WXYZ)
  - Hàng 4: `C` (Xóa tất cả), `0` (Khoảng trắng `␣`), `⌫` (Backspace xóa 1 ký tự)
- **Thiết kế phím**:
  - Tái sử dụng `MaterialButton` hoặc `Surface` Material 3 bo góc (corner 12dp-16dp), màu nền `colorSurfaceContainerLow`/`colorSurfaceContainer`.
  - Nhãn số lớn (TitleLarge / 20sp, bold) và nhãn chữ cái phụ nhỏ (BodySmall / 11sp, uppercase).
  - Phản hồi xúc giác: phát rung haptic nhẹ (`VIRTUAL_KEY`) khi chạm phím.
  - Phím `⌫` hỗ trợ cả click (xóa 1 số) và long-click (xóa toàn bộ).

### 2. Thuật toán Pure Engine `T9SearchEngine`

Hoàn toàn độc lập với Android Context, xử lý chuỗi và xếp hạng kết quả:
- **Bảng ánh xạ ký tự ITU E.161**:
  - `2`: `[a, b, c, 2]`
  - `3`: `[d, e, f, 3]`
  - `4`: `[g, h, i, 4]`
  - `5`: `[j, k, l, 5]`
  - `6`: `[m, n, o, 6]`
  - `7`: `[p, q, r, s, 7]`
  - `8`: `[t, u, v, 8]`
  - `9`: `[w, x, y, z, 9]`
  - `0`: khoảng trắng `' '` và số `0`
  - `1`: các ký tự đặc biệt, dấu câu và số `1`
- **Chuẩn hóa chuỗi (Normalization)**:
  - Tên ứng dụng được chuyển thành chữ thường và bỏ dấu tiếng Việt (thông qua `AppSearchEngine.normalize`, ví dụ `Đ` -> `D`, `á` -> `a`).
  - Mỗi tên ứng dụng được chuyển đổi thành một chuỗi đại diện số tương ứng (T9 numeric signature, ví dụ `Chrome` -> `247663`).
- **Xếp hạng & Đối chiếu (Matching & Ranking)**:
  - **Hạng 1 (Prefix Match)**: Dãy số T9 của tên app bắt đầu bằng query số người dùng bấm (ví dụ `247` khớp đầu `247663` của **Chrome**).
  - **Hạng 2 (Word Prefix Match)**: Dãy số của một từ bất kỳ trong tên app bắt đầu bằng query (ví dụ `247` khớp từ thứ hai trong **Google Chrome**).
  - **Hạng 3 (Substring Match)**: Dãy số query xuất hiện ở vị trí bất kỳ trong tên app.
  - Kết quả được sắp xếp theo: `Hạng khớp` -> `Yêu thích (isFavorite)` -> `Số lần mở (openCount)` -> `Tên app (alphabetical)`.

### 3. Tương thích với Search Filter Chips (SEARCH-009)

- Dãy ứng dụng ứng viên (`candidates`) được lọc qua `SearchFilterEngine.filterCandidates(...)` trước khi đưa vào `T9SearchEngine`.
- Kết quả T9 phản ánh đúng bộ lọc đang chọn (Tất cả / Lens này / Yêu thích / Đã ẩn).

### 4. Cài đặt trong Settings (`FrmSettings`)

- Switch trong `FrmSettings` (tab Settings):
  - Tiêu đề: "Bàn phím số T9 trong tìm kiếm" (`setting_enable_t9_search_keypad`).
  - Mô tả: "Hiển thị bàn phím số ở đáy ô tìm kiếm để gõ nhanh bằng một tay" (`setting_enable_t9_search_keypad_desc`).
  - Key SharedPreferences: `UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD` (`enable_t9_search_keypad`), boolean, **mặc định `false`** (tắt mặc định).
- Hành vi bàn phím:
  - Khi bật: mở `SearchView` sẽ hiển thị layout bàn phím T9 ở đáy, tự động ẩn bàn phím mềm hệ thống (IME keyboard).
  - Khi tắt: `SearchView` ẩn layout bàn phím T9, bàn phím mềm hệ thống hoạt động như bình thường.

### 5. Bản địa hóa (I18n)

- Đầy đủ bản dịch cho tiêu đề và mô tả cài đặt trên 17 locales của ứng dụng.

## Verification Gate

- Unit test: `T9SearchEngineTest` (kiểm tra ánh xạ 2-9, khoảng trắng 0, ký tự 1, tiếng Việt bỏ dấu, prefix match, word match, substring match, thứ tự xếp hạng).
- Unit test: `UtilSettingsT9Test` (mặc định false, lưu và đọc lại giá trị).
- Unit test: `T9SearchStringsTest` (17 locales đủ 2 keys mới).
- Widget test: `ActHomeT9KeypadWidgetTest` (mô phỏng bật T9, mở SearchView, bấm các phím `2-4-7`, xác nhận ô tìm kiếm cập nhật và danh sách app hiển thị đúng Chrome).
- Widget test: `FrmSettingsT9WidgetTest` (toggle switch trong Settings lưu SharedPreferences).
- Device testing: Xác minh trên TECNO KJ7 `115333744A005844`.
- Smoke test tay trên thiết bị thật, bảo đảm không có quảng cáo che màn hình.
- Audit tự chấm đạt điểm >9.0/10.
