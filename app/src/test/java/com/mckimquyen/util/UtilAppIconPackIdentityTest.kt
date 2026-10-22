package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilAppIconPackIdentityTest {
    @Test
    fun selectedPackLookupUsesPackageNameAfterDisplayLabelChanges() {
        val manager = UtilIconPackManager()
        val pack = manager.IconPack().apply {
            mPackageName = PACK_PACKAGE
            mName = RENAMED_LABEL
        }
        val otherPack = manager.IconPack().apply {
            mPackageName = OTHER_PACKAGE
            mName = LEGACY_LABEL
        }

        val selected = UtilApp.findIconPackByPackage(listOf(otherPack, pack), PACK_PACKAGE)

        assertSame("renamed label must not prevent selecting the saved package", pack, selected)
    }

    @Test
    fun absentPackageIdentityUsesSystemIconPackFallback() {
        val manager = UtilIconPackManager()
        val onlyPack = manager.IconPack().apply {
            mPackageName = PACK_PACKAGE
            mName = RENAMED_LABEL
        }

        val selected = UtilApp.findIconPackByPackage(listOf(onlyPack), MISSING_PACKAGE)

        assertSame("missing stored package identity must fall back to system icons", null, selected)
    }

    @Test
    fun iconPackCacheTokenIgnoresDisplayLabelAndChangesWithPackageVersion() {
        val initialToken = UtilApp.iconPackTokenFor(PACK_PACKAGE, VERSION_TOKEN)
        val localeChangedToken = UtilApp.iconPackTokenFor(PACK_PACKAGE, VERSION_TOKEN)
        val updatedPackToken = UtilApp.iconPackTokenFor(PACK_PACKAGE, UPDATED_VERSION_TOKEN)

        assertEquals("locale-only rename does not alter identity token", initialToken, localeChangedToken)
        assertEquals("package update still changes token", false, initialToken == updatedPackToken)
    }

    private companion object {
        const val PACK_PACKAGE = "com.example.nova"
        const val OTHER_PACKAGE = "com.example.other"
        const val MISSING_PACKAGE = "com.example.unavailable"
        const val RENAMED_LABEL = "Nova Icons"
        const val LEGACY_LABEL = "Nova Icon Pack"
        const val VERSION_TOKEN = "1:42"
        const val UPDATED_VERSION_TOKEN = "2:99"
    }
}
