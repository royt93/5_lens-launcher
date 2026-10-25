# PERF-008 — FrmSettings.assignValues đọc 35 prefs và resolve launcher hệ thống mỗi lần resume

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-25 — audit 9.2/10, 36 targeted tests on TECNO |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Settings performance |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`ui/FrmSettings.kt:303-395` gọi từ `onViewCreated:96` và `onResume:101`: ~35 lần đọc prefs + `UtilLauncher.getNameHomeLauncher` (`:319`, `resolveActivity` + `loadLabel` của app khác, đồng bộ trên main thread). Tab luôn RESUMED vì `setOffscreenPageLimit(2)` (`ActSettings.java:188`). Cùng lớp lỗi UI-024.

## User story

Là người dùng, tôi muốn chuyển tab Settings không bị khựng.

## Acceptance criteria

- [x] Cache tên launcher mặc định; invalidate khi Fragment stop/launcher chooser mở.
- [x] `TextView`/color drawable chỉ cập nhật khi giá trị thực sự đổi.
- [x] Resume tests xác nhận số lần resolve launcher giữ nguyên khi trạng thái không đổi và tăng đúng một lần khi invalidated.
- [x] Đo bind time trên TECNO: 30 samples sau warmup, p50 10.79 ms → 2.50 ms, p95 15.12 ms → 2.85 ms. Đây là thời gian `assignValues()` wall-clock, không phải frame/jank đo toàn màn hình.

## Implementation notes

Giữ hành vi hiển thị nguyên vẹn.

## Verification and Definition of Done

- [x] Unit tests: existing pure utility tests pass; no standalone policy method was added.
- [x] Widget/integration: `FrmSettingsResumePerformanceWidgetTest` 8/8 plus targeted Settings regression tests (MaterialYou 3, CleanLens 7, Background 7, SearchHint 1, ActSettingsLayout 2); total batch 28/28 on TECNO BG6.
- [x] Performance measurement: old bind p50/p95 10.79/15.12 ms; optimized bind p50/p95 2.50/2.85 ms, 30 measured runs after 5 warmups on TECNO. Values are test-loop timings, not full screen jank/frame metrics.
- [x] Room and default app prefs logical dumps/XML matched baseline exactly after targeted tests.
- [x] Full host gate: `testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` BUILD SUCCESSFUL; 1,050 unit tests, 0 failures/errors/skips.
- [x] Device smoke: TECNO BG6 Android 13, `/tmp/perf008-smoke.png`, no ad obscures Settings; Home launcher restored to foreground.
- [x] `git diff --check` and `bash scripts/check-secrets.sh` pass.
- [ ] Post-change audit `>9.0/10` and close/push decision pending.
- [ ] Preserve local BUILD-003 staged/uncommitted changes and signing config; do not include them in PERF-008 commit.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story PERF-008 (FrmSettings.assignValues đọc 35 prefs và resolve launcher hệ thống mỗi lần resume) trong doc/task/todo/p3-perf-perf-008-settings-tab-resume-cost.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_PERF-008_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
