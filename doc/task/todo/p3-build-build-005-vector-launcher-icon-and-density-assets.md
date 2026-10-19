# BUILD-005 — Icon launcher vector và dọn density lệch (5 IconLauncherShape, IconLocation, IconMissingDensityFolder)

| Field | Value |
|---|---|
| Type | idea |
| Status | todo |
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

- [ ] `ic_launcher_960.png` thay bằng vector/kích thước cố định, xóa PNG.
- [ ] `drawable-hdpi/ic_launcher_round.png` và `l_error_404.png` sang `drawable-nodpi` hoặc vector.
- [ ] Shortcut icon chuyển vector.
- [ ] Quyết định số phận `SplashAct` (xóa hoặc SplashScreen API), cần owner xác nhận.
- [ ] 5 warning `IconLauncherShape` chỉ hết nếu vẽ lại silhouette PNG API 25 (việc thiết kế); ghi rõ nếu không làm.
- [ ] Đo kích thước APK trước/sau.

## Implementation notes

Gộp F1, F2, I1, I2, N3 của audit build.

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
