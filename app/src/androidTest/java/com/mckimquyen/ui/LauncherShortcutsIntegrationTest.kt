package com.mckimquyen.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ShortcutManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LAUNCH-001: proves the manifest-declared static shortcuts (res/xml/shortcuts.xml) actually
 * resolve and launch on the real installed package, using the real ShortcutManager - not just
 * that the XML parses. This is what would have caught the original targetPackage="com.mckimquyen"
 * vs. applicationId="com.mckimquyen.lenslauncher" mismatch.
 */
@RunWith(AndroidJUnit4::class)
class LauncherShortcutsIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifestShortcuts_declareBothExpectedIds() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        val ids = shortcutManager.manifestShortcuts.map { it.id }
        assertTrue("expected utilSettings shortcut, got $ids", ids.contains("utilSettings"))
        assertTrue("expected apps shortcut, got $ids", ids.contains("apps"))
    }

    @Test
    fun manifestShortcuts_haveLocalizedLabels() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        for (shortcut in shortcutManager.manifestShortcuts) {
            assertTrue("${shortcut.id} must have a short label", !shortcut.shortLabel.isNullOrBlank())
            assertTrue("${shortcut.id} must have a long label", !shortcut.longLabel.isNullOrBlank())
        }
    }

    @Test
    fun manifestShortcuts_intentsTargetTheRealInstalledPackage() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        val shortcuts = shortcutManager.manifestShortcuts
        assertTrue("expected at least 2 manifest shortcuts", shortcuts.size >= 2)
        for (shortcut in shortcuts) {
            val intent = shortcut.intent
            assertNotNull("${shortcut.id} must declare an intent", intent)
            assertEquals(
                "${shortcut.id}'s targetPackage must match the real installed applicationId",
                context.packageName,
                intent!!.component?.packageName
            )
        }
    }

    @Test
    fun manifestShortcuts_intentsResolveToAnInstalledActivity() {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        for (shortcut in shortcutManager.manifestShortcuts) {
            val intent = shortcut.intent!!
            val resolved = context.packageManager.resolveActivity(intent, 0)
            assertNotNull("${shortcut.id}'s intent must resolve to an installed activity", resolved)
        }
    }

    @Test
    fun settingsShortcut_launchesActSettings() {
        launchShortcut("utilSettings").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertEquals(ActSettings::class.java, activity.javaClass)
            }
        }
    }

    @Test
    fun appsShortcut_launchesActHome() {
        launchShortcut("apps").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertEquals(ActHome::class.java, activity.javaClass)
            }
        }
    }

    private fun launchShortcut(shortcutId: String): ActivityScenario<Activity> {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        val shortcut = shortcutManager.manifestShortcuts.first { it.id == shortcutId }
        val intent = shortcut.intent!!.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ActivityScenario.launch(intent)
    }
}
