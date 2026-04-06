# KẾ HOẠCH NÂNG CẤP AD SYSTEM - 260117lens-launcher

*Phiên bản: 1.1 (Release Update)*

## 1. Phân Tích Logic Ad Hiện Tại
Ứng dụng Lens Launcher hiện tại đang tích hợp Ad System nội bộ tại thư mục `com.mckimquyen.sdkadbmob` gồm `AdMobManager.kt`.
- Sử dụng Google Mobile Ads (`play-services-ads:23.6.0`).
- Có cấu hình Ad IDs cho cả Release và Debug (trong `app/build.gradle`).

### Các Touchpoint Ad Hiện Có:
1. **App Open Ad**: Kích hoạt tại `checkShowAd()` thuộc màn hình `ActSettings.java` ngay sau khi vào app. Đóng vai trò như là Splash Ad.
2. **Ad Banner**: Kích hoạt tại `setupViews()` trong `ActSettings.java` để hiển thị ở bottom layout.
3. **Ad Interstitial**: Nạp ngầm ở `setupViews()`. Kích hoạt chạy ad trước khi nhảy sang 2 thao tác: (A) click button "Show Apps" (chuyển sang Home mặc định), và (B) khi click Menu > "About" để mở trang ActAbout.
4. **Ad Reward**: Không tồn tại trong code hiện hành.
5. **App Open Background**: Phải được gỡ bỏ phân luồng toàn cục để phù hợp với đặc thù của Launcher App.

## 2. Kế Hoạch Thay Thế bằng `_260321AdmobWrapper`

### Quy định tuân thủ:
- **Chỉ thay thế** những điểm gắn ad bên trên.
- **Không khởi tạo bất kì ad nào mới**, đặc biệt bỏ qua Ad Reward theo yêu cầu.
- Tuân thủ tuyệt đối Chính sách Cửa Hàng Google Play dành cho ứng dụng Launcher.

### Các Bước Thực Hiện Chi Tiết (Step by step):

**Bước 1: Cấu hình Build gradle**
- Tại file `settings.gradle` (hoặc root `build.gradle`), bổ sung: `maven { url 'https://jitpack.io' }`
- Tại `app/build.gradle`:
  - Khai báo các field biến như `APPLOVIN_SDK_KEY`, `APPLOVIN_BANNER_ID`, v.v. (kể cả chưa dùng cũng sẽ đặt rỗng `""` vì SDK Yêu cầu cấu hình Data config).
  - Bật `IS_ENABLE_ADMOB = false` (trong khối Debug ưu tiên AppLovin).
  - Xóa đi: `implementation 'com.google.android.gms:play-services-ads:23.6.0'` và thư viện `applovin` mediation, vì Wrapper đã ôm trọn các library này (SDK version pull từ Wrapper là ổn định nhất).
  - Thêm mới vào: `implementation 'com.github.royt93:AdmobWrapper:1.1.1'`

**Bước 2: Cập Nhật `AndroidManifest.xml`**
- Chèn metadata khai báo SDK keys trực tiếp vào bên trong thẻ `<application>` (như đã có sẵn App_ID của Google Ads). Yêu cầu bổ sung thêm `<meta-data android:name="applovin.sdk.key" android:value="YOUR_APPLOVIN_SDK_KEY" />`.

**Bước 3: Khởi tạo Ad trong `RApplication.java`**
- Xóa hàm khởi tạo thread ngầm `setupAdmob()` cũ.
- Áp dụng Config mới: gọi `AdSdkConfig`, tiếp nối với `AdManager.setConfig(config)` và `AdManager.earlyInit(this)`. 
- Khởi tạo SDK (MobileAds hoặc AppLovin). *Lưu ý: AppLovin SDK callback sẽ chạy trên Background Thread.*

**Bước 4: Thay thế API Ad cho `ActSettings.java`**
- **Splash Screen**: Dùng API của Wrapper: `AdManager.INSTANCE.initSplashScreen(this, callback)`. 
- **Banner**: Chèn 3 lines gọi `AdManager.INSTANCE.bannerResume/Pause/Destroy` vào các lifecycle callbacks `onResume`, `onPause`, `onDestroy` của `ActSettings.java` để ngăn chặn rò rỉ Impression ảo.
- **Interstitial**: Điều chỉnh lại mã khi bấm button Launch Apps & Open Menu About, thay callback SDK interface cũ bằng callback lambda của `AdManager`.
- Đảm bảo gỡ interface `AdMobManager.InterstitialAdListener` đã implement trong Class.

**Bước 5: Dọn dẹp Code cũ**
- Xoá hoàn toàn package `com/mckimquyen/sdkadbmob` để dọn rác và triệt tiêu bộ nhớ.
- Xoá hoàn toàn `Applovin.kt` (code thừa).

---

## 3. 💡 BÀI HỌC KINH NGHIỆM CHUYÊN SÂU & RỦI RO (Dành cho Maintainer)
*Được kiểm duyệt khắt khe nhằm ngăn chặn lỗi crash và vi phạm chính sách.*

### Rủi Ro 1: Chính Sách Google Play (Launcher App)
Do ứng dụng này là một **Launcher** (khai báo `android.intent.category.HOME` trong `AndroidManifest`), Màn hình chính (`ActSettings.java`) sẽ liên tục được đẩy lên Foreground mỗi khi người dùng thoát ra khỏi các ứng dụng khác (ví dụ: bấm nút Home từ Chrome). 
- **Sai lầm:** Dùng `ProcessLifecycleOwner` (`AdManager.INSTANCE.registerAppOpenAdLifecycle()`) để theo dõi Background và show App Open Ad toàn cục.
- **Hậu quả:** Quảng cáo sẽ đập vào mặt người dùng mỗi lần họ trở về Màn hình chính của hệ điều hành. Điều này vi phạm nghiêm trọng chính sách **Disruptive Ads** (quảng cáo xuất hiện ngoài ngữ cảnh sử dụng app), dẫn đến **chặt đứt tài khoản Google Play** (App Ban).
- **Phòng ngừa:** Đã GỠ BỎ HOÀN TOÀN cấu hình `registerAppOpenAdLifecycle`. Chỉ kích hoạt App Open Ad an toàn dưới dạng Splash Screen lúc truy cập mục Cài đặt (`checkShowAd()`).

### Rủi Ro 2: Main Thread Crash (Lỗi Đa Luồng)
- Các callback của SDK mạng quảng cáo (như thư viện `AppLovinSdk.initializeSdk` v11.x) **không được đảm bảo chạy trên Main Thread** (thường rơi vào Background Worker).
- **Hậu quả:** Nếu đưa các hàm khởi tạo Lifecycle của Android (`ProcessLifecycleOwner.get()`) vào bên trong callback này để chờ SDK load xong, ứng dụng sẽ bị **Crash đột ngột** với Exception: `IllegalStateException("getLifecycle() cannot be invoked from a background thread")`.
- **Phòng ngừa:** Bất kỳ phương thức nào đụng tới UI hoặc Lifecycle Owner bên trong Async callback Network/SDK phải được nhốt vào ống gửi `new Handler(Looper.getMainLooper()).post(...)` hoặc `runOnUiThread`.

### Rủi Ro 3: Race Condition (Chồng Chéo Quảng Cáo)
Nếu gắn `LifecycleObserver` trễ (sau khi Activity đã hoàn thành chu trình `onCreate` và rơi vào `onResume`), Android Framework sẽ có cơ chế "bù lỗ" (catch-up events) và gọi ngược event `ON_START` ngay lập tức. Điều này kích hoạt lệnh tải quảng cáo nền chồng chéo lên `Splash Ad` đang hiển thị. Việc request Ad quá dồn dập trong cùng một Frame gây ra báo cáo **Invalid Traffic** (Lưu lượng không hợp lệ) từ Google và AppLovin.
-> Cấu trúc `bannerResume`, `bannerPause` và `bannerDestroy` phải map chuẩn xác tuyệt đối vòng đời thiết bị thay vì phụ thuộc Thread ngầm, kết hợp chặn Refresh bằng các cờ boolean nội bộ.
