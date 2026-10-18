# SEARCH-010 — Bàn phím số T9 trong SearchView (T9 Dialpad Search)

| Field | Value |
|---|---|
| Status | done |
| Priority | P2 |
| Owner decision | 2026-10-18, implementation plan executed & audited |

## Scope

- Thêm bàn phím số 12 nút (T9 dialpad) docked ở đáy màn hình `SearchView`, đẩy `searchResults` lên trên qua `layout_weight="1"`.
- Bố cục 12 phím (4x3): hàng 1 (1, 2-ABC, 3-DEF), hàng 2 (4-GHI, 5-JKL, 6-MNO), hàng 3 (7-PQRS, 8-TUV, 9-WXYZ), hàng 4 (Clear, 0, Backspace).
- Nút `Clear` xóa trắng text, `Backspace` xóa 1 ký tự (nhấn giữ xóa hết). Có haptic `VIRTUAL_KEY` khi nhấn.
- Pure engine `T9SearchEngine`:
  - Chuẩn hóa text qua `AppSearchEngine.normalize` (bỏ dấu tiếng Việt, ví dụ Đồng -> Dong -> 3664).
  - Ánh xạ ITU E.161: 2 (ABC), 3 (DEF), 4 (GHI), 5 (JKL), 6 (MNO), 7 (PQRS), 8 (TUV), 9 (WXYZ), 0 (khoảng trắng/0), 1 (ký tự khác/1).
  - Xếp hạng 3 tiers: Prefix match (1) > Word prefix match (2) > Substring match (3). Tie-break: isFavorite desc -> openCount desc -> label asc.
- Tương thích với Search Filter Chips (SEARCH-009): candidates được lọc qua `SearchFilterEngine` trước khi chuyển cho `T9SearchEngine`.
- Cài đặt trong Settings (`FrmSettings`):
  - Toggle Switch `swEnableT9SearchKeypad` ("Bàn phím số T9 trong tìm kiếm").
  - Lưu qua `UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD` ("enable_t9_search_keypad"), boolean, mặc định `false` (tắt mặc định).
  - Khi bật: mở `SearchView` hiện bàn phím T9 ở đáy, tắt bàn phím ảo hệ thống. Khi tắt: dùng bàn phím hệ thống như bình thường.
- Bản địa hóa đầy đủ 17 locales cho 3 chuỗi mới (`setting_enable_t9_search_keypad`, `setting_enable_t9_search_keypad_desc`, `t9_key_clear`).

## Verification

- [x] Pure engine unit tests: `T9SearchEngineTest` (3 tests pass: Latin/Vietnamese mapping, prefix/word-prefix/substring ranking, blank query empty).
- [x] Settings unit tests: `UtilSettingsT9Test` (2 tests pass: default false, save/read toggle).
- [x] I18n strings tests: `T9SearchStringsTest` (1 test pass: đủ 17 locales cho 2 setting keys) + `AllStringsTranslationTest` (pass: đủ 17 locales bao gồm cả `t9_key_clear`).
- [x] Full JVM unit regression: 912/912 tests pass, 0 failures, 0 errors.
- [x] Android Lint: 0 errors, 27 warnings (không phát sinh warning mới).
- [x] Targeted instrumentation tests trên Samsung S24 Ultra `R5CX613VZBR`:
  - `ActHomeT9KeypadWidgetTest`: 1 test pass (bấm phím 2-4-7 lọc ra đúng Chrome).
  - `FrmSettingsT9WidgetTest`: 1 test pass (switch mặc định off, toggle lưu true).
  - Regression tests: `ActHomeSearchFilterWidgetTest` (1/1 pass), `FrmSettingsEdgeSwipeWidgetTest` (1/1 pass), `ActHomeEdgeSwipeWidgetTest` (1/1 pass), `LensViewPagerGestureIntegrationTest` (3/3 pass).
  - Tổng targeted instrumentation: **8 tests pass trên S24 Ultra**.
- [x] Smoke test tay & trực quan trên Samsung S24 Ultra `R5CX613VZBR`:
  - Consent dialog xử lý đúng quy tắc: dừng lại chờ user bấm Đồng ý và Tiếp tục, báo done mới tiếp tục.
  - Mở SearchView kiểm tra giao diện, danh sách kết quả và thanh filter chips hiển thị đúng, không có quảng cáo che màn hình.
- [x] Secret scan: `bash scripts/check-secrets.sh` exit 0.
- [x] Diff check: `git diff --check` sạch.
- [x] Audit tự chấm: **9.75/10** (>9.0); xem `doc/task/AUDIT_SEARCH-010_2026-10-18.md`.
- [x] Đã hoàn tất commit và push theo owner approval.

## Follow-up OPPO — 2026-10-18

- Sửa padding/inset MaterialButton và cỡ nhãn hai dòng; dành navigation-bar inset cho SearchView content để hàng cuối không bị che.
- Chip dùng selector tint theo checked state, tắt checked icon trùng. Không sửa drawable dùng chung ở nơi khác.
- Test chip lưu/khôi phục workspace và filter; hash các prefs liên quan trước/sau test giống nhau. Không thể suy ra hay khôi phục các giá trị thiết bị đã bị test cũ ghi đè trước khi có snapshot.
- OPPO CPH1989 `FUJZIFIR7DQCNRWW`: keypad đầy đủ, `247` cho Chrome thật; screenshot không có quảng cáo. T9 hiện OFF (key vắng ở lần kiểm cuối).
- Bản cuối: 921/921 unit; lint 0 lỗi/38 warnings; T9/chip 2/2 widget pass, nằm trong 38 targeted instrumentation pass. Diff check sạch; staged secret scan chạy trước commit.
- Audit fix search: **9.3/10**. Giới hạn: chưa smoke dark mode/landscape/font cực lớn; không có thay đổi logic ranking/filter.
- Owner duyệt commit/push sau review; fix search tách commit khỏi FISH-026.

## Follow-up cấu hình UI — 2026-10-18

- Inflate thật `act_home` trên OPPO CPH1989 `FUJZIFIR7DQCNRWW` với light/dark, portrait/landscape, font 1.0/1.5/2.0. Tái hiện hai lỗi: (1) font 1.5, nhãn cần 158px nhưng phím cố định 144px; (2) landscape thật, keypad 4 hàng tràn khung và đẩy kết quả xuống 0px.
- Sửa: phím `wrap_content` + `minHeight=48dp`, bỏ rowWeight; keypad nằm trong `BoundedHeightNestedScrollView` giới hạn 40% chiều cao cha và cuộn khi thấp. Giữ nhãn hai dòng, không giảm font; portrait vẫn hiện đủ 12 phím.
- Test mới `ActHomeSearchConfigurationWidgetTest`: ma trận 6 cấu hình (12 phím, touch 48dp, text không bị cắt, không chồng nhau), tint chip dark mode, và Activity landscape thật: bấm `247` ra Chrome, keypad trong vùng nhìn thấy, phím cuối cuộn tới được, còn đủ một hàng kết quả 48dp. Test lưu/khôi phục T9, filter, apps; đặt orientation về giá trị gốc.
- Red rồi green: font 1.5 fail `bottom=158 height=144`; landscape fail `rvH=0`, sau chỉnh 95px còn thiếu, giới hạn 40% đạt.
- Smoke OPPO: portrait light bấm `247` ra Chrome; landscape thật có hàng Chrome đầy đủ; dark portrait đủ phím/chip; không thấy quảng cáo. Rotation/night mode đã khôi phục; T9 prefs hiện `false` (key không còn vắng). Giới hạn: không smoke landscape dark, font 1.5 trên thiết bị thật và không đổi font thật hệ thống.
- Bản cuối: JVM 921/921, lint 0 lỗi/38 cảnh báo, 10 targeted widget pass (cấu hình, T9, chip, settings, pager/edge swipe).

## Smoke bổ sung Samsung Galaxy A11 — 2026-10-18

- Owner đổi target tường minh: OPPO ngắt kết nối, BG6 dừng khi screenshot có Test Ad, sau đó khóa Samsung SM_A115F `R9JN61LDLFJ`, Android 12, 720×1560, density override 320. Không thao tác lại device cũ.
- Smoke thật với app Dark, font hệ thống 1.5 và 2.0: `247` trả Chrome, keypad cuộn tới Clear/0/Backspace. Dark + landscape tại font gốc 1.1 vẫn có Chrome và các chip đọc được. Không thấy quảng cáo trên screenshot Samsung.
- Phát hiện/sửa RED→GREEN hai lỗi: font 2.0 ở portrait hẹp làm MNO xuống 3 dòng (test trước chỉ có 2.0 landscape); Material SearchView tự mở IME dù EditText.showSoftInputOnFocus=false. Giảm padding ngang 10 phím số về 0, không giảm font; gọi `searchView.setAutoShowKeyboard(!t9Enabled)` theo API native.
- Bản cuối mở T9 không cần BACK để ẩn IME. 7 cấu hình inflation, 10 targeted instrumentation pass trên Samsung; full JVM 921/921, lint 0 lỗi/38 warnings, build pass.
- Khôi phục font=1.1, accelerometer_rotation=1, user_rotation=0, system night=no. Chỉ khôi phục ba key smoke (`night_mode`, T9, calculator) bằng run-as từ snapshot, kiểm tra trạng thái key vắng chính xác; không ghi đè prefs khác, không xóa DB, không đổi locale/default launcher.
- Giới hạn còn lại: chưa smoke kết hợp landscape+font 2.0; test native flag chứng minh cơ chế auto-IME, screenshot mở search thật chứng minh IME không che T9.

- Follow-up Samsung: lỗi T9 auto-IME + wrap 3 dòng font 2.0 tái hiện trên Galaxy A11, sửa/test RED-GREEN. Dark landscape + 2.0 portrait smoke; system settings/T9/night exact snapshot restored. 921 JVM, 10 targeted Samsung pass. Audit combined 9.4/10.
