# UI-027 — Overlay quảng cáo dùng colorWhite cứng, tương phản 1.69:1 ở dark/dynamic color

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Accessibility |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`res/layout/act_settings.xml:136` nền `?attr/colorPrimary`; `:160` `indeterminateTint="@color/colorWhite"`, `:167` `textColor="@color/colorWhite"`. `values-night/colors.xml:4` `colorPrimary=#FFB3B8` còn `colorWhite` không có override night. Chữ `ads_action_disclosure` gần như vô hình ở dark mode và với `DynamicColors` (`RApplication.java:139`). Đây là text công bố quảng cáo (a11y + policy). UI-005 đổi nền sang `?attr` nhưng bỏ sót hai thuộc tính foreground.

## User story

Là người dùng dark mode, tôi muốn đọc được dòng cảnh báo có quảng cáo.

## Acceptance criteria

- [ ] Chữ và spinner dùng `?attr/colorOnPrimary`.
- [ ] Test widget đo tương phản >= 4.5:1 ở light, dark, và một bảng dynamic color.
- [ ] Không đổi chuỗi hay hành vi quảng cáo.

## Implementation notes

Hai thuộc tính trong một file layout.

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
Làm story UI-027 (Overlay quảng cáo dùng colorWhite cứng, tương phản 1.69:1 ở dark/dynamic color) trong doc/task/todo/p2-ui-ui-027-ad-overlay-contrast-night.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-027_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
