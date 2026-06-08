# Kế hoạch Migrate hệ thống Ad sang `AdmobApplovinWrapper` (1.1.5)

## (1) Touchpoint table

| Slot | File hiện tại | Trigger | API mới | API cũ thay thế |
|---|---|---|---|---|
| Banner | `ActSettings.java` | `initSplashScreen` success callback | `AdManager.loadBanner` (auto-lifecycle) | `com.roy.sdkadbmob.AdManager.INSTANCE.loadBanner` |
| Interstitial | `ActSettings.java` | Khi user click một số feature | `AdManager.showInterstitial` | `com.roy.sdkadbmob.AdManager.INSTANCE.showInterstitial` |
| App Open | `ActSettings.java` (hiện tại đóng vai trò Splash) | Thay vì `initSplashScreen`, sẽ dùng `awaitSplashComplete` | `AdManager.awaitSplashComplete` | `com.roy.sdkadbmob.AdManager.INSTANCE.initSplashScreen` |
| Rewarded | `FVipManagement.kt` (VIP screen) | User click "Watch ad → 3 days VIP" | `AdManager.loadRewarded` + `showRewarded`, fallback `showInterstitial` | (chưa có) |

## (2) Step-by-step migrate plan

- **Bước 1: Setup Dependency & Cấu hình**
  - Update `app/build.gradle`: Xoá `AdmobWrapper:1.1.1`, thêm `com.github.royt93:AdmobApplovinWrapper:1.1.5`.
  - Cập nhật các biến `BuildConfig`: Đảm bảo có đủ AppLovin ID (thêm Rewarded ID) và AdMob ID (thêm Rewarded ID, có thể dùng test ID như User yêu cầu).
  - Thêm `AdKeys.kt` để quản lý VIP Secret (Base64) và Privacy Policy URL.

- **Bước 2: App Init + Consent**
  - Trong `RApplication.java`:
    - Xóa toàn bộ logic `setupAdmob()` cũ (không tự gọi `MobileAds.initialize` hay `AppLovinSdk...`).
    - Khởi tạo `AdSdkConfig` mới từ package mới (`com.roy.admobwrapper`).
    - Gọi `AdManager.initialize(this)` (gộp init, không cần callback phức tạp).

- **Bước 3: Tích hợp UMP Consent & App Open**
  - Do app dùng `ActSettings` làm entry point (Splash bị tắt), thêm logic `AdManager.requestConsentInfoUpdate` vào đầu `onCreate` của `ActSettings`.
  - Đợi consent, sau đó gọi `AdManager.awaitSplashComplete` trước khi load nội dung chính.

- **Bước 4: Nâng cấp Banner & Interstitial**
  - Trong `ActSettings.java`:
    - Đổi package name `com.roy.sdkadbmob.AdManager` thành `com.roy.admobwrapper.AdManager`.
    - Gắn `AdManager.loadBanner` vào container banner, bật autoManageLifecycle = true (không cần tự gọi pause/resume/destroy ở `ActSettings`).
    - Xoá các thủ công cleanup banner.
    - Cập nhật các hàm `showInterstitial` thành API mới.

- **Bước 5: Xây dựng VIP UI (Mandatory)**
  - Thêm folder `com/mckimquyen/feature/vip`.
  - Tạo `FVipManagement.kt`, `VipKeys.kt`, `VipPrefs.kt` và layout XML tương ứng theo đúng UI/UX từ SDK template.
  - Implement logic progress bar, animation, first install grace (1-day free), manual VIP code redemption.
  - Wire VIP Badge vào Toolbar của `ActSettings` (hoặc `ActHome`).

- **Bước 6: Dọn dẹp & Review Leak**
  - Grep kiểm tra không còn dùng package `com.roy.sdkadbmob`.
  - Chạy build test (Debug/Release).
  - Kiểm tra các quy tắc memory leak trong VIP fragment.

## (3) Bảng so sánh trước/sau

| Feature | Trước đây | Sau khi Migrate |
|---|---|---|
| Provider | AdMob / AppLovin (code tự phân nhánh) | AdMob + AppLovin MAX (SDK gộp, 1 dòng cấu hình) |
| Architecture | Tự init SDK rườm rà trong Application | One-shot init gọn gàng |
| App Open | Tự quản lý Lifecycle rườm rà | Tự động qua `ProcessLifecycleOwner` |
| Consent | Chưa có UMP / Consent message | Đầy đủ UMP (GDPR compliant) |
| Memory leak risk | Phải tự gọi pause/resume/destroy banner | Auto-lifecycle, an toàn |
| Frequency cap | Hard cap cơ bản | 7-layer AdSafety tự động chống click tặc |
| VIP | Không có | VIP System xịn với Key, Trial, UI/UX Animation |
| Lifecycle | Custom logic | Dựa trên Coroutine + Jetpack Lifecycle |

## (4) Thông tin đã tích hợp & cấu hình thành công

- **AppLovin Rewarded ID**: `6a333043c24bd06e` (Đã tích hợp vào `build.gradle` debug/release).
- **AdMob Rewarded ID**: Dùng ID test (`ca-app-pub-3940256099942544/5224354917`) theo yêu cầu của user.
- **Privacy Policy URL**: `https://loitp.notion.site/Term-Privacy-Policy-Disclaimer-319b1cd8783942fa8923d2a3c9bce60f` (Đã tích hợp thành công vào cấu hình Consent UMP + Footer VIP screen).
- **VIP keys**: 30-ngày & 3-ngày đã được obfuscate dạng Base64 và lookup case-insensitive thành công.

## (5) Kết quả Audit & Test Verification

- **Unit Tests**: `VipKeysTest`, `VipPrefsTest` (Robolectric) đã PASS.
- **Widget Tests**: `FVipManagementWidgetTest` đã PASS.
- **Integration Tests**: `AdManagerIntegrationTest` đã PASS.
- **Aesthetics & UI/UX**: Tái hiện đầy đủ 13 thành phần UI và 5 loại hiệu ứng động (Pulse, Slide-in, Rotation Shimmer, Count-up, Confetti) cùng tương tác rung haptic phản hồi.
- **Memory Leak check**:
  - Không giữ reference của Binding sau `onDestroyView` (mọi callback và timer đều check safe binding null).
  - Sử dụng `CountDownTimer` thay cho `Handler.postDelayed`.
  - Mọi Animator đều được hủy và thu hồi tại `onDestroyView`.

