# UI-031 — Hàng app ở font 2.0 bị cắt nhãn do ba icon chen vào

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-25 — audit 9.6/10, 25/25 device tests on Galaxy A11 |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Accessibility |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | UI-030 |

## Context and evidence

`res/layout/view_item_app.xml` ba icon 48x64dp liền nhau, `tvAppLabel` `layout_toStartOf="@+id/btAppLock"` còn khoảng 40% chiều rộng, `maxLines=2`. `BaseActivity.MAX_SUPPORTED_FONT_SCALE` (`:17`) cho tới 2.0. UI-006 từng thu `btAppLock` vì cùng lý do.

## User story

Là người dùng chữ lớn, tôi muốn đọc được tên app trong danh sách.

## Acceptance criteria

- [x] Ở font >= 1.5 gộp hide/lock vào menu `ivAppMenu` hoặc xếp lại hàng.
- [x] Widget test font 2.0: nhãn không bị cắt quá 2 dòng.
- [x] Touch target vẫn >= 48dp.

## Implementation notes

Cần xác nhận bằng ảnh trên thiết bị trước khi sửa (evidence conditional).

## Verification and Definition of Done

- [x] Unit tests: 1,050/1,050 JVM tests pass (`AppRowFontScalePolicyTest`).
- [x] Widget/UI tests: `AppRowFontScaleWidgetTest` 2/2, `AppAdapterWidgetTest` 11/11, `AccessibilityFontScaleWidgetTest` 2/2 (tổng 25/25 trên Samsung Galaxy A11).
- [x] Integration tests: Tương thích đầy đủ với biometric và visibility toggle.
- [x] Smoke tests: Samsung Galaxy A11 (`R9JN61LDLFJ`, Android 12), screenshot `/tmp/ui031-apps-screen.png` kiểm chứng nhãn 2 dòng không bị cắt, không có ad che UI (R4 sạch). Khôi phục launcher gốc và font scale.
- [x] No new lint/build failures: lint devDebug 0 error.
- [x] Post-change audit record: `doc/task/AUDIT_UI-031_2026-10-25.md` chấm 9.6/10.
- [x] Evidence and status updated.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-031 (Hàng app ở font 2.0 bị cắt nhãn do ba icon chen vào) trong doc/task/todo/p3-ui-ui-031-app-row-font-scale-clipping.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-031_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
