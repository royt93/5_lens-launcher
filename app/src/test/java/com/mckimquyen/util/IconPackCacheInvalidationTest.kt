package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import androidx.preference.PreferenceManager
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.services.BroadcastReceivers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class IconPackCacheInvalidationTest {
    private val context: android.app.Application = RuntimeEnvironment.getApplication()
    private val pm: PackageManager = Mockito.mock(PackageManager::class.java)
    private val packResources: Resources = Mockito.mock(Resources::class.java)
    private val application: RApplication = object : RApplication() {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(name, mode)
        override fun getPackageManager() = pm
        override fun getResources() = packResources
        override fun getPackageName() = context.packageName
    }
    private val settings get() = UtilSettings(application)
    private var queries = 0

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
        Mockito.`when`(packResources.configuration).thenReturn(context.resources.configuration)
        settings.saveIconPackPackageName(SELECTED_PACK)
        Mockito.`when`(packResources.getStringArray(R.array.icon_pack_launchers)).thenReturn(ACTIONS)
        Mockito.`when`(pm.queryIntentActivities(any(Intent::class.java), anyInt())).thenAnswer {
            queries++
            emptyList<android.content.pm.ResolveInfo>()
        }
    }

    private fun lookup() = application.iconPackManager.getAvailableIconPacksWithIcons(false, application)

    private fun event(action: String, packageName: String = SELECTED_PACK, replacing: Boolean = false) {
        BroadcastReceivers.AppsUpdatedReceiver().onReceive(application,
            Intent(action, Uri.parse("package:$packageName")).putExtra(Intent.EXTRA_REPLACING, replacing))
    }

    @Test
    fun selectionChangesInvalidateLazilyButSameValueDoesNot() {
        lookup()
        settings.saveIconPackPackageName(OTHER_PACK)
        assertEquals("save không tự quét catalog", ACTIONS.size, queries)
        lookup()
        assertEquals("đổi pack làm mới đúng một batch", ACTIONS.size * TWO_BATCHES, queries)
        settings.saveIconPackPackageName(OTHER_PACK)
        lookup()
        assertEquals("lưu lại cùng pack giữ cache", ACTIONS.size * TWO_BATCHES, queries)
    }

    @Test
    fun selectedPackageReplacementInvalidatesButUnrelatedPackageDoesNot() {
        lookup()
        event(Intent.ACTION_PACKAGE_REPLACED, OTHER_PACK)
        lookup()
        assertEquals(ACTIONS.size, queries)
        event(Intent.ACTION_PACKAGE_REPLACED)
        assertEquals("receiver chỉ invalidate, không query", ACTIONS.size, queries)
        lookup()
        lookup()
        assertEquals(ACTIONS.size * TWO_BATCHES, queries)
    }

    @Test
    fun replacementRemovalWaitsForReplacementButFinalRemovalInvalidates() {
        lookup()
        event(Intent.ACTION_PACKAGE_REMOVED, replacing = true)
        lookup()
        assertEquals(ACTIONS.size, queries)
        event(Intent.ACTION_PACKAGE_REMOVED)
        lookup()
        assertEquals(ACTIONS.size * TWO_BATCHES, queries)
        assertEquals("pack bị gỡ không xóa lựa chọn đã lưu", SELECTED_PACK,
            settings.getString(UtilSettings.KEY_ICON_PACK_PACKAGE_NAME))
    }

    @Test
    fun selectedPackReinstallAndChangedEventsRefreshCatalog() {
        lookup()
        event(Intent.ACTION_PACKAGE_ADDED)
        lookup()
        assertEquals(ACTIONS.size * TWO_BATCHES, queries)
        event(Intent.ACTION_PACKAGE_CHANGED)
        lookup()
        assertEquals(ACTIONS.size * THREE_BATCHES, queries)
    }

    private companion object {
        const val SELECTED_PACK = "com.example.selectedicons"
        const val OTHER_PACK = "com.example.othericons"
        const val TWO_BATCHES = 2
        const val THREE_BATCHES = 3
        val ACTIONS = arrayOf("pack.first", "pack.second")
    }
}
