package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ
 * Migrate từ java.util.Observable sang LiveData
 *
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 *
 * @deprecated Sử dụng AppEventManager.appsEdited thay thế
 */
@Deprecated("Use AppEventManager.appsEdited instead")
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
        AppEventManager.notifyAppsEdited(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo app đã được edit (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
