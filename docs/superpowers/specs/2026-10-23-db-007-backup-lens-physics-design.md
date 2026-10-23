# DB-007 — Backup v2 mang physics từng lens

Ngày: 2026-10-23. Trạng thái: chờ owner duyệt (quyết định lens default đã chốt). Branch `dev`; thiết bị khóa Pixel 7 Pro `2B051FDH3006MU`.

## Vấn đề đã xác minh

`LayoutBackupIo.exportAsync` chỉ xuất `LensAppSettingsBackup` (scope, selection, frozen, hidden) và `LayoutBackupLens` chỉ mang các trường đó. Bốn key physics per-lens trong SharedPreferences không có trong backup:

| Base key | Kiểu | Lens default (`lensKey`) | Lens phụ (`lensKey`) | Getter/Setter |
|---|---|---|---|---|
| `distortion_factor` | Float | `distortion_factor` | `distortion_factor_<lensId>` | `getDistortionFactor`/`saveDistortionFactor` |
| `min_icon_size` | Float | `min_icon_size` | `min_icon_size_<lensId>` | `getIconSize`/`saveIconSize` |
| `custom_distortion_factor` | Float | `custom_distortion_factor` | `custom_distortion_factor_<lensId>` | `getCustomDistortionFactor`/`saveCustomDistortionFactor` |
| `smart_focus_bias` | Boolean | `smart_focus_bias` | `smart_focus_bias_<lensId>` | `isSmartFocusBias`/`saveSmartFocusBias` |

Hậu quả: khôi phục backup trên máy mới (hoặc sau khi reset) trả lens về giá trị chung/mặc định, mất cảm giác đã tinh chỉnh. Lens không có override kế thừa key chung không hậu tố; đó là trạng thái hợp lệ, khác với "override bằng đúng giá trị mặc định".

## Mục tiêu và ràng buộc

1. Thêm dữ liệu **additive**, tùy chọn: file v2 hiện có vẫn đọc được; không tăng `schemaVersion`.
2. Export/import khứ hồi giữ đúng bốn giá trị cho từng lens **có override**.
3. Lens không có override không bị biến thành override: export không ghi giá trị cho key đó (trường vắng trong `physics`); import xóa override cũ của lens khi file mới có `physics` nhưng thiếu trường đó, để lens kế thừa lại.
4. Import file không có block physics (file cũ) **không đụng** các key physics hiện có trên máy.
5. Giữ tính toàn vẹn: một giá trị physics sai kiểu hoặc ngoài khoảng hợp lệ làm file bị coi là malformed, như quy ước hiện tại của `LayoutBackupLens.fromJson`, không import một phần.
6. Rollback import (snapshot/restore prefs) phải bao gồm các key mới để lỗi giữa chừng khôi phục đúng.
7. Không thêm dependency, không đổi Room schema, không đổi prefs ngoài bốn key. Không thay `ponytail` commit() synchronous contract.

## Thiết kế đề xuất

### Lens default và lens phụ

`lensKey(baseKey, "default") == baseKey`, tức là key của lens default **chính là** key chung mà mọi lens phụ không có override kế thừa. Hai trường hợp phải xử lý khác nhau:

- **Lens default:** key không hậu tố. "Có giá trị" nghĩa là key tồn tại trong prefs; export ghi giá trị đó. **Quyết định owner (2026-10-23):** import chỉ **ghi** khi file có trường; trường thiếu **không xóa** key chung trên máy, vì key này là giá trị kế thừa của mọi lens phụ không có override và xóa nó sẽ lặng lẽ đổi cảm giác các lens đó. Đánh đổi chấp nhận: không khôi phục được trạng thái "default chưa từng có giá trị" trên máy đích đã có giá trị.
- **Lens phụ:** key `<base>_<lensId>`. Có key = override; không có key = kế thừa key chung (tức giá trị của lens default).

Bảng ở trên dùng đúng cách ánh xạ này; export/import luôn gọi một helper chung để tính tên key, không tự nối chuỗi.

### Dữ liệu

Thêm `data class LensPhysicsBackup(val distortionFactor: Float?, val iconSize: Float?, val customDistortionFactor: Float?, val smartFocusBias: Boolean?)`. Trường `null` nghĩa là key không tồn tại cho lens đó.

`LayoutBackupLens` thêm `val physics: LensPhysicsBackup? = null`:

- `physics == null` (JSON không có trường `physics`): file cũ hoặc không có thông tin. Import **bỏ qua**, giữ nguyên mọi key physics trên máy.
- `physics != null`: nguồn sự thật cho lens đó. Trường có giá trị thì ghi key. Trường `null`: với **lens phụ** thì **xóa** key để lens kế thừa lại; với **lens default** thì **giữ nguyên** key trên máy (xem quyết định owner ở trên).

JSON ví dụ: `"physics": {"distortionFactor": 0.8, "iconSize": 24.0, "smartFocusBias": true}`. Trường vắng hoặc `null` JSON nghĩa là không có key.

### Export

`LayoutBackupIo.exportAsync` đọc từng key bằng `prefs.contains(key)` rồi mới đọc giá trị, không dùng getter có fallback hoặc clamp (getter sẽ biến "kế thừa" thành một giá trị cụ thể). Export mới **luôn** ghi object `physics` cho mọi lens, kể cả `{}` khi lens không có key nào. Nhờ vậy `{}` phân biệt được "lens này không có override" với file cũ "không có thông tin", và import file mới sẽ xóa override thừa trên máy đích.

### Import

- **Parse (`LayoutBackupLens.fromJson`):** thiếu `physics` ⇒ `null`. `physics` là JSON `null`, mảng, chuỗi hoặc số ⇒ malformed (trả `null`, cả file bị từ chối như quy ước hiện tại). Trong object: trường vắng hoặc JSON `null` ⇒ không có key; giá trị Float phải là số hữu hạn nằm trong khoảng bên dưới; `smartFocusBias` phải là Boolean thật. Sai kiểu, NaN/Infinity hoặc ngoài khoảng ⇒ malformed; không clamp ngầm.
- **Khoảng hợp lệ** (hằng sẵn có trong `UtilSettings`):
  - `distortionFactor` và `customDistortionFactor`: `MIN_DISTORTION_FACTOR` (0.5) đến `MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR` (5.0).
  - `iconSize`: `MIN_ICON_SIZE` (10.0) đến `MAX_ICON_SIZE + MIN_ICON_SIZE` (55.0).
- **Ghi (`replaceWorkspacePreferences`):** `LensAppSettingsBackup` nhận thêm `physics: LensPhysicsBackup? = null`. Với mỗi lens có `physics != null`, trong cùng editor `commit()` hiện có: ghi các key có giá trị; với lens phụ `remove` các key `null`, với lens default bỏ qua key `null`. Lens có `physics == null` không bị chạm. Key của lens khác không bao giờ bị chạm.
- **Xóa hàng loạt:** `isWorkspacePreferenceKey` vẫn **không** gồm bốn key physics, để import file cũ không xóa giá trị trên máy. Chỉ phần ghi theo từng lens ở trên được xóa key.
- **Rollback:** `snapshotWorkspacePreferences` và `restoreWorkspacePreferences` thêm một tập key physics riêng (đủ các key của mọi lens đang có) để lỗi giữa chừng khôi phục đúng, kể cả key ban đầu vắng (phải bị xóa lại).

### Tương thích

- File v2 cũ: `physics = null`, import không đổi key physics; fixture v2 hiện có phải pass nguyên vẹn.
- Backup v1: không có lens ⇒ không đụng physics.
- File mới đọc bởi bản app cũ: trường lạ có bị parser cũ bỏ qua hay từ chối chưa được xác minh trong spec này; test parser sẽ kiểm tra hành vi thật của parser hiện tại với khóa không biết và kết quả được ghi vào audit như một giới hạn, không phỏng đoán.

## Kiểm thử

### Unit
- Parse/serialize `LayoutBackupLens` khứ hồi với bốn giá trị; trường vắng; object rỗng; số NaN/Infinity/ngoài khoảng; sai kiểu; `physics` là mảng/chuỗi ⇒ malformed.
- Snapshot factory/`toJson`: lens không override xuất `physics: {}`.
- `UtilSettings.replaceWorkspacePreferences`: lens có physics ghi đúng key; lens `physics == null` giữ nguyên key; lens phụ có `{}` xóa override; lens default có `{}` giữ nguyên key chung; key lens khác không bị chạm.
- Rollback `restoreWorkspacePreferences` khôi phục đủ bốn key (cả key vắng).

### Integration
- Export → import trên Robolectric/instrumentation với nhiều lens (default + lens có override + lens kế thừa) và xác nhận lens kế thừa vẫn kế thừa khi giá trị chung đổi sau import.
- Fixture v2 hiện tại (không `physics`) import không làm mất giá trị máy.
- Lỗi commit giữa chừng ⇒ rollback giữ nguyên prefs.

### Thiết bị
- Chạy các test backup hiện có và mới trên Pixel qua `adb -s … am instrument`; không đổi prefs người dùng ngoài snapshot/restore trong test (`DeviceStateGuard`). Smoke Home sau test.
- Full gate `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`, `scripts/check-secrets.sh`.

## File dự kiến

- `model/LayoutBackup.kt` (parse/serialize `physics`), `util/UtilSettings.kt` (`LensAppSettingsBackup`, replace/snapshot/restore), `util/LayoutBackupIo.kt` (export/import wiring), tests dưới `app/src/test/java/com/mckimquyen/model/` và `util/`, androidTest backup hiện có.
- Story, `doc/feature.md`, `doc/task/README.md`, audit DB-007.

## Không làm

Không tăng schema version, không đồng bộ physics toàn cục (không hậu tố), không thay đổi UI hay hành vi pinch, không backup các key khác (`pending_distortion`, scale/animation-time toàn cục).
