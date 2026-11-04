# FISH-036 — Chia sẻ cấu hình quang học qua file

**Ngày:** 2026-11-04

**Trạng thái:** Thiết kế đã duyệt trong hội thoại; bản spec chờ owner đọc lại trước lập plan. Chưa triển khai.

## Mục tiêu và quyết định đã duyệt

Xuất/nhập cảm giác quang học của một lens qua JSON riêng, không mang layout hay danh sách app.

- Bộ dữ liệu đầy đủ: profile, distortion, scale, icon size, animation time, hood, Smart Focus và Custom preset nếu đã lưu.
- Hai mục Xuất/Nhập nằm trong `LensSettingsActivity` hiện có, không thêm mục vào menu Home.
- Target là `EXTRA_LENS_ID` của trang settings khởi chạy picker, kể cả lens này không active trên Home. Không lấy lại target từ `active_lens_id` khi picker trả về.
- Scale/animation và Custom scale/time giữ scope global hiện có. Preview phân tách ảnh hưởng lens đích, global, Custom và cảnh báo kế thừa từ default lens.
- Sai kiểu/cấu trúc/range, thiếu trường bắt buộc hoặc số không hữu hạn: từ chối toàn file. Không clamp hoặc nhập từng phần.
- JSON riêng; tái dùng mẫu SAF, không mở rộng workspace backup hoặc gọi Room layout replacement.
- Không dependency/schema/key settings mới; không mở rộng Ads/IAP/TalkBack. Quyết định mới nhất 2026-11-04: chỉ TECNO cho mọi phiên, thay Pixel. Không push khi chưa có yêu cầu hiện hành.

## Kiến trúc đã kiểm chứng

`ui/LensSettingsActivity.kt` tồn tại: `targetId`, `EXTRA_LENS_ID`, `loadTarget()`, `bind()`, `ready`/`busy`, dialog state và `lifecycleScope`. Tích hợp tại đây; không thêm Activity hoặc đổi entry point sang `FrmLens`/`ActSettings`.

`ActSettings` có ví dụ đăng ký SAF `CreateDocument("application/json")` và `OpenDocument()`. `LayoutBackupIo` cung cấp mẫu IO, nhưng write path của nó thay workspace nên không dùng cho profile.

`UtilSettings.lensKey` giữ default lens trên key không hậu tố; lens khác dùng hậu tố. Distortion/icon size/Smart Focus có kế thừa key chung; profile/hood không kế thừa. Custom distortion theo lens, Custom scale/time là global. `hasCustomPreset()` dựa trên sự có mặt của `KEY_CUSTOM_SCALE_FACTOR`.

`UtilSettings.snapshotKeys`/`restoreKeys` đã hỗ trợ snapshot đúng tập key, giữ cả trạng thái key vắng và synchronous commit. Tái dùng thay vì snapshot toàn bộ prefs.

## Định dạng v1

Tên gợi ý cố định: `lens_optical_profile.json`, MIME `application/json`, UTF-8. Không đưa tên lens vào tên file hoặc nội dung.

```json
{
  "format": "lens-optical-profile",
  "version": 1,
  "optical": {
    "profile": "FISHEYE",
    "distortionFactor": 2.5,
    "scaleFactor": 1.0,
    "iconSize": 21.0,
    "animationTimeMs": 200,
    "hood": 1.0,
    "smartFocusBias": false
  },
  "custom": {
    "distortionFactor": 3.3,
    "scaleFactor": 1.3,
    "animationTimeMs": 200
  }
}
```

`format`, `version`, `optical` và mọi field trong `optical` bắt buộc. `custom` có thể vắng; nếu có phải là object đủ cả ba field, không chấp nhận `null`. Field ngoài tập được định nghĩa, key trùng, dữ liệu thừa sau JSON đều bị từ chối. Chỉ bốn profile enum hiện có được chấp nhận: FISHEYE, ANAMORPHIC, CYLINDRICAL, PINCUSHION.

Không chứa lens ID/name, active lens, Room rows, app list, usage stats, layout, freeze/scope/hidden/favorite/folder, đường dẫn hoặc prefs tùy ý. Không dùng workspace JSON làm profile.

## Validation và IO

- Named limit `16 * 1024` byte. Đọc tối đa giới hạn cộng một byte; quá giới hạn dừng trước parse. Export cũng không vượt giới hạn.
- Đọc/ghi stream qua SAF `Uri` trên IO; `use` đóng stream. Không suy ra raw filesystem path. Không network, FileProvider hay share sheet mới.
- UTF-8 lỗi bị từ chối, không thay byte hỏng thành replacement character.
- Không ép numeric string/Boolean thành số. Version và animation cần giá trị số nguyên biểu diễn chính xác trong miền cho phép; enum và format so chính xác.
- Float hữu hạn, kiểm range trước chuyển sang Float. Không cho NaN/Infinity/overflow/underflow vượt range.
- Distortion và Custom distortion: `[0.5, 5.0]`, suy từ constants hiện có.
- Icon size: `[10, 55]` dp, suy từ `MIN_ICON_SIZE` và `MAX_ICON_SIZE`.
- Scale và Custom scale: `[1.0, 3.5]`, suy từ persistence range hiện có; không tự dùng slider ceiling thấp hơn để giới hạn file.
- Animation và Custom animation: `[100, 400]` ms, suy từ constants hiện có.
- Hood: `[LensHood.MIN, LensHood.FULL]` hiện là `[0.2, 1.0]`; không ép giá trị file vào bước slider.
- Sai format/malformed, version không hỗ trợ, quá lớn, provider/IO lỗi có kết quả phân biệt; không ghi settings ở bước parse.
- Export báo lỗi nếu provider trả stream null, ghi hoặc đóng stream thất bại. Provider có thể để lại file đích chưa hoàn chỉnh; không báo thành công hoặc hứa rollback file ở provider bên ngoài.

## Snapshot xuất và Custom

Chụp giá trị hiệu lực của target lens lúc bấm Xuất, trước khi mở picker. Không serialize trạng thái kế thừa key: máy nhận nhận giá trị cụ thể. Picker hủy không ghi file.

Chỉ có `custom` khi `hasCustomPreset()` đúng. Khi đã có Custom, xuất effective Custom distortion cho lens nguồn và global Custom scale/time. Khi chưa có Custom, bỏ cả object, không tự tạo preset từ live fallback.

Import file không có `custom` giữ nguyên toàn bộ Custom của máy nhận. File có Custom ghi Custom distortion đúng lens đích và global Custom scale/time; preview cảnh báo lens khác có thể kế thừa shared Custom distortion khi target là default.

Đọc snapshot/preview không được tự sửa prefs qua các getter có side effect clamp; dùng raw snapshot + resolver hoặc cách đọc không ghi. Dữ liệu nguồn sai kiểu/không hữu hạn phải báo lỗi xuất, không âm thầm phát file hỏng.

## Preview và apply

1. Đọc/parse hoàn chỉnh trước preview; kiểm lens đích còn tồn tại trong Room. Lens mất thì báo lỗi, không fallback.
2. Preview hiện tên lens đích cùng `hiện tại → sắp nhập`, chia nhóm lens đích, global và Custom.
3. Nêu rõ scale/animation và Custom scale/time ảnh hưởng toàn app. Target default còn phải cảnh báo distortion/icon/Smart Focus và Custom distortion có thể ảnh hưởng lens kế thừa.
4. Cancel/Back/dismiss không ghi bất kỳ key nào. Không tự xác nhận sau recreate.
5. Xác nhận: disable thao tác lặp; kiểm lại lens tồn tại; validate typed payload một lần nữa; snapshot chính xác key sẽ ghi/xóa; dùng một `SharedPreferences.Editor.commit()` trên IO cho toàn bộ thay đổi.
6. Trong cùng commit xóa pending distortion của target để pinch cũ không sống lại đè dữ liệu vừa nhập. Không gọi nhiều `save*` riêng rẽ.
7. Commit false/exception: phục hồi snapshot bằng `restoreKeys`, không báo thành công. Nếu cả restore lỗi, báo không khôi phục được và giữ thông tin lỗi; không gọi đó là rollback thành công.
8. Sau commit kiểm lại target. Nếu lens đã bị xóa giữa lúc commit, phục hồi key đã chạm, không khôi phục key riêng của lens đã xóa; báo lỗi. Không ghi Room.
9. Thành công: rebind control với listener guard, phát refresh/event hiện có để Home đọc lại cấu hình; không đổi active lens hoặc layout app.

Giới hạn: Room existence và SharedPreferences không có transaction chung. Recheck giảm cửa sổ race, không hứa exactly-once khi process bị kill giữa hai storage. Batch commit bảo vệ tập settings khỏi N lần ghi riêng lẻ, không thay thế transaction Room.

## Lifecycle

Đăng ký launchers trước STARTED trong `LensSettingsActivity`. Target luôn là `EXTRA_LENS_ID` cố định của Activity. Lưu export snapshot và preview đã parse bằng bounded JSON trong saved instance state; restore phải validate lại. ActivityResult cancellation xóa pending operation.

IO dùng `lifecycleScope`, không giữ Activity callback trong `ApplicationScope`. Destroy hủy job và dismiss/release dialog. Nếu recreate cắt ngang read, cho chọn lại file thay vì tự áp dữ liệu; preview đã lưu thì dựng lại. Không giữ stream qua recreate. Apply đã xác nhận không được tự chạy lại sau phục hồi state.

## Files dự kiến

- Thêm `model/LensOpticalProfile.kt`: typed payload, strict parser/serializer và validation; dùng `org.json` đã có, không thêm JSON library.
- Thêm `util/LensOpticalProfileIo.kt`: suspend IO giới hạn dung lượng, strict UTF-8, typed IO result; không tự thay settings.
- Sửa `util/UtilSettings.kt`: capture không ghi, touched-key set, batch apply/rollback, revalidate payload. Tái dùng snapshot helpers.
- Sửa `ui/LensSettingsActivity.kt`, `res/layout/act_lens_settings.xml`: hai control, SAF callbacks, preview, lifecycle/busy state. Giữ Material theme/insets/scrolling/touch target chuẩn.
- Chuỗi mới đầy đủ mọi locale hiện có; test đúng phạm vi từng lớp. Không thêm abstraction/framework dự phòng.

## Kiểm thử và gate

- Unit: round-trip, đủ bốn enum, các biên range, missing/unknown/duplicate field, trailing data, version/format lạ, sai kiểu, số không hữu hạn, Custom có/vắng, size limit và UTF-8 hỏng.
- Settings unit: target riêng/global/default inheritance, giữ Custom khi vắng, clear pending pinch, snapshot không ghi, failed commit/failed rollback, key khác giữ nguyên.
- Widget: hai entry point, đúng ID kể cả target không active, preview đủ giá trị/cảnh báo, Cancel/Back không ghi, busy chặn double confirm, recreate/destroy, không tự apply.
- Integration: provider stream thật/test provider, SAF callback tới preview/apply, provider lỗi, target bị xóa, Home reload, parse lỗi không đổi prefs hoặc Room.
- Test fixture phải backup/restore Room, prefs và snapshot app, không dùng workspace import rollback để dọn fixture vì API đó có thể giữ stats từ fixture. Không chạy test phá DB trên máy thật.
- Host gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.
- TECNO-only: xác minh `adb devices -l`, dùng serial khóa; backup APK/data/prefs/system state + hash trước test. SAF smoke xuất/nhập/cancel/file lỗi, kiểm Home và restore từng file. Gặp ad/consent trong screenshot thì dừng chờ owner `done`.
- Mutation validate/target/global/confirm guard; audit >9.0 mới được đóng story. Commit local được phép theo plan sau duyệt; không push tự động.
