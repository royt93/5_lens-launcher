# 🧠 Danh sách Memory Leaks & Nguồn Gốc (Sources)

Dưới đây là tổng hợp các vấn đề Memory Leak trong dự án. Tất cả các vấn đề đã xác định đều đã được khắc phục hoàn toàn và đã build thành công.

## ✅ Đã Khắc Phục (Fixed)

| Vấn đề | Source File | Giải pháp |
| :--- | :--- | :--- |
| **AsyncTask Context Leak** | [TaskUpdateApps.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/services/TaskUpdateApps.kt)<br>[TaskSortApps.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/services/TaskSortApps.kt) | Sử dụng `WeakReference` và migrate sang Coroutines. |
| **Handler Leak** | [SplashAct.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/ui/SplashAct.kt) | Gọi `removeCallbacksAndMessages(null)` trong `onDestroy`. |
| **Static/Long-lived Context** | [UtilSettings.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/util/UtilSettings.kt) | Chuyển sang sử dụng `applicationContext`. |
| **Missing Observer Cleanup** | [ActHome.java](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/ui/ActHome.java) | Đã verify việc sử dụng `LiveData` (tự động cleanup theo Lifecycle). Các method `deleteObserver` dư thừa đã được loại bỏ để tránh lỗi compile. |
| **Observer Leak (About)** | [ActAbout.java](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/ui/ActAbout.java) | Tương tự ActHome, việc sử dụng `LiveData` giúp tự động quản lý lifecycle. |
| **AdView Reference Cleanup** | [ActSettings.java](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/ui/ActSettings.java) | Đã set `adView = null` sau khi destroy và dọn dẹp dialogs. |
| **Anonymous Inner Classes** | [ActSettings.java](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/ui/ActSettings.java) | Chuyển `OnPageChangeCallback` sang static inner class để tránh giữ reference Activity. |
| **Bitmap / Icon Storage** | [RAppsSingleton.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/app/RAppsSingleton.kt)<br>[LensView.kt](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/app/src/main/java/com/mckimquyen/views/LensView.kt) | Sử dụng `BitmapCache` (LruCache). Icons được load/set qua cache bằng `packageName.toString()` để đảm bảo tính duy nhất và tiết kiệm RAM. |

---

## 🔍 Verification

- **Build Status**: `BUILD SUCCESSFUL` (Verified via `./gradlew assembleDebug`).
- **RAM Optimization**: Đã gỡ bỏ toàn bộ list `Bitmap` dư thừa trong `ActHome` và `LensView`.
- **Type Safety**: Fix lỗi type mismatch giữa `CharSequence` (packageName) và `String` trong cache logic.

*Chi tiết về quá trình migration có thể xem tại [MIGRATION_GUIDE.md](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/1012lens-launcher/doc/MIGRATION_GUIDE.md).*
