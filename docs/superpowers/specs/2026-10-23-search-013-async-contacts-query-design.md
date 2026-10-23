# SEARCH-013 — Async Contacts Search

Ngày: 2026-10-23. Trạng thái: draft owner review. Branch `dev`; thiết bị khóa `Pixel 7 Pro 2B051FDH3006MU`, Android 17.

## Vấn đề đã xác minh

Đường hiện tại trong `ActHome.java`:

`appSearch.onTextChanged` (`:1450`) → `updateSearchResults` (`:1682`) → `updateContactAction` (`:1932`) → `ContactSearchEngine.firstContactForQuery` → `ContentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)`.

Query đồng bộ trên main thread, không có `selection`/giới hạn trước khi cursor được đọc; toàn bộ Phone rows được materialize rồi rank trong bộ nhớ. Đường chỉ được gọi cho explicit contact prefixes và khi quyền READ_CONTACTS đã cấp. Cùng lượt gõ còn chạy quick-action resolvers; chúng không thuộc scope story này.

## Mục tiêu và ràng buộc

1. Không gọi ContentProvider query từ main thread trong keypress path.
2. Debounce truy vấn Contacts; hủy tác vụ cũ khi query đổi hoặc search đóng/view detach.
3. Bảo toàn exact contact-prefix parsing, permission behavior, ranking và `ContactSearchResult` actions.
4. Không thay đổi kết quả final so với cùng dữ liệu hiện tại; stale result không được ghi đè query mới hơn.
5. Đọc cursor trên worker; đóng cursor bằng `use`; catch `SecurityException` và trả empty như hiện tại; luôn truyền `CancellationException`.
6. Không thêm dependency/schema. Scope không refactor toàn `HomeSearchController`, không debounce quick actions hoặc app search.
7. Không thay đổi permission hoặc contacts của Pixel để đo; không clear dữ liệu app/DB.

## Thiết kế đề xuất

- Giữ `ContactSearchEngine.search` là hàm đồng bộ nội bộ thuần về ranking + provider access, nhưng chỉ gọi provider từ coroutine worker. Phân biệt rõ unit test `testRows` không phải phép đo main-thread.
- Thêm controller nhỏ, gắn vòng đời với Search UI trong `ActHome`: một `CoroutineScope`/`Job` bị cancel khi input thay đổi/đóng search/destroy, một debounce constant được đặt tên và test bằng `TestDispatcher`.
- Trên mỗi update:
  1. Hủy job cũ và tăng generation.
  2. Tính explicit contact query / permission gate đồng bộ, rẻ. Nếu không cần Contacts lookup, cập nhật row theo behavior hiện có và không query.
  3. Launch trên dispatcher worker, delay debounce, sau delay kiểm query/generation, gọi provider, rank.
  4. Chuyển kết quả lên main; chỉ render nếu query/generation vẫn là mới nhất và Activity/SearchView còn active.
- Không dùng cache contacts dài hạn trong story này vì invalidation theo contact changes có thêm receiver/registration và scope lớn hơn; ContentProvider query chỉ chạy một lần cho final debounced prefix. Nếu benchmark cho thấy vẫn chậm, cache/invalidation thành story riêng.
- Giữ projection hiện có; query filter bằng selection chưa đề xuất vì semantics matching hiện tại cho substring/tokens trên normalized full name và số điện thoại không thể diễn tả tương đương bằng SQL LIKE cho mọi locale.

## Kiểm thử

### Unit (RED/GREEN)

- Contact query extraction/permission gates giữ nguyên.
- Controller debounce: nhiều keystroke chỉ query một lần cho giá trị cuối.
- Query 1 bị gate/đang chạy rồi query 2 tới: query 1 bị cancel; kết quả query 1 không render.
- Search đóng/detach cancel coroutine; không callback vào Activity đã destroy.
- Ranking order và dial/message intents khớp behavior cũ.
- ContentResolver fake: query chạy không-main thread, cursor đóng trên success/exception/cancellation, SecurityException → empty, CancellationException không bị nuốt.

### Widget/integration

- `ActHome` test inject controllable Contacts provider/repository fixture: mở search, gõ prefix nhanh, chỉ kết quả final hiện; đóng SearchView trước completion không render stale row.
- Activity rotation/destroy trong lúc query chờ: job bị cancel, không leak observer/job và không cập nhật view.
- Permission denied path không query; permission request UX hiện có không đổi.
- Không yêu cầu real contacts permission trên thiết bị; fixture Contacts cursor không sửa danh bạ thật.

### Benchmark và device

- Tạo deterministic fixture có nhiều contacts (`1000` rows) ngoài DB/prefs thiết bị; baseline đồng bộ cũ và candidate debounce/worker phải dùng cùng query/rank data.
- Đo latency từ cuối cùng keystroke đến kết quả hiển thị (p50/p95 qua nhiều lượt), main-thread query count phải bằng 0. Không đặt threshold thời gian dễ flake; assert loại bỏ query main và chỉ một query sau burst.
- Full gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- Pixel `2B051FDH3006MU`: chỉ test UI fixture không yêu cầu READ_CONTACTS; foreground/device-state phải xác minh. Không xóa/thêm danh bạ thật. R4: ad che screenshot thì dừng chờ owner `done`.
- Snapshot/restore app prefs, settings, DB nếu test thay đổi; audit ghi rõ nếu device benchmark thực bị chặn bởi fixture/permission.

## File dự kiến

- `search/ContactSearchEngine.kt`: giữ/điều chỉnh provider method để nhận dispatcher call path rõ, test cancellation/query thread.
- `ui/ActHome.java` và/hoặc một controller Kotlin nhỏ trong `search/`: debounce, job cancellation, generation-check, main-thread UI publish.
- Unit tests dưới `app/src/test/java/com/mckimquyen/search/`.
- Widget/integration tests dưới `app/src/androidTest/java/com/mckimquyen/ui/`.
- Story, `doc/feature.md`, `doc/task/README.md`, audit SEARCH-013.

## Out of scope

QuickActionEngine resolver N+1, contacts cache + invalidation receiver, permission UX redesign, matching/ranking changes, Contact database copy/cache, SEARCH-014 shortcut Drawable cache.
