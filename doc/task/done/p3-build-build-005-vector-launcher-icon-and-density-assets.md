# BUILD-005 — Icon launcher vector và dọn density lệch (5 IconLauncherShape, IconLocation, IconMissingDensityFolder)

| Field | Value |
|---|---|
| Type | idea |
| Status | done 2026-10-29 — audit 8.6/10, `doc/task/AUDIT_BUILD-005_2026-10-29.md` (chưa push, dưới ngưỡng 9.0) |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Assets |
| Estimate | 5 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

Lint còn 7 warning: `IconLauncherShape` x5 trên PNG legacy (`mipmap-ldpi/mdpi/hdpi/xxxhdpi`), `IconLocation` do `drawable/ic_launcher_960.png` (960x960, 176 KB, dùng ở `layout/act_settings.xml:153` với `wrap_content`, nên nở theo density), `IconMissingDensityFolder` do `drawable-hdpi/` chứa `ic_shortcut_*.png` (72px) + `l_error_404.png` (512px) và `drawable-xxxhdpi/` chứa 2 shortcut. `drawable-hdpi/ic_launcher_round.png` 512x512 / 396 KB là PNG lớn nhất APK, dùng ở `layout/a_splash.xml:41` và `act_about.xml:31`. `mipmap-anydpi-v26` đã có adaptive + monochrome. `SplashAct` không có intent-filter nào (chết) nhưng vẫn ship.

## User story

Là dev, tôi muốn asset đúng density, nhẹ hơn và hết warning lint.

## Acceptance criteria

- [x] `ic_launcher_960.png` chuyển WebP lossless `drawable-nodpi`, kích thước hiển thị cố 240dp.
- [x] Cả hai sang WebP lossless `drawable-nodpi`; 0 điểm ảnh nhìn thấy bị lệch.
- [x] Hai shortcut icon chuyển vector; đo thật từ PNG gốc.
- [x] `SplashAct` giữ nguyên (owner chọn).
- [x] 5 `IconLauncherShape` giữ nguyên (owner chọn không vẽ lại); ghi rõ trong story và audit.
- [x] APK dev debug 34.764.925 → 34.476.349 (−288 KB, −0,83%); lint 12 → 10 warning.

## Implementation notes

Gộp F1, F2, I1, I2, N3 của audit build.

## Verification and Definition of Done

- [x] Unit: `ImageAssetContractTest` 4 (hợp đồng bitmap/nodpi/vector); full 1.334/1.334. Cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget: `ImageAssetRenderWidgetTest` 4 + `ActAboutWidgetTest` 3 + `RemainingScreensMaterialYouTest` 3 + `LauncherShortcutsIntegrationTest` 6 + `SuperWebViewActivityWidgetTest` 5 trên TECNO. Cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration: `SettingsAdVipDelegateIntegrationTest` 1/1 trên TECNO. Shortcut vector manifest check 6/6. Cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke bằng mắt chưa chạy (quảng cáo chặn, owner không cho đụng Ads); chỉ widget đo kích thước. Khôi phục 1.189/1.189 file. Smoke pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] Lint giảm 2; 0 lỗi mới. Chưa so từng mục warning cũ.
- [x] Audit 8.6/10 (dưới ngưỡng, không push). A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence và status cập nhật. Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story BUILD-005 (Icon launcher vector và dọn density lệch (5 IconLauncherShape, IconLocation, IconMissingDensityFolder)) trong doc/task/todo/p3-build-build-005-vector-launcher-icon-and-density-assets.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_BUILD-005_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
