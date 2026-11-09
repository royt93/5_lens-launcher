package com.mckimquyen.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensOpticalProfile
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.OpticalCustomPreset
import com.mckimquyen.model.OpticalProfileParseResult
import com.mckimquyen.util.LensProfile
import com.mckimquyen.util.UtilSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-036: nút Xuất/Nhập đi qua launcher SAF thật; fixture chỉ tạo và xóa lens riêng của test. */
@RunWith(AndroidJUnit4::class)
class LensOpticalProfileWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val prefs get() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var targetId: String
    private lateinit var settings: UtilSettings
    private val files = mutableListOf<File>()

    private val incoming = LensOpticalProfile(
        LensProfile.ANAMORPHIC, 3.3f, 1.3f, 22f, 250L, 0.45f, true,
        OpticalCustomPreset(4.1f, 1.6f, 300L)
    )

    @Before fun setup() {
        AppDatabase.init(context)
        settings = UtilSettings(context)
        settings.sortType // Khởi tạo pref mặc định trước khi chụp trạng thái để so sánh.
        // The first Activity launch of a fresh install writes the locale-migration flag. Do that here, as an installed app
        // that has run before would already have, so the "writes nothing" snapshots below do not depend on the device
        // having been used with this app (they failed on a device with the app freshly installed).
        com.mckimquyen.util.LocaleHelper.onAttach(context)
        targetId = "f036-${UUID.randomUUID()}"
        runBlocking { dao.insertOrUpdate(LensWorkspace(targetId, "F036", orderIndex = 900)) }
        settings.saveDistortionFactor(targetId, 4.1f)
        settings.saveIconSize(targetId, 30f)
        settings.saveLensProfile(targetId, LensProfile.CYLINDRICAL)
        settings.saveLensHood(targetId, 0.7f)
        settings.saveSmartFocusBias(targetId, false)
        prefs.edit().putFloat(UtilSettings.KEY_SCALE_FACTOR, 2f)
            .putLong(UtilSettings.KEY_ANIMATION_TIME, 300L).commit()
    }

    @After fun cleanup() {
        runBlocking { dao.findById(targetId)?.let { dao.delete(it) } }
        settings.deleteLensSettings(targetId)
        files.forEach(File::delete)
    }

    private fun launch() = ActivityScenario.launch<LensSettingsActivity>(
        Intent(context, LensSettingsActivity::class.java).putExtra(LensSettingsActivity.EXTRA_LENS_ID, targetId)
    )

    private fun fileWith(content: ByteArray?): File = File(context.cacheDir, "f036-${UUID.randomUUID()}.json").also {
        content?.let { bytes -> it.writeBytes(bytes) }
        files += it
    }

    /**
     * Filter phải có data type: intent mở LensSettingsActivity không có action và không có type, nên filter chỉ
     * theo action sẽ khớp luôn nó và monitor chặn việc khởi chạy Activity.
     */
    private fun answerPicker(action: String, uri: Uri?, resultCode: Int = Activity.RESULT_OK): Instrumentation.ActivityMonitor =
        instrumentation.addMonitor(
            IntentFilter(action).apply {
                addDataType(if (action == Intent.ACTION_OPEN_DOCUMENT) "*/*" else LensOpticalProfile.MIME_TYPE)
            },
            Instrumentation.ActivityResult(resultCode, uri?.let { Intent().setData(it) }),
            true
        )

    private fun awaitReady(scenario: ActivityScenario<LensSettingsActivity>) {
        waitUntil {
            var ready = false
            scenario.onActivity { ready = it.findViewById<Slider>(R.id.sbLensSettingsIconSize).isEnabled }
            ready
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        assertTrue("Điều kiện chưa đạt sau $WAIT_MS ms", condition())
    }

    private fun click(scenario: ActivityScenario<LensSettingsActivity>, id: Int) =
        scenario.onActivity { it.findViewById<View>(id).performClick() }

    private fun dialog(scenario: ActivityScenario<LensSettingsActivity>): AlertDialog {
        var found: AlertDialog? = null
        waitUntil { scenario.onActivity { found = it.dialogForTest }; found?.isShowing == true }
        return requireNotNull(found)
    }

    private fun press(dialog: AlertDialog, button: Int) = instrumentation.runOnMainSync { dialog.getButton(button).performClick() }

    private fun snapshot() = prefs.all.toMap()

    @Test fun buttonsExistAndBindingDoesNotWritePreferences() {
        val before = snapshot()
        launch().use { scenario ->
            awaitReady(scenario)
            scenario.onActivity {
                assertNotNull(it.findViewById<View>(R.id.btLensSettingsExportProfile))
                assertNotNull(it.findViewById<View>(R.id.btLensSettingsImportProfile))
            }
        }
        assertEquals(before, snapshot())
    }

    @Test fun exportWritesCurrentTargetProfileToPickedDocument() {
        val destination = fileWith(null)
        val monitor = answerPicker(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(destination))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsExportProfile)
                waitUntil { destination.length() > 0 }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        val parsed = LensOpticalProfile.parse(destination.readText())
        assertEquals(OpticalProfileParseResult.Success(requireNotNull(settings.captureOpticalProfile(targetId))), parsed)
        assertFalse(destination.readText().contains(targetId))
    }

    @Test fun canceledPickerWritesNothing() {
        val before = snapshot()
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, null, Activity.RESULT_CANCELED)
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertNull(it.dialogForTest) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(before, snapshot())
    }

    @Test fun malformedFileShowsNoPreviewAndWritesNothing() {
        val before = snapshot()
        val source = fileWith("{ not a profile".toByteArray())
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(source))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                instrumentation.waitForIdleSync()
                Thread.sleep(SETTLE_MS)
                scenario.onActivity { assertNull(it.dialogForTest) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(before, snapshot())
    }

    @Test fun previewListsGroupsAndCancelWritesNothing() {
        val before = snapshot()
        val source = fileWith(incoming.toJson().toByteArray())
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(source))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                val preview = dialog(scenario)
                val text = preview.findViewById<android.widget.TextView>(android.R.id.message)?.text.toString()
                assertTrue(text.contains(context.getString(R.string.lens_optical_target_group)))
                assertTrue(text.contains(context.getString(R.string.lens_optical_global_warning)))
                assertTrue(text.contains(context.getString(R.string.lens_optical_custom_group)))
                // The preview formats numbers with the device locale (a comma decimal separator on vi-VN, de-DE...), so build the
                // expected text the same way instead of hard-coding "3.30".
                val expected = String.format(java.util.Locale.getDefault(), "%.2f", incoming.distortionFactor)
                assertTrue("preview must show $expected, was: $text", text.contains(expected))
                press(preview, DialogInterface.BUTTON_NEGATIVE)
                instrumentation.waitForIdleSync()
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(before, snapshot())
    }

    @Test fun confirmAppliesToTargetOnlyAndKeepsActiveLens() {
        settings.saveDistortionFactor("f036-other", 4.4f)
        prefs.edit().putString(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID).commit()
        val source = fileWith(incoming.toJson().toByteArray())
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(source))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                press(dialog(scenario), DialogInterface.BUTTON_POSITIVE)
                waitUntil { settings.getDistortionFactor(targetId) == 3.3f }
                waitUntil {
                    var enabled = false
                    scenario.onActivity { enabled = it.findViewById<Slider>(R.id.sbLensSettingsIconSize).isEnabled }
                    enabled
                }
                scenario.onActivity {
                    assertEquals(3.3f, it.findViewById<Slider>(R.id.sbLensSettingsDistortion).value, 0.001f)
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            settings.deleteLensSettings("f036-other")
        }
        assertEquals(LensProfile.ANAMORPHIC, settings.getLensProfile(targetId))
        assertEquals(22f, settings.getIconSize(targetId), 0f)
        assertEquals(0.45f, settings.getLensHood(targetId), 0f)
        assertTrue(settings.isSmartFocusBias(targetId))
        assertEquals(1.3f, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR), 0f)
        assertEquals(250L, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, prefs.getString(UtilSettings.KEY_ACTIVE_LENS_ID, null))
    }

    @Test fun targetDeletedBeforeConfirmDoesNotWrite() {
        val source = fileWith(incoming.toJson().toByteArray())
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(source))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                val preview = dialog(scenario)
                runBlocking { dao.findById(targetId)?.let { dao.delete(it) } }
                settings.deleteLensSettings(targetId)
                val before = snapshot()
                press(preview, DialogInterface.BUTTON_POSITIVE)
                instrumentation.waitForIdleSync()
                Thread.sleep(SETTLE_MS)
                assertEquals(before, snapshot())
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun recreateRestoresPreviewWithoutApplying() {
        val before = snapshot()
        val source = fileWith(incoming.toJson().toByteArray())
        val monitor = answerPicker(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(source))
        try {
            launch().use { scenario ->
                awaitReady(scenario)
                click(scenario, R.id.btLensSettingsImportProfile)
                dialog(scenario)
                scenario.recreate()
                awaitReady(scenario)
                val restored = dialog(scenario)
                assertTrue(restored.isShowing)
                assertEquals(before, snapshot())
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(before, snapshot())
    }

    private companion object {
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 400L
    }
}
