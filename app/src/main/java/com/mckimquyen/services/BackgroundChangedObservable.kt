@file:Suppress("DEPRECATION") // Suppress java.util.Observable deprecation for entire file

package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ.
 * Migrate từ java.util.Observable sang LiveData.
 * <p>
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 * <p>
 * Note: java.util.Observable deprecated từ Java 9, nhưng class này vẫn giữ để
 * backward compatibility. Tất cả calls đều delegate sang AppEventManager (LiveData).
 *
 * @deprecated Sử dụng AppEventManager.backgroundChanged thay thế
 */
class BackgroundChangedObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: BackgroundChangedObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            BackgroundChangedObservable()
        }
    }

    /**
     * Thông báo background đã thay đổi với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        // Fix BUG-13: chỉ delegate sang AppEventManager (LiveData).
        AppEventManager.notifyBackgroundChanged(data)
    }

    /**
     * Thông báo background đã thay đổi (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
