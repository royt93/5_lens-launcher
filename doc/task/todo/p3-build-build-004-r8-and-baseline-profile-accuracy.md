# BUILD-004 — Baseline/startup profile lệch mục tiêu và comment R8 sai

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Startup |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`app/src/main/generated/baselineProfiles/baseline-prof.txt` 8916 dòng nhưng 2234 dòng là AppLovin, chỉ 495 dòng là code app; `baseline-prof.txt` và `startup-prof.txt` giống hệt (cùng md5) nên `includeInStartupProfile=true` (`BaselineProfileGenerator.kt:25`) không tạo profile hẹp hơn. `app/proguard-rules.pro:5` ghi 'R8 full mode enabled' nhưng không có flag (R8 full là mặc định từ AGP 8.0, comment lệch); `:10-15` dùng flag ProGuard legacy mà R8 bỏ qua; `:20` `-keep class com.mckimquyen.model.** { *; }` giữ cả package, nên hẹp còn `@Entity`/`@Dao`. `app/build.gradle:307-309` `bundle.language.enableSplit=false` giữ cả 17 locale trong mọi bản tải.

## User story

Là người dùng, tôi muốn app khởi động nhanh và bản tải nhỏ.

## Acceptance criteria

- [ ] Profile tập trung đường `ActHome`→`LensView`, startup profile khác baseline.
- [ ] Sửa comment/flag R8 và hẹp keep rule, release build vẫn chạy (smoke).
- [ ] Đánh giá bật language split: giữ `LocaleHelper` đổi ngôn ngữ trong app hoạt động (smoke trên máy).
- [ ] Đo cold start trước/sau bằng `:baselineprofile`.

## Implementation notes

Gộp E1, E2, E5 của audit build. Mỗi hạng mục là một PR riêng.

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
Làm story BUILD-004 (Baseline/startup profile lệch mục tiêu và comment R8 sai) trong doc/task/todo/p3-build-build-004-r8-and-baseline-profile-accuracy.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_BUILD-004_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
