package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ
 * Migrate từ java.util.Observable sang LiveData
 *
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 *
 * @deprecated Sử dụng AppEventManager.backgroundChanged thay thế
 */
@Deprecated("Use AppEventManager.backgroundChanged instead")
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
        AppEventManager.notifyBackgroundChanged(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo background đã thay đổi (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
