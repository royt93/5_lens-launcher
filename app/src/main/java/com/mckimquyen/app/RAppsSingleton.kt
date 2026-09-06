package com.mckimquyen.app

import android.graphics.Bitmap
import com.mckimquyen.model.App

/**
 * ============================================================================
 * APPS SINGLETON - Fisheye Launcher
 * ============================================================================
 * Singleton pattern để quản lý danh sách apps và icons trong toàn bộ app
 * Đây là single source of truth cho app data
 *
 * CHỨC NĂNG:
 * - Lưu trữ danh sách apps được load từ PackageManager
 * - Lưu trữ danh sách bitmap icons của các apps
 * - Cung cấp thread-safe access cho toàn bộ app
 * - Tránh duplicate data giữa các Fragments/Activities
 *
 * ARCHITECTURE PATTERN:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                      RAppsSingleton                             │
 * │  ┌─────────────┐                    ┌──────────────┐           │
 * │  │  mApps      │                    │  mAppIcons   │           │
 * │  │ List<App>   │                    │ List<Bitmap> │           │
 * │  └─────────────┘                    └──────────────┘           │
 * └────────────────────▲────────────────────────▲───────────────────┘
 *                      │                        │
 *          ┌───────────┴────────────┬───────────┴──────────┐
 *          │                        │                      │
 *     TaskUpdateApps           FrmLens                 FrmApps
 *    (Write data)           (Read data)            (Read data)
 *
 * DATA FLOW:
 * 1. TaskUpdateApps query PackageManager → Load apps & icons
 * 2. TaskUpdateApps commits a generation-guarded snapshot to RAppsSingleton
 * 3. Observable.notifyObservers() → Notify Fragments
 * 4. FrmLens/FrmApps → Read from RAppsSingleton → Update UI
 *
 * THREAD SAFETY:
 * - Singleton instance: LazyThreadSafetyMode.SYNCHRONIZED
 *   → Chỉ 1 instance được tạo dù có nhiều threads cùng access
 * - App snapshot reads/writes and targeted mutations are synchronized.
 * - Callers receive defensive list copies so external mutation cannot corrupt stored state.
 *
 * MEMORY OPTIMIZATION:
 * App snapshots are copied at the boundary. Bitmap payloads remain in BitmapCache.
 *
 * Fix 3.4 - Thread-safe singleton:
 *   - OLD: Double-checked locking với synchronized block → Phức tạp, dễ lỗi
 *   - NEW: Lazy delegate với SYNCHRONIZED mode → Kotlin handle internally
 *
 * USAGE EXAMPLE:
 * ```kotlin
 * // Get singleton instance
 * val singleton = RAppsSingleton.instance
 *
 * // Read apps (thread-safe)
 * val apps: ArrayList<App>? = singleton.apps
 * val icons: ArrayList<Bitmap>? = singleton.appIcons
 *
 * // Write apps (should be called from background thread)
 * GlobalScope.launch(Dispatchers.IO) {
 *     singleton.apps = loadedApps
 *     singleton.appIcons = loadedIcons
 *
 *     // Notify UI
 *     withContext(Dispatchers.Main) {
 *         LoadedObservable.instance.updateValue(null)
 *     }
 * }
 * ```
 *
 * IMPORTANT NOTES:
 * - Singleton tồn tại trong suốt lifecycle của app
 * - Data không bị clear khi Activity destroyed
 * - Cần careful với memory leaks (bitmaps rất tốn memory)
 * - Nên implement bitmap caching/recycling để giảm memory usage
 *
 * LIMITATIONS:
 * - Không persist data: Khi app killed, data mất
 * - Không sync across processes: Mỗi process có instance riêng
 * - Snapshot mutation must go through the synchronized methods below.
 *
 * WHY NOT USE:
 * - ViewModel: Scoped to Activity/Fragment lifecycle, mất khi destroy
 * - Database: Quá chậm cho frequent reads, icons không nên lưu DB
 * - SharedPreferences: Không phù hợp cho complex objects và bitmaps
 * - Static fields: Không có lifecycle-aware, dễ memory leak
 *
 * ============================================================================
 */
class RAppsSingleton private constructor() {

    // ========================================================================
    // PRIVATE FIELDS - Internal Storage
    // ========================================================================

    /**
     * Danh sách apps được load từ PackageManager
     * Chứa thông tin: label, package name, visibility, lock status, etc.
     * null = chưa load, ArrayList() = đã load nhưng rỗng
     */
    private var mApps: ArrayList<App>? = null

    // NOT storing mAppIcons anymore to save memory (Fix 5.1)
    // Icons are now managed by BitmapCache (LruCache)

    // ========================================================================
    // PUBLIC PROPERTIES - Thread-Safe Access
    // ========================================================================

    /**
     * Public property để get/set danh sách apps
     */
    var apps: ArrayList<App>?
        @Synchronized get() = mApps?.let(::ArrayList) ?: ArrayList()
        set(apps) {
            synchronized(this) {
                mApps = apps?.let(::ArrayList)
            }
        }

    /** Commits one immutable app/icon generation as a single synchronized operation. */
    fun replaceSnapshot(apps: List<App>, icons: Map<String, Bitmap>) {
        synchronized(this) {
            icons.forEach { (iconCacheKey, icon) ->
                com.mckimquyen.util.BitmapCache.put(iconCacheKey, icon)
            }
            // CORE-002: drop any bitmap left over from a package/icon-pack identity
            // this generation no longer produces (removed app, upgrade, icon-pack switch).
            // Guarded on a non-empty apps list: an empty snapshot only ever comes from a
            // transient/partial PackageManager scan (this launcher is always itself a
            // launcher target), never a real "every app was uninstalled" state, so it must
            // not be allowed to wipe every other app's still-valid cached icon.
            if (apps.isNotEmpty()) {
                com.mckimquyen.util.BitmapCache.retainKeys(apps.map { it.iconCacheKey }.toSet())
            }
            mApps = ArrayList(apps)
        }
    }

    /**
     * Cập nhật trạng thái in-memory của app trong singleton để đồng bộ với Database.
     * Tránh đọc lại DB hoặc re-query toàn bộ danh sách khi thay đổi nhỏ (visible, lock, openCount).
     */
    fun updateAppState(
        packageName: String,
        name: String,
        isOpened: Boolean? = null,
        isVisible: Boolean? = null,
        openCount: Long? = null,
        paletteColor: Int? = null
    ) {
        synchronized(this) {
            val list = mApps ?: return
            for (i in list.indices) {
                val app = list[i]
                if (app.packageName.toString() == packageName && app.name.toString() == name) {
                    list[i] = app.copyWithLockAndVisibility(
                        newOpened = isOpened ?: app.isOpened,
                        newVisible = isVisible ?: app.isVisible,
                        newOpenCount = openCount ?: app.openCount
                    ).copyWithPaletteColor(paletteColor ?: app.paletteColor)
                    break
                }
            }
        }
    }

    fun findApp(packageName: String, name: String): App? = synchronized(this) {
        mApps?.firstOrNull {
            it.packageName.toString() == packageName && it.name.toString() == name
        }
    }

    fun updateOrganization(
        packageName: String,
        name: String,
        favorite: Boolean,
        folder: String?,
        zone: com.mckimquyen.model.PinnedZone
    ) {
        synchronized(this) {
            val list = mApps ?: return
            val index = list.indexOfFirst {
                it.packageName.toString() == packageName && it.name.toString() == name
            }
            if (index >= 0) {
                list[index] = list[index].copyWithOrganization(favorite, folder, zone)
            }
        }
    }

    fun updateAppOrder(packageName: String, name: String, order: Int) {
        synchronized(this) {
            val list = mApps ?: return
            val index = list.indexOfFirst {
                it.packageName.toString() == packageName && it.name.toString() == name
            }
            if (index >= 0) list[index] = list[index].copyWithOrder(order)
        }
    }

    /**
     * Lấy icon của app từ cache
     * @param iconCacheKey App.iconCacheKey (CORE-002) — không dùng packageName trần
     * @return Bitmap icon hoặc null nếu không có trong cache
     */
    fun getAppIcon(iconCacheKey: String): Bitmap? {
        return com.mckimquyen.util.BitmapCache.get(iconCacheKey)
    }

    /**
     * Lưu icon của app vào cache
     * @param iconCacheKey App.iconCacheKey (CORE-002) — không dùng packageName trần
     * @param icon Bitmap icon cần cache
     */
    fun setAppIcon(iconCacheKey: String, icon: Bitmap) {
        com.mckimquyen.util.BitmapCache.put(iconCacheKey, icon)
    }

    /**
     * Xóa toàn bộ dữ liệu apps và icons
     * Nên gọi khi cần refresh toàn bộ dữ liệu hoặc memory low
     */
    fun clearAllData() {
        synchronized(this) {
            mApps = null
            com.mckimquyen.util.BitmapCache.clear()
        }
    }

    // ========================================================================
    // COMPANION OBJECT - Singleton Instance
    // ========================================================================

    companion object {
        /**
         * Thread-safe singleton instance với lazy initialization
         *
         * LAZY INITIALIZATION:
         * - Instance chỉ được tạo khi lần đầu tiên access
         * - Không tạo nếu app không cần (memory efficient)
         * - by lazy { } = Kotlin delegate property
         *
         * THREAD SAFETY:
         * - LazyThreadSafetyMode.SYNCHRONIZED = Double-checked locking
         * - Lock được acquire khi tạo instance
         * - Lock được release sau khi instance created
         * - Subsequent calls không cần lock → Fast
         *
         * JAVA INTEROP:
         * - @JvmStatic → Accessible từ Java code
         * - Java: RAppsSingleton.getInstance() hoặc RAppsSingleton.instance
         *
         * ALTERNATIVE MODES:
         * - SYNCHRONIZED: Thread-safe, có locking overhead (DEFAULT - đang dùng)
         * - PUBLICATION: Thread-safe, không có locking, nhưng có thể tạo multiple instances rồi discard
         * - NONE: Không thread-safe, nhanh nhất, chỉ dùng cho single-threaded
         *
         * WHY SYNCHRONIZED MODE:
         * - App này có nhiều threads (UI, background tasks, coroutines)
         * - Cần đảm bảo chỉ 1 instance được tạo
         * - Performance overhead không đáng kể (chỉ lần đầu tiên)
         *
         * MEMORY:
         * - Instance tồn tại cho đến khi app process bị kill
         * - Không bị clear khi Activity destroyed
         * - GC không thu hồi instance (có strong reference từ Companion object)
         */
        @JvmStatic
        val instance: RAppsSingleton by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            RAppsSingleton()
        }
    }

    // ========================================================================
    // FUTURE IMPROVEMENTS
    // ========================================================================
    //
    // 💡 SUGGESTIONS:
    //
    // 1. Bitmap Caching:
    //    - Implement LruCache<String, Bitmap> để cache icons
    //    - Giảm memory footprint
    //    - Faster access khi scroll
    //
    // 2. Coroutines Flow:
    //    - Replace Observable pattern với Kotlin Flow
    //    - Type-safe, null-safe, modern reactive programming
    //    - Better lifecycle-aware với StateFlow/SharedFlow
    //
    // 3. Thread-Safe List:
    //    - Use CopyOnWriteArrayList thay vì ArrayList
    //    - Thread-safe cho modifications
    //    - Trade-off: Slower writes, faster reads
    //
    // 4. Immutable Data:
    //    - Return List<App> thay vì ArrayList<App>?
    //    - Return copy (Collections.unmodifiableList) để prevent modifications
    //    - Trade-off: More memory, safer API
    //
    // 5. Memory Management:
    //    - Add clearCache() method để clear bitmaps khi memory low
    //    - Register ComponentCallbacks2.onTrimMemory() callback
    //    - Recycle bitmaps không còn sử dụng
    //
    // 6. Persistence:
    //    - Persist app settings (visibility, lock status) to database
    //    - Không persist bitmaps (quá lớn, load từ PackageManager nhanh hơn)
    //    - Cache app list để reduce startup time
    //
    // ========================================================================
}
