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
 * @deprecated Sử dụng AppEventManager.nightModeChanged thay thế
 */
class NightModeObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: NightModeObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            NightModeObservable()
        }
    }

    /**
     * Thông báo night mode đã thay đổi với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        AppEventManager.notifyNightModeChanged(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo night mode đã thay đổi (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
