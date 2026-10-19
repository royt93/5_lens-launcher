# BUILD-003 — Dọn repo: local.properties bị track, Jetifier thừa, permission thừa, rác JVM

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Build hygiene |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`git ls-files` liệt kê `local.properties` (chứa `sdk.dir` cục bộ) dù `.gitignore` đã có. `gradle.properties:2` `android.enableJetifier=true` nhưng `verification-metadata.xml` không có artifact `com.android.support`. `AndroidManifest.xml:21,27` `REQUEST_DELETE_PACKAGES` và `POST_NOTIFICATIONS` không có call site (uninstall đi qua `ACTION_DELETE` ở `UtilApp.kt:311`; DND không cần `POST_NOTIFICATIONS`). `util/Logger.java:31,36` `i()/w()` không gate `BuildConfig.DEBUG` trong khi proguard strip `Log.i/w`. Root có 4 `replay_pid*.log` (~22 MB) và 3 `hs_err_pid*.log` (JIT C2 crash aarch64); `gradle.properties:1` chỉ `-Xmx2048m`.

## User story

Là dev, tôi muốn repo sạch, build không lãng phí và manifest chỉ khai quyền thật dùng.

## Acceptance criteria

- [ ] `git rm --cached local.properties`.
- [ ] Tắt Jetifier, `assembleProductionRelease` vẫn xanh.
- [ ] Xóa 2 permission thừa sau khi xác nhận ad SDK không cần `POST_NOTIFICATIONS` (kiểm merged manifest); thêm test chặn permission thừa.
- [ ] Quyết định gate `Logger.i/w` và có test.
- [ ] Xóa log rác trên đĩa; cân nhắc nâng heap Gradle.

## Implementation notes

Gộp F6, F7, N4, E6, I4 của audit build. Test guard permission mô phỏng `BackupRulesTest`.

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
Làm story BUILD-003 (Dọn repo: local.properties bị track, Jetifier thừa, permission thừa, rác JVM) trong doc/task/todo/p3-build-build-003-repo-hygiene-quick-wins.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_BUILD-003_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
