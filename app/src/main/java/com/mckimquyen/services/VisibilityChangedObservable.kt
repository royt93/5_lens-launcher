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
 * @deprecated Sử dụng AppEventManager.visibilityChanged thay thế
 */
class VisibilityChangedObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: VisibilityChangedObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            VisibilityChangedObservable()
        }
    }

    /**
     * Thông báo visibility đã thay đổi với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        AppEventManager.notifyVisibilityChanged(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo visibility đã thay đổi (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
