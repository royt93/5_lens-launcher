package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PREF-001 widget proof: FrmSettings' displayed background selection reflects the persisted
 * BackgroundMode correctly, and that selection survives a fragment recreate (config-change
 * proxy) unchanged - the stable enum-name persistence is what makes this locale/reorder-safe,
 * unlike the old raw-string comparison it replaces.
 */
@RunWith(AndroidJUnit4::class)
class FrmSettingsBackgroundWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() {
        rawPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun colorMode_showsHexTextAndColorSwatch() {
        val settings = UtilSettings(context)
        settings.save(BackgroundMode.COLOR)
        settings.save(UtilSettings.KEY_BACKGROUND_COLOR, "#FF112233")

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val tvSelectedBackground = fragment.requireView().findViewById<TextView>(R.id.tvSelectedBackground)
                val ivSelectedBackgroundColor = fragment.requireView().findViewById<View>(R.id.ivSelectedBackgroundColor)
                assertEquals("#112233", tvSelectedBackground.text.toString())
                assertTrue(ivSelectedBackgroundColor.isVisible)
            }
        }
    }

    @Test
    fun wallpaperMode_showsWallpaperLabelAndHidesColorSwatch() {
        val settings = UtilSettings(context)
        settings.save(BackgroundMode.WALLPAPER)

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val tvSelectedBackground = fragment.requireView().findViewById<TextView>(R.id.tvSelectedBackground)
                val ivSelectedBackgroundColor = fragment.requireView().findViewById<View>(R.id.ivSelectedBackgroundColor)
                val expected = fragment.resources.getStringArray(R.array.backgrounds)[BackgroundMode.WALLPAPER.ordinal]
                assertEquals(expected, tvSelectedBackground.text.toString())
                assertFalse(ivSelectedBackgroundColor.isVisible)
            }
        }
    }

    @Test
    fun colorMode_survivesRecreate() {
        val settings = UtilSettings(context)
        settings.save(BackgroundMode.COLOR)
        settings.save(UtilSettings.KEY_BACKGROUND_COLOR, "#FF445566")

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.recreate()
            scenario.onFragment { fragment ->
                val tvSelectedBackground = fragment.requireView().findViewById<TextView>(R.id.tvSelectedBackground)
                assertEquals("#445566", tvSelectedBackground.text.toString())
            }
        }
    }

    @Test
    fun iconPack_showsTranslatedDefaultLabel_whenMarkerIsStored() {
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val tvSelectedIconPack = fragment.requireView().findViewById<TextView>(R.id.tvSelectedIconPack)
                assertEquals(
                    fragment.getString(R.string.setting_default_icon_pack),
                    tvSelectedIconPack.text.toString()
                )
            }
        }
    }

    @Test
    fun iconPack_showsRealPackNameUnchanged_whenAThirdPartyPackIsStored() {
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, "Nova Icon Pack")

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val tvSelectedIconPack = fragment.requireView().findViewById<TextView>(R.id.tvSelectedIconPack)
                assertEquals("Nova Icon Pack", tvSelectedIconPack.text.toString())
            }
        }
    }
}
