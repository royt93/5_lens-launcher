# 📋 MIGRATION GUIDE - Hướng dẫn Fix các vấn đề còn lại

## ✅ Đã Fix (Completed)

### 1. Deprecation & Warnings
- ✅ **1.1**: AsyncTask → Coroutines (TaskUpdateApps.kt, TaskSortApps.kt)
- ✅ **1.2**: Observable/Observer → LiveData (AppEventManager.kt + wrapper classes)
- ✅ **1.3**: FragmentStatePagerAdapter → BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
- ✅ **1.4**: fitSystemWindows → WindowInsetsCompat (LensView.kt)
- ✅ **1.5**: Update dependencies (build.gradle)

### 2. Redundant Logic
- ✅ **2.1**: Optimize ArrayList copy trong RAppsSingleton

### 3. Logic Errors & Thread Safety
- ✅ **3.3**: Thread safety Observable (AppEventManager với object singleton)
- ✅ **3.4**: Thread safety Singleton (RAppsSingleton với lazy)

### 4. Memory Leaks
- ✅ **4.1**: AsyncTask context leak (WeakReference trong Task classes)
- ✅ **4.3**: Handler cleanup SplashAct
- ✅ **4.4**: UtilSettings context retention (use Application Context)

---

## ⏳ Chưa Fix (Pending) - Cần làm tiếp

### **2.2: Remove redundant null checks**
**File**: `ActHome.java:68, 141-143`

```java
// ❌ Hiện tại
Objects.requireNonNull(Objects.requireNonNull(RAppsSingleton.getInstance()).getApps())

// ✅ Fix
RAppsSingleton.getInstance().getApps() // instance không bao giờ null với lazy
```

---

### **2.3: Remove commented code**
**Files**:
- `ActSettings.java:219-269, 314-324`
- `ActHome.java:64`

Xóa tất cả code đã comment:
```java
// Xóa dòng này:
//        updateColor();
```

---

### **3.1: IndexOutOfBounds trong removeHiddenApps** ⚠️ CRITICAL
**File**: `ActHome.java:114-122`

```java
// ❌ Hiện tại - Bug khi remove trong loop
private void removeHiddenApps() {
    for (int i = 0; i < listApp.size(); i++) {
        if (!AppPersistent.getAppVisibility(...)) {
            listApp.remove(i);
            listAppIcon.remove(i);
            i--; // Hack nhưng vẫn có thể bug
        }
    }
}

// ✅ Fix - Loop ngược từ cuối về đầu
private void removeHiddenApps() {
    // Loop ngược để tránh IndexOutOfBounds khi remove
    for (int i = listApp.size() - 1; i >= 0; i--) {
        App app = listApp.get(i);
        if (!AppPersistent.getAppVisibility(
                app.getPackageName(),
                app.getName())) {
            listApp.remove(i);
            listAppIcon.remove(i);
        }
    }
}
```

---

### **3.2: Missing observer cleanup** ⚠️ MEMORY LEAK
**File**: `ActHome.java:130-133`

```java
// ❌ Hiện tại - Chỉ remove LoadedObservable
@Override
protected void onDestroy() {
    LoadedObservable.getInstance().deleteObserver(this);
    super.onDestroy();
}

// ✅ Fix - Remove TẤT CẢ observers
@Override
protected void onDestroy() {
    // Remove tất cả observers để tránh memory leak
    LoadedObservable.getInstance().deleteObserver(this);
    VisibilityChangedObservable.getInstance().deleteObserver(this);
    BackgroundChangedObservable.getInstance().deleteObserver(this);
    NightModeObservable.getInstance().deleteObserver(this);
    LockChangedObservable.getInstance().deleteObserver(this);
    super.onDestroy();
}
```

---

### **3.5: Null check package name**
**File**: `AppAdapter.java:147`

```java
// ❌ Hiện tại
if (app.getPackageName().equals(PKG_NAME)) {...}

// ✅ Fix
if (app.getPackageName() != null && app.getPackageName().equals(PKG_NAME)) {...}
```

---

### **4.2: Observer cleanup ActAbout** ⚠️ MEMORY LEAK
**File**: `ActAbout.java`

```java
// onCreate có:
NightModeObservable.getInstance().addObserver(this);

// ✅ Thêm vào onDestroy:
@Override
protected void onDestroy() {
    NightModeObservable.getInstance().deleteObserver(this);
    super.onDestroy();
}
```

---

### **4.5: AdView null after destroy**
**File**: `ActSettings.java:556-558`

```java
// ❌ Hiện tại
if (adView != null) {
    adView.destroy();
}

// ✅ Fix
if (adView != null) {
    adView.destroy();
    adView = null; // Set null để GC
}
```

---

### **4.6: Anonymous inner classes memory leak**
**File**: `ActSettings.java:139-156, 277-285`

**Option 1**: Convert sang static inner class với WeakReference
```java
// ❌ Hiện tại - Anonymous class giữ reference đến Activity
viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
    @Override
    public void onPageSelected(int position) {
        // Access activity members
    }
});

// ✅ Fix - Static class với WeakReference
private static class PageChangeListener implements ViewPager.OnPageChangeListener {
    private final WeakReference<ActSettings> activityRef;

    PageChangeListener(ActSettings activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public void onPageSelected(int position) {
        ActSettings activity = activityRef.get();
        if (activity != null && !activity.isFinishing()) {
            // Use activity
        }
    }
}

// Sử dụng:
viewPager.addOnPageChangeListener(new PageChangeListener(this));
```

**Option 2**: Remove listener trong onDestroy
```java
private ViewPager.OnPageChangeListener pageChangeListener;

@Override
protected void onCreate(Bundle savedInstanceState) {
    pageChangeListener = new ViewPager.OnPageChangeListener() {...};
    viewPager.addOnPageChangeListener(pageChangeListener);
}

@Override
protected void onDestroy() {
    if (viewPager != null && pageChangeListener != null) {
        viewPager.removeOnPageChangeListener(pageChangeListener);
        pageChangeListener = null;
    }
    super.onDestroy();
}
```

---

### **5.1: Bitmap LruCache implementation** ⚠️ CRITICAL - OutOfMemoryError
**File**: `RAppsSingleton.kt`, `TaskUpdateApps.kt`

Tạo BitmapCache manager:

```kotlin
// Tạo file mới: app/src/main/java/com/mckimquyen/util/BitmapCache.kt
package com.mckimquyen.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * LruCache để quản lý Bitmap icons, tránh OutOfMemoryError
 */
object BitmapCache {
    // Sử dụng 1/8 heap size cho cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // Size tính bằng KB
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Recycle bitmap khi bị remove khỏi cache
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (cache.get(key) == null) {
            cache.put(key, bitmap)
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
```

**Sử dụng trong RAppsSingleton**:
```kotlin
// Thay đổi logic store icons
class RAppsSingleton private constructor() {
    // Không lưu ArrayList<Bitmap> nữa, dùng cache
    private var mApps: ArrayList<App>? = null

    fun getIconForApp(packageName: String): Bitmap? {
        return BitmapCache.get(packageName)
    }

    fun setIconForApp(packageName: String, icon: Bitmap) {
        BitmapCache.put(packageName, icon)
    }
}
```

---

### **5.2: Database async operations** ⚠️ CRITICAL - ANR Risk
**File**: `ActHome.java:114-122`, và mọi nơi dùng `AppPersistent`

**Fix bằng Coroutines**:

```kotlin
// Migrate ActHome.java sang ActHome.kt hoặc wrap trong lifecycleScope

// ❌ Hiện tại - DB trên Main thread
private void removeHiddenApps() {
    for (...) {
        if (!AppPersistent.getAppVisibility(...)) { // ← DB call trên Main
            ...
        }
    }
}

// ✅ Fix - Async với Coroutines
private fun removeHiddenApps() {
    lifecycleScope.launch {
        // Background thread
        val visibleApps = withContext(Dispatchers.IO) {
            listApp.filterIndexed { index, app ->
                AppPersistent.getAppVisibility(
                    app.packageName,
                    app.name
                )
            }
        }

        val visibleIcons = withContext(Dispatchers.IO) {
            visibleApps.mapNotNull { app ->
                listApp.indexOf(app).let { index ->
                    if (index >= 0) listAppIcon.getOrNull(index) else null
                }
            }
        }

        // Main thread - Update UI
        listApp.clear()
        listApp.addAll(visibleApps)
        listAppIcon.clear()
        listAppIcon.addAll(visibleIcons)

        // Notify adapter
        lensViews.setApps(listApp, listAppIcon)
    }
}
```

**Hoặc dùng AsyncTask replacement**:
```java
// Tạo helper class
class AsyncDatabaseTask {
    interface Callback<T> {
        void onResult(T result);
    }

    static <T> void execute(
        Supplier<T> backgroundWork,
        Callback<T> callback
    ) {
        new Thread(() -> {
            T result = backgroundWork.get();
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(result);
            });
        }).start();
    }
}

// Sử dụng:
private void removeHiddenApps() {
    AsyncDatabaseTask.execute(
        () -> {
            // Background: Filter apps
            List<App> visible = new ArrayList<>();
            for (App app : listApp) {
                if (AppPersistent.getAppVisibility(...)) {
                    visible.add(app);
                }
            }
            return visible;
        },
        (visibleApps) -> {
            // Main thread: Update UI
            listApp.clear();
            listApp.addAll(visibleApps);
            lensViews.setApps(listApp, listAppIcon);
        }
    );
}
```

---

### **5.3: RApplication background thread**
**File**: `RApplication.java:59-70`

```java
// ❌ Hiện tại - Tạo raw Thread
new Thread(() -> {
    MobileAds.initialize(...);
    AdMobManager.init(...); // ← Có thể touch UI
}).start();

// ✅ Fix - Đảm bảo AdMob init trên Main thread
new Handler(Looper.getMainLooper()).post(() -> {
    MobileAds.initialize(this, initializationStatus -> {
        // Callback đã chạy trên Main thread
        AdMobManager.init(this);
    });
});
```

---

### **5.4: Bitmap recycle check**
**File**: `LensView.kt:409-445`

```kotlin
// ❌ Hiện tại - Draw bitmap không check recycle
canvas.drawBitmap(icon, ...)

// ✅ Fix - Check trước khi draw
if (icon != null && !icon.isRecycled) {
    canvas.drawBitmap(icon, ...)
}
```

**Apply toàn bộ nơi draw bitmap**:
```kotlin
// Tạo extension function
fun Canvas.drawBitmapSafe(bitmap: Bitmap?, ...) {
    if (bitmap != null && !bitmap.isRecycled) {
        this.drawBitmap(bitmap, ...)
    }
}

// Sử dụng
canvas.drawBitmapSafe(icon, ...)
```

---

### **5.5: PackageManager exception handling**
**File**: `UtilApp.kt:41`

```kotlin
// ❌ Hiện tại
val activities = packageManager.queryIntentActivities(intent, 0)

// ✅ Fix - Catch SecurityException
val activities = try {
    packageManager.queryIntentActivities(intent, 0)
} catch (e: SecurityException) {
    Log.e("UtilApp", "SecurityException when querying apps", e)
    emptyList()
} catch (e: RuntimeException) {
    Log.e("UtilApp", "RuntimeException when querying apps", e)
    emptyList()
}
```

---

### **5.7: Race condition flag synchronization**
**File**: Bất kỳ nơi nào có shared mutable state

```kotlin
// ❌ Hiện tại - Không synchronized
private var isLoading = false

fun loadData() {
    if (isLoading) return
    isLoading = true
    // ...
}

// ✅ Fix Option 1 - @Volatile + synchronized
@Volatile
private var isLoading = false

@Synchronized
fun setLoading(loading: Boolean) {
    isLoading = loading
}

fun loadData() {
    if (isLoading) return
    setLoading(true)
    // ...
}

// ✅ Fix Option 2 - AtomicBoolean
private val isLoading = AtomicBoolean(false)

fun loadData() {
    if (!isLoading.compareAndSet(false, true)) return
    // ...
    isLoading.set(false)
}
```

---

### **5.8: State restoration cho all Activities** ⚠️ CRITICAL - Crash on rotation

**ActHome.java**:
```java
@Override
protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    // Lưu state
    outState.putParcelableArrayList("listApp", listApp);
    // Icons không save được (Bitmap not Parcelable)
    // Sẽ reload từ RAppsSingleton
}

@Override
protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    // Restore state
    ArrayList<App> restoredApps = savedInstanceState.getParcelableArrayList("listApp");
    if (restoredApps != null) {
        listApp = restoredApps;
        // Reload icons từ Singleton hoặc cache
        listAppIcon = RAppsSingleton.getInstance().getAppIcons();
        lensViews.setApps(listApp, listAppIcon);
    }
}
```

**ActSettings.java**:
```java
@Override
protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("currentPage", viewPager.getCurrentItem());
}

@Override
protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    int currentPage = savedInstanceState.getInt("currentPage", 0);
    viewPager.setCurrentItem(currentPage);
}
```

---

## 🔧 Cách Migrate ActHome.java sang Kotlin (Recommended)

Để dễ dàng sử dụng Coroutines và LiveData:

1. **Android Studio**: Right-click `ActHome.java` → Convert Java File to Kotlin File
2. **Fix errors** sau khi convert
3. **Add lifecycle scope**:
```kotlin
class ActHome : ActBase(), LifecycleOwner {
    // Bây giờ có thể dùng lifecycleScope
    private fun removeHiddenApps() {
        lifecycleScope.launch {
            // Coroutines code
        }
    }
}
```

---

## 📝 Testing Checklist

Sau khi fix, test các scenario sau:

- [ ] Rotate màn hình (configuration change) - không crash
- [ ] Load nhiều apps (100+) - không OutOfMemoryError
- [ ] Background/Foreground nhiều lần - không memory leak
- [ ] Thao tác nhanh trên UI - không ANR
- [ ] Mở app từ launcher - smooth, không lag

---

## 🚀 Priority Order (Thứ tự ưu tiên)

1. **CRITICAL** (Fix ngay):
   - 3.1: IndexOutOfBounds
   - 5.1: Bitmap LruCache
   - 5.2: Database async
   - 5.8: State restoration

2. **HIGH** (Fix sớm):
   - 3.2: Missing observer cleanup
   - 4.2: Observer cleanup ActAbout
   - 5.4: Bitmap recycle check

3. **MEDIUM** (Fix khi rảnh):
   - 2.2, 2.3: Code cleanup
   - 3.5, 4.5, 4.6: Minor fixes
   - 5.3, 5.5, 5.7: Safety improvements

---

**Tác giả**: Claude Code Migration Assistant
**Ngày**: 2025-10-06
**Version**: 1.0
