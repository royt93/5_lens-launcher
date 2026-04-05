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
 * @deprecated Sử dụng AppEventManager.appsEdited thay thế
 */
class EditedObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: EditedObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            EditedObservable()
        }
    }

    /**
     * Thông báo app đã được edit với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        // Fix BUG-13: chỉ delegate sang AppEventManager (LiveData).
        AppEventManager.notifyAppsEdited(data)
    }

    /**
     * Thông báo app đã được edit (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
