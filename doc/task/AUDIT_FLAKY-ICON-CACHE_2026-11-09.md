# AUDIT — Test icon đo bằng widget bị flaky do quét app thật (2026-11-09)

Bắt đầu từ một test FISH-040 (`LensViewChromaticAberrationWidgetTest.theOffsetFadesWithTheLensAndSnapsUnderReducedMotion`) fail một lần trong suite 30 lớp trên Pixel rồi pass khi chạy riêng. Thiết bị: **Pixel 7 Pro** cho phần tái hiện (lúc đó đang khóa), **Samsung S24 Ultra `R5CX613VZBR`, user 0** cho phần kiểm chứng sau khi owner chốt "chỉ dùng S24U cho mọi phiên". Chưa push.

## Nguyên nhân gốc (đã chứng minh)

`RApplication.sDisableAutoAppRefresh` được khởi tạo bằng `isTestEnvironment()` **lúc class nạp**, trước khi instrumentation đăng ký, nên luôn `false` trong mọi lần chạy test. Quét `PackageManager` khi khởi động vẫn chạy, commit danh sách app thật và `BitmapCache.retainKeys()` xoá các icon giả mà test đã seed. Test vẽ icon trống nên fail ở thời điểm ngẫu nhiên.

Bằng chứng:
- Trên Pixel, lớp FISH-040 chạy riêng: **4/12 lần fail, và cả 4 lần đều có đúng 1 dòng log `Evicted … stale icon cache entries`**; 8/8 lần pass không có dòng đó. Mỗi lần fail là một test khác nhau, giá trị đều là 0 (alpha 0, tổng alpha bitmap 0).
- Chẩn đoán tạm tại điểm gọi: `updateApps() disabled=false isTestEnv=true` ở `RApplication.onCreate`.
- Hai test tất định mới (`RApplicationAutoRefreshUnderTestTest`) **fail 3/3 trên code cũ, pass 3/3 sau sửa** (Pixel); trên S24U cũng vậy (bản chưa sửa fail 3/3 test cờ, bản sửa pass 3/3).

## Sửa

`sDisableAutoAppRefresh` mặc định `false` và được bật trong `RApplication.onCreate()` khi có instrumentation. Sau sửa trên Pixel: **0/12 fail, 0 eviction**, log `auto app refresh disabled` xuất hiện đúng 1 lần mỗi lần chạy.

## Tác dụng phụ đã phát hiện và xử lý

- `LensViewFocusHaloLiveVisualSmokeTest` cố ý cần **app thật** trong lens, nên fail 3/3 sau sửa (pass 3/3 trước sửa). Đã sửa test để tự gọi `appRefreshPipeline.execute()`; pass 4/4 trên Pixel, 12/12 riêng và 8/8 theo thứ tự suite trên S24U.
- `LensViewHoverMagnifyReachabilityTest` (FISH-046) flaky 2/10 trên S24U vì chính test rung ngón 1 px mỗi 16 ms, khoảng **21 dp/s ở mật độ 3,0, trên ngưỡng 15 dp/s**; kết quả phụ thuộc scheduler. Đây là lỗi của test do tôi viết. Đã đổi rung thành 40% ngưỡng tính theo dp (độc lập mật độ) và thêm test bảo vệ; sau đó **0/20** trên S24U.

## Chưa chứng minh / giới hạn

- **S24U không tái hiện flaky gốc**: bản chưa sửa cũng pass 8/8 lớp FISH-040 và 0 eviction. Tỷ lệ 4/12 → 0/12 chỉ đo trên Pixel; kết luận "hết flaky" trên S24U dựa vào hai test tất định và cơ chế, không phải đo tỷ lệ.
- 12 lần chạy sau sửa là mẫu nhỏ (kèm lý do cơ học rõ ràng).
- Hồi quy S24U (bản đã sửa): `app` 18/18; `views` 302 test, 2 fail trong suite — một là smoke ở trên (không tái hiện được: 12/12 riêng, 8/8 theo thứ tự suite, **nguyên nhân lần fail đó chưa giải thích**), một là test magnify (đã sửa ở trên); `ui` 560 test, 3 fail.
- 3 test `ui` fail (`LensOpticalProfileWidgetTest.previewListsGroupsAndCancelWritesNothing`, `ResetDefaultsWidgetTest.cancelLeavesEveryValueUntouched`, `…resetThenUndoRestoresValues…`) **fail y hệt trên bản chưa sửa (Pixel, baseline 4 fail gồm một test khác)**, nên không do thay đổi này; chưa điều tra nguyên nhân. `ResetDefaultsWidgetTest` báo `expected:<[]> but was:<[UI034 hint]>` (một giá trị prefs còn sót từ test khác).
- Thiết kế `sDisableAutoAppRefresh` tắt quét lúc khởi động khi chạy dưới test; các test cần danh sách app thật phải tự gọi quét (như smoke ở trên). Chưa rà soát toàn bộ ~560 test `ui` để chắc không còn test khác phụ thuộc quét khởi động; hồi quy `ui` cho thấy không có test mới nào fail so với baseline, nhưng đó là một lần chạy mỗi bên.
- Pixel vẫn đang giữ bản debug và test APK của cuộc điều tra (backup `/tmp/f048-pixel-20261109-105310`), không động tới theo chính sách chỉ dùng S24U.

## Khôi phục S24U

Gỡ app chính, cài lại đúng test APK gốc: hash `d7fb756f…` trước và sau trùng; danh sách package 779/779 trùng; font/animator/haptic/HOME/locale/density không đổi; user 95 và 150 không có app; không còn file tạm.
