# PERF-006 — Reload một icon quét lại toàn bộ icon pack và parse appfilter mỗi lần

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-23 |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Icon packs |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | CORE-005 |

## Context and evidence

`util/UtilApp.kt:222` `loadSingleAppIcon` tạo `UtilIconPackManager()` mới với `forceReload=true` nên `UtilIconPackManager.java:421-446` chạy `queryIntentActivities` cho mọi launcher action + `getApplicationInfo` mỗi gói, rồi parse `appfilter.xml`. Gọi từ `LensView.kt:1506` và `SearchResultAdapter.kt:150` mỗi lần icon bị evict; khi cache thrash trên máy 346 app (UI-020) đây là N lần quét PackageManager.

## User story

Là người dùng nhiều app, tôi muốn cuộn lens không bị khựng khi icon nạp lại.

## Acceptance criteria

- [x] Một instance icon-pack ở application scope, invalidate khi đổi pack.
- [x] `forceReload=false` cho reload từng icon.
- [x] Test số lần `queryIntentActivities` khi reload N icon liên tiếp (<=1 batch discovery; mỗi launcher action cần một raw query).
- [x] Đo thời gian reload trước/sau: 50 reload fresh 302.7 ms vs shared 249.7 ms trên Pixel; discovery riêng 45 ms vs <1 ms.

## Implementation notes

Gộp EN-2 và E3 của audit.

## Kết quả và bằng chứng (2026-10-23)

- `RApplication` sở hữu 1 `UtilIconPackManager` dùng chung; `loadSingleAppIcon` gọi `forceReload=false`.
- Query count test (`UtilAppIconPackDiscoveryTest`): 5 lần reload icon lẻ giảm từ 10 query xuống 2 query (1 batch duy nhất).
- Concurrency test (`IconPackCacheConcurrencyTest`): 2 cold lookups đồng thời chỉ quét 1 batch; 2 lookups cùng 1 pack chỉ load 1 lần; cleanup không recycle Resources bitmap; invalidate không block.
- Invalidation test (`IconPackCacheInvalidationTest`): đổi pack hoặc nhận event PACKAGE_REPLACED/CHANGED/REMOVED (đúng pack) invalidate lười; cùng pack không invalidate thừa.
- Sửa lỗi dispatch: `ActSettings.sendUpdateAppsBroadcast` gọi `AppEventManager.notifyAppsUpdated(null)` trực tiếp (`ActSettingsArchitectureIntegrationTest` pass); `FrmSettings.resetToDefault` phát `notifyAppsUpdated` (`IconPackResetRefreshTest` pass).
- Fix race: `TaskUpdateApps.execute()` xóa `lastShells` ngay; shells chỉ được publish khi generation được chấp nhận (commit), nên scan cũ không ghi lại cache (`TaskUpdateAppsTest` 11/11 pass).
- Benchmark trên Pixel 7 Pro: discovery 50 lần fresh 45ms vs cached <1ms (chỉ đo discovery, không suy ra tỉ lệ vì số ms bị làm tròn).
- Full verification: 991 unit pass, lint 0 lỗi / 7 warnings. Audit: `doc/task/AUDIT_PERF-006_2026-10-23.md` (9.3/10).

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
Làm story PERF-006 (Reload một icon quét lại toàn bộ icon pack và parse appfilter mỗi lần) trong doc/task/todo/p2-perf-perf-006-icon-reload-reuses-icon-pack-cache.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_PERF-006_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
