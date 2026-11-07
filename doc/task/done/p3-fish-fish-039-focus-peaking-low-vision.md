# FISH-039 — Focus peaking cho người thị lực kém

| Field | Value |
|---|---|
| Type | idea |
| Status | done |
| Priority | P3 |
| Evidence | idea |
| Epic | Accessibility |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Mở rộng `DepthOfField.band()` sẵn có: viền nét cho icon trong vùng sharp khi bật blur.

## User story

Là người thị lực kém, tôi muốn thấy rõ icon đang được lấy nét.

## Acceptance criteria

- [x] Viền nét opt-in. (công tắc riêng `focus_peaking`, mặc định tắt, có trong reset; ảnh Settings bật bằng chạm thật)
- [x] Không tốn thêm allocation trong `onDraw`. (một Paint tái sử dụng; `FocusPeakingNoAllocationTest` kiểm tĩnh hai hàm mới; gfxinfo 0 jank)
- [x] Test + ảnh smoke. (unit 1589, widget 13, integration 7, Settings 4, smoke ảnh 2; ảnh viền khi kéo và khi ghim)

## Implementation notes

Nhỏ, độc lập.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-039 (Focus peaking cho người thị lực kém) trong doc/task/todo/p3-fish-fish-039-focus-peaking-low-vision.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-039_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.

## Kết quả

Đóng 2026-11-07, audit 9,2/10 (`doc/task/AUDIT_FISH-039_2026-11-06.md`). Thiết bị: Pixel 7 Pro `2B051FDH3006MU` cho test, frame budget và ảnh adb; Samsung S24 Ultra `R5CX613VZBR` cho ảnh công tắc Settings và trạng thái ghim (owner đổi thiết bị). Chấp nhận giới hạn: số đo frame một lượt mỗi trạng thái trên Pixel (p50 5→6 ms, p99 7→10 ms, 0 jank); owner chưa thử tay; màu highlight có thể trùng nền; kiểm tra không-allocation là kiểm tĩnh; bản dịch 16 locale do AI viết. Chưa push.
