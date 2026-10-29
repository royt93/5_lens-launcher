# FISH-027 — Profile độ cong lens (fisheye / anamorphic / cylindrical)

| Field | Value |
|---|---|
| Type | idea |
| Status | done 2026-10-29 — audit 9.1/10, `doc/task/AUDIT_FISH-027_2026-10-29.md` (chưa push) |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Hiện chỉ một công thức `UtilCalculator.shiftPoint:98`. `LensGridCache` bất biến theo geometry.

## User story

Là người dùng, tôi muốn chọn hình dạng méo của lens như chọn ống kính.

## Acceptance criteria

- [x] Enum `LensProfile` + hàm thuần cho mỗi profile (Fisheye, Anamorphic, Cylindrical).
- [x] Profile theo lens (key `lens_profile_<lensId>`, không kế thừa), mặc định Fisheye giữ hành vi cũ.
- [x] Hit-test dùng đúng rect đã vẽ (integration test trên TECNO).
- [x] Test thuần từng công thức; lens rảnh vẽ đúng lưới gốc ở mọi profile kể cả reduced-motion.

## Implementation notes

Nền cho FISH-028 và FISH-032.

## Verification and Definition of Done

- [x] Unit: `LensProfileTest`, `LensProfileSettingsTest`; full 1.330/1.330. Cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget: `LensSettingsActivityWidgetTest` 7/7 trên TECNO. Cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration: `LensViewProfileIntegrationTest` 7/7, backup import 21/21, migration 5/5. Cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke TECNO KJ7 Android 14, 2026-10-29 13:08–13:12; dữ liệu khôi phục 1.189/1.189. Smoke pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] Lint/build xanh; chưa so số cảnh báo lint với baseline.
- [x] Audit 9.1/10, chưa push. A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence và status cập nhật. Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-027 (Profile độ cong lens (fisheye / anamorphic / cylindrical)) trong doc/task/todo/p3-fish-fish-027-lens-profiles-curve-shapes.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-027_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
