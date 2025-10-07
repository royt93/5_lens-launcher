package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Task để cập nhật danh sách ứng dụng
 * Đã migrate từ AsyncTask sang Coroutines để tránh memory leak và deprecated API
 *
 * Fix: 1.1 - Migrate AsyncTask sang Coroutines
 * Fix: 4.1 - Sử dụng WeakReference để tránh Context leak
 */
class TaskUpdateApps(
    private val packageManager: PackageManager,
    context: Context,
    application: Application
) {
    // Sử dụng WeakReference để tránh memory leak khi giữ reference đến Context/Application
    private val contextRef = WeakReference(context)
    private val applicationRef = WeakReference(application)

    // Danh sách apps và icons kết quả
    private var mApps: ArrayList<App>? = null
    private var mAppIcons: ArrayList<Bitmap>? = null

    /**
     * Hàm chính để thực thi task (for Java compatibility)
     * Non-blocking, returns immediately
     */
    @Suppress("DEPRECATION")
    fun execute() {
        GlobalScope.launch {
            executeAsync()
        }
    }

    /**
     * Suspend version of execute for Kotlin coroutines
     */
    suspend fun executeAsync() {
        // Thực hiện công việc nặng trên background thread
        doInBackground()

        // Cập nhật kết quả trên main thread
        withContext(Dispatchers.Main) {
            onPostExecute()
        }
    }

    /**
     * Thực hiện load danh sách apps trên background thread
     * Tương đương với doInBackground() của AsyncTask
     */
    private suspend fun doInBackground() = withContext(Dispatchers.IO) {
        val context = contextRef.get() ?: return@withContext
        val utilSettings = UtilSettings(context)

        // Load danh sách apps từ PackageManager
        val apps = UtilApp.getApps(
            packageManager,
            context,
            applicationRef.get() ?: return@withContext,
            utilSettings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME) ?: "",
            utilSettings.sortType
        )

        // Lọc và lưu apps có icon hợp lệ
        mApps = ArrayList()
        mAppIcons = ArrayList()

        for (app in apps) {
            val appIcon = app.icon
            if (appIcon != null) {
                mApps?.add(app)
                mAppIcons?.add(appIcon)
            }
        }
    }

    /**
     * Cập nhật kết quả vào Singleton và gửi broadcast
     * Tương đương với onPostExecute() của AsyncTask
     * Chạy trên Main thread
     */
    private fun onPostExecute() {
        val application = applicationRef.get() ?: return

        // Cập nhật Singleton với danh sách apps mới
        RAppsSingleton.instance?.let { singleton ->
            singleton.apps = mApps
            singleton.appIcons = mAppIcons
        }

        // Gửi broadcast thông báo apps đã load xong
        val appsLoadedIntent = Intent(application, BroadcastReceivers.AppsLoadedReceiver::class.java)
        application.sendBroadcast(appsLoadedIntent)
    }
}
