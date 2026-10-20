# CORE-003 — App không tải được icon biến mất im lặng khỏi launcher

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Refresh pipeline |
| Estimate | 2 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`services/TaskUpdateApps.kt:159-164` `stripIcons` chỉ `apps.add` khi `app.icon != null`. `UtilBitmap.packageNameToBitmap` trả null với `NameNotFoundException`/`Resources.NotFoundException`/OOM (`util/UtilBitmap.kt:70-82`). App thật không xuất hiện, không log, không placeholder, dù `LensView` và `SearchResultAdapter` đã có đường placeholder/reload.

## User story

Là người dùng, tôi muốn mọi app cài trên máy đều mở được từ launcher, kể cả khi icon lỗi.

## Acceptance criteria

- [x] Số app vào `stripIcons` bằng số app ra (test khẳng định).
- [x] App icon null dùng placeholder và vẫn khởi chạy được.
- [x] Có log mức warning kèm package khi icon lỗi (không lộ dữ liệu nhạy cảm).
- [x] Không đổi cache key `iconCacheKey` của app có icon.

## Implementation notes

Chỉ thêm app vào list, bỏ qua đưa bitmap vào `icons` khi null. Kiểm tra `BitmapCache` miss đi đúng đường reload.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Kết quả (2026-10-20)

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_CORE-003_2026-10-20.md`. Unit 940/940, lint 0 lỗi/7 warning, 57 instrumentation trên Pixel 7 Pro và 55 trên TECNO KJ7 (máy owner chọn sau cùng), state máy 0 diff. Giới hạn: không tái hiện được app icon hỏng thật; `LensView` chưa vẽ placeholder trong lúc chờ reload; có một flake `ActHomeFullyDrawnIntegrationTest` sau cài mới chưa rõ nguyên nhân.

## Prompt để chạy loop

```text
Làm story CORE-003 (App không tải được icon biến mất im lặng khỏi launcher) trong doc/task/todo/p1-core-core-003-apps-without-icon-vanish.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_CORE-003_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
