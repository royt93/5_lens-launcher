# SEARCH-018 — Fuzzy Levenshtein tạo match rác ở CJK; thêm khớp chữ cái đầu pinyin/romaji

| Field | Value |
|---|---|
| Type | idea |
| Status | done |
| Priority | P3 |
| Evidence | conditional |
| Epic | Search quality |
| Estimate | 8 SP |
| Risk | Medium |
| Dependencies | SEARCH-012 |

## Context and evidence

`search/AppSearchEngine.kt:99-114` ngưỡng 2 edit cho token >=6 ký tự tính theo ký tự: nhãn CJK 4 ký tự (`微信读书` vs `微信支付`, khoảng cách 2) khớp fuzzy; tier word-prefix (`:63`) không bao giờ bắn cho nhãn không có khoảng trắng.

**Kiểm chứng 2026-10-27:** ví dụ `微信读书` vs `微信支付` KHÔNG khớp (token 4 ký tự chỉ cho 1 edit). Rác thật vẫn có ở 1 edit: gõ `微信读书` ra `微信支书`, gõ `微信读` ra `微信书`. Tier word-prefix không bắn cho nhãn CJK: đúng. Quyết định owner: tắt fuzzy khi nhãn/token có CJK; pinyin lấy từ `android.icu` Han-Latin; luôn bật, không công tắc. Kana/romaji ngoài phạm vi.

## User story

Là người dùng tiếng Trung/Nhật, tôi muốn tìm đúng app, không ra kết quả rác.

## Acceptance criteria

- [x] Gate fuzzy theo script hoặc ngưỡng theo tỉ lệ.
- [x] Khớp chữ cái đầu / pinyin offline qua ICU của hệ điều hành (API 29+), luôn bật, không thêm dependency (romaji/kana ngoài phạm vi).
- [x] Test với bộ nhãn CJK thật.

## Implementation notes

SP 8 phải tách khi thực hiện: story con 1 sửa fuzzy (3 SP), story con 2 initial matching.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests: `PinyinSearchWidgetTest` trên KJ7. Accessibility: Not applicable, không có control/chuỗi mới (đã review).
- [x] Integration: ICU thật qua Robolectric + widget thật; persistence Not applicable, không lưu trạng thái mới (đã review).
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story SEARCH-018 (Fuzzy Levenshtein tạo match rác ở CJK; thêm khớp chữ cái đầu pinyin/romaji) trong doc/task/todo/p3-search-search-018-cjk-fuzzy-and-pinyin-initials.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-018_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
