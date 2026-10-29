# UI-033 — Trang gom mọi cài đặt theo từng lens

| Field | Value |
|---|---|
| Type | new |
| Status | todo |
| Priority | P3 |
| Evidence | idea |
| Epic | Lens manager |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | UI-032 |

## Context and evidence

Cài đặt per-lens rải rác: icon size và distortion ở `FrmLens.kt:113,121`, Smart Focus ở `FrmSettings:254` và menu lens (`ActHome.java:937`), scope/freeze/hidden ở menu lens (`:947-953`). `menu_lens_row.xml` hiện chỉ có Rename/Delete.

## User story

Là người nhiều lens, tôi muốn một nơi chỉnh mọi thứ của một lens.

## Acceptance criteria

- [ ] Mở từ hàng lens trong `LensManagementActivity`.
- [ ] Gom icon size, distortion, Smart Focus, scope, freeze, hidden.
- [ ] Dùng đúng key `_<lensId>` hiện có, không đổi schema.
- [ ] Widget test + a11y.

## Implementation notes

Thiết kế UI qua brainstorm trước khi làm.

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
Làm story UI-033 (Trang gom mọi cài đặt theo từng lens) trong doc/task/todo/p3-ui-ui-033-per-lens-settings-page.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-033_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
