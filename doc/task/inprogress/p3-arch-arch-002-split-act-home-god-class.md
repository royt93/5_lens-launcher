# ARCH-002 — Tách ActHome (2358 dòng) thành các bộ điều khiển nhỏ

| Field | Value |
|---|---|
| Type | enhance |
| Status | inprogress |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Architecture |
| Estimate | 8 SP |
| Risk | High |
| Dependencies | CORE-004, UI-028 |

## Context and evidence

`ui/ActHome.java:101-2358`. ARCH-001 đã tách `ActSettings` (946→669) nhưng `ActHome` chưa. Cụm tách được: search UI/state (`:1418-1758`, ~340 dòng, `HomeSearchController`), quick action + permission (`:1795-2001`, ~210 dòng, `HomeQuickActionDelegate`), T9 keypad (`:1563-1613`, `T9KeypadBinder`), lens menu + dialog (`:916-1416`, ~500 dòng, `LensMenuCoordinator`), insets/chrome (`:422-501`, `:1767-1789`, `:2185-2194`). `lensViews` (`:186`) là state chia sẻ giữa pager callback, bind, export, pinch nên cần một chủ sở hữu.

## Baseline (2026-10-27, trước khi đụng code)

- `ActHome.java` **2.454 dòng** (story ghi 2.358, đã cũ), 55 field, 30 `@VisibleForTesting`, 34 chỗ dùng `lensViews`. `CORE-004` và `UI-028` đã `done`.
- Thiết bị TECNO KJ7 `115333744A005844` (Android 14), 35 lớp `ActHome*` / 145 test: **35/35 xanh** trên code chưa đổi, không có lớp đỏ hay flaky có sẵn. Dữ liệu app sao lưu 583.680 byte trước khi cài.
- Lỗi script phát hiện: `adb shell` trong vòng lặp `while read` nuốt stdin nên lần chạy đầu chỉ chạy 1 lớp; đã thêm `</dev/null` và ghi vào plan.

## Tiến độ cụm 1 (chưa commit/push refactor)

- Đã chuyển pager/bind sang `ui/home/LensViewHolder.java`; giữ tên/phạm vi method cũ của `ActHome` bằng callback host có tên riêng để tránh trùng chữ ký Java.
- Build app + androidTest và 1.262 unit test xanh. `lintDevDebug` chạy riêng xanh; lượt gate gộp lỗi `FileNotFoundException` trong kapt stub androidTest khi lint phân tích, nên chưa coi gate gộp là xanh.
- KJ7 `115333744A005844` ngắt kết nối trước regression sau refactor; backup còn tại `/tmp/b-arch002/state/before.tar` (583.680 byte), chưa thể khôi phục.
- Owner sau đó yêu cầu "dùng pixel thay thế đi": khóa Pixel 7 Pro `2B051FDH3006MU` cho phần còn lại của phiên (Android 17/API 37). Backup riêng `/tmp/b-arch002-pixel/before.tar` (665.600 byte) và APK cũ; locale `[]`, font `1.0`, Contacts denied. Baseline Pixel từ APK HEAD chưa refactor: 35/35 lớp xanh (145 test). Bản cụm 1 đang chạy 35 lớp + 4 lớp phụ; chưa commit/push refactor.

## Tiến độ cụm 2-4 (28/10/2026, đã commit, chưa push)

- Cụm 2 `HomeQuickActionDelegate` (ActHome 2.185 → 1.942), cụm 3 `HomeSearchController` + `T9KeypadBinder` (→ 1.550), cụm 4 `LensMenuCoordinator` (→ 1.054 dòng, đạt mục tiêu < 1.500). `LensMenuCoordinator.java` 703 dòng; menu, dialog, share launcher, launch intent giữ storage trên `ActHome` vì test đọc theo tên.
- Cụm 4: `LensMenuLogicTest` 6 test (đỏ vì thiếu lớp, rồi xanh), mutation 8/8 bị bắt. Unit 1.285/1.285, lint 0 lỗi, `git diff --check` và `check-secrets.sh` sạch.
- Owner chốt "chỉ dùng TECNO cho toàn bộ phiên": regression cụm 4 chạy trên TECNO KJ7 `115333744A005844` (Android 14): 36 lớp `ActHome*` + 14 lớp thêm, 50 lớp / 244 test xanh. Dữ liệu app khôi phục từ `/tmp/b-arch002-tecno/before.tar` (37.788.672 byte), 1.189/1.189 file khớp hash, locale `[en]`, font `1.15`, Contacts denied như trước.
- Ngoại lệ cần ghi nhận: trước khi owner chốt TECNO, một lượt chạy dở đã cài APK cụm 4 và chạy 3 lớp lên Pixel `2B051FDH3006MU` (đã dừng); dữ liệu Pixel chưa so lại với backup. Regression các cụm 1-3 chạy trên Pixel theo yêu cầu trước đó.

## User story

Là dev, tôi muốn `ActHome` đủ nhỏ để sửa và test an toàn.

## Acceptance criteria

- [ ] Mỗi cụm chuyển ra lớp riêng có test đơn vị, `ActHome` giảm dưới 1500 dòng.
- [ ] Không đổi hành vi (toàn bộ widget test hiện có xanh).
- [ ] `lensViews` có một chủ sở hữu duy nhất.
- [ ] Tách theo từng PR nhỏ, mỗi cụm một PR.

## Implementation notes

SP vượt 5 nên chia thành 5 story con khi thực hiện (README quy định tách trên 8). Làm sau UI-028 và CORE-004.

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
Làm story ARCH-002 (Tách ActHome (2358 dòng) thành các bộ điều khiển nhỏ) trong doc/task/todo/p3-arch-arch-002-split-act-home-god-class.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_ARCH-002_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
