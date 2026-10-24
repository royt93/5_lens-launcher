# SEARCH-012 — T9 vô dụng với nhãn app không phải chữ Latin

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-24 — audit 9.1/10, `doc/task/AUDIT_SEARCH-012_2026-10-23.md` |
| Priority | P2 |
| Evidence | confirmed |
| Epic | SEARCH-010 |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`search/T9SearchEngine.kt:58` `charToT9Digit` có `else -> '1'`: mọi chữ Hán/Nhật/Hàn/Cyrillic/Thai/Ả Rập/Devanagari thành `'1'`, `toT9Digits("微信")=="11"`. Query `1` trả toàn bộ app, mọi digit khác trả rỗng. App có 17 locale, 8 không phải Latin. `T9SearchEngineTest` chỉ có 3 test Latin + `Đồng`.

## User story

Là người dùng ngôn ngữ không-Latin, tôi muốn bàn T9 tìm được app theo nhãn của tôi.

## Acceptance criteria

- [x] Nhãn CJK/Cyrillic/Thai/Ả Rập không còn map thành chuỗi `1`; ký tự không hỗ trợ bị bỏ qua.
- [x] Owner chọn fallback sang package name aliases Latin khi nhãn chính không match; label match có priority cao hơn alias.
- [x] Unit tests cho CJK, Cyrillic, Thai, Ả Rập, label hỗn hợp; widget keypad test WeChat thật trên OPPO.
- [x] Latin và Vietnamese mapping/ranking tests giữ nguyên.

## Implementation notes

Chốt phương án với owner trong story (alias fallback là rẻ nhất). Không thêm dependency.

## Verification and Definition of Done

- [x] TDD `T9SearchEngineTest` RED/GREEN 7/7; `AppSearchEngineTest` 19/19.
- [x] `ActHomeT9KeypadWidgetTest` test phím thật 1/836 trên OPPO CPH1989 Android 11.
- [x] `AppSearchIntegrationTest` 6/6; không đổi persistence/SDK.
- [x] OPPO CPH1989 `FUJZIFIR7DQCNRWW`, Android 11; bộ 57 test thiết bị pass. APK OPPO baseline, preference XML, system settings và foreground launcher khôi phục; Room lens rows giữ theo owner choice và được ghi rõ trong UI-032 audit.
- [x] Full gate BUILD SUCCESSFUL: 1043 unit, lint, assemble app/androidTest (chưa so warning lint baseline).
- [x] Audit `doc/task/AUDIT_SEARCH-012_2026-10-23.md`, 9.1/10.
- [x] Evidence/status cập nhật.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story SEARCH-012 (T9 vô dụng với nhãn app không phải chữ Latin) trong doc/task/todo/p2-search-search-012-t9-non-latin-locales.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-012_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
