# PERF-007 — Gom đọc và ghi Room khi scan app

Ngày: 2026-10-23. Trạng thái: chờ owner duyệt spec. Nhánh: `dev`, không worktree.

## Vấn đề đã xác minh

`UtilApp.getAppShells` gọi `findAnyByIdentifier` cho từng launcher activity; nếu palette còn bằng 0, tính màu từ icon và gọi `setAppPaletteColorAndAwait` ngay trong vòng lặp. Mỗi lần ghi mở transaction riêng: `insertIfAbsent` rồi `updatePaletteColor`.

Mục tiêu: một lượt đọc Room trước vòng lặp và một transaction ghi palette mới sau scan. Số statement ghi vẫn tỷ lệ với số app có palette mới; không tuyên bố toàn bộ batch là một câu SQL.

## Phạm vi và bất biến

- Không đổi schema, dependency, cache icon, sort, layout, ads/VIP hoặc generation guard.
- Palette dùng chung theo identifier; visibility, lock, organization, order và open count giữ nguyên.
- Không cài/gỡ app để đo, không clear DB/prefs thiết bị. Pixel 7 Pro `2B051FDH3006MU` vẫn là target duy nhất; phối hợp tránh thao tác đồng thời với phiên khác.
- Full refresh có các lượt đọc Room phục vụ merge lens/open count riêng. Cam kết một lượt đọc chỉ áp dụng phần tra palette trong `getAppShells`, không phải toàn pipeline.

## Thiết kế đọc

1. Tái dùng `AppPersistentDao.getAllRows()` một lần trước vòng lặp.
2. Tạo map theo identifier, giữ row đầu tiên gặp cho mỗi identifier, kể cả palette bằng 0. Không dùng `associateBy` ghi đè bằng row cuối và không tự chọn màu non-zero, MAX hoặc lens hiện hành.
3. Với nhiều lens có palette đồng nhất, kết quả giữ nguyên. Nếu dữ liệu cũ có palette khác nhau giữa lens, `findAnyByIdentifier LIMIT 1` hiện không có `ORDER BY`, nên không có hợp đồng chọn row xác định. Spec này cũng không bổ sung chính sách sửa dữ liệu hay ưu tiên lens; test phải ghi nhận giới hạn đó, không hứa giữ một màu vốn không xác định.
4. Tra map trong vòng lặp. Palette 0 và icon null giữ nguyên hành vi fallback. Palette mới tính bằng 0 không được ghi.

## Thiết kế ghi

- Gom row `AppPersistent.defaults(packageName, name).copy(paletteColor = color)` cho app có palette mới khác 0; không cần `Pair` chứa màu trùng lặp.
- Khi pending không rỗng, gọi một helper DAO `setPaletteColors(rows: List<AppPersistent>)` có `@Transaction`.
- Trong transaction, mỗi row dùng `insertIfAbsent(row)` rồi `updatePaletteColor(row.identifier, row.paletteColor)`. Không dùng REPLACE hoặc ghi đè toàn entity.
- Conflict là `(LENS_ID, IDENTIFIER)`: row chỉ tồn tại ở lens khác vẫn dẫn tới tạo row ở lens default như helper cũ; row default có sẵn được giữ nguyên ngoài palette. `updatePaletteColor` cập nhật mọi lens cùng identifier.
- Nếu cùng component xuất hiện lặp trong kết quả PackageManager, dùng palette vừa tính trong map để tránh tính/ghi lặp và giữ các shell cùng màu.
- Lỗi DB hoặc cancellation phải truyền lên; transaction rollback nguyên lô. `TaskUpdateApps` không commit snapshot mới khi scan thất bại. Không thêm catch nuốt lỗi.

## File dự kiến

- `util/UtilApp.kt`: đọc map một lần, gom palette và gọi helper cuối vòng lặp.
- `model/AppPersistentDao.kt`: thêm helper transaction; tái dùng query/insert hiện có.
- Tests cạnh scanner/DAO hiện có; không tạo abstraction production chỉ để mock.
- `doc/feature.md`, story PERF-007, README và audit: kết quả thực tế, số query, benchmark và giới hạn.

## Kiểm thử và bằng chứng

### Unit — RED/GREEN

- Đường scan thật gọi `getAllRows` một lần, không gọi `findAnyByIdentifier` cho từng app.
- Palette ở lens không phải default được tái dùng; palette non-zero không bị tính/ghi lại.
- Icon null, màu mới bằng 0, danh sách app rỗng và component lặp không gây ghi sai.
- Nhiều palette mới gọi helper batch một lần; không gọi helper ghi từng app trong vòng lặp.

### Integration Room

- Batch nhiều app mới/existing; giữ visibility, lock, favorite, folder, pinned zone, order và open count.
- Chỉ có row lens khác: giữ row đó và semantics tạo row default; palette nhất quán sau ghi.
- Ghi theo một transaction; lỗi giữa lô rollback mọi thay đổi. Kiểm cancellation rollback bằng fixture kiểm soát được nếu Room test pattern hỗ trợ; không coi compile-RED là bằng chứng rollback.
- Query count tách SELECT, statement ghi và số transaction, tránh đánh tráo một transaction thành một SQL.

### Thiết bị và hiệu năng

- Đo trước/sau cùng fixture, số app, trạng thái palette, icon pack và cache tương đương. Đo cả warm palette và palette cần tính mới; không benchmark baseline thiếu bitmap decode/coroutine/settings so với production đầy đủ.
- Dùng DB fixture riêng cho benchmark nếu cần seed/xóa palette; không xóa hoặc thay dữ liệu người dùng trên DB thật để tạo cold state.
- Chạy widget reload/regression Home hiện có: không thêm UI mới; smoke Home sau refresh phải hiện grid app. Unit không thay thế bằng chứng UI.
- Full gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`, `scripts/check-secrets.sh`.
- Instrumentation chỉ chạy qua `adb -s 2B051FDH3006MU ... am instrument`, không Gradle `connected*`. Gặp ad che UI dừng chờ owner `done`.
- Snapshot/restore dữ liệu test và thiết bị; audit >9/10 và gate xanh trước push `origin/dev`, không stage `.idea`.

## Không làm

Không sửa palette khi đổi icon pack, định nghĩa lại ưu tiên palette giữa lens có dữ liệu khác nhau, dùng SQL sinh động kích thước lớn, thêm cache lâu dài hoặc thay đổi pipeline ngoài phần palette.
