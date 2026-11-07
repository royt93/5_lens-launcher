# FISH-040 — Sai sắc (chromatic aberration) ở mép lens

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done 2026-11-07 (audit 9,1/10; Samsung S24 Ultra; chưa push) |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | FISH-034 |

## Context and evidence

Tách ra từ `FISH-034` (owner, 2026-11-06): story đó chỉ làm vignette. Sai sắc nhân 2–3 lần chi phí vẽ icon ở mép; bài học `FISH-007` là layer full-resolution đẩy p50 kéo lens từ 6 ms lên 27 ms trên Pixel 7 Pro. Số đo hiện tại trên TECNO KJ7: p50/p90 6 ms với vignette bật/tắt (xem `doc/task/AUDIT_FISH-034_2026-11-06.md`).

## User story

Là người dùng, tôi muốn mép lens có cảm giác quang học thật hơn ngoài việc tối dần.

## Acceptance criteria

- [x] Opt-in, mặc định tắt, có trong reset-to-defaults; gate reduced-motion như `FISH-007`/`FISH-034`. (`ResetEntriesTest`, `FrmSettingsChromaticAberrationWidgetTest` 4, `theOffsetFadesWithTheLensAndSnapsUnderReducedMotion`)
- [x] Không đổi geometry/hit-test/`LensGridCache`. (`geometryHitTestAndGridCacheAreIdenticalWithAndWithoutTheEffect`)
- [x] Đo frame 120 Hz trước/sau; nếu vượt ngân sách thì downsample hoặc giảm số icon áp dụng, không tuyên bố pass khi thiếu số đo hợp lệ. Cap 8 vượt (mean +0,54 ms), hạ xuống 4: mean 8,11 so với 8,10 ms off, jank 0,42% so với 0,44%, >2400 frame/lần, 3 lần mỗi trạng thái. Ngưỡng tuyệt đối 8,3 ms ở p90 không đạt kể cả khi tắt.
- [x] Phương án rõ cho API < 31 (không có `RenderEffect`). Vẽ lại bitmap với `ColorMatrixColorFilter` + `PorterDuff.Mode.ADD`, cùng một đường mã cho mọi API; chỉ phủ bằng canvas phần mềm trong widget test.

## Implementation notes

Cần chọn cơ chế (vẽ icon nhiều lần lệch pha vs `RenderEffect`/`ColorMatrix`) bằng brainstorming; chưa có thiết kế. Phải tương thích với `LensVignette` (alpha) và DoF.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries where applicable.
- [x] Smoke tests on the locked device; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.

## Kết quả

Audit: `doc/task/AUDIT_FISH-040_2026-11-07.md` (9,1/10). Cap `MAX_ICONS = 4` (hạ từ 8 sau phép đo frame). Giới hạn chính: viền cap 4 nhẹ hơn rõ rệt, owner chưa xem tay; mutation chạy ở cap 8; số đo frame một máy, sạc AC. Commit: `6e8aa3bb`, `9235d35c`, `b7bd3227`, `55deeed5`, `0ef0ce59`.
