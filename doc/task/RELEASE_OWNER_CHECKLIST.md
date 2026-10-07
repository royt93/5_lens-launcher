# Release 2026.10.07 — owner checklist

Việc chỉ owner làm được. Audit tĩnh 2026-10-07 trên nhánh `dev` (SHA gốc `2ce7c70`).

## Quyết định owner (07/10/2026)

- Rewarded ad ID: **giữ ID test Google ở release**, chấp nhận rủi ro; ghi nhận, không đổi code.
- Consent quảng cáo (ADS-001): **skip, owner làm sau**. Khớp quyết định declined 22/09.
- Permission (location, contacts, delete-packages): **giữ cả ba**, chỉ khai Data safety + giải trình.
- Signing (SEC-001): **để sau, dùng key hiện tại cho bản này**. Key đã lộ trong history; rotate khi sẵn sàng.

## Rủi ro còn mở (đã được owner chấp nhận)


- [ ] **Signing (SEC-001)**: keystore cũ `app/keystore.jks` + password nằm trong history Git (commit `a8701ba`, `ac7e944`, `67e7f81`). Coi như lộ.
  - Xác định là upload key hay app-signing key; Play App Signing → request upload key reset trong Play Console.
  - Tạo keystore mới, để ngoài repo, nạp qua `ANDROID_RELEASE_*` hoặc `keystore.properties` (đã gitignore).
  - Purge history: `scripts/purge-keystore-history.sh` (backup key trước, báo mọi người clone lại).
  - Chi tiết: `doc/task/SEC-001_OWNER_CHECKLIST.md`.
- [ ] **Rewarded ad ID release (BUG-6)**: `app/build.gradle` release đang dùng ID test Google `ca-app-pub-3940256099942544/5224354917`. Tạo ad unit rewarded thật trong AdMob, thay vào, hoặc ẩn rewarded ở release. `ActVipManagement` gọi `AdManager.showRewarded`.
- [ ] **Consent quảng cáo (ADS-001)**: `RApplication.setupAdmob()` init SDK trước khi có consent. Đang giao Ad SDK team. Nếu phát hành EEA/UK: cấu hình UMP message trong AdMob và xác nhận SDK chặn load khi `canRequestAds=false`. Không có fix trong repo này.

## Play Console

- [ ] **Data safety form** khớp merged manifest (build `productionRelease`):
  - Advertising ID (`AD_ID`, `ACCESS_ADSERVICES_*`): AdMob + AppLovin.
  - Vị trí xấp xỉ/chính xác: chỉ để đọc tên WiFi (`ACCESS_FINE_LOCATION`, quick action). Không gửi đi, không lưu.
  - Danh bạ (`READ_CONTACTS`): đọc tại chỗ khi tìm "contact X", không cache, không gửi đi.
  - Camera: chỉ đèn pin (`CAMERA`), `uses-feature` đã `required=false`.
  - Danh sách app cài đặt: launcher cần `queries` MAIN/LAUNCHER; kiểm tra có cần khai `QUERY_ALL_PACKAGES` không (hiện không dùng).
  - Backup: `vip_screen_prefs`, `app_search_history`, `app_preferences` bị loại khỏi backup (REL-002). Room + prefs mặc định (layout lens) vẫn được backup.
- [ ] **Privacy policy URL** (Notion) còn truy cập công khai và nêu đúng các mục trên.
- [ ] **Permission giải trình**: `REQUEST_DELETE_PACKAGES` (gỡ app từ menu), vị trí (SSID), danh bạ. Chuẩn bị lời giải trình nếu review hỏi.
- [ ] **Default launcher / HOME**: mô tả store nói rõ app là launcher thay thế.
- [ ] Upload thử lên **internal/closed track** trước production.

## Đã làm trong đợt này (code)

- Bump `versionCode 20261007` / `versionName 2026.10.07`.
- `dataExtractionRules` + `fullBackupContent` + `BackupRulesTest` (3 test).
- Build `assembleProductionRelease` (R8 + shrink) qua với keystore tạm ngoài repo; native lib 4 ABI đạt 16 KB zipalign.
- Unit 725/725, lint `productionDebug` 0 error / 8 warning (icon).

## Chưa làm / cần device

- Smoke bản release R8 trên TECNO (KJ7/BG6): lúc audit chỉ có S24U cắm, policy cấm. Cắm TECNO rồi chạy smoke.
- Audit chấm điểm >9 theo working agreement trước khi push `master`.
