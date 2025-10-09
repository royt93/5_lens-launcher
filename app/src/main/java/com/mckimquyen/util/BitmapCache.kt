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
    private val cache = object : LruCache<String, Bitmap>(cacheSize) {

        /**
         * Tính size của bitmap trong cache (đơn vị KB)
         */
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        /**
         * Tự động recycle bitmap khi bị remove khỏi cache để giải phóng memory
         */
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Chỉ recycle khi bitmap bị evict (không phải khi update)
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                try {
                    oldValue.recycle()
                    Log.d(TAG, "Recycled bitmap for key: $key")
                } catch (e: Exception) {
                    Log.e(TAG, "Error recycling bitmap for key: $key", e)
                }
            }
        }
    }

    /**
     * Lấy bitmap từ cache
     * @param key Package name của app
     * @return Bitmap hoặc null nếu không có trong cache
     */
    fun get(key: String): Bitmap? {
        return try {
            cache.get(key)?.takeIf { !it.isRecycled }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bitmap for key: $key", e)
            null
        }
    }

    /**
     * Lưu bitmap vào cache
     * @param key Package name của app
     * @param bitmap Icon bitmap cần cache
     */
    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        try {
            if (get(key) == null) {
                cache.put(key, bitmap)
                Log.d(TAG, "Cached bitmap for key: $key, size: ${bitmap.byteCount / 1024}KB")
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
