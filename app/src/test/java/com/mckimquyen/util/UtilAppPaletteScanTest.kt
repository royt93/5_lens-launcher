package com.mckimquyen.util

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.room.Room
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import java.io.IOException
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilAppPaletteScanTest {
    private var database: AppDatabase? = null
    private val db get() = requireNotNull(database)
    private val statements = Collections.synchronizedList(mutableListOf<String>())
    private var dummyBitmap: Bitmap? = null

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        statements.clear()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ -> statements.add(sql) }, Executor { it.run() })
            .build()
        setDatabaseInstance(database)
        dummyBitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        dummyBitmap?.recycle()
        dummyBitmap = null
        database?.close()
        database = null
        resetAppDatabaseInstance()
    }

    @Test
    fun scanReadsPaletteCacheOnceInsteadOfQueryingPerApp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dao = db.appPersistentDao()
        dao.insert(AppPersistent.defaults(PKG_A, COMPONENT).copy(paletteColor = COLOR_A))
        dao.insert(AppPersistent.defaults(PKG_B, COMPONENT, SECOND_LENS).copy(paletteColor = COLOR_B))
        dao.insert(AppPersistent.defaults(PKG_C, COMPONENT).copy(paletteColor = COLOR_C))

        val pm = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        val app = Mockito.mock(Application::class.java)
        Mockito.`when`(app.applicationContext).thenReturn(context)
        Mockito.`when`(app.packageManager).thenReturn(pm)
        Mockito.`when`(app.resources).thenReturn(resources)
        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(emptyArray())

        val packages = listOf(PKG_A, PKG_B, PKG_C)
        val activities = packages.map { pkg -> launcherActivity(pkg) }

        Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer { call ->
            val intent = call.getArgument<Intent>(0)
            if (intent.action == Intent.ACTION_MAIN) activities else emptyList<ResolveInfo>()
        }
        packages.forEach { pkg ->
            val ai = ApplicationInfo().apply {
                packageName = pkg
                icon = 1
            }
            val pi = PackageInfo().apply {
                packageName = pkg
                firstInstallTime = 1000L
                lastUpdateTime = 2000L
            }
            Mockito.`when`(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).thenReturn(ai)
            Mockito.`when`(pm.getPackageInfo(pkg, 0)).thenReturn(pi)
            Mockito.`when`(pm.getResourcesForApplication(ai)).thenReturn(context.resources)
        }
        val drawable = BitmapDrawable(context.resources, dummyBitmap)
        Mockito.`when`(pm.getApplicationIcon(anyString())).thenReturn(drawable)

        // Xóa log SQL từ phần seed DB; chỉ đếm câu lệnh trong getAppShells
        statements.clear()

        val shells = UtilApp.getAppShells(pm, context, app, UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME)

        assertEquals(3, shells.size)
        assertEquals(COLOR_A, shells.single { it.packageName == PKG_A }.paletteColor)
        assertEquals("palette từ lens phụ phải được giữ", COLOR_B, shells.single { it.packageName == PKG_B }.paletteColor)
        assertEquals(COLOR_C, shells.single { it.packageName == PKG_C }.paletteColor)

        val persistentReads = statements.filter { sql ->
            sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
                sql.contains("APP_PERSISTENT", ignoreCase = true)
        }
        // Baseline: 3 lần findAnyByIdentifier (WHERE IDENTIFIER = ?). Mong đợi: đúng 1 lần đọc cache.
        assertEquals("toàn bộ scan chỉ được đọc cache Room một lần", 1, persistentReads.size)
        assertTrue(
            "không được query từng identifier bằng findAnyByIdentifier trong vòng lặp",
            persistentReads.none { it.contains("WHERE IDENTIFIER =", ignoreCase = true) }
        )
    }

    @Test
    fun scanPersistsNewlyComputedPalettesInSingleBatch() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val pm = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        val app = Mockito.mock(Application::class.java)
        Mockito.`when`(app.applicationContext).thenReturn(context)
        Mockito.`when`(app.packageManager).thenReturn(pm)
        Mockito.`when`(app.resources).thenReturn(resources)
        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(emptyArray())

        val packages = listOf(PKG_A, PKG_B)
        val activities = packages.map { pkg -> launcherActivity(pkg) }
        Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer { call ->
            val intent = call.getArgument<Intent>(0)
            if (intent.action == Intent.ACTION_MAIN) activities else emptyList<ResolveInfo>()
        }
        packages.forEach { pkg ->
            val ai = ApplicationInfo().apply { packageName = pkg; icon = 1 }
            val pi = PackageInfo().apply { packageName = pkg; firstInstallTime = 1L; lastUpdateTime = 1L }
            Mockito.`when`(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).thenReturn(ai)
            Mockito.`when`(pm.getPackageInfo(pkg, 0)).thenReturn(pi)
            Mockito.`when`(pm.getResourcesForApplication(ai)).thenReturn(context.resources)
        }
        val drawable = BitmapDrawable(context.resources, dummyBitmap)
        Mockito.`when`(pm.getApplicationIcon(anyString())).thenReturn(drawable)

        statements.clear()
        val shells = UtilApp.getAppShells(pm, context, app, UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME)

        assertEquals(2, shells.size)
        shells.forEach { shell ->
            assertTrue("mỗi app mới phải có paletteColor được tính từ icon", shell.paletteColor != 0)
        }

        // Cold scan: đúng 1 SELECT bảng chính; mỗi app mới 1 INSERT OR IGNORE + 1 UPDATE palette.
        // Room còn phát câu phụ (SELECT CHANGES/LAST_INSERT_ROWID) và log BEGIN lồng nhau, nên
        // không dùng số BEGIN làm bằng chứng transaction; rollback thật đã kiểm ở Room test.
        val scanStatements = statements.map { it.trimStart().uppercase() }
        assertEquals(1, scanStatements.count { it.startsWith("SELECT * FROM APP_PERSISTENT") })
        assertEquals(shells.size, scanStatements.count { it.startsWith("INSERT OR IGNORE INTO `APP_PERSISTENT`") })
        assertEquals(shells.size, scanStatements.count { it.startsWith("UPDATE APP_PERSISTENT SET PALETTE_COLOR") })

        // Palette mới phải được ghi vào DB
        val dao = db.appPersistentDao()
        val storedA = dao.findByIdentifier(AppPersistent.generateIdentifier(PKG_A, COMPONENT))
        val storedB = dao.findByIdentifier(AppPersistent.generateIdentifier(PKG_B, COMPONENT))
        assertNotNull("PKG_A phải được lưu", storedA)
        assertNotNull("PKG_B phải được lưu", storedB)
        assertEquals(shells.single { it.packageName == PKG_A }.paletteColor, storedA?.paletteColor)
        assertEquals(shells.single { it.packageName == PKG_B }.paletteColor, storedB?.paletteColor)
    }

    private fun launcherActivity(pkg: String): ResolveInfo {
        val appInfo = ApplicationInfo().apply { packageName = pkg }
        val info = ActivityInfo().apply {
            packageName = pkg
            name = COMPONENT
            icon = ICON_RESOURCE_ID
            applicationInfo = appInfo
        }
        val resolve = ResolveInfo()
        resolve.activityInfo = info
        return resolve
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private fun resetAppDatabaseInstance() = setDatabaseInstance(null)

    private companion object {
        const val PKG_A = "com.example.alpha"
        const val PKG_B = "com.example.beta"
        const val PKG_C = "com.example.gamma"
        const val COMPONENT = "MainActivity"
        const val SECOND_LENS = "work"
        const val ICON_SIZE = 16
        const val ICON_RESOURCE_ID = 0
        const val COLOR_A = 0xFF112233.toInt()
        const val COLOR_B = 0xFF334455.toInt()
        const val COLOR_C = 0xFF556677.toInt()
    }
}
