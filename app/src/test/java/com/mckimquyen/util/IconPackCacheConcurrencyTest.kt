package com.mckimquyen.util

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class IconPackCacheConcurrencyTest {
    @Test
    fun concurrentColdLookupsShareOneDiscoveryBatch() {
        val application = Mockito.mock(Application::class.java)
        val pm = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(application.packageManager).thenReturn(pm)
        Mockito.`when`(application.resources).thenReturn(resources)
        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(ACTIONS)
        val queryCount = AtomicInteger()
        val firstQueryEntered = CountDownLatch(1)
        val releaseFirstQuery = CountDownLatch(1)
        val secondLookupStarted = CountDownLatch(1)
        Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer {
            if (queryCount.incrementAndGet() == 1) {
                firstQueryEntered.countDown()
                check(releaseFirstQuery.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            emptyList<android.content.pm.ResolveInfo>()
        }
        val manager = UtilIconPackManager()
        val workers = Executors.newFixedThreadPool(WORKER_COUNT)
        try {
            val first = workers.submit<List<UtilIconPackManager.IconPack>> {
                manager.getAvailableIconPacksWithIcons(false, application)
            }
            assertTrue(firstQueryEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val second = workers.submit<List<UtilIconPackManager.IconPack>> {
                secondLookupStarted.countDown()
                manager.getAvailableIconPacksWithIcons(false, application)
            }
            assertTrue(secondLookupStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            // Nhường thời gian để lookup thứ hai chạm cache khi query đầu còn bị giữ.
            second.getIfCompletedBeforeRelease(releaseFirstQuery)
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals("hai cold lookup chỉ được discovery một batch", ACTIONS.size, queryCount.get())
        } finally {
            releaseFirstQuery.countDown()
            workers.shutdownNow()
            assertTrue("worker phải kết thúc", workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    @Test
    fun invalidationDoesNotWaitForBlockedQueryOrLoseItsGeneration() {
        val application = Mockito.mock(Application::class.java)
        val pm = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(application.packageManager).thenReturn(pm)
        Mockito.`when`(application.resources).thenReturn(resources)
        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(ACTIONS)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queries = AtomicInteger()
        Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer {
            if (queries.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            emptyList<android.content.pm.ResolveInfo>()
        }
        val manager = UtilIconPackManager()
        val workers = Executors.newFixedThreadPool(WORKER_COUNT)
        try {
            val lookup = workers.submit { manager.getAvailableIconPacksWithIcons(false, application) }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val invalidation = workers.submit { manager.invalidate() }
            invalidation.get(CONTENTION_WINDOW_MS, TimeUnit.MILLISECONDS)
            release.countDown()
            lookup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            manager.getAvailableIconPacksWithIcons(false, application)
            manager.getAvailableIconPacksWithIcons(false, application)
            assertEquals("invalidation giữa scan không bị lần publish cũ nuốt mất", ACTIONS.size * WORKER_COUNT, queries.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    @Test
    fun concurrentIconLookupsLoadPackResourcesOnlyOnce() {
        val application = Mockito.mock(Application::class.java)
        val pm = Mockito.mock(PackageManager::class.java)
        val resources = Mockito.mock(Resources::class.java)
        val assets = Mockito.mock(android.content.res.AssetManager::class.java)
        Mockito.`when`(application.packageManager).thenReturn(pm)
        Mockito.`when`(application.resources).thenReturn(resources)
        Mockito.`when`(resources.getStringArray(R.array.icon_pack_launchers)).thenReturn(emptyArray<String>())
        Mockito.`when`(resources.assets).thenReturn(assets)
        Mockito.`when`(assets.open("appfilter.xml")).thenThrow(java.io.IOException("fixture không có appfilter"))
        val manager = UtilIconPackManager()
        manager.getAvailableIconPacksWithIcons(false, application)
        val pack = manager.IconPack().apply { mPackageName = PACK_NAME }
        val loads = AtomicInteger()
        val firstLoadEntered = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        Mockito.`when`(pm.getResourcesForApplication(PACK_NAME)).thenAnswer {
            if (loads.incrementAndGet() == 1) {
                firstLoadEntered.countDown()
                check(releaseLoad.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            resources
        }
        val bitmap = android.graphics.Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        val workers = Executors.newFixedThreadPool(WORKER_COUNT)
        try {
            val first = workers.submit<android.graphics.Bitmap> { pack.getIconForPackage(TARGET_PACKAGE, bitmap) }
            assertTrue(firstLoadEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val second = workers.submit<android.graphics.Bitmap> {
                secondStarted.countDown()
                pack.getIconForPackage(TARGET_PACKAGE, bitmap)
            }
            assertTrue(secondStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            second.getIfCompletedBeforeRelease(releaseLoad)
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals("một pack chỉ load appfilter một lần khi truy cập đồng thời", 1, loads.get())
        } finally {
            releaseLoad.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            bitmap.recycle()
        }
    }

    @Test
    fun cleanupDoesNotRecycleResourcesOwnedBitmap() {
        val manager = UtilIconPackManager()
        val pack = manager.IconPack()
        val bitmap = android.graphics.Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        val field = pack.javaClass.getDeclaredField("mBackImages").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val images = field.get(pack) as MutableList<android.graphics.Bitmap>
        images.add(bitmap)
        try {
            pack.cleanup()
            org.junit.Assert.assertFalse("bitmap từ Resources có thể còn đang được nơi khác vẽ", bitmap.isRecycled)
            assertTrue(images.isEmpty())
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun java.util.concurrent.Future<*>.getIfCompletedBeforeRelease(release: CountDownLatch) {
        try {
            get(CONTENTION_WINDOW_MS, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            // Cache lock đúng sẽ chờ query đầu; baseline thiếu lock sẽ hoàn thành sớm.
        } finally {
            release.countDown()
        }
    }

    private companion object {
        const val PACK_NAME = "com.example.icons"
        const val TARGET_PACKAGE = "com.example.target"
        const val ICON_SIZE = 8
        const val WORKER_COUNT = 2
        const val TIMEOUT_SECONDS = 5L
        const val CONTENTION_WINDOW_MS = 300L
        val ACTIONS = arrayOf("pack.first", "pack.second")
    }
}
