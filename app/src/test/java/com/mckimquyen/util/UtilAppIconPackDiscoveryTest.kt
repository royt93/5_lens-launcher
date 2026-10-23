package com.mckimquyen.util

import com.mckimquyen.app.RApplication
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import com.mckimquyen.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Gọi public reload thật; chỉ thay PackageManager để đếm discovery không cần cài icon pack. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilAppIconPackDiscoveryTest {
    @Test
    fun repeatedSingleIconReloadsDiscoverPacksOnlyOnce() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val packageManager = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        val application = object : RApplication() {
            override fun getApplicationContext() = context
            override fun getPackageManager() = packageManager
            override fun getResources() = resources
        }
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val icon = BitmapDrawable(context.resources, bitmap)
        val info = ApplicationInfo().apply { packageName = TARGET_PACKAGE }
        var discoveryQueries = 0

        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(LAUNCHER_ACTIONS)
        Mockito.`when`(packageManager.getApplicationInfo(anyString(), anyInt())).thenReturn(info)
        Mockito.`when`(packageManager.getResourcesForApplication(info)).thenReturn(context.resources)
        Mockito.`when`(packageManager.getApplicationIcon(anyString())).thenReturn(icon)
        Mockito.`when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer {
            discoveryQueries++
            emptyList<android.content.pm.ResolveInfo>()
        }

        try {
            repeat(RELOAD_COUNT) {
                val result = UtilApp.loadSingleAppIcon(application, TARGET_PACKAGE, NO_ICON_RESOURCE)
                assertNotNull("mỗi reload phải qua default icon decode rồi mới discovery", result)
            }
            assertEquals(
                "năm reload chỉ được discovery một batch launcher actions",
                LAUNCHER_ACTIONS.size,
                discoveryQueries
            )
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.perf006"
        const val RELOAD_COUNT = 5
        const val ICON_SIZE = 16
        const val NO_ICON_RESOURCE = 0
        val LAUNCHER_ACTIONS = arrayOf("com.example.iconpack.FIRST", "com.example.iconpack.SECOND")
    }
}
