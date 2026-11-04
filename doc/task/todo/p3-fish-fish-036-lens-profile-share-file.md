# FISH-036 — Chia sẻ 'lens profile' (cấu hình quang học) qua file

| Field | Value |
|---|---|
| Type | exclusive |
| Status | picked — đang thiết kế 2026-11-04; chưa triển khai |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | FISH-027, DB-007 |

## Context and evidence

- `LensSettingsActivity` đã giữ `EXTRA_LENS_ID`, validate lens tồn tại trong Room qua `loadTarget()`, và có lifecycle-aware Activity class phù hợp cho SAF launchers.
- `LayoutBackupIo` cung cấp mẫu callback + `ApplicationScope` cho SAF, nhưng workspace import thay Room/layout; không tái dùng write path cho profile.
- `LensPhysicsBackup` trong `model/LayoutBackup.kt` strict-parses per-lens distortion/icon/custom distortion/Smart Focus/profile/hood. Nó không bao gồm các global `scale_factor`, `animation_time`, custom scale/time.
- `UtilSettings` lưu scale/animation và custom preset scale/time global; profile, distortion, icon size, hood, Smart Focus và custom distortion theo lens. Target import là lens đang mở trong `LensSettingsActivity`.
- Owner quyết định 2026-11-03/04: profile file chứa bộ quang học đầy đủ (profile, distortion, scale, icon size, animation time, hood, Smart Focus, Custom preset); import áp global fields đúng scope global hiện có; preview phân tách global/per-lens field và chỉ ghi sau xác nhận; file có field sai kiểu, thiếu field bắt buộc, số non-finite hoặc ngoài range bị từ chối toàn bộ; entry point đặt trong `LensSettingsActivity`.
- Không chứa Room layout/app rows, không tạo lens tự động, không mở rộng thành workspace backup.

## User story

Là người dùng, tôi muốn gửi bạn cấu hình lens của tôi.

## Acceptance criteria

- [ ] Xuất/nhập qua SAF, validate chặt khi nhập (trust boundary).
- [ ] Giá trị ngoài phạm vi bị clamp/từ chối.
- [ ] Test file hỏng/độc.
- [ ] Smoke.

## Implementation notes

Làm sau FISH-027 và DB-007.

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
Làm story FISH-036 (Chia sẻ 'lens profile' (cấu hình quang học) qua file) trong doc/task/todo/p3-fish-fish-036-lens-profile-share-file.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-036_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
