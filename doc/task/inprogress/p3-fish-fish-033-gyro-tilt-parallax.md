# FISH-033 — Thấu kính nghiêng theo cảm biến (tilt parallax)

| Field | Value |
|---|---|
| Type | exclusive |
| Status | in progress 2026-11-04 — code/UI/test xong, audit 9,30/10, TECNO smoke sensor→render và touch override đạt; chưa đo A/B pin/frame, giữ in progress |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Tâm fisheye dịch theo `TYPE_GAME_ROTATION_VECTOR`, không cần quyền. Gate theo `LensPhysicsPolicy.shouldReduceLensMotion()` như FISH-007/026. `LensGestureState` có sẵn để nhường pan.

## User story

Là người dùng, tôi muốn lens nghiêng nhẹ theo máy như ống kính thật.

## Acceptance criteria

- [ ] Opt-in, tắt khi reduced-motion/pin yếu/nhiệt.
- [ ] Hủy đăng ký sensor đúng vòng đời (không rò).
- [ ] Test thuần phép dịch tâm + đo pin/frame.
- [ ] Có hộp cảnh báo chóng mặt trong mô tả setting.

## Implementation notes

Rủi ro pin và say chuyển động; đặt mặc định tắt.

Spec: `docs/superpowers/specs/2026-11-04-fish-033-tilt-parallax-design.md`. Đã chốt: tilt chỉ lúc rảnh (chạm thì nhường), công tắc chung mặc định tắt, biên độ cố định nhỏ không slider, gate bằng `shouldReduceLensMotion` + vòng đời, `ActHome` sở hữu sensor, chỉ TECNO.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [ ] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-033 (Thấu kính nghiêng theo cảm biến (tilt parallax)) trong doc/task/todo/p3-fish-fish-033-gyro-tilt-parallax.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tiến độ 2026-11-04

- Task 1 `TiltParallax`: 13 unit test; mutation clamp/dead-zone/sign/rotation/reference/smoothing đều bị bắt, NaN guard mutant equivalent.
- Task 2 gate/setting/reset: full unit regression 1486/1486, lint 0 errors; 5 gate + default-on mutations bị bắt.
- Task 3 `TiltSensorSource`: 10 unit test; 9 lifecycle/sensor mutations bị bắt.
- Task 4 `LensViewTiltIntegrationTest` 9/9; Task 5 `ActHomeTiltWidgetTest` 9/9 + `ActHomeMultiLensWidgetTest` 4/4 + `ActHomeLensManagementWidgetTest` 29/29 trên TECNO KJ7. Một ActHome onDestroy-only mutant equivalent do onPause luôn stop.
- Task 6 UI: `FrmLensTiltWidgetTest` 3/3, 17 locale, full gate 1496/1496, lint 0 error/12 warning; TECNO khôi phục 1191/1191.
- Audit `doc/task/AUDIT_FISH-033_2026-11-04.md`: 9,20/10. Smoke trên TECNO: listener đúng vòng đời; probe tạm chứng minh sensor→filter→setTiltOffset→draw khi nghiêng thật (offset ~130×79 px, ảnh grid biến dạng); lần “chưa thấy” trước chỉ ghi dữ liệu gần pose tham chiếu. Probe đã gỡ; còn touch override vật lý, A/B pin/frame; dữ liệu khôi phục 1191/1191.

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-033_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
