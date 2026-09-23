# SEC-001 — Checklist owner-action (Roy tự làm, Claude không có quyền)

> Nguồn: `doc/task/inprogress/p0-sec-sec-001-rotate-release-signing.md`. File này chỉ
> là checklist thao tác cụ thể cho phần Claude không tự động hoá được (cần quyền Play
> Console / CI secret store / lịch sử Git). Cập nhật checkbox trong file story chính
> khi xong, không tick ở đây.

## Bối cảnh đã xác nhận (2026-09-23)

- `app/keystore.jks` đã bị xoá khỏi HEAD (không còn tracked), nhưng còn tồn tại trong lịch sử Git ở 3 commit: `a8701ba`, `ac7e944`, `67e7f81` (`git log --all --oneline -- app/keystore.jks`).
- `gradle.properties` hiện tại không còn `KS_ALIAS`/`KS_PW` plaintext.
- `.gitignore` đã chặn `*.jks`, `*.keystore`, `keystore.properties`.
- Không có `keystore.properties` local nào bị commit nhầm hiện tại.
- Build release đã fail sớm và rõ ràng nếu thiếu biến ký (`ANDROID_RELEASE_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`).

## Bước 1 — Xác định loại key bị lộ

- [ ] Vào Play Console → app → **Release → Setup → App signing**.
- [ ] Xem key bị lộ (`app/keystore.jks` cũ) là **upload key** hay **app signing key**:
  - Nếu Play App Signing đang bật (khả năng cao với app đã publish): key bị lộ thường chỉ là **upload key**, key ký thật (app signing key) do Google giữ, không lộ — rủi ro thấp hơn nhiều.
  - Nếu app KHÔNG dùng Play App Signing: key bị lộ chính là app signing key thật — rủi ro cao nhất, không revoke được, chỉ có thể migrate sang Play App Signing hoặc (xấu nhất) đổi package name.

## Bước 2 — Rotate / revoke

- [ ] Nếu là upload key: Play Console có nút **"Request upload key reset"** — làm theo hướng dẫn Google (cần xác minh danh tính chủ tài khoản).
- [ ] Nếu chưa bật Play App Signing: cân nhắc bật ngay (Google sẽ giữ app signing key, giảm rủi ro về sau) trước khi làm gì khác.
- [ ] Sau khi có key mới: generate keystore mới, **không** dùng lại alias/password cũ.
- [ ] Lưu key mới vào chỗ an toàn ngoài repo (password manager / secret vault), backup ít nhất 2 nơi — mất key ký = không update được app nữa.

## Bước 3 — Cấu hình CI secret store

- [ ] Set 4 biến môi trường trong CI (GitHub Actions secrets hoặc tương đương):
  `ANDROID_RELEASE_STORE_FILE`, `ANDROID_RELEASE_STORE_PASSWORD`,
  `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`.
- [ ] Test: chạy `./gradlew assembleProductionRelease` (hoặc `bundle*Release`) trên CI, xác nhận build xanh và không log lộ giá trị secret (kiểm tra log output).

## Bước 4 — Validate trên track không phải production

- [ ] Build 1 bản release bằng key mới, upload lên **Internal testing** hoặc **Closed testing** track (KHÔNG production).
- [ ] Xác nhận Play Console chấp nhận upload (không báo lỗi signature mismatch).
- [ ] Cài thử bản này trên máy đã có sẵn bản cũ — xác nhận update (không phải cài mới đè) chạy được, không mất data.

## Bước 5 — Purge lịch sử Git (phối hợp, rủi ro cao)

- [ ] Backup toàn bộ repo trước khi làm (mọi branch, mọi remote).
- [ ] Thông báo TRƯỚC cho bất kỳ ai khác có clone repo — sau khi rewrite history, họ phải re-clone hoặc `git fetch` + reset cứng, không được merge/rebase bình thường.
- [ ] Dùng `git filter-repo` (khuyến nghị, thay cho `filter-branch` đã deprecated) để xoá `app/keystore.jks` khỏi mọi commit:
  ```
  git filter-repo --path app/keystore.jks --invert-paths
  ```
- [ ] Force-push toàn bộ branch bị ảnh hưởng lên remote — **đây là thao tác phá hoại không đảo ngược được, Claude sẽ không tự chạy lệnh này**, Roy tự chạy khi đã sẵn sàng.
- [ ] Sau force-push: báo mọi người re-clone; xoá reflog + chạy GC trên server (GitHub tự làm với repo cũ sau 1 khoảng thời gian, hoặc liên hệ GitHub Support để xoá cache sớm hơn nếu cần).

## Bước 6 — Secret scanning + tài liệu ownership

- [ ] Bật secret scanning cho repo (GitHub có sẵn **Secret scanning** trong Settings → Security nếu repo private/public phù hợp gói, hoặc dùng `gitleaks`/`trufflehog` như pre-commit hook / CI step).
- [ ] Ghi lại vào `doc/task/inprogress/p0-sec-sec-001-rotate-release-signing.md` (hoặc file riêng): ai giữ key mới, key lưu ở đâu, ai có quyền truy cập CI secrets.

## Khi xong hết

- [ ] Cập nhật checkbox tương ứng trong `doc/task/inprogress/p0-sec-sec-001-rotate-release-signing.md`.
- [ ] Chuyển story sang `doc/task/done/` khi toàn bộ Acceptance criteria + Definition of Done pass.
- [ ] Xoá file checklist này (`doc/task/SEC-001_OWNER_CHECKLIST.md`) sau khi xong, hoặc giữ lại làm tham khảo — tuỳ Roy.
