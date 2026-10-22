package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.util.UtilIconPackManager.IconPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsIconPackIdentityTest {
    private lateinit var settings: UtilSettings
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager = UtilIconPackManager()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun legacyLabelMigratesOnceToUniqueInstalledPackageIdentity() {
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, LEGACY_LABEL)
        val catalog = listOf(iconPack(PACKAGE_NAME, LEGACY_LABEL))

        assertEquals(PACKAGE_NAME, selectedPackageName(settings, catalog))
        assertEquals(PACKAGE_NAME, settings.getString(PACKAGE_KEY))
        assertTrue(settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME) != null)

        val renamedCatalog = listOf(iconPack(PACKAGE_NAME, RENAMED_LABEL))
        assertEquals(PACKAGE_NAME, selectedPackageName(settings, renamedCatalog))
        assertEquals("legacy label should not be rewritten after migration", LEGACY_LABEL, settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME))
    }

    @Test
    fun missingLegacyPackKeepsPendingChoiceAndRetriesWhenPackAppears() {
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, LEGACY_LABEL)

        assertEquals("system fallback while pack is absent", DEFAULT_PACKAGE, selectedPackageName(settings, emptyList()))
        assertFalse("do not persist default for an unresolved legacy selection", PreferenceManager.getDefaultSharedPreferences(context).contains(PACKAGE_KEY))
        assertEquals(LEGACY_LABEL, settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME))

        assertEquals(PACKAGE_NAME, selectedPackageName(settings, listOf(iconPack(PACKAGE_NAME, LEGACY_LABEL))))
        assertEquals(PACKAGE_NAME, settings.getString(PACKAGE_KEY))
    }

    @Test
    fun ambiguousLegacyLabelDoesNotChooseArbitraryPackage() {
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, LEGACY_LABEL)
        val catalog = listOf(
            iconPack(PACKAGE_NAME, LEGACY_LABEL),
            iconPack(SECOND_PACKAGE_NAME, LEGACY_LABEL)
        )

        assertEquals(DEFAULT_PACKAGE, selectedPackageName(settings, catalog))
        assertFalse("ambiguous labels must stay pending instead of selecting the first pack", PreferenceManager.getDefaultSharedPreferences(context).contains(PACKAGE_KEY))
        assertEquals(LEGACY_LABEL, settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME))
    }

    @Test
    fun defaultLegacySentinelMigratesToExplicitEmptyIdentity() {
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)

        assertEquals(DEFAULT_PACKAGE, selectedPackageName(settings, emptyList()))
        assertEquals(DEFAULT_PACKAGE, settings.getString(PACKAGE_KEY))
    }

    private fun selectedPackageName(settings: UtilSettings, packs: List<IconPack>): String {
        val resolver = UtilSettings::class.java.getDeclaredMethod("iconPackPackageName", List::class.java)
            .apply { isAccessible = true }
        return resolver.invoke(settings, packs) as String
    }

    private fun iconPack(packageName: String, label: String): IconPack = manager.IconPack().apply {
        mPackageName = packageName
        mName = label
    }

    private companion object {
        const val PACKAGE_KEY = "icon_pack_package_name"
        const val PACKAGE_NAME = "com.example.icons"
        const val SECOND_PACKAGE_NAME = "com.example.othericons"
        const val LEGACY_LABEL = "Nova Icon Pack"
        const val RENAMED_LABEL = "Nova Icons"
        const val DEFAULT_PACKAGE = ""
    }
}
