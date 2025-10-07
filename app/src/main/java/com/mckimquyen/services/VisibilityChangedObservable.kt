package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ
 * Migrate từ java.util.Observable sang LiveData
 *
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 *
 * @deprecated Sử dụng AppEventManager.visibilityChanged thay thế
 */
@Deprecated("Use AppEventManager.visibilityChanged instead")
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
