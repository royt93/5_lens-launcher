package com.mckimquyen.ui

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LocaleHelper
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-032: LensManagementActivity must restore dialogs and draft text across Activity recreation.
 * ActivityScenario.recreate() covers recreation, not an OS process-kill/relaunch cycle.
 */
@RunWith(AndroidJUnit4::class)
class LensManagementStateRestorationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private var originalPrefs: Map<String, Any?> = emptyMap()
    // The device's real lens rows and active lens, restored in tearDown (DeviceStateGuard does not cover Room).
    private var originalRoom: LensManagerRoomSnapshot? = null
    private var originalActiveLensId: String? = null

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        originalRoom = LensManagerRoomSnapshot.capture(context)
        originalActiveLensId = UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        originalPrefs = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        LocaleHelper.setLocale(context, "en")
        dao.deleteAll()
        dao.insertAll(
            listOf(
                LensWorkspace.createDefault().copy(groupName = "Personal"),
                LensWorkspace("work", "Work", 1, 20L, "Work")
            )
        )
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() = runBlocking {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val editor = preferences.edit().clear()
        originalPrefs.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> Unit
                else -> error("Unsupported preference value for '$key'")
            }
        }
        check(editor.commit()) { "Unable to restore lens manager preferences" }
        originalRoom?.restore(context)
        if (originalActiveLensId == null) check(preferences.edit().remove(UtilSettings.KEY_ACTIVE_LENS_ID).commit())
        else UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, originalActiveLensId)
    }

    private fun findInput(dialog: AlertDialog): EditText {
        val input = dialog.findViewById<EditText>(android.R.id.edit)
        return requireNotNull(input) { "EditText android.R.id.edit not found in dialog" }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            Thread.sleep(POLL_MS)
        }
        assertTrue(message, condition())
    }

    @Test
    fun renameLensDialogDraftSurvivesActivityRecreation() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showRenameLensDialogForTest(LensWorkspace("work", "Work", 1, 20L, "Work"))

                val dialog = requireNotNull(activity.lensGroupDialogForTest)
                assertTrue("Rename dialog must be showing before recreate", dialog.isShowing)
                val input = findInput(dialog)
                input.setText("Work Draft New Name")
            }

            // Kích hoạt Activity recreation (xoay màn hình / process death)
            scenario.recreate()

            awaitCondition("Rename dialog must be restored after recreate") {
                var restored = false
                scenario.onActivity { activity ->
                    val dialog = activity.lensGroupDialogForTest
                    if (dialog != null && dialog.isShowing) {
                        val text = dialog.findViewById<EditText>(android.R.id.edit)?.text?.toString()
                        restored = text == "Work Draft New Name"
                    }
                }
                restored
            }
        }
    }

    @Test
    fun assignGroupPickerSurvivesActivityRecreation() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("work", true)
                activity.findViewById<android.view.View>(com.mckimquyen.R.id.btLensGroupAssign).performClick()
            }
            awaitDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensGroupDialogForTest)
                assertTrue("assign-group picker must show existing groups", dialog.listView.count > 0)
            }
            scenario.recreate()
            awaitDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensGroupDialogForTest)
                assertTrue("restored assign-group picker must retain group options", dialog.listView.count > 0)
            }
        }
    }

    private fun awaitDialog(scenario: ActivityScenario<LensManagementActivity>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var showing = false
        while (System.currentTimeMillis() < deadline && !showing) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { showing = it.lensGroupDialogForTest?.isShowing == true }
            if (!showing) Thread.sleep(POLL_MS)
        }
        assertTrue("lens group dialog must show", showing)
    }

    @Test
    fun createGroupDialogDraftSurvivesActivityRecreation() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.toggleEditModeForTest()
                activity.selectLensForGroupEdit("work", true)
                activity.findViewById<android.view.View>(com.mckimquyen.R.id.btLensGroupAssign).performClick()
            }
            awaitDialog(scenario)
            scenario.onActivity { activity ->
                val picker = requireNotNull(activity.lensGroupDialogForTest)
                val createIndex = (0 until picker.listView.count).first { index ->
                    picker.listView.adapter.getItem(index).toString() == activity.getString(com.mckimquyen.R.string.lens_group_create)
                }
                picker.listView.performItemClick(picker.listView.getChildAt(createIndex), createIndex, picker.listView.adapter.getItemId(createIndex))
            }
            awaitDialog(scenario)
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.lensGroupDialogForTest)
                assertTrue("Create-group dialog must be showing before recreate", dialog.isShowing)
                val input = findInput(dialog)
                input.setText("New Draft Group")
            }

            scenario.recreate()

            awaitCondition("Create group dialog must be restored after recreate") {
                var restored = false
                scenario.onActivity { activity ->
                    val dialog = activity.lensGroupDialogForTest
                    if (dialog != null && dialog.isShowing) {
                        val text = dialog.findViewById<EditText>(android.R.id.edit)?.text?.toString()
                        restored = text == "New Draft Group"
                    }
                }
                restored
            }
        }
    }

    private companion object {
        const val WAIT_MS = 6_000L
        const val POLL_MS = 100L
    }
}
