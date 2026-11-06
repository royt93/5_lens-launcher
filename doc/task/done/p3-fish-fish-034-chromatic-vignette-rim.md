# FISH-034 — Vignette và sai sắc ở mép lens theo độ méo

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Nối tiếp `DepthOfField` (`views/DepthOfField.kt:94`) trên `RenderEffect` API 31+, fallback alpha dưới đó.

## User story

Là người dùng, tôi muốn độ cong có cảm giác quang học thật.

## Acceptance criteria

- [x] Opt-in, gate reduced-motion (reduced motion làm transition snap, không tắt hiệu ứng).
- [x] Không đổi geometry/hit-test (49 điểm thăm dò giống hệt bật/tắt; `LensGridCache` không recompute).
- [x] Đo frame 120Hz trước/sau như FISH-007: p50/p90 6 ms cả tắt lẫn bật (1689 vs 1688 frame).
- [x] Fallback API < 31: không cần `RenderEffect`, một đường code bằng `Paint.alpha` cho mọi API (chỉ phủ bằng canvas phần mềm, chưa đo máy API < 31).
- [x] Phạm vi thu hẹp theo owner: chỉ vignette; sai sắc tách thành `FISH-040`.

## Implementation notes

Gộp N4 (audit lens) và EX-4 (audit model). Owner chọn 2026-11-06: **chỉ vignette** trong story này (giảm alpha icon theo khoảng cách, chỉ khi lens đang phóng); phần **sai sắc (chromatic aberration)** tách thành story riêng. Spec: `docs/superpowers/specs/2026-11-06-fish-034-vignette-rim-design.md`; plan: `docs/superpowers/plans/2026-11-06-fish-034-vignette-rim.md`; audit `doc/task/AUDIT_FISH-034_2026-11-06.md` (9,1/10).

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
Làm story FISH-034 (Vignette và sai sắc ở mép lens theo độ méo) trong doc/task/todo/p3-fish-fish-034-chromatic-vignette-rim.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-034_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
