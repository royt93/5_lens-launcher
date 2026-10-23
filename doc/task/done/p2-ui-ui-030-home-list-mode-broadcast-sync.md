# UI-030 — Ẩn/khóa app ở list mode trên Home không phát broadcast, lưới không đồng bộ

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-23 — audit 9.2/10, `doc/task/AUDIT_UI-030_2026-10-23.md` |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Home list mode |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`adt/AppAdapter.java:809-833` `sendChangeAppsVisibilityBroadcast`/`sendChangeAppsLockBroadcast` chỉ gửi khi `mActivityContext instanceof ActSettings`. Nhưng `ui/ActHome.java:552` `homeAppAdapter = new AppAdapter(this, ...)` cũng dùng adapter này nên ở list mode trên Home, toggle ẩn/khóa không phát broadcast. `AppViewHolder` (`:436-971`) còn tự gọi `AppPersistent`, `Biometric`, `sendBroadcast`. Cả 3 widget test của adapter dựng trong ngữ cảnh `ActSettings`, không phủ nhánh này.

## Implemented evidence

- RED trước sửa: Home list bấm hide không phát visibility event. Sau sửa: Home widget pass và lens grid test qua Fisheye.
- Callback biometric được mock ở prompt boundary; production `Biometric.toggleLockApp` callback chạy thật trong JVM unit test. Không có hardware fingerprint test.
- Audit chi tiết: `doc/task/AUDIT_UI-030_2026-10-23.md`.

## User story

Là người dùng list mode, tôi muốn ẩn/khóa app có hiệu lực ngay ở mọi màn hình.

## Acceptance criteria

- [x] Visibility ở Home phát `AppEventManager.visibilityChanged`; list cập nhật và chuyển fisheye xác nhận lens grid không hiện lại app đã ẩn. Lock phát sau biometric success; callback được kiểm bằng JVM test production với mock `BiometricPrompt`.
- [x] Rút persistence/event của visibility, lock, organization sang `AppRowActions`; không còn persistence/broadcast trong các handler hàng này.
- [x] Widget test thật trong `ActHome` list mode và `ActSettings`; xác nhận lưới Fisheye sau toggle.
- [x] `ActSettings` visibility event có test riêng; adapter regressions pass.

## Implementation notes

Gộp ENH-2 của audit ui. Có thể tách thành hai PR nhỏ: sửa lỗi trước, refactor sau.

## Verification and Definition of Done

- [x] `AppRowActionsTest` 4/4, `AppAdapterAuthenticationTest` 1/1; mutation checks bắt notify trước persist/auth.
- [x] Widget tests Home/Settings + 40 device tests; accessibility labels/resources không đổi.
- [x] Room in-memory test xác nhận đúng lens persistence, biometric callback production; thiết bị kiểm Room/LiveData/Activity wiring.
- [x] Smoke Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-10-23 17:51; Home foreground, app grid đầy đủ, không quảng cáo; APK baseline khôi phục và Google Play foreground trở lại.
- [x] Full gate pass: 1027 unit tests, lint, assemble app và androidTest; 0 failure.
- [x] `doc/task/AUDIT_UI-030_2026-10-23.md`, 9.2/10.
- [x] Evidence/status/docs cập nhật; `git diff --check` + `scripts/check-secrets.sh` cần chạy lần cuối trước commit.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-030 (Ẩn/khóa app ở list mode trên Home không phát broadcast, lưới không đồng bộ) trong doc/task/todo/p2-ui-ui-030-home-list-mode-broadcast-sync.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-030_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
