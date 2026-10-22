# CORE-005 — Icon pack nhận diện bằng nhãn hiển thị thay vì package

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-22 |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Icon packs |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`util/UtilApp.kt:87` `iconPacks.find { it.mName == iconPackLabelName }` với `UtilSettings.KEY_ICON_PACK_LABEL_NAME`. Icon pack đổi tên khi update hoặc nhãn đổi theo locale thì không khớp, `iconPackToken = "system"` (`:90-92`), icon pack tự tắt và toàn bộ `iconCacheKey` đổi (re-decode toàn bộ). Cùng họ lỗi PREF-001 nhưng identity vẫn là nhãn.

## User story

Là người dùng icon pack, tôi muốn lựa chọn của tôi không tự mất khi đổi ngôn ngữ hay cập nhật pack.

## Acceptance criteria

- [x] Lưu `packageName` làm identity, nhãn chỉ để hiển thị.
- [x] Migration đọc giá trị nhãn cũ và ánh xạ sang package, không mất lựa chọn đã lưu.
- [x] Đổi locale không đổi `iconCacheKey`.
- [x] Test: pack đổi nhãn vẫn được chọn.

## Kết quả và giới hạn (2026-10-22)

Package identity, migration, app loader/reload, settings dialog/summary, `TaskUpdateApps` full loader, và locale-stable cache token đã hoàn tất. Full unit **979/979**, lint **0 lỗi/7 warnings**, build thành công; Pixel 7 Pro target `2B051FDH3006MU`: settings/dialog **7 tests** và `LensViewIconReloadWidgetTest` **1 test** pass. Audit: `doc/task/AUDIT_CORE-005_2026-10-22.md` (**9.1/10**). Chưa có third-party icon-pack fixture thật để đổi locale/package display name trên máy; coverage làm bằng injected catalog và package-only key tests.

## Implementation notes

Chạm vùng FISH-019 (hoãn) nên giữ cache global, không namespace theo lens.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story CORE-005 (Icon pack nhận diện bằng nhãn hiển thị thay vì package) trong doc/task/todo/p2-core-core-005-icon-pack-identity-by-package.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_CORE-005_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
