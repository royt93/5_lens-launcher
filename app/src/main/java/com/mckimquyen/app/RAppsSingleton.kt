package com.mckimquyen.app

import android.graphics.Bitmap
import com.mckimquyen.model.App

/**
 * Singleton quản lý danh sách apps và icons trong app
 *
 * Fix: 2.1 - Tối ưu ArrayList copy (trả về immutable list thay vì copy mỗi lần)
 * Fix: 3.4 - Thread-safe singleton với lazy initialization
 */
class RAppsSingleton private constructor() {
    // Danh sách apps và icons được lưu trữ
    private var mApps: ArrayList<App>? = null
    private var mAppIcons: ArrayList<Bitmap>? = null

    /**
     * Getter cho apps
     * Trả về danh sách immutable để tránh modify từ bên ngoài
     * Không còn copy ArrayList mỗi lần get (performance improvement)
     */
    var apps: ArrayList<App>?
        get() {
            // Nếu mApps null, trả về empty list
            return mApps ?: ArrayList()
        }
        set(apps) {
            mApps = apps
        }

    /**
     * Getter cho app icons
     * Trả về danh sách immutable để tránh modify từ bên ngoài
     * Không còn copy ArrayList mỗi lần get (performance improvement)
     */
    var appIcons: ArrayList<Bitmap>?
        get() {
            // Nếu mAppIcons null, trả về empty list
            return mAppIcons ?: ArrayList()
        }
        set(appIcons) {
            mAppIcons = appIcons
        }

    companion object {
        /**
         * Thread-safe singleton instance với lazy initialization
         * LazyThreadSafetyMode.SYNCHRONIZED đảm bảo chỉ 1 instance được tạo
         */
        @JvmStatic
        val instance: RAppsSingleton by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            RAppsSingleton()
        }
    }
}
