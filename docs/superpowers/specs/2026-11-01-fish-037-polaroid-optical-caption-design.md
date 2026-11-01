# FISH-037 — Polaroid có chú thích quang học và contact-sheet nhiều lens

Date: 2026-11-01
Status: Owner-approved design

## Context

`PolaroidExportHelper` (FISH-011) chụp đúng một `LensView` đang hiển thị, bọc khung trắng, in hai dòng caption (tên lens, "via <app>") và chia sẻ qua `FileProvider` (`cache/polaroid/`). Hai điểm vào cùng gọi `LensMenuCoordinator.exportActiveLensImage()`: mục "Chia sẻ ảnh lens" trong menu lens trên Home, và nút "Chia sẻ ảnh lens" ở tab Lens (đi qua `ActHome` với `EXTRA_AUTO_EXPORT_LENS` và cờ bền `pending_auto_export_lens`, FISH-013, chỉ xóa khi export tới trạng thái kết thúc).

Từ FISH-027/028/030 mỗi lens có profile, hood, độ méo, kích thước icon riêng, nhưng ảnh chia sẻ không nói gì về chúng và chỉ chụp được lens đang xem.

Phạm vi loại trừ do owner chốt: Ads/IAP/monetization và mọi việc TalkBack mới.

## Hành vi cho người dùng

- **Caption quang học:** dòng 2 của polaroid thay "via <app>" bằng thông số của đúng lens được chụp, dạng `<Profile> · d=2.5 · s=1.0 · hood=100%`. Tên profile theo locale hiện tại (chuỗi `lens_profile_*` đã có ở 17 locale), số theo `Locale` hiện tại. Dòng "via <app>" xuống dòng 3 nhỏ hơn, giữ nguyên nội dung và chuỗi `lens_share_caption_via`.
- **Một lens:** khi chỉ có một lens, bấm chia sẻ xuất thẳng một polaroid như cũ, không hộp thoại, chỉ khác caption.
- **Nhiều lens:** khi có từ 2 lens, bấm chia sẻ mở hộp thoại chọn lens (checkbox, dùng `LensDialogs.multiChoice`, được mở rộng như ở mục Thành phần). Mặc định chỉ tick lens đang xem. Tối đa **6** lens; nút xác nhận bị vô hiệu khi không chọn lens nào và tick thêm lens thứ 7 bị từ chối, kèm dòng báo giới hạn. Xác nhận với 1 lens chọn thì xuất polaroid đơn của lens đó (không nhất thiết lens đang xem); từ 2 lens chọn thì xuất **một tấm contact sheet**.
- **Contact sheet:** lưới **2 cột**, thứ tự theo thứ tự lens trong pager, mỗi ô là một polaroid thu nhỏ có tên lens và caption quang học riêng, nền trắng. Số hàng = ceil(n / 2); ô lẻ cuối để trống. Dòng "via <app>" chỉ in một lần ở chân cả tấm, không lặp ở mỗi ô.
- **Lỗi và hủy:** mọi thất bại (bitmap không tạo được, hết bộ nhớ, ghi file, FileProvider) trả `null` cho callback và hiện `error_lens_share_failed` có sẵn như hiện nay. Hủy hộp thoại không làm gì.

## Nguồn ảnh của từng lens

Chỉ lens đang xem có `LensView` sống. Với mỗi lens được chọn khác lens đó, dựng một `LensView` **tạm, không gắn vào cửa sổ**: đặt `lensId`, `setApps` bằng đúng danh sách app theo scope/hidden của lens đó (cùng logic `LensAppScope.filter` mà `ActHome.scopedApps` dùng, rút ra một hàm dùng chung để hai nơi không lệch nhau), `setHasOtherLenses(false)`, `measure/layout` đúng kích thước trang đang xem, `resetToIdleForExport()`, rồi `draw` vào bitmap và hủy view. Lens đang xem vẫn chụp từ view sống như hiện tại.

Thứ không có ở view tạm và chấp nhận: dữ liệu icon đã nằm trong `RAppsSingleton`/`BitmapCache` dùng chung nên vẫn có; badge thông báo và Smart Focus áp theo cài đặt từng lens như `LensView` làm khi vẽ.

## Giới hạn kích thước và bộ nhớ

- Tối đa 6 lens mỗi tấm; cạnh dài của bitmap contact sheet ≤ **4096 px**; ước tính bộ nhớ bitmap cuối ≤ **64 MB** (ARGB_8888, 4 byte/pixel).
- Mỗi ô được chụp ở kích thước trang rồi **thu nhỏ** tới kích thước ô tính từ giới hạn trên; ô vẽ xong vào bitmap tổng thì recycle ngay trước khi chụp ô kế tiếp, nên tại một thời điểm chỉ giữ bitmap tổng cộng một bitmap ô.
- Việc chụp (cần luồng chính) chạy tuần tự từng lens; ghép và nén chạy trên `Dispatchers.IO`. `OutOfMemoryError` ở bất kỳ bước nào được bắt, giải phóng bitmap đã tạo, trả `null`.

## Thành phần

- `util/PolaroidCaptionFormatter.kt` (hàm thuần): dựng dòng 2 từ tên profile đã dịch, distortion, scale, hood và `Locale`. Làm tròn: distortion và scale 1 chữ số thập phân, hood nguyên phần trăm. Giá trị không hữu hạn hiển thị `–` thay vì `NaN`.
- `util/PolaroidContactSheetLayout.kt` (hàm thuần): từ số lens, kích thước trang và giới hạn, tính số cột/hàng, kích thước ô, khoảng cách, kích thước bitmap tổng và `(left, top)` từng ô; đảm bảo cạnh dài ≤ 4096 và `w*h*4 ≤ 64 MB`.
- `util/PolaroidExportHelper.kt`: giữ API `exportAsync` hiện có (đơn lens, thêm tham số caption quang học), thêm `exportContactSheetAsync` nhận danh sách `LensExportSpec(lensName, optical caption, view hoặc nguồn dựng view)`. Hàm thuần của FISH-011 (`calculatePolaroidLayout`, `buildCaptionLine1`, `sanitizeFileName`, `buildShareIntent`) không đổi chữ ký.
- `ui/home/LensMenuCoordinator.java`: `exportActiveLensImage()` quyết định theo số lens (1 lens → xuất thẳng; ≥ 2 → hộp thoại). Cờ `pending_auto_export_lens` giữ đúng ngữ nghĩa FISH-013: chỉ xóa khi export (đơn hoặc ghép) tới trạng thái kết thúc; hủy hộp thoại cũng là trạng thái kết thúc nên xóa cờ, tránh tái hiện hộp thoại ở lần mở sau.
- `ui/dialog/LensDialogs.multiChoice`: hiện chưa có giới hạn số tick và không vô hiệu nút xác nhận khi rỗng, nên thêm hai tham số tùy chọn `maxSelected: Int = Int.MAX_VALUE` và `requireSelection: Boolean = false` (giá trị mặc định giữ nguyên hành vi cho hai nơi gọi hiện có là chọn app và app tạm ẩn, có test hồi quy). Khi vượt `maxSelected`, tick bị bỏ lại và hiện `Toast` báo giới hạn; khi `requireSelection` và không còn tick nào thì nút xác nhận vô hiệu.
- `util/LensAppScope` hoặc helper cạnh `ActHome.scopedApps`: hàm dùng chung lọc app theo lens, ActHome gọi lại hàm này.
- Chuỗi mới (đủ 17 locale): tiêu đề hộp thoại chọn lens, dòng báo giới hạn 6 lens, nhãn viết tắt `d`/`s`/`hood` nếu cần dịch (mặc định giữ ký hiệu quốc tế `d`, `s`, `hood`). Test dịch hiện có (`LensStringTranslationTest`, `AllStringsTranslationTest`) buộc dịch thật, nên không để bản tiếng Anh nguyên văn.
- Không đổi `file_paths.xml` (vẫn `cache/polaroid/`), không thêm quyền, dependency, key preference hay Room.

## Xử lý lỗi và tương thích

- Lens không còn tồn tại hoặc không có app nào trong scope: ô vẫn được vẽ (lưới trống) với caption quang học; không làm hỏng cả tấm.
- Tên lens trùng sau khi làm sạch tên file: contact sheet dùng một tên file cố định `lenses` và polaroid đơn giữ tên theo lens như hiện nay (đã có ghi chú `ponytail:` về va chạm tên, giữ nguyên).
- Hành vi cũ giữ: export chỉ chạy khi view đã layout (`width/height > 0`), `resetToIdleForExport` trước khi vẽ, giải phóng bitmap trong `finally`.

## Xác minh

- TDD đỏ → xanh. Unit: `PolaroidCaptionFormatterTest` (định dạng, làm tròn, locale, không hữu hạn, mọi profile), `PolaroidContactSheetLayoutTest` (1–6 lens, ô lẻ, cạnh dài ≤ 4096, bộ nhớ ≤ 64 MB, trang rất cao hoặc rất rộng, đầu vào 0), mở rộng `PolaroidExportHelperTest` cho dòng caption mới.
- Androidtest: dựng bitmap thật của contact sheet và polaroid đơn (kích thước đúng layout, pixel nền trắng, vùng ô có nội dung, ô lẻ trống), `LensView` tạm có icon theo scope lens đó, hộp thoại chọn lens (mặc định, giới hạn 6, vô hiệu khi rỗng, hủy xóa cờ pending), luồng 1 lens không hiện hộp thoại, export lỗi trả null và hiện toast, hồi quy FISH-013 (cờ pending xóa đúng một lần).
- Mutation trên hàm thuần và nhánh quyết định hộp thoại/giới hạn.
- Gate: `testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `scripts/check-secrets.sh`.
- Smoke trên TECNO (KJ7 ưu tiên, BG6 thay thế, tuân theo yêu cầu mới nhất của owner): backup trước, xác nhận hash APK, chia sẻ một lens và nhiều lens, xem ảnh PNG thật sinh ra trong `cache/polaroid/`, kiểm kích thước file và thời gian, dừng nếu quảng cáo hoặc consent che màn hình, khôi phục từng file. OPPO `FUJZIFIR7DQCNRWW` còn giữ APK FISH-031 chưa khôi phục và nằm ngoài quy tắc TECNO hiện tại; không đụng khi chưa hỏi owner.
- Audit `doc/task/AUDIT_FISH-037_<ngày>.md`, điểm phải trên 9,0 mới đủ ngưỡng push; không push khi chưa có yêu cầu rõ của owner.

## Đánh đổi đã chấp nhận

- Contact sheet dựng lại từng lens bằng view tạm nên có thể khác chút so với khi người dùng vuốt tới lens đó (badge, hiệu ứng nền); chấp nhận vì lưới icon ở trạng thái nghỉ là thứ cần chia sẻ.
- Tối đa 6 lens và thu nhỏ ô làm ô nhỏ khi chọn nhiều lens; đổi lại không OOM trên máy yếu.
- Thêm một bước hộp thoại cho người có từ 2 lens muốn chia sẻ một lens; đổi lại không cần thêm mục menu.
