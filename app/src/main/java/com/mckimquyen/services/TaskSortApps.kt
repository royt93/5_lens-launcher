package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilAppSorter
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Task để sắp xếp lại danh sách ứng dụng.
 * Đã migrate từ AsyncTask sang Coroutines để tránh memory leak và deprecated API.
 * <p>
 * Fix: 1.1 - Migrate AsyncTask sang Coroutines
 * Fix: 4.1 - Sử dụng WeakReference để tránh Context leak
 * Fix: 5.1 - Migrate from GlobalScope to ApplicationScope (lifecycle-aware)
 */
class TaskSortApps(
    context: Context,
    application: Application
) {
    // Sử dụng WeakReference để tránh memory leak khi giữ reference đến Context/Application
    private val contextRef = WeakReference(context)
    private val applicationRef = WeakReference(application)

    // Danh sách apps kết quả sau khi sort
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
        // Thực hiện công việc sort trên background thread
        doInBackground()

        // Cập nhật kết quả trên main thread
        withContext(Dispatchers.Main) {
            onPostExecute()
        }
    }

    /**
     * Thực hiện sắp xếp apps trên background thread
     * Tương đương với doInBackground() của AsyncTask
     */
    private suspend fun doInBackground() = withContext(Dispatchers.IO) {
        val context = contextRef.get() ?: return@withContext
        val utilSettings = UtilSettings(context)

        // Lấy danh sách apps hiện tại từ Singleton
        val apps = RAppsSingleton.instance.apps ?: return@withContext

        // Sắp xếp apps theo sort type từ settings
        UtilAppSorter.sort(apps, utilSettings.sortType)

        // Lọc và lưu apps có icon hợp lệ sau khi sort, cache icon vào BitmapCache
        mApps = ArrayList()

        for (app in apps) {
            val appIcon = app.icon
            if (appIcon != null) {
                mApps?.add(app)
                // Đảm bảo icon được cache (dù thường đã được cache từ TaskUpdateApps)
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

        // Cập nhật Singleton với danh sách apps đã sort
        RAppsSingleton.instance.let { singleton ->
            singleton.apps = mApps
        }

        // Gửi broadcast thông báo apps đã được sort xong
        val appsLoadedIntent = Intent(application, BroadcastReceivers.AppsLoadedReceiver::class.java)
        application.sendBroadcast(appsLoadedIntent)
    }
}
