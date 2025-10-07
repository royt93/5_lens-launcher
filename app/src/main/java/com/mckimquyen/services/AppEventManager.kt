package com.mckimquyen.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Quản lý các sự kiện trong app sử dụng LiveData thay cho Observable/Observer deprecated
 *
 * Fix: 1.2 - Migrate từ Observable/Observer sang LiveData
 * Fix: 3.3 - Thread-safe với Singleton pattern đúng cách
 *
 * Các sự kiện được quản lý:
 * - appsLoaded: Khi danh sách apps được load xong
 * - appsUpdated: Khi danh sách apps được cập nhật
 * - appsEdited: Khi một app được chỉnh sửa
 * - backgroundChanged: Khi background thay đổi
 * - visibilityChanged: Khi visibility của app thay đổi
 * - lockChanged: Khi lock status thay đổi
 * - nightModeChanged: Khi night mode thay đổi
 */
object AppEventManager {

    // LiveData cho sự kiện apps loaded
    private val _appsLoaded = MutableLiveData<Any?>()
    val appsLoaded: LiveData<Any?> = _appsLoaded

    // LiveData cho sự kiện apps updated
    private val _appsUpdated = MutableLiveData<Any?>()
    val appsUpdated: LiveData<Any?> = _appsUpdated

    // LiveData cho sự kiện apps edited
    private val _appsEdited = MutableLiveData<Any?>()
    val appsEdited: LiveData<Any?> = _appsEdited

    // LiveData cho sự kiện background changed
    private val _backgroundChanged = MutableLiveData<Any?>()
    val backgroundChanged: LiveData<Any?> = _backgroundChanged

    // LiveData cho sự kiện visibility changed
    private val _visibilityChanged = MutableLiveData<Any?>()
    val visibilityChanged: LiveData<Any?> = _visibilityChanged

    // LiveData cho sự kiện lock changed
    private val _lockChanged = MutableLiveData<Any?>()
    val lockChanged: LiveData<Any?> = _lockChanged

    // LiveData cho sự kiện night mode changed
    private val _nightModeChanged = MutableLiveData<Any?>()
    val nightModeChanged: LiveData<Any?> = _nightModeChanged

    /**
     * Thông báo sự kiện apps đã được load
     */
    fun notifyAppsLoaded(data: Any? = null) {
        _appsLoaded.postValue(data)
    }

    /**
     * Thông báo sự kiện apps đã được update
     */
    fun notifyAppsUpdated(data: Any? = null) {
        _appsUpdated.postValue(data)
    }

    /**
     * Thông báo sự kiện apps đã được edit
     */
    fun notifyAppsEdited(data: Any? = null) {
        _appsEdited.postValue(data)
    }

    /**
     * Thông báo sự kiện background đã thay đổi
     */
    fun notifyBackgroundChanged(data: Any? = null) {
        _backgroundChanged.postValue(data)
    }

    /**
     * Thông báo sự kiện visibility đã thay đổi
     */
    fun notifyVisibilityChanged(data: Any? = null) {
        _visibilityChanged.postValue(data)
    }

    /**
     * Thông báo sự kiện lock status đã thay đổi
     */
    fun notifyLockChanged(data: Any? = null) {
        _lockChanged.postValue(data)
    }

    /**
     * Thông báo sự kiện night mode đã thay đổi
     */
    fun notifyNightModeChanged(data: Any? = null) {
        _nightModeChanged.postValue(data)
    }
}
