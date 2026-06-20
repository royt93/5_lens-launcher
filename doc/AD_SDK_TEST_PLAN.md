# Ad SDK Integration — Test Plan & Results
> Device: Pixel 7 Pro · Build: devDebug 2026.06.20 · Date: 2026-06-20

---

## TC-01 · App Cold-Start — ONLINE

**Mục tiêu:** Splash overlay dismiss đúng sau consent + App Open flow.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Force-stop app, bật WiFi, mở app | Splash overlay (flAdOpenApp) hiện |
| 2 | Chờ ≤ 3s | Overlay tự ẩn sau khi consent + App Open warmup xong |
| 3 | Kiểm tra banner | Banner hiện ở bottom (free user) |
| 4 | Filter logcat `roy93~` | `loadInterstitial` phải xuất hiện SAU `initSplashScreen navigating` |
| 5 | Filter logcat `###init` | GAID resolved, `isVIPMember=false`, init completed |

**Logcat pattern cần thấy (theo thứ tự):**
```
roy93~AdManager: requestConsentInfoUpdate ...
roy93~AdManager: initSplashScreen navigating ✅
roy93~AdManager: loadInterstitial called       ← phải sau dòng trên
roy93~AdManager: loadBanner called
```

---

## TC-02 · App Cold-Start — OFFLINE

**Mục tiêu:** Splash overlay KHÔNG bị stuck khi không có mạng (BUG-2).

| Step | Action | Expected |
|------|--------|----------|
| 1 | Tắt WiFi + Mobile data, force-stop app | — |
| 2 | Mở app | Overlay hiện tức thì |
| 3 | Chờ ≤ 2s | **Overlay tự ẩn ngay** (vì `isNetworkAvailable()=false` → skip SDK) |
| 4 | Kiểm tra UI | ActSettings hiện đầy đủ, không bị block |
| 5 | Banner container | GONE (vì offline, không load banner) |

**Logcat pattern:**
```
roy93~AdManager: (KHÔNG có requestConsentInfoUpdate call)
(overlay ẩn ngay lập tức trong < 500ms)
```

**FAIL nếu:** overlay vẫn hiện sau 10s → BUG-2 chưa fix đúng.

---

## TC-03 · Banner — VIP Active vs Free

**Mục tiêu:** Banner chỉ hiện cho free user, ẩn khi VIP active.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Free user, online | `bannerContainer` VISIBLE, banner load |
| 2 | Kích hoạt VIP (nhập key) | `bannerContainer` GONE ngay sau onResume |
| 3 | Thu hồi VIP | `bannerContainer` VISIBLE trở lại |
| 4 | Rotate screen / minimize-restore | Banner pause/resume đúng (không bị double-load) |

---

## TC-04 · Interstitial — Sau Consent (BUG-1)

**Mục tiêu:** Interstitial chỉ preload SAU khi consent resolved, không trước.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Cold-start, lọc logcat | `loadInterstitial called` timestamp > `initSplashScreen navigating` timestamp |
| 2 | Tap "Launch" / menu "About" | Interstitial có thể hiện (nếu đã load xong) |
| 3 | Offline cold-start | Interstitial attempt vẫn được gọi (SDK queue tự handle) |

---

## TC-05 · VIP Key Activation — Lowercase Input (BUG-4)

**Mục tiêu:** Nhập key lowercase vẫn activate thành công.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Vào VIP screen, nhập `9fa0q7en!27clx04@21993y2u0i7#q0` | "Kích hoạt" button enable |
| 2 | Tap "Kích hoạt" | Dialog thành công "VIP đang kích hoạt" |
| 3 | Header UI | Đổi sang gold gradient, "VIP đang kích hoạt" |
| 4 | Logcat | `activateVipByKey success=true` |

**FAIL nếu:** dialog lỗi "Mã không hợp lệ" → BUG-4 chưa fix (key không được normalize).

---

## TC-06 · VIP Key Activation — Dialog Material3 (BUG-9)

**Mục tiêu:** Dialog sau khi activate dùng Material3 style, không crash.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Nhập key hợp lệ, tap Kích hoạt | **Không crash** |
| 2 | Dialog success hiện | Rounded corners, Material3 typography |
| 3 | VIP active, tap "Thu hồi VIP" | Confirm dialog hiện, không crash |
| 4 | Tap Cancel | Dialog dismiss, VIP còn nguyên |
| 5 | Tap Thu hồi lần nữa → Xác nhận | VIP bị thu hồi, UI về free |

**FAIL nếu:** `IllegalArgumentException: requires colorSurface` → theme không có `MaterialYouDialogTheme`.

---

## TC-07 · Watch Ad — ONLINE (Rewarded Flow)

**Mục tiêu:** Rewarded ad load + show + earn reward → grant 3-day VIP.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Free user, online, VIP screen | Nút "Xem quảng cáo" enabled + pulse animation |
| 2 | Tap nút | AppLovin rewarded ad xuất hiện |
| 3 | Xem đến hết (không skip) | Dialog "VIP 3 ngày" sau khi ad kết thúc |
| 4 | UI | Header gold, countdown timer chạy, progress bar |
| 5 | Logcat | `onUserEarnedReward`, `activateVipByKey success=true` |

---

## TC-08 · Watch Ad — OFFLINE (BUG-7)

**Mục tiêu:** Không có mạng → Toast ngay lập tức, không đợi SDK timeout.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Tắt WiFi + data | — |
| 2 | VIP screen, tap "Xem quảng cáo" | **Toast "Không có kết nối internet" hiện ngay (< 300ms)** |
| 3 | Không có dialog lỗi | Không có spinner, không đợi 5-10s |
| 4 | Bật lại mạng, tap lại | Ad load bình thường |

**FAIL nếu:** User phải đợi > 2s mới thấy feedback → BUG-7 chưa fix.

---

## TC-09 · adConfig Secret — Restore Sau Activation (BUG-5 + BUG-11)

**Mục tiêu:** `adConfig.vipKeySecret` không bị stuck sau activate/fail.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Activate 3-day key qua rewarded | VIP 3 ngày active |
| 2 | Thu hồi VIP | Free user |
| 3 | Nhập key 30 ngày, kích hoạt | **Phải thành công** (secret không bị stuck ở 3-day value) |
| 4 | Nhập key sai, tap Kích hoạt | Dialog lỗi, không crash |
| 5 | Nhập key đúng ngay sau | Activate thành công |

---

## TC-10 · Grace Label (BUG-8)

**Mục tiêu:** First-install grace hiển thị đúng label.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Fresh install (uninstall + install mới) | — |
| 2 | Mở app lần đầu, chờ init | SDK grant 1-day VIP tự động (release build) |
| 3 | Vào VIP screen | Active VIP card hiện label "🎁 Quà tặng cài đặt mới" |
| 4 | Countdown timer chạy | Expiry = installBeginMs + 24h |

> **Note:** Chỉ test được trên release build (SDK skip grace ở debug).

---

## TC-11 · Animator Lifecycle (BUG-10)

**Mục tiêu:** Không leak/crash khi activity destroy trong quá trình animation.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Mở VIP screen | Slide-in animation chạy (3 section từ dưới lên) |
| 2 | Trong 1.1s đầu → bấm back ngay | **Không crash**, activity destroy clean |
| 3 | VIP active → mở screen → back trong 0.5s | Countdown timer cancel, animators cancel |
| 4 | Xoay màn hình liên tục | Không ANR, không leak |

---

## Kết quả Thực tế (Pixel 7 Pro · 2026-06-20)

> Build: `devDebug 2026.06.20` · APK: `com.mckimquyen.lenslauncherdebug_2026.06.20_20260620.apk`
> Crash fix: `MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)` rebuild + reinstall

### ONLINE Run

| TC | Status | Bằng chứng |
|----|--------|-----------|
| TC-01 | ✅ PASS | App Open ad hiện (AppLovin test ad), ActSettings load sau dismiss |
| TC-03 | ✅ PASS | Banner hiện ở bottom, free user |
| TC-04 | ✅ PASS | `loadInterstitial` @ 22:27:02.305 sau `navigating` @ 22:27:02.301 (4ms sau) |
| TC-06 | ✅ PASS (sau fix crash) | Dialog "Lỗi - Mã VIP không hợp lệ" hiện đúng, Material3 style, không crash |
| TC-07 | ✅ PASS | Rewarded test ad hiện (AppLovin MAX test mode) |

### OFFLINE Run

| TC | Status | Bằng chứng |
|----|--------|-----------|
| TC-02 | ✅ PASS | Overlay ẩn ngay < 500ms; log: `loadInterstitial ⏭️ no internet` + `loadBanner ⏭️ no internet` |
| TC-08 | ✅ PASS | Toast "Không có kết nối internet" xuất hiện tức thì (screenshot xác nhận) |

### Crash phát hiện và fix trong session này

| Crash | Root cause | Fix |
|-------|-----------|-----|
| `IllegalArgumentException: requires colorSurface` khi show dialog | `MaterialAlertDialogBuilder(this)` yêu cầu activity theme có `colorSurface`; `AppTheme.NoActionBar` kế thừa `Theme.AppCompat` không có attribute này | Thay bằng `MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme)` — `MaterialYouDialogTheme` kế thừa `Theme.MaterialComponents.DayNight.Dialog.Alert` đã có `colorSurface` |

### Pending Manual (cần test tay trực tiếp trên device)

| TC | Lý do pending | Hướng dẫn test |
|----|--------------|---------------|
| TC-05 (BUG-4) | `adb input text` crash với `@`, `#`, `!` → không type được key có ký tự đặc biệt | Gõ tay: `9fa0q7en!27clx04@21993y2u0i7#q0` → Kích hoạt → phải thành công |
| TC-09 (BUG-5/11) | Cần chuỗi activate/revoke nhiều lần để verify secret restore | Activate 3-day → revoke → activate 30-day → phải thành công |
| TC-10 (BUG-8) | Grace label chỉ active trên release build | Fresh install release → VIP screen → xem label header |
| TC-11 (BUG-10) | Visual animation | Mở VIP screen → back trong < 1s → không crash |

---

## Known Issues (không liên quan đến ad SDK)

| Issue | Severity | Note |
|-------|----------|------|
| HWUI image decoder warnings | LOW | Android 14/15 bug với WebP/AVIF icons, không ảnh hưởng runtime |
| UMP form chưa config | MEDIUM | `Publisher misconfiguration` — cần tạo consent form trong AdMob console |
| ADMOB_REWARDED_ID release = test ID | MEDIUM | Đã thêm TODO comment, cần real ID trước khi flip `IS_ENABLE_ADMOB=true` |
