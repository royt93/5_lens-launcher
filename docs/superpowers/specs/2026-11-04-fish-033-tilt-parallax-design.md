# FISH-033 — Thấu kính nghiêng theo cảm biến (tilt parallax)

**Ngày:** 2026-11-04

**Trạng thái:** Thiết kế đã duyệt trong hội thoại; spec chờ owner đọc lại trước lập plan. Chưa triển khai.

## Mục tiêu và quyết định đã duyệt

Khi người dùng không chạm vào lens, tâm méo của fisheye trôi nhẹ theo góc nghiêng của máy, tạo cảm giác ống kính thật. Chạm vào lens thì ngón tay thắng hoàn toàn.

- **Lúc rảnh:** đặt một tâm méo ảo và dịch nó theo góc nghiêng; có chạm thì nhường hoàn toàn cho ngón tay, không cộng dồn.
- **Phạm vi:** một công tắc toàn cục `KEY_TILT_PARALLAX`, mặc định **tắt**, không theo từng lens, không đưa vào backup.
- **Cường độ:** biên độ tối đa cố định, nhỏ, là hằng số có tên; **không có slider**.
- **Tự tắt:** dùng lại `LensPhysicsPolicy.shouldReduceLensMotion(context)` (reduced-motion, battery saver, thermal ≥ MODERATE), cộng điều kiện vòng đời (Activity tạm dừng, search đang mở, máy không có sensor).
- **Sensor:** `Sensor.TYPE_GAME_ROTATION_VECTOR`, không cần quyền.
- **Sở hữu:** `ActHome` đăng ký sensor một lần và đẩy offset vào `LensView` hiện tại (`ActHome.lensViews`); `LensView` không đăng ký sensor.
- **Cảnh báo:** mô tả setting nêu rõ rủi ro chóng mặt.
- Không Ads/IAP/TalkBack. Chỉ test trên TECNO KJ7 `115333744A005844`. Không push khi chưa có yêu cầu hiện hành.

## Kiến trúc đã kiểm chứng trong code

- `LensView` lưu tâm méo ở `mTouchX`/`mTouchY`; `-Float.MAX_VALUE` nghĩa là không có chạm. `hasActiveTouch` quyết định có áp công thức méo hay không. Khi rảnh `drawGrid` vẽ đúng lưới gốc, và mọi test geometry/hit-test dựa vào đúng trạng thái đó. Tilt phải giữ nguyên hành vi này khi tắt.
- `LensGridCache` chỉ phụ thuộc kích thước/icon/insets, không phụ thuộc tâm méo hay distortion; không được đổi.
- `ActHome.lensViews` là trang lens đang hiển thị; `LensViewHolderHost.activeLensView()` trả về nó. `ActHome` hiện không override `onPause`; `BaseActivity.onPause` có sẵn.
- `LensView.onDetachedFromWindow` đã reset trạng thái chạm (BUG-019) và trang ViewPager2 được tái dùng, nên offset tilt phải bị xóa khi detach.
- Mẫu công tắc tương tự: `KEY_LENS_ELASTIC_OVERSCROLL` có hằng key/default, getter/setter trong `UtilSettings`, `MaterialSwitch` trong `frm_lens.xml`, nối trong `FrmLens`, và nằm trong `ResetEntries` kèm `ResetEntriesTest` (danh sách key cố định).
- Máy TECNO KJ7 có `Game Rotation Vector Sensor` (AOSP, `type 15`). Sensor có thể vắng trên máy khác; khi đó tính năng phải im lặng không hoạt động.

## Hành vi

1. Tilt chỉ hoạt động khi **tất cả** đúng: công tắc bật, `shouldReduceLensMotion` sai, Activity ở trạng thái resumed, search overlay không hiển thị, `ActHome` không ở chế độ list, máy có sensor.
2. Khi chạm lưới: `mTouchX/mTouchY` do ngón tay quyết định; tilt không đóng góp và không ghi vào các trường chạm.
3. Khi rảnh và tilt đang hoạt động: `LensView` vẽ với tâm ảo = tâm lưới + offset tilt, cùng `animationMultiplier` nhỏ cố định (hằng số có tên) để méo nhẹ. Hit-test dùng trạng thái chạm thật, nên không có icon nào bị coi là "đang chạm" chỉ vì tilt.
4. Khi tilt tắt hoặc không có offset: `LensView` vẽ lưới gốc, bit-for-bit như hiện tại (test bảo vệ).
5. Gate đổi trạng thái giữa chừng (thermal tăng, battery saver bật, search mở): hủy đăng ký sensor và xóa offset ngay.
6. Mất offset khi: detach, Activity pause, đổi trang lens (trang cũ xóa offset, trang mới nhận từ mẫu kế tiếp), tắt công tắc.

## Phép tính (`util/TiltParallax.kt`, thuần)

Đầu vào: vector quay thô của sensor (x, y, z, w quaternion hoặc 3 thành phần `rotation vector`), hướng màn hình hiện tại, kích thước vùng lưới. Đầu ra: `TiltOffset(x, y)` bằng pixel, đã clamp và làm mịn.

- Quy đổi quaternion về góc pitch/roll quanh trục màn hình, có xét xoay màn hình (0/90/180/270) để nghiêng trái/phải luôn là dịch ngang theo người dùng.
- **Góc tham chiếu**: lấy từ mẫu ổn định đầu tiên sau khi bật hoặc sau khi Activity resumed, để người dùng cầm máy ở tư thế quen thuộc là "tâm". Reset khi bật lại.
- Clamp góc tới `TILT_MAX_ANGLE_RADIANS`, chuẩn hóa về `[-1, 1]`, nhân biên độ `TILT_MAX_OFFSET_FRACTION` của cạnh ngắn vùng lưới (khoảng 12%, hằng số có tên).
- Làm mịn bằng bộ lọc thông thấp một cực (`TILT_SMOOTHING_FACTOR`), có vùng chết nhỏ (`TILT_DEAD_ZONE_RADIANS`) để rung tay không làm trôi.
- Không NaN/Infinity ra ngoài: đầu vào không hữu hạn trả offset 0 và không cập nhật bộ lọc.
- Không cấp phát trong hàm cập nhật chính để dùng được ở tần số sensor (trả giá trị qua trường có sẵn hoặc `value class`).

## Sensor và vòng đời (`util/TiltSensorSource.kt`)

- Nhận `SensorManager` qua constructor; `start(listener)` chỉ đăng ký nếu `getDefaultSensor(TYPE_GAME_ROTATION_VECTOR)` không null, dùng `SENSOR_DELAY_UI`; `stop()` hủy đăng ký và đưa trạng thái về rảnh. Gọi lặp `start`/`stop` an toàn (idempotent), không đăng ký hai lần.
- Không giữ tham chiếu `Activity`; callback chỉ giữ hàm nhận offset. `ActHome` đặt callback ở `onResume` và xóa ở `onPause`/`onDestroy`.
- Xử lý sensor báo độ chính xác thấp/`SENSOR_STATUS_UNRELIABLE`: bỏ mẫu, không dịch.
- Đăng ký trên main thread (callback main) để không cần đồng bộ; `LensView.invalidate()` chỉ khi offset đổi quá ngưỡng nhỏ, tránh vẽ lại liên tục khi máy đứng yên.

## Thay đổi file dự kiến

- Thêm `util/TiltParallax.kt` (toán + hằng số) và `util/TiltSensorSource.kt` (đăng ký sensor).
- Sửa `util/UtilSettings.kt`: `KEY_TILT_PARALLAX`, `DEFAULT_TILT_PARALLAX = false`, `getTiltParallax`/`saveTiltParallax`, nhánh `getBoolean`.
- Sửa `ui/ActHome.java`: tạo/đăng ký/hủy `TiltSensorSource` đúng vòng đời, đánh giá gate, đẩy offset vào `lensViews`; thêm `onPause`.
- Sửa `views/LensView.kt`: nhận offset tilt ảo, dùng khi rảnh; xóa khi detach/reset.
- Sửa `ui/FrmLens.kt`, `res/layout/frm_lens.xml`: công tắc và mô tả cảnh báo chóng mặt.
- Sửa `ui/settings/ResetEntries.kt` + `ResetEntriesTest`: đưa vào danh sách reset (mặc định tắt) theo mẫu elastic overscroll.
- Chuỗi mới ở đủ 17 locale: tên công tắc và mô tả (kèm cảnh báo).

## Ngoài phạm vi

Slider cường độ, bật theo từng lens, ngưỡng pin riêng, đọc `BatteryManager`, tilt khi đang chạm, dịch cả lưới như parallax nền, đổi hit-test, đổi `LensGridCache`, backup/import, quyền mới, dependency, TalkBack.

## Kiểm thử

- **Unit (JVM):** `TiltParallax` — góc 0 cho offset 0, clamp ở biên, xoay màn hình 0/90/180/270, vùng chết, làm mịn hội tụ, không NaN, góc tham chiếu. `UtilSettings` — mặc định tắt, lưu/đọc. `ResetEntries` — key mới có trong danh sách. Gate — bảng (công tắc, reduced-motion, battery saver, thermal, search mở, không sensor).
- **Widget:** `ActHome` bật công tắc rồi pause/resume — sensor đăng ký đúng một lần, hủy đúng lúc; recreate không để lại listener; search mở thì hủy; reduced-motion thì không đăng ký.
- **Integration (TECNO KJ7):** `LensView` thật — tắt thì vẽ đúng lưới gốc; bật với offset khác 0 thì tâm méo dịch đúng hướng; chạm vào thì offset bị bỏ qua; detach xóa offset; `LensGridCache` không recompute khi tilt đổi.
- **Smoke tay (TECNO KJ7):** bật công tắc, nghiêng máy thấy lưới trôi nhẹ; chạm thì nhường ngón tay; mở search thì dừng; tắt công tắc về lưới gốc; kiểm số listener sensor đăng ký bằng `dumpsys sensorservice` trước/sau.
- **Đo:** số mẫu sensor/s và thời gian frame khi bật so với tắt trên TECNO; nếu không đo được pin thật thì nói rõ là chưa đo.
- Mutation: bỏ gate reduced-motion, bỏ hủy đăng ký, tilt vẫn áp khi có chạm, đổi dấu trục, bỏ clamp, đăng ký trùng.
- Gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`. Backup/restore TECNO bằng hash từng file trước và sau, như FISH-036.

## Rủi ro đã biết

- Say chuyển động: giảm bằng mặc định tắt, biên độ nhỏ, làm mịn, vùng chết, cảnh báo trong mô tả; không thể loại bỏ hoàn toàn với người nhạy cảm.
- Pin: sensor chạy liên tục khi bật; giảm bằng `SENSOR_DELAY_UI`, hủy khi nền/search mở/battery saver. Mức tiêu thụ thật chưa đo trước khi triển khai.
- Khác thiết bị: hướng trục sensor và xoay màn hình có thể lệch giữa nhà sản xuất; chỉ xác minh trên TECNO KJ7, không khẳng định cho máy khác.
- `shouldReduceLensMotion` đọc `ANIMATOR_DURATION_SCALE`, nên người tắt animation hệ thống sẽ không có tilt (đúng ý đồ).
