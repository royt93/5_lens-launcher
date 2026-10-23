# SEARCH-014 — shortcutsByPackage giữ Drawable không giải phóng

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-23 — audit 9.1/10, `doc/task/AUDIT_SEARCH-014_2026-10-23.md` |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Memory |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`search/SearchResultAdapter.kt:54` map `shortcutsByPackage`; `onDetachedFromRecyclerView` (`:94-101`) chỉ clear tập `loading*`, giữ Drawable (mỗi entry tới 3 icon). Adapter của `ActHome` sống suốt Activity nên map lớn dần theo số package từng tìm, không tham gia `onTrimMemory` như `BitmapCache`. Cùng họ UI-020.

## User story

Là người dùng, tôi muốn app không phình RAM khi dùng search lâu.

## Acceptance criteria

- [x] `ShortcutCache` (LruCache, trần 30 package) + `ActHome.onTrimMemory` hạ cache theo mức `TRIM_MEMORY_*`.
- [x] `ShortcutCacheTest` 7/7 (có mutation check) và widget test cache bị chặn trần trên Pixel.
- [x] Luồng nạp lần đầu không đổi (vẫn query nền rồi render); chỉ thay cấu trúc lưu cache. Chưa đo thời gian riêng.

## Implementation notes

Dùng `LruCache` kích thước cố định (hằng số có tên), clear trong `onTrimMemory`.

## Verification and Definition of Done

- [x] `ShortcutCacheTest` 7/7, RED trước (thiếu class), mutation bắt 4/7 test.
- [x] `SearchResultAdapterWidgetTest` thêm 1 test; 21 test search trên Pixel pass.
- [x] Not applicable (reviewed) — không đổi persistence/SDK; `AppSearchIntegrationTest` 6/6 vẫn pass.
- [x] Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-10-23: 21 test thiết bị liên quan search pass, APK baseline và Google Play foreground khôi phục. Không chụp ảnh smoke riêng (ghi trong audit).
- [x] Full gate xanh: 1039 unit, lint, assemble app + androidTest (chưa so số warning lint với baseline).
- [x] `doc/task/AUDIT_SEARCH-014_2026-10-23.md`, 9.1/10.
- [x] Evidence/status cập nhật.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story SEARCH-014 (shortcutsByPackage giữ Drawable không giải phóng) trong doc/task/todo/p2-search-search-014-shortcut-drawable-cache-unbounded.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-014_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
