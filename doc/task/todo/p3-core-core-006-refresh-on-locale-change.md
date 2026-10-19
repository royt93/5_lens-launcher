# CORE-006 — Đổi ngôn ngữ không làm mới nhãn app và thứ tự A-Z

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Refresh pipeline |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`app/RApplication.java:180-190` `registerPackageChangeReceiver` chỉ lắng `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED`. Nhãn lấy từ `resolveInfo.loadLabel` (`UtilApp.kt:104`) và nằm trong `RAppsSingleton` tới package event kế tiếp, nên sau khi đổi ngôn ngữ (`LocaleHelper`) hoặc locale máy, nhãn và thứ tự vẫn theo ngôn ngữ cũ.

## User story

Là người dùng đổi ngôn ngữ, tôi muốn tên app và thứ tự cập nhật ngay.

## Acceptance criteria

- [ ] Đăng ký `ACTION_LOCALE_CHANGED` và đổi locale trong app đều kích hoạt refresh.
- [ ] Test: đổi locale làm snapshot được tính lại.
- [ ] Hủy đăng ký đúng chỗ (không rò receiver).

## Implementation notes

Receiver đăng ký động như các receiver hiện có.

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
Làm story CORE-006 (Đổi ngôn ngữ không làm mới nhãn app và thứ tự A-Z) trong doc/task/todo/p3-core-core-006-refresh-on-locale-change.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_CORE-006_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
