# UI-026 — updateColor và FrmSettings crash khi màu nền lưu trong prefs hỏng

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Stability |
| Estimate | 2 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`ui/ActHome.java:309` `Color.parseColor(kBackgroundColor)` không bọc try/catch, được gọi trong `onResume()` (`:2044`). `KEY_BACKGROUND_COLOR` chỉ có default khi key vắng; giá trị rỗng/sai định dạng (import backup, prefs bị sửa) ném `IllegalArgumentException`. `ui/FrmSettings.kt:316,327` `"#${full.substring(3)}"` ném `StringIndexOutOfBoundsException` trên chuỗi rỗng (`?: ""` chỉ chặn null). Không có đường phục hồi trong app.

## User story

Là người dùng, tôi muốn app không crash mỗi lần mở dù dữ liệu màu bị hỏng.

## Acceptance criteria

- [x] Giá trị màu sai/rỗng rơi về màu mặc định, không crash, có log warning.
- [x] Hàm parse màu dùng chung một chỗ (không còn `parseColor` trần).
- [x] Test đỏ: prefs `""`, `"zzz"`, `"#12"` không làm `onResume` ném.
- [x] Widget test mở `ActHome` với prefs hỏng.

## Implementation notes

Một helper `parseColorOrDefault` trong `util/`; dùng ở cả `ActHome` và `FrmSettings`.

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

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_UI-026_2026-10-20.md`. Unit 961/961, lint 0 lỗi/7 warning, 17 instrumentation + smoke tay trên TECNO KJ7 (máy owner chọn). RED thật trên máy (`IllegalArgumentException: Unknown color`). Giới hạn: không có đường nhập màu tùy ý trong UI nên prefs hỏng chỉ tái hiện bằng test/backup; một lần treo instrumentation chưa rõ nguyên nhân; trong lúc làm đã để lại state máy lệch (đã khôi phục về 0 diff, ghi chi tiết trong audit).

## Prompt để chạy loop

```text
Làm story UI-026 (updateColor và FrmSettings crash khi màu nền lưu trong prefs hỏng) trong doc/task/todo/p1-ui-ui-026-update-color-crash-bad-hex.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-026_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
