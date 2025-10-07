package com.mckimquyen.services

import java.util.Observable

/**
 * Wrapper class để maintain backward compatibility với code cũ
 * Migrate từ java.util.Observable sang LiveData
 *
 * Fix: 1.2 - Observable/Observer deprecated
 * Fix: 3.3 - Thread-safe singleton
 *
 * @deprecated Sử dụng AppEventManager.lockChanged thay thế
 */
@Deprecated("Use AppEventManager.lockChanged instead")
class LockChangedObservable private constructor() : Observable() {

    companion object {
        // Thread-safe singleton pattern với lazy initialization
        @JvmStatic
        val instance: LockChangedObservable by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            LockChangedObservable()
        }
    }

    /**
     * Thông báo lock status đã thay đổi với data
     * Delegate sang AppEventManager
     */
    @Synchronized
    fun updateValue(data: Any?) {
        AppEventManager.notifyLockChanged(data)
        setChanged()
        notifyObservers(data)
    }

    /**
     * Thông báo lock status đã thay đổi (không có data)
     * Delegate sang AppEventManager
     */
    fun update() {
        updateValue(null)
    }
}
