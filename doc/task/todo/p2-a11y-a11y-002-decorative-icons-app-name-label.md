# A11Y-002 — 30 icon trang trí mang contentDescription app_name, TalkBack đọc sai

| Field | Value |
|---|---|
| Type | fix |
| Status | declined by owner (2026-10-29; no TalkBack work; do not re-propose unless explicitly reopened) |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Accessibility |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Owner decision — 2026-10-29

Owner declined TalkBack/accessibility work for now. Keep this story as historical backlog; do not propose or implement unless the owner explicitly reopens this scope.

## Context and evidence

`res/layout/frm_settings.xml` 27 chỗ `contentDescription="@string/app_name"` (vd `:59,108,267,316,433,489`), thêm `act_settings.xml:109,150` và `a_splash.xml:39`. Đây là icon hàng cài đặt cạnh `MaterialSwitch`/`TextView` đã có nhãn; TalkBack đọc "Fisheye Launcher" trước mỗi hàng. `act_settings.xml:109` là `fabSort` (nút thật) mang nhãn sai. Lint không bắt vì nhãn có nhưng sai. Mẫu đúng đã có ở `item_lens_group_lens.xml` (`importantForAccessibility="no"`).

## User story

Là người dùng TalkBack, tôi muốn nghe nhãn đúng của từng cài đặt, không lặp tên app.

## Acceptance criteria

- [ ] Icon trang trí dùng `importantForAccessibility="no"`/`contentDescription="@null"`.
- [ ] `fabSort` có nhãn thật (chuỗi mới, đủ 17 locale).
- [ ] Test duyệt cây view: không còn node tương tác nào có nhãn bằng `app_name` ngoài logo.
- [ ] Không đổi bố cục.

## Implementation notes

Chuỗi mới cho `fabSort` phải qua `LensStringTranslationTest` (không để nguyên tiếng Anh).

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
Làm story A11Y-002 (30 icon trang trí mang contentDescription app_name, TalkBack đọc sai) trong doc/task/todo/p2-a11y-a11y-002-decorative-icons-app-name-label.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_A11Y-002_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
