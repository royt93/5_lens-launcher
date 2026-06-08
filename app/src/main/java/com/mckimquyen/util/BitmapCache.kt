package com.mckimquyen.util

import android.graphics.Bitmap
import android.util.LruCache
import android.util.Log

/**
 * LruCache để quản lý Bitmap icons của apps, tránh OutOfMemoryError
 *
 * Fix: 5.1 - Bitmap memory management với LruCache
 *
 * Cache sử dụng 1/8 heap memory, tự động recycle bitmap khi bị evict
 */
object BitmapCache {

    private const val TAG = "BitmapCache"

    // Tính toán max memory cho cache (1/8 heap size)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    /**
     * LruCache lưu trữ bitmap với key là package name
     * Size tính bằng KB
     */
    private data class CachedBitmap(
        val bitmap: Bitmap,
        val sizeKb: Int
    )

    private val cache = object : LruCache<String, CachedBitmap>(cacheSize) {

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

    // Target size for launcher icons (192x192 is ideal for xxxhdpi screens)
    private const val TARGET_ICON_SIZE = 192

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
                    Bitmap.createScaledBitmap(bitmap, TARGET_ICON_SIZE, TARGET_ICON_SIZE, true)
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

    /**
     * Lấy thông tin về cache để debug
     */
    fun getCacheInfo(): String {
        return "Cache size: ${cache.size()}KB / ${cache.maxSize()}KB, " +
                "Hit count: ${cache.hitCount()}, Miss count: ${cache.missCount()}"
    }
}
