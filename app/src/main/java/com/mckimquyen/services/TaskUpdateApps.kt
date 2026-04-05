package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Task để cập nhật danh sách ứng dụng.
 * Đã migrate từ AsyncTask sang Coroutines để tránh memory leak và deprecated API.
 * <p>
 * Fix: 1.1 - Migrate AsyncTask sang Coroutines
 * Fix: 4.1 - Sử dụng WeakReference để tránh Context leak
 * Fix: 5.1 - Migrate from GlobalScope to ApplicationScope (lifecycle-aware)
 */
class TaskUpdateApps(
    private val packageManager: PackageManager,
    context: Context,
    application: Application
) {
    // Sử dụng WeakReference để tránh memory leak khi giữ reference đến Context/Application
    private val contextRef = WeakReference(context)
    private val applicationRef = WeakReference(application)

    // Danh sách apps kết quả
    private var mApps: ArrayList<App>? = null

    /**
     * Hàm chính để thực thi task (for Java compatibility).
     * Non-blocking, returns immediately.
     * <p>
     * Uses ApplicationScope instead of GlobalScope for proper lifecycle management.
     * ApplicationScope is tied to the Application lifecycle and automatically cleaned up
     * when the process is killed.
     */
    fun execute() {
        ApplicationScope.scope.launch {
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

        // Lọc và lưu apps có icon hợp lệ, cache icon vào BitmapCache
        mApps = ArrayList()

        for (app in apps) {
            val appIcon = app.icon
            if (appIcon != null) {
                // Fix BUG-07: Lưu App với icon = null để tránh dual-storage (bitmap 2 lần).
                // Icon đã được cache vào BitmapCache, giữ thêm trong App.icon là dư thừa.
                mApps?.add(app.copy(icon = null))
                RAppsSingleton.instance.setAppIcon(app.packageName.toString(), appIcon)
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
        RAppsSingleton.instance.let { singleton ->
            singleton.apps = mApps
        }

        // Gửi broadcast thông báo apps đã load xong
        val appsLoadedIntent = Intent(application, BroadcastReceivers.AppsLoadedReceiver::class.java)
        application.sendBroadcast(appsLoadedIntent)
    }
}
