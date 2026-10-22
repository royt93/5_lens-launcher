package com.mckimquyen.ui.settings

import com.mckimquyen.util.UtilIconPackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SettingsIconPackOptionsTest {
    @Test
    fun displayLabelsRemainSeparateFromPackageIdentityIncludingDuplicateLabels() {
        val manager = UtilIconPackManager()
        val packs = listOf(
            manager.IconPack().apply { mPackageName = FIRST_PACKAGE; mName = SHARED_LABEL },
            manager.IconPack().apply { mPackageName = SECOND_PACKAGE; mName = SHARED_LABEL }
        )

        val options = SettingsDialogCoordinator.buildIconPackOptions(DEFAULT_LABEL, packs)

        assertEquals(3, options.size)
        assertEquals(DEFAULT_LABEL, options[0].displayName)
        assertEquals("", options[0].packageName)
        assertEquals(SHARED_LABEL, options[1].displayName)
        assertEquals(FIRST_PACKAGE, options[1].packageName)
        assertEquals(SHARED_LABEL, options[2].displayName)
        assertEquals(SECOND_PACKAGE, options[2].packageName)
        assertSame("selected row is resolved by stored packageName", options[2], options[
            SettingsDialogCoordinator.selectedIconPackIndex(options, SECOND_PACKAGE)
        ])
        assertEquals(0, SettingsDialogCoordinator.selectedIconPackIndex(options, MISSING_PACKAGE))
    }

    private companion object {
        const val DEFAULT_LABEL = "Default"
        const val SHARED_LABEL = "Localized duplicate label"
        const val FIRST_PACKAGE = "com.example.first"
        const val SECOND_PACKAGE = "com.example.second"
        const val MISSING_PACKAGE = "com.example.unavailable"
    }
}
