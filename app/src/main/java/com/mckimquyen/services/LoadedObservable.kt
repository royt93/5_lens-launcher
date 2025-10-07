package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ
 * Migrate từ java.util.Observable sang LiveData
 *
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 *
 * @deprecated Sử dụng AppEventManager.appsLoaded thay thế
 */
@Deprecated("Use AppEventManager.appsLoaded instead")
class LoadedObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: LoadedObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            LoadedObservable()
        }
    }

    /**
     * Thông báo apps đã được load với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        AppEventManager.notifyAppsLoaded(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo apps đã được load (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
