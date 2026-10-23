package com.mckimquyen.util

import android.app.Application
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-006: đo riêng chi phí discovery icon pack trên thiết bị thật — quét mới mỗi lần
 * (hành vi cũ của loadSingleAppIcon) so với manager dùng chung (hành vi mới). Chỉ đo
 * [UtilIconPackManager.getAvailableIconPacksWithIcons], không đo cả reload end-to-end, và
 * máy test không có icon pack nào nên chưa gồm chi phí parse appfilter.xml.
 */
@RunWith(AndroidJUnit4::class)
class IconPackDiscoveryBenchmarkTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun sharedManagerSkipsDiscoveryQueriesOnSubsequentCalls() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val shared = UtilIconPackManager()

        // Khởi động để JIT/PackageManager cache không tính vào mốc nào.
        shared.getAvailableIconPacksWithIcons(false, application)
        UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)

        val freshStart = System.nanoTime()
        repeat(RELOAD_COUNT) {
            UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)
        }
        val freshNanos = System.nanoTime() - freshStart

        val cachedStart = System.nanoTime()
        repeat(RELOAD_COUNT) {
            shared.getAvailableIconPacksWithIcons(false, application)
        }
        val cachedNanos = System.nanoTime() - cachedStart

        Log.i(
            TAG,
            "discovery reloads=$RELOAD_COUNT freshMs=${freshNanos / NANOS_PER_MS} " +
                "cachedMs=${cachedNanos / NANOS_PER_MS}"
        )
        assertTrue(
            "discovery dùng chung phải nhanh hơn ít nhất $MIN_SPEEDUP lần so với quét mới mỗi lần " +
                "(fresh=${freshNanos}ns cached=${cachedNanos}ns)",
            cachedNanos * MIN_SPEEDUP < freshNanos
        )
    }

    @Test
    fun measurePublicReloadAgainstLegacyFreshManager() = kotlinx.coroutines.runBlocking {
        val application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val targetPackage = application.packageName
        suspend fun legacyReload() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val defaultBitmap = UtilBitmap.packageNameToBitmap(application.packageManager, targetPackage, NO_ICON_RESOURCE)
                ?: return@withContext null
            val settings = UtilSettings(application)
            val packs = UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)
            val selectedPackage = settings.iconPackPackageName(packs)
            packs.firstOrNull { it.mPackageName == selectedPackage }
                ?.getIconForPackage(targetPackage, defaultBitmap) ?: defaultBitmap
        }

        repeat(WARMUP_COUNT) {
            org.junit.Assert.assertNotNull(legacyReload())
            org.junit.Assert.assertNotNull(UtilApp.loadSingleAppIcon(application, targetPackage, NO_ICON_RESOURCE))
        }
        var legacyNanos = 0L
        var sharedNanos = 0L
        repeat(RELOAD_COUNT) { index ->
            // Đảo thứ tự theo lượt để hạn chế sai lệch do warmup và tải hệ thống.
            if (index % COMPARISON_ORDER_COUNT == 0) {
                val start = System.nanoTime()
                org.junit.Assert.assertNotNull(legacyReload())
                legacyNanos += System.nanoTime() - start
                val sharedStart = System.nanoTime()
                org.junit.Assert.assertNotNull(UtilApp.loadSingleAppIcon(application, targetPackage, NO_ICON_RESOURCE))
                sharedNanos += System.nanoTime() - sharedStart
            } else {
                val start = System.nanoTime()
                org.junit.Assert.assertNotNull(UtilApp.loadSingleAppIcon(application, targetPackage, NO_ICON_RESOURCE))
                sharedNanos += System.nanoTime() - start
                val legacyStart = System.nanoTime()
                org.junit.Assert.assertNotNull(legacyReload())
                legacyNanos += System.nanoTime() - legacyStart
            }
        }
        // Thời gian là dữ liệu đo, không dùng làm assertion tốc độ dễ flake.
        Log.i(TAG, "endToEnd reloads=$RELOAD_COUNT legacyNs=$legacyNanos sharedNs=$sharedNanos")
        Unit
    }

    private companion object {
        const val NO_ICON_RESOURCE = 0
        const val WARMUP_COUNT = 5
        const val COMPARISON_ORDER_COUNT = 2
        const val TAG = "PERF006"
        const val RELOAD_COUNT = 50
        const val MIN_SPEEDUP = 2
        const val NANOS_PER_MS = 1_000_000L
    }
}
