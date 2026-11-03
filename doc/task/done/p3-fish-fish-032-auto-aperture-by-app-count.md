# FISH-032 — Khẩu độ tự động: độ cong mặc định theo số app của lens

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done (2026-11-03, audit 9.35/10; Pixel 7 Pro smoke + full host gate pass) |
| Priority | P3 |
| Evidence | approved design: `docs/superpowers/specs/2026-11-03-fish-032-auto-aperture-design.md` |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`getDistortionFactor(lensId)` per-lens có sẵn (`FrmLens.kt:242`). Lens 8 app và lens 300 app hiện dùng cùng độ cong.

## User story

Là người dùng, tôi muốn lens mới tạo có độ cong hợp lý ngay.

## Acceptance criteria

- [x] Ánh xạ số hàng layout thực copy → distortion mặc định bằng named constants: `0..24 → 3.5f`, `25..99 → 2.5f`, `>=100 → 1.8f`.
- [x] Người dùng ghi đè bằng pinch vẫn thắng; không tự tính lại khi count thay đổi.
- [x] Lens cũ không đổi; mapper, settings và production Add lens flow được test.

## Implementation notes

Không đổi `LensGridCache`.

## Verification and Definition of Done

- [x] Unit tests cover pure mapping, per-lens state transitions, override, and all mapping boundaries; TDD RED/GREEN recorded in commit history.
- [x] Widget/production flow test covers Add lens count bands and preserved settings.
- [x] Integration tests cover DAO copied-count propagation, Home lens creation, pinch/profile, and backup/import boundaries.
- [x] Pixel 7 Pro smoke passed; APK SHA, device info, timestamp, backup/restore checks and resulting state are in `doc/task/AUDIT_FISH-032_2026-11-03.md`.
- [x] Host gate, lint, diff check and secret scan pass; no new failure.
- [x] Audit score `9.35/10` (>9.0), recorded in `doc/task/AUDIT_FISH-032_2026-11-03.md`.
- [x] Evidence/status updated before moving to `done`.
- [ ] Push: intentionally not performed; no current explicit push authorization.

No additional accessibility control or UI was introduced; existing menu semantics remain unchanged.

## Prompt để chạy loop

```text
Làm story FISH-032 (Khẩu độ tự động: độ cong mặc định theo số app của lens) trong doc/task/todo/p3-fish-fish-032-auto-aperture-by-app-count.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-032_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
