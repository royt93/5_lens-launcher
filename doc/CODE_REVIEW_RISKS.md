# 🔍 Code Review Round 2 — Risk Assessment

> Self-review lần 2: Kiểm tra sâu hơn lần 1, phát hiện thêm 3 vấn đề mới.  
> Ngày: 2026-04-05.

---

## ❌ Bugs Phát Hiện & Đã Fix Trong Review

### 🔴 BUG-R1 (CRITICAL, ĐÃ FIX): `TaskSortApps` → Launcher trắng sau khi sort

**File:** `TaskSortApps.kt` dòng 80-86

**Nguyên nhân gốc rễ (Root Cause):**
BUG-07 fix đặt `app.copy(icon=null)` khi TaskUpdateApps chạy lần đầu. Kết quả: TẤT CẢ App object trong `RAppsSingleton.apps` đều có `icon = null`.

**Logic lỗi trong TaskSortApps:**
```kotlin
// TRƯỚC (BUG):
for (app in apps) {
    val appIcon = app.icon         // ← Lần 2+: luôn null
    if (appIcon != null) {         // ← Lần 2+: luôn false
        mApps?.add(app.copy(...))  // ← KHÔNG BAO GIỜ THÊM APP
        RAppsSingleton.instance.setAppIcon(...)
    }
}
// Kết quả: mApps = rỗng → launcher hiện 0 apps
```

**Fix đã áp dụng:**
```kotlin
// SAU (FIXED):
for (app in apps) {
    val appIcon = app.icon
    if (appIcon != null) {
        // Lần đầu (icon vẫn còn): cache vào BitmapCache
        RAppsSingleton.instance.setAppIcon(app.packageName.toString(), appIcon)
    }
    // Luôn thêm app (icon đã trong BitmapCache rồi)
    mApps?.add(app.copy(icon = null))
}
```

**Kịch bản crash:** Mở Settings → đổi Sort Type → apps biến mất khỏi launcher.

---

### 🟡 BUG-R2 (MEDIUM, ĐÃ FIX): `RApplication.java` → 3 dead imports

**File:** `RApplication.java` dòng 8-17

```java
// Dead imports sau khi đổi sang Thread thay vì coroutine:
import com.mckimquyen.app.ApplicationScope; // ← KHÔNG DÙNG
import kotlinx.coroutines.BuildersKt;       // ← KHÔNG DÙNG
import kotlinx.coroutines.Dispatchers;      // ← KHÔNG DÙNG
```

**Fix:** Xóa 3 import này.  
**Rủi ro thực tế:** Không crash, nhưng gây nhầm lẫn và compile warning tiềm ẩn.

---

### 🟡 BUG-R3 (xác nhận từ Review 1, ĐÃ FIX): `AppAdapter` → blank icons

**File:** `AppAdapter.java` dòng 212 (đã fix trong Review 1)

```java
// TRƯỚC: null sau BUG-07
ivAppIcon.setImageBitmap(mApp.getIcon());

// SAU: lấy từ BitmapCache
Bitmap cachedIcon = RAppsSingleton.getInstance().getAppIcon(packageName);
ivAppIcon.setImageBitmap(cachedIcon);
```

---

## 📊 Phân Tích Toàn Diện Các Thay Đổi

### Luồng dữ liệu icon — Kiểm tra hoàn chỉnh

```
PackageManager → app.icon (not null)
     ↓
TaskUpdateApps.doInBackground()
     ├── BitmapCache.put(packageName, icon)   ✅ lưu icon
     └── mApps.add(app.copy(icon = null))     ✅ tiết kiệm RAM

RAppsSingleton.apps = mApps   (icon = null trong tất cả App objects)
     ↓
BroadcastReceivers → AppEventManager.notifyAppsLoaded()
     ↓
[LensView] drawAppIcon() → RAppsSingleton.getAppIcon() → BitmapCache.get() ✅
[AppAdapter] setAppElement() → RAppsSingleton.getAppIcon() → BitmapCache.get() ✅ (sau fix)
     ↓
TaskSortApps.doInBackground() — sort → mApps.add(app.copy(icon = null)) ✅ (sau fix)
     [TRƯỚC FIX: if(appIcon!=null) filter ra hết → mApps rỗng ❌]
```

---

## 🟢 Các Thay Đổi Được Xác Nhận An Toàn

| Bug ID | Fix | Xác nhận |
| :--- | :--- | :--- |
| BUG-09 | ActAbout animator cancel | ✅ An toàn |
| BUG-10 | ActSettings Handler guard | ✅ An toàn |
| BUG-11 | WebView destroy | ✅ An toàn |
| BUG-13 | BroadcastReceivers direct AppEventManager | ✅ An toàn — `postValue()` thread-safe, BroadcastReceiver onReceive chạy trên Main |
| BUG-14 | FrmLens onDestroyView null refs | ✅ An toàn |

---

## 🟡 Rủi Ro Còn Lại (Chưa Fix, Theo Dõi)

### `initSplashScreen` — collectLatest Race Condition

**Mức độ:** MEDIUM — chỉ có thể xảy ra khi `EventBus.sendEvent()` bị gọi nhiều lần.

**Hiện tại:** `EventBus.sendEvent(true)` chỉ được gọi 1 lần duy nhất, tại:
```kotlin
// AdMobManager.kt:123
ApplicationScope.scope.launch(Dispatchers.Default) {
    EventBus.sendEvent(true)   ← Chỉ chỗ này
}
```

**initSplashScreen được gọi từ:** `ActSettings.checkShowAd()` → được gọi trong `onCreate()` của `ActSettings`.

**Phân tích flow thực tế:**
1. App start: `RApplication.setupAdmob()` chạy → `AdMobManager.init()` → `EventBus.sendEvent(true)` (1 lần)
2. `ActSettings.onCreate()` → `checkShowAd()` → `initSplashScreen()` → collector lắng nghe
3. Nếu `sendEvent()` đã xảy ra trước khi collector được đăng ký → **SharedFlow bỏ lỡ event** → ad không load → `onAdLoaded` không được gọi → **splash stuck!**

**ĐÂY LÀ BUG TIỀM ẨN NGHIÊM TRỌNG** về race condition giữa `sendEvent` và `collect`:

```
Timeline A (Normal):
  T=0: initSplashScreen() → collector start
  T=500ms: MobileAds.initialize() done → EventBus.sendEvent(true) → collector nhận ✅

Timeline B (Race Condition - Bug!):
  T=0: MobileAds.initialize() done → EventBus.sendEvent(true) (emit)  
  T=10ms: initSplashScreen() → collector start → MẤT EVENT ❌ (SharedFlow không buffer)
  → loadAppOpenAd() KHÔNG BAO GIỜ được gọi
  → onAdLoaded() KHÔNG BAO GIỜ được gọi
  → flAdOpenApp overlay còn hiển thị mãi mãi
```

**`MutableSharedFlow()` với `replay=0` (default) → không buffer → late subscriber bỏ lỡ event!**

---

## 🚨 Recommended Fix cho Race Condition

**Option 1 (Đơn giản nhất):** Thêm `replay=1` cho SharedFlow:
```kotlin
object EventBus {
    private val _eventFlow = MutableSharedFlow<Boolean>(replay = 1)  // Buffer 1 event
    val eventFlow = _eventFlow.asSharedFlow()
    suspend fun sendEvent(value: Boolean) { _eventFlow.emit(value) }
}
```
→ Late subscriber sẽ nhận lại event đã emit. An toàn vì chỉ có 1 subscriber (`initSplashScreen`).

**Option 2 (Đúng đắn hơn):** Dùng `lifecycleScope` + `SharedFlow` có buffer.

---

## 📋 Tóm Tắt Action Items

| Priority | Action | Status |
| :--- | :--- | :--- |
| 🔴 CRITICAL | TaskSortApps blank app list (BUG-R1) | ✅ Fixed |
| 🔴 CRITICAL | AppAdapter blank icons (BUG-R3) | ✅ Fixed |
| 🟡 MEDIUM | RApplication dead imports (BUG-R2) | ✅ Fixed |
| 🟡 MEDIUM | EventBus replay=0 → late subscriber miss event | **TODO: Thêm `replay=1`** |
| 🟢 LOW | BUG-12 recycle trên API 25-27 | Test thực tế |

---

## ✅ Build Status

| Lần build | Kết quả |
| :--- | :--- |
| Sau fix BUG-R1 + BUG-R2 | ✅ BUILD SUCCESSFUL in 2s |
