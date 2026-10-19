# FISH-031 — Vòng tiêu điểm đa lớp: giữ lâu mở shortcut ngay trong vòng

| Field | Value |
|---|---|
| Type | exclusive |
| Status | todo |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`LensView` có `mLongPressArmed`/`mLongPressTriggered` (`:1105-1112`) và halo FISH-024; shortcut có sẵn `AppShortcutsProvider.kt:31`. Popup hiện tại (`LensView.kt:684`) là menu hệ thống.

## User story

Là người dùng, tôi muốn giữ icon để lens phóng thêm và hiện shortcut của app ngay tại đó.

## Acceptance criteria

- [ ] Giữ lâu phóng thêm một bậc và hiện shortcut trong vòng.
- [ ] Gate reduced-motion, có đường thay thế a11y.
- [ ] Không phá pan/pinch.
- [ ] Test + smoke + đo frame.

## Implementation notes

Đặc trưng launcher fisheye.

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
Làm story FISH-031 (Vòng tiêu điểm đa lớp: giữ lâu mở shortcut ngay trong vòng) trong doc/task/todo/p3-fish-fish-031-focus-ring-quick-shortcuts.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-031_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
