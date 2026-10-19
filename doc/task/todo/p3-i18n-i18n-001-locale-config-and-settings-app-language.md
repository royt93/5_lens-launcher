# I18N-001 — Thiếu android:localeConfig nên Android 13+ không hiện 'Ngôn ngữ ứng dụng'

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Localization |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`grep localeConfig app/src/main` không có kết quả; `res/xml/` chỉ có `backup_rules`, `data_extraction_rules`, `file_paths`, `shortcuts`. App ship 17 locale nhưng tự override locale qua `util/LocaleHelper.kt:57-81` và `ui/BaseActivity.kt:50-59` nên không xuất hiện trong Settings hệ thống.

## User story

Là người dùng Android 13+, tôi muốn đổi ngôn ngữ app từ Cài đặt hệ thống.

## Acceptance criteria

- [ ] Thêm `res/xml/locales_config.xml` đủ 17 locale và khai trong manifest.
- [ ] Chọn ngôn ngữ ở hệ thống và trong app cho cùng kết quả (một nguồn sự thật).
- [ ] Fallback đúng cho minSdk 25-32.
- [ ] Test ưu tiên `LocaleHelper` và smoke trên Android 13+.

## Implementation notes

Gộp N1 (audit build) và ENHANCE-4 (audit search).

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
Làm story I18N-001 (Thiếu android:localeConfig nên Android 13+ không hiện 'Ngôn ngữ ứng dụng') trong doc/task/todo/p3-i18n-i18n-001-locale-config-and-settings-app-language.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_I18N-001_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
