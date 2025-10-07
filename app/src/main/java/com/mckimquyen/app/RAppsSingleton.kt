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
 * 2. TaskUpdateApps.onPostExecute() → Save to RAppsSingleton
 * 3. Observable.notifyObservers() → Notify Fragments
 * 4. FrmLens/FrmApps → Read from RAppsSingleton → Update UI
 *
 * THREAD SAFETY:
 * - Singleton instance: LazyThreadSafetyMode.SYNCHRONIZED
 *   → Chỉ 1 instance được tạo dù có nhiều threads cùng access
 * - Getters/Setters: Thread-safe vì ArrayList là reference type
 *   → Assignments (=) là atomic operations trong JVM
 * - List modifications: KHÔNG thread-safe, cần synchronize nếu modify từ nhiều threads
 *
 * MEMORY OPTIMIZATION:
 * Fix 2.1 - Không copy ArrayList mỗi lần get():
 *   - OLD: return ArrayList(mApps) → Tốn memory, tốn CPU
 *   - NEW: return mApps ?: ArrayList() → Trả về reference, nhanh hơn
 *   - Trade-off: Caller có thể modify list → Cần careful khi sử dụng
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
 * - List modifications không thread-safe: Cần synchronize manually
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

    /**
     * Danh sách bitmap icons tương ứng với mApps
     * Index phải match với mApps: mAppIcons[i] là icon của mApps[i]
     * null = chưa load, ArrayList() = đã load nhưng rỗng
     */
    private var mAppIcons: ArrayList<Bitmap>? = null

    // ========================================================================
    // PUBLIC PROPERTIES - Thread-Safe Access
    // ========================================================================

    /**
     * Public property để get/set danh sách apps
     *
     * GETTER:
     * - Trả về reference của mApps (không copy) → Fast, memory efficient
     * - Nếu null → trả về empty ArrayList để tránh NullPointerException
     * - Caller có thể modify list → Careful!
     *
     * SETTER:
     * - Thay thế toàn bộ list bằng list mới
     * - Assignment là atomic operation → Thread-safe
     * - Old list sẽ được GC collect nếu không còn reference
     *
     * THREAD SAFETY:
     * - Read/Write: Thread-safe (atomic reference assignment)
     * - Modify elements: KHÔNG thread-safe (cần synchronize)
     *
     * EXAMPLE:
     * ```kotlin
     * // Safe read
     * val apps = singleton.apps
     * apps?.forEach { app -> println(app.label) }
     *
     * // Safe write (replace entire list)
     * singleton.apps = newAppsList
     *
     * // UNSAFE modification (cần synchronize)
     * singleton.apps?.add(newApp)  // ⚠️ Not thread-safe!
     *
     * // Safe modification (synchronized)
     * synchronized(singleton) {
     *     singleton.apps?.add(newApp)  // ✅ Thread-safe
     * }
     * ```
     */
    var apps: ArrayList<App>?
        get() {
            // Return mApps hoặc empty list nếu null
            // Elvis operator (?:) để handle null case
            return mApps ?: ArrayList()
        }
        set(apps) {
            // Replace toàn bộ list
            mApps = apps
        }

    /**
     * Public property để get/set danh sách bitmap icons
     *
     * GETTER:
     * - Trả về reference của mAppIcons (không copy) → Fast, memory efficient
     * - Nếu null → trả về empty ArrayList để tránh NullPointerException
     * - Caller có thể modify list → Careful!
     *
     * SETTER:
     * - Thay thế toàn bộ list bằng list mới
     * - Assignment là atomic operation → Thread-safe
     * - Old bitmaps sẽ được GC collect nếu không còn reference
     *
     * MEMORY WARNING:
     * - Bitmaps rất tốn memory (mỗi icon ~100KB-1MB)
     * - 100 apps × 500KB/icon = 50MB RAM
     * - Nên implement BitmapCache với LruCache để giảm memory
     *
     * THREAD SAFETY:
     * - Read/Write: Thread-safe (atomic reference assignment)
     * - Modify elements: KHÔNG thread-safe (cần synchronize)
     *
     * EXAMPLE:
     * ```kotlin
     * // Safe read
     * val icons = singleton.appIcons
     * val firstIcon = icons?.getOrNull(0)
     *
     * // Safe write (replace entire list)
     * singleton.appIcons = newIconsList
     *
     * // UNSAFE modification
     * singleton.appIcons?.add(newBitmap)  // ⚠️ Not thread-safe!
     *
     * // Safe modification (synchronized)
     * synchronized(singleton) {
     *     singleton.appIcons?.add(newBitmap)  // ✅ Thread-safe
     * }
     * ```
     */
    var appIcons: ArrayList<Bitmap>?
        get() {
            // Return mAppIcons hoặc empty list nếu null
            // Elvis operator (?:) để handle null case
            return mAppIcons ?: ArrayList()
        }
        set(appIcons) {
            // Replace toàn bộ list
            mAppIcons = appIcons
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
