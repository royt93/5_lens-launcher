package com.mckimquyen.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Log
import androidx.core.graphics.scale

/**
 * LruCache để quản lý Bitmap icons của apps, tránh OutOfMemoryError
 *
 * Fix: 5.1 - Bitmap memory management với LruCache
 *
 * PERF-002: budget is derived from the device's standard (non-large) memory class and the
 * actual cached icon size, not from `Runtime.maxMemory()` - which reflects the manifest's
 * `largeHeap` flag and would otherwise let an inflated heap silently inflate the cache budget.
 * Call [init] once, before any [get]/[put], from Application.onCreate().
 */
object BitmapCache {

    private const val TAG = "BitmapCache"

    // Target size for launcher icons (192x192 is ideal for xxxhdpi screens)
    private const val TARGET_ICON_SIZE = 192
    private const val BYTES_PER_ICON_PIXEL = 4 // ARGB_8888
    private val ICON_SIZE_KB = (TARGET_ICON_SIZE * TARGET_ICON_SIZE * BYTES_PER_ICON_PIXEL) / 1024

    // Ceiling: never budget more than this many icons' worth of memory, regardless of how
    // large the device's memory class is - a launcher grid has no legitimate need for more.
    private const val MAX_CACHED_ICONS = 300
    private const val MIN_CACHE_SIZE_KB = 4 * 1024 // floor for very low memory-class devices
    private const val FALLBACK_STANDARD_HEAP_MB = 64 // used only if init() is never called

    @Volatile
    private var cacheSizeKb: Int = computeCacheSizeKb(FALLBACK_STANDARD_HEAP_MB)

    private fun computeCacheSizeKb(standardMemoryClassMb: Int): Int {
        val heapBudgetKb = (standardMemoryClassMb * 1024) / 8
        val iconBudgetKb = ICON_SIZE_KB * MAX_CACHED_ICONS
        return heapBudgetKb.coerceAtMost(iconBudgetKb).coerceAtLeast(MIN_CACHE_SIZE_KB)
    }

    /**
     * Must be called once, before any [get]/[put], typically from Application.onCreate().
     * Sizes the cache from the device's standard memory class - unaffected by the app's
     * `largeHeap` manifest flag - so the budget reflects the real device, not an inflated heap.
     */
    fun init(context: Context) {
        val activityManager = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val standardMemoryClassMb = activityManager?.memoryClass ?: FALLBACK_STANDARD_HEAP_MB
        cacheSizeKb = computeCacheSizeKb(standardMemoryClassMb)
        // LruCache's maxSize is fixed at construction; resize() is required to actually apply
        // a new budget (reassigning cacheSizeKb alone would not affect the already-built cache).
        cache.resize(cacheSizeKb)
        Log.d(TAG, "Initialized: memoryClass=${standardMemoryClassMb}MB, cacheSize=${cacheSizeKb}KB")
    }

    /**
     * LruCache lưu trữ bitmap với key là package name
     * Size tính bằng KB
     */
    private data class CachedBitmap(
        val bitmap: Bitmap,
        val sizeKb: Int
    )

    private val cache = object : LruCache<String, CachedBitmap>(cacheSizeKb) {

        /**
         * Tính size của bitmap trong cache (đơn vị KB)
         */
        override fun sizeOf(key: String, cachedBitmap: CachedBitmap): Int {
            return cachedBitmap.sizeKb
        }

        /**
         * Fix BUG-06: KHÔNG recycle bitmap thủ công khi bị remove khỏi cache.
         * LẬP LUẬN:
         * - LruCache evict có thể xảy ra đồng thời khi UI thread đang vẽ bitmap đó
         * - Nếu recycle, LensView.drawAppIcon() sẽ gặp "bitmap is recycled" → visual glitch
         * - An toàn hơn: để GC tự thu hồi sau khi không còn reference nào giữ bitmap
         *
         * Trade-off: memory không được giải phóng ngay lập tức, nhưng tránh được crash/glitch.
         */
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedBitmap,
            newValue: CachedBitmap?
        ) {
            Log.d(TAG, "Bitmap evicted from cache for key: $key, evicted=$evicted")
            // Do not call oldValue.recycle() - let GC handle it to avoid race conditions
        }
    }

    /**
     * Lấy bitmap từ cache
     * @param key Package name của app
     * @return Bitmap hoặc null nếu không có trong cache
     */
    fun get(key: String): Bitmap? {
        return try {
            val cached = cache.get(key) ?: return null
            if (cached.bitmap.isRecycled) {
                cache.remove(key)
                null
            } else {
                cached.bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bitmap for key: $key", e)
            null
        }
    }

    /**
     * Lưu bitmap vào cache. Tự động resize nếu kích thước lớn hơn TARGET_ICON_SIZE.
     * @param key Package name của app
     * @param bitmap Icon bitmap cần cache
     */
    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        try {
            if (get(key) == null) {
                val originalWidth = bitmap.width
                val originalHeight = bitmap.height
                val originalSizeKb = bitmap.byteCount / 1024
                val optimizedBitmap = if (bitmap.width > TARGET_ICON_SIZE || bitmap.height > TARGET_ICON_SIZE) {
                    bitmap.scale(TARGET_ICON_SIZE, TARGET_ICON_SIZE)
                } else {
                    bitmap
                }
                val optimizedSizeKb = optimizedBitmap.byteCount / 1024
                cache.put(key, CachedBitmap(optimizedBitmap, optimizedSizeKb))
                Log.d(TAG, "Cached bitmap for key: $key, size: ${optimizedSizeKb}KB (Original: ${originalWidth}x${originalHeight}, ${originalSizeKb}KB)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error putting bitmap for key: $key", e)
        }
    }

    /**
     * Builds a cache key that identifies one renderable icon uniquely.
     *
     * CORE-002: the old key was package name only, so two activities in the same
     * package collided, and an icon-pack switch or package upgrade kept showing the
     * stale bitmap cached under that package name. Folding component identity, the
     * package's version/update token, and the active icon pack's identity/version
     * into the key means a real content change always produces a new key instead of
     * colliding with — or refusing to replace — an unrelated cached bitmap.
     */
    fun buildKey(packageName: String, componentName: String, versionToken: String, iconPackToken: String): String {
        return "$packageName/$componentName#$versionToken#$iconPackToken"
    }

    /**
     * Evicts every cached entry whose key is not in [validKeys].
     *
     * Called after each full app-list commit so packages that were removed, updated,
     * or re-keyed by an icon-pack change stop holding bitmaps no current app can reach.
     */
    fun retainKeys(validKeys: Set<String>) {
        try {
            val staleKeys = cache.snapshot().keys.filterNot { it in validKeys }
            staleKeys.forEach { cache.remove(it) }
            if (staleKeys.isNotEmpty()) {
                Log.d(TAG, "Evicted ${staleKeys.size} stale icon cache entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retaining keys", e)
        }
    }

    /**
     * PERF-002: evicts least-recently-used entries down to [fraction] of the cache's current
     * size, for moderate memory pressure where a full [clear] would be overkill (and would
     * force every visible icon to reload). `LruCache.trimToSize` only ever evicts, so calling
     * this with `fraction >= 1f` is a safe no-op rather than growing the cache.
     */
    fun trimToFraction(fraction: Float) {
        try {
            val targetSizeKb = (cache.size() * fraction.coerceIn(0f, 1f)).toInt()
            cache.trimToSize(targetSizeKb)
            Log.d(TAG, "Trimmed cache to ${(fraction * 100).toInt()}% (${cache.size()}KB / ${cache.maxSize()}KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming cache", e)
        }
    }

    /**
     * Xóa toàn bộ cache và recycle tất cả bitmaps
     * Nên gọi khi cần giải phóng memory (ví dụ: onLowMemory)
     */
    fun clear() {
        try {
            cache.evictAll()
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    /** Current configured cache budget in KB (see [init]). */
    fun maxSizeKb(): Int = cache.maxSize()

    /** Current cache occupancy in KB. */
    fun sizeKb(): Int = cache.size()

    /**
     * Lấy thông tin về cache để debug
     */
    fun getCacheInfo(): String {
        return "Cache size: ${cache.size()}KB / ${cache.maxSize()}KB, " +
                "Hit count: ${cache.hitCount()}, Miss count: ${cache.missCount()}"
    }
}
