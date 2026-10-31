# FISH-028 — Bán kính ảnh hưởng của lens (lens hood)

| Field | Value |
|---|---|
| Type | idea |
| Status | done 2026-10-31 — audit 9.2/10, `doc/task/AUDIT_FISH-028_2026-10-31.md` (chưa push) |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | FISH-027 |

## Context and evidence

Méo hiện lan toàn màn hình (`b = max(lensPosition, boundary - lensPosition)`).

## User story

Là người dùng nhiều app, tôi muốn icon ngoài vùng lens đứng yên để lưới dễ đọc.

## Acceptance criteria

- [x] Setting bán kính (slider Lens hood) trong trang cài đặt lens, mặc định 100% giữ hành vi cũ; dùng smoothstep falloff.
- [x] Test thuần công thức biên (`LensHoodTest`, `LensHoodSettingsTest`).
- [x] Smoke ảnh trên TECNO KJ7 (hood 35% vs 100%).

## Implementation notes

Làm cùng tầng công thức với FISH-027.

## Known limitation (owner decision 2026-10-31)

With a small hood combined with high distortion the position mapping can step back by up to ~1 px between two points 1 px apart at the hood edge (worst measured: 1.011 px at hood 0.2 and distortion 5.0; 0 px at distortion 2.5 with hood >= 0.35). The owner chose to keep the formula as is. See `doc/task/AUDIT_FISH-028_2026-10-31.md`.

## Verification and Definition of Done

- [x] Unit: `LensHoodTest`, `LensHoodSettingsTest`; full unit 1.364/1.364. Cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget: `LensSettingsActivityWidgetTest` 8/8 trên TECNO. Cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration: `LensViewProfileIntegrationTest` 12/12, backup import 21/21, migration 5/5, hồi quy 20 lớp trên TECNO. Cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke TECNO KJ7 Android 14, 2026-10-31 11:44–11:47; dữ liệu khôi phục 1.192/1.192. Smoke pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] Lint 0 error; chưa so warning baseline.
- [x] Audit 9.2/10, chưa push. A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence và status cập nhật. Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-028 (Bán kính ảnh hưởng của lens (lens hood)) trong doc/task/todo/p3-fish-fish-028-lens-hood-bounded-radius.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-028_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
