# FISH-032 — Auto aperture theo số app trong lens

Ngày 2026-11-03. Thiết kế owner đã duyệt.

## Mục tiêu

Khi tạo lens mới bằng cách nhân bản layout của lens nguồn, chọn distortion ban đầu theo số app layout được sao chép. Không đổi distortion của lens đã tồn tại, không chạy migration, không thay đổi giá trị sau khi người dùng chủ động chỉnh bằng pinch hoặc slider.

## Quy tắc đã chốt

Số app là số hàng `AppPersistent` được `duplicateLensLayout(sourceLensId, targetLensId)` thực sự sao chép, không phải số app đã cài trong PackageManager, không phải số app đang hiển thị sau scope/temporary-hidden.

| Số hàng layout được sao chép | Distortion ban đầu |
|---:|---:|
| 0–24 | 3,5 |
| 25–99 | 2,5 |
| 100 trở lên | 1,8 |

Các giá trị là distortion dương trong miền hiện có 0,5–5,0. Ngưỡng và giá trị mỗi mức phải là named constants. Auto-aperture chạy đúng một lần khi khởi tạo lens mới; pinch/slider tiếp tục gọi `saveDistortionFactor(lensId, value)` như hiện tại và luôn thắng. Thay đổi app count, package refresh hoặc mở lại app không tự cập nhật distortion.

Nếu lens nguồn có distortion do người dùng chỉnh tay, lens mới vẫn nhận mức theo app count. Physics và các thiết lập khác tiếp tục được nhân bản hiện tại.

## Phạm vi kỹ thuật

- Thêm hàm thuần `ApertureAutoDefault.forAppCount(count: Int): Float` và constants cho các ngưỡng/mức.
- Luồng tạo lens hiện nằm trong `LensMenuCoordinator.createLensDialog` → `LensWorkspace.createLens` → `LensWorkspace.createLensNow` (tạo row lens và nhân bản layout qua `AppPersistentDao.duplicateLensLayout`) → `LensMenuCoordinator` gọi `UtilSettings.duplicateLensSettings`.
- Thay đổi tối thiểu: truy vấn số hàng của lens nguồn trước khi duplicate (hoặc lấy count từ DAO ở cùng thao tác DB), truyền count tới `UtilSettings.duplicateLensSettings`, nơi distortion nguồn được thay bằng `ApertureAutoDefault.forAppCount(count)`; các settings còn lại giữ nguyên.
- Không thay `LensGridCache`, schema Room, backup format, layout cũ, UI hoặc preference keys. Không thêm dependency.
- Lens cũ giữ giá trị hiện có. Không có startup migration hoặc dynamic recompute.
- Hàng `AppPersistent` stale (app đã gỡ nhưng row còn) vẫn được tính nếu nó được duplicate. Đây là giới hạn đã duyệt; không thêm cleanup khác cho story này.

## Lỗi và vòng đời

Đếm thực hiện trên tập layout nguồn đúng lúc tạo lens. Nếu truy vấn thất bại, thao tác tạo lens vốn đã thất bại thì không lưu distortion mới; không tạo lens rỗng giả. Không giữ Activity/Context trong helper thuần. Không thêm listener/timer/subscription.

## Kiểm thử và xác nhận

### Unit

- `ApertureAutoDefault.forAppCount`: 0 và số âm vào mức ít app; 24→3,5; 25→2,5; 99→2,5; 100 và số lớn→1,8.
- `duplicateLensSettings`: nguồn và target khác lens; lens target nhận theo app count kể cả khi source distortion đã được chỉnh tay; settings khác vẫn được copy; source không đổi.
- Test dùng SharedPreferences isolated, dọn state trong `finally`/fixture hiện có.

### Integration / thiết bị

- Dùng đường production tạo lens thật: lens nguồn có các layout count tại hai phía ngưỡng; gọi tạo lens mới, đọc `getDistortionFactor(newLensId)` để chứng minh persisted setting.
- Test khởi động/lens cũ chứng minh distortion đã lưu không đổi và không có migration.
- Test pinch/slider hiện có vẫn chứng minh write người dùng thắng sau khi lens được tạo.
- Chạy trên Pixel duy nhất đã khóa trong phiên; không dùng TECNO/Samsung/OPPO. Smoke thao thao tác thật: tạo lens mới từ nguồn có ít app và nhiều app, xác nhận slider value/profile page, quay Home; giữ nguyên role HOME, locale, font và dữ liệu gốc bằng backup/hash/restore.
- Nếu quảng cáo hoặc consent che screenshot, dừng theo R4; không chấp thuận consent hoặc bypass.

### Gate

`./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`. Ghi số unit pass/fail/skip, test thiết bị theo class, SHA-256 APK build/cài, model/Android/timestamp và kết quả restore.

### Mutation

- Đổi các ngưỡng 24/25 và 99/100: phải bị bắt bởi test biên.
- Đảo mức 3,5 và 1,8: phải bị bắt.
- Dùng distortion source thay vì auto: phải bị bắt.
- Ghi distortion lên lens nguồn hoặc thay đổi lens cũ: phải bị bắt.

## Ngoài phạm vi

- Auto update distortion khi count thay đổi sau khi lens đã tạo.
- UI toggle “Auto”, preset/profile mới, đổi độ méo âm, thay grid geometry.
- Cleanup app rows stale, backup/schema migration, Ads/IAP/monetization, TalkBack/A11Y feature work.

## Điều kiện đóng

Chỉ đóng khi gate và smoke Pixel xanh, device state restore hash khớp, audit độc lập chấm **>9,0/10**, story/tracker cập nhật. Push chỉ sau yêu cầu/ủy quyền hiện hành; không stage `.idea/`.
