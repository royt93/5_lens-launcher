# UI-035 — Khóa một lens (chế độ khách)

| Field | Value |
|---|---|
| Type | new |
| Status | inprogress |
| Priority | P3 |
| Evidence | idea |
| Epic | Privacy |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

Hạ tầng có sẵn: per-lens scope (`LensAppScope`), `Biometric.toggleLockApp` (`AppAdapter.java:764`), `isLensFrozen` (`ActHome.java:1087`).

## User story

Là người dùng, tôi muốn đưa máy cho người khác mà không để họ chuyển sang lens riêng tư.

## Acceptance criteria

- [x] Cờ `lens_locked_<lensId>` chặn pager/edge-swipe/shortcut/management/list mode và bind page.
- [x] Mở khóa bằng system biometric/device credential; API 25–29 dùng các flow tách biệt.
- [x] Không lưu bí mật; ghi rõ privacy-guard limitation.
- [x] Unit, widget, integration automation pass; manual owner smoke còn thiếu.

## Implementation notes

Liên quan bảo mật: audit riêng về cách bypass (recents, ADB).

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (TDD RED/GREEN).
- [x] Widget/UI tests cover menu behavior, pager prefetch, locked target and list mode; AndroidX accessibility warnings only.
- [x] Integration tests cover storage/backup/auth/navigation boundaries.
- [ ] Manual owner smoke on TECNO with real biometric/PIN and cancel/unavailable cases; record evidence and restore device state (automated test restore is verified).
- [x] No new lint/build failures: 0 lint errors, 15 warnings.
- [ ] Audit `doc/task/AUDIT_UI-035_2026-11-05.md` scores 8.8/10; needs manual smoke and re-audit above 9.0 before push.
- [ ] Evidence/status updated before moving file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-035 (Khóa một lens (chế độ khách)) trong doc/task/todo/p3-ui-ui-035-lens-guest-lock.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-035_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
