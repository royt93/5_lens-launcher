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
- 3 test `ui` fail trên S24U đã điều tra (chỉ sửa test, không sửa code sản phẩm); nguyên nhân là bản cài mới trên S24U (app chính không có sẵn) chạy các luồng lần-đầu mà test không tính tới:
  - `LensOpticalProfileWidgetTest.previewListsGroupsAndCancelWritesNothing`: máy đặt `vi-VN`, số hiển thị `3,30` chứ không phải `3.30`; thêm nữa cờ migrate locale chưa ghi. Sửa: so với `String.format(Locale.getDefault(), "%.2f", …)` và gọi `LocaleHelper.onAttach(context)` ở setup. **8/8 × 3 lần** trên S24U.
  - `ResetDefaultsWidgetTest.cancelLeavesEveryValueUntouched`: hộp thoại Terms & Privacy (cờ `KEY_READ_POLICY`) và bottom sheet chọn ngôn ngữ (cờ `Locale.Helper.Language.Selected`) hiện lần đầu, cướp focus và nuốt click. Sửa: đặt sẵn hai cờ trong `setUp`, khôi phục trong `tearDown`.
  - `ResetDefaultsWidgetTest.resetThenUndo…`: ban đầu fail 10/10 (`expected:<[]> but was:<[UI034 hint]>`; bằng chứng: chụp màn hình thấy bottom sheet ngôn ngữ nằm dưới hộp thoại reset; chờ 1,5 s thì pass). Sau hai sửa trên còn lỗi `RootViewWithoutFocusException` ở **~1/12 đến 4/15 lần** chạy cả lớp. Lúc fail, `dumpsys window` báo `ActSettings` là cửa sổ giữ focus và activity đang resumed, nhưng Espresso vẫn thấy root `has-window-focus=false` trong 10 s; **nguyên nhân chưa tìm ra**, không đoán. Test thêm bước chờ hình học nút xác nhận ổn định (`awaitDialogSettled`) và đây là biện pháp giảm, không phải sửa tận gốc.
  - Hai giả thuyết đã loại bằng đo: `hasOnClickListeners()` luôn true (AlertController gắn handler mặc định) nên không dùng được để biết listener thật đã gắn; thêm test seam vào `ActSettings` là thừa, đã bỏ.
- **Hồi quy gói `ui` trên Samsung A50s `R58MA6WYRPE` (owner đổi khóa từ S24U sang A50s ngày 2026-11-09)**, 560 test mỗi lượt, một lượt mỗi bên:
  - Test cũ (trước `cf756f10`): 11 fail. Test đã sửa: 12 fail. Các fail trùng nhau, **không test nào được sửa trên A50s**: A50s đã có dữ liệu app (cờ policy/ngôn ngữ/locale đã đặt) nên hai test tôi sửa vốn không fail ở đó; `LensOpticalProfileWidgetTest` và `cancelLeavesEveryValueUntouched` pass ở cả hai lượt.
  - 11 fail chung là fail sẵn của A50s, không do thay đổi này: `ActHomeTiltWidgetTest` ×5 (không có listener cảm biến), `BaseActivityRefreshRateWidgetTest` (không có chế độ tần số cao), `FrmSettingsHapticIntensityWidgetTest` ×2 (animator scale = 0.0, tiền đề "không ở chế độ giảm chuyển động"), `LensInertiaScreenshotSmokeTest`, `LensManagementActivityWidgetTest` (cùng animator 0.0), và `ResetDefaultsWidgetTest.resetThenUndo…` (lỗi focus ở trên, **cũng tái hiện trên A50s**, nên không riêng S24U).
  - Fail thứ 12 (`ActHomeFullyDrawnIntegrationTest.coldStartPipeline…`) chỉ xuất hiện ở lượt đầu. Để kiểm tra nó có do `94fbf030` không, chạy xen kẽ gói `ui` với **cùng test APK**, chỉ đổi APK chính (`RApplication.java` trước và sau `94fbf030`), 3 lượt mỗi bên trên A50s: **0/3 lượt fail ở cả hai bên**, riêng test này chạy một mình cũng 6/6 pass. Không tái hiện được; coi là flaky không rõ nguyên nhân, không có bằng chứng liên quan tới thay đổi.
  - Cùng 6 lượt A/B: **6 fail ổn định ở cả hai bên** (`ActHomeTiltWidgetTest` ×5 và `LensInertiaScreenshotSmokeTest`) và 4–5 test dao động theo lượt ở cả hai bên (animator/haptic/refresh-rate, `LensManagementActivityWidgetTest`), nên các fail này thuộc môi trường A50s, không phải thay đổi này. Bản mới có 1 lần `ActHomeMultiLensRecentAppsIntegrationTest.launchRecordedOnOneLensAppearsInThePanelOpenedFromAnotherLens` fail (1/3), bản cũ 0/3; mẫu quá nhỏ để kết luận, chưa điều tra. Tổng số fail mỗi lượt dao động 6–11 và không phân biệt được hai bên.
  - Phần suy luận cho các fail do animator 0.0 và thiếu cảm biến là từ thông báo lỗi, chưa kiểm bằng cách đổi cài đặt máy.
- Thiết kế `sDisableAutoAppRefresh` tắt quét lúc khởi động khi chạy dưới test; các test cần danh sách app thật phải tự gọi quét (như smoke ở trên). Chưa rà soát toàn bộ ~560 test `ui` để chắc không còn test khác phụ thuộc quét khởi động; hồi quy `ui` cho thấy không có test mới nào fail so với baseline, nhưng đó là một lần chạy mỗi bên.
- Pixel vẫn đang giữ bản debug và test APK của cuộc điều tra (backup `/tmp/f048-pixel-20261109-105310`), không động tới theo chính sách chỉ dùng S24U.

## Khôi phục S24U

Gỡ app chính, cài lại đúng test APK gốc: hash `d7fb756f…` trước và sau trùng; danh sách package 779/779 trùng; font/animator/haptic/HOME/locale/density không đổi; user 95 và 150 không có app; không còn file tạm.
