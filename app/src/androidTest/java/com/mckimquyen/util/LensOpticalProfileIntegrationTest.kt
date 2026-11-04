package com.mckimquyen.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.model.LensOpticalProfile
import com.mckimquyen.model.OpticalCustomPreset
import com.mckimquyen.views.LensView
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-036: file thật qua ContentResolver thật, rồi LensView thật đọc giá trị đã nhập.
 * Không dùng Room, nên không chạm lens hay app row của máy; prefs do DeviceStateGuard khôi phục.
 */
@RunWith(AndroidJUnit4::class)
class LensOpticalProfileIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val themed get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme)
    private val lensId = "f036-int-${UUID.randomUUID()}"
    private val controlId = "f036-ctl-${UUID.randomUUID()}"
    private lateinit var settings: UtilSettings
    private val files = mutableListOf<File>()

    @Before fun setup() {
        settings = UtilSettings(context)
        settings.sortType // Khởi tạo pref mặc định trước khi so sánh.
    }

    @After fun cleanup() {
        settings.deleteLensSettings(lensId)
        settings.deleteLensSettings(controlId)
        files.forEach(File::delete)
    }

    private fun sample(profile: LensProfile = LensProfile.ANAMORPHIC, custom: OpticalCustomPreset? = null) =
        LensOpticalProfile(profile, 3.3f, 1.3f, 22f, 250L, 0.45f, true, custom)

    private fun file(bytes: ByteArray? = null): File =
        File(context.cacheDir, "f036-int-${UUID.randomUUID()}.json").also {
            bytes?.let { content -> it.writeBytes(content) }
            files += it
        }

    private fun paddedTo(size: Int): ByteArray {
        val json = sample().toJson().toByteArray(Charsets.UTF_8)
        return json + ByteArray(size - json.size) { ' '.code.toByte() }
    }

    @Test fun everyProfileAndOptionalCustomRoundTripThroughRealResolver() = runBlocking {
        val resolver = context.contentResolver
        LensProfile.entries.forEach { shape ->
            listOf(null, OpticalCustomPreset(4.1f, 1.6f, 300L)).forEach { custom ->
                val value = sample(shape, custom)
                val target = file()
                assertTrue(LensOpticalProfileIo.write(resolver, Uri.fromFile(target), value))
                assertEquals(OpticalProfileReadResult.Success(value), LensOpticalProfileIo.read(resolver, Uri.fromFile(target)))
            }
        }
    }

    @Test fun exportedFileNeverContainsLayoutOrIdentity() = runBlocking {
        val target = file()
        assertTrue(LensOpticalProfileIo.write(context.contentResolver, Uri.fromFile(target), sample()))
        val text = target.readText()
        val root = org.json.JSONObject(text)
        assertEquals(setOf("format", "version", "optical"), root.keys().asSequence().toSet())
        listOf("package", "lensId", "layout", "hidden", "selection", "favorite").forEach {
            assertFalse("file must not mention $it", text.contains(it, ignoreCase = true))
        }
        assertFalse(text.contains(lensId))
    }

    @Test fun byteCeilingAndEncodingAreEnforcedOnRealFiles() = runBlocking {
        val resolver = context.contentResolver
        assertEquals(
            OpticalProfileReadResult.Success(sample()),
            LensOpticalProfileIo.read(resolver, Uri.fromFile(file(paddedTo(LensOpticalProfile.MAX_BYTES))))
        )
        assertEquals(
            OpticalProfileReadResult.TooLarge,
            LensOpticalProfileIo.read(resolver, Uri.fromFile(file(paddedTo(LensOpticalProfile.MAX_BYTES + 1))))
        )
        assertEquals(
            OpticalProfileReadResult.Malformed,
            LensOpticalProfileIo.read(resolver, Uri.fromFile(file(byteArrayOf(0xc3.toByte(), 0x28))))
        )
        assertEquals(
            OpticalProfileReadResult.Malformed,
            LensOpticalProfileIo.read(resolver, Uri.fromFile(file("{\"format\":\"workspace\"}".toByteArray())))
        )
    }

    @Test fun missingAndUnreadableSourcesReportFailureWithoutTouchingPreferences() = runBlocking {
        val before = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all.toMap()
        val resolver = context.contentResolver
        val absent = File(context.cacheDir, "f036-missing-${UUID.randomUUID()}.json")
        assertEquals(OpticalProfileReadResult.IoFailure, LensOpticalProfileIo.read(resolver, Uri.fromFile(absent)))
        assertEquals(OpticalProfileReadResult.IoFailure, LensOpticalProfileIo.read(resolver, Uri.fromFile(context.cacheDir)))
        assertFalse(LensOpticalProfileIo.write(resolver, Uri.fromFile(File(absent, "child.json")), sample()))
        assertEquals(before, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all)
    }

    private fun lensView(id: String): LensView {
        lateinit var view: LensView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = LensView(themed)
            view.lensId = id
            view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            view.setApps(ArrayList((0 until APP_COUNT).map { App(id = it, label = "A$it", packageName = "p.$it", name = "N$it") }))
        }
        return view
    }

    private fun drawn(view: LensView, x: Float, y: Float, multiplier: Float): List<RectF> {
        var rects: List<RectF> = emptyList()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.setLensStateForTest(x, y, multiplier, false)
            view.draw(Canvas())
            rects = view.drawnRectsForTest()
        }
        return rects
    }

    @Test fun lensViewDrawsWithTheImportedProfileNotTheOldOne() = runBlocking {
        settings.saveLensProfile(lensId, LensProfile.FISHEYE)
        settings.saveDistortionFactor(lensId, 2.5f)
        settings.saveLensProfile(controlId, LensProfile.FISHEYE)
        settings.saveDistortionFactor(controlId, 2.5f)
        val view = lensView(lensId)
        val control = lensView(controlId)
        val base = drawn(view, -Float.MAX_VALUE, -Float.MAX_VALUE, 0f)
        val fx = base[CHECK_INDEX].centerX() + FINGER_OFFSET_PX
        val fy = base[CHECK_INDEX].centerY() + FINGER_OFFSET_PX
        val before = drawn(view, fx, fy, 1f)[CHECK_INDEX]
        assertNotEquals("Fisheye warps vertically", base[CHECK_INDEX].centerY(), before.centerY(), EPSILON)

        val file = file(sample(LensProfile.ANAMORPHIC).toJson().toByteArray())
        val parsed = (LensOpticalProfileIo.read(context.contentResolver, Uri.fromFile(file)) as OpticalProfileReadResult.Success).value
        assertEquals(OpticalProfileApplyResult.APPLIED, settings.applyOpticalProfile(lensId, parsed))

        val reread = lensView(lensId)
        val unchangedBase = drawn(reread, -Float.MAX_VALUE, -Float.MAX_VALUE, 0f)
        val after = drawn(reread, fx, fy, 1f)[CHECK_INDEX]
        assertEquals("Anamorphic leaves Y untouched", unchangedBase[CHECK_INDEX].centerY(), after.centerY(), EPSILON)
        assertNotEquals("Anamorphic still warps X", unchangedBase[CHECK_INDEX].centerX(), after.centerX(), EPSILON)
        val ctl = drawn(control, fx, fy, 1f)[CHECK_INDEX]
        assertNotEquals("The untouched control lens keeps warping Y", base[CHECK_INDEX].centerY(), ctl.centerY(), EPSILON)
    }

    @Test fun importedValuesPersistAcrossANewSettingsInstanceAndOtherLensesKeepTheirs() = runBlocking {
        settings.saveDistortionFactor(controlId, 4.4f)
        settings.saveLensProfile(controlId, LensProfile.CYLINDRICAL)
        assertEquals(OpticalProfileApplyResult.APPLIED, settings.applyOpticalProfile(lensId, sample(custom = OpticalCustomPreset(4.7f, 1.8f, 330L))))

        val fresh = UtilSettings(context)
        assertEquals(3.3f, fresh.getDistortionFactor(lensId), 0f)
        assertEquals(LensProfile.ANAMORPHIC, fresh.getLensProfile(lensId))
        assertEquals(0.45f, fresh.getLensHood(lensId), 0f)
        assertEquals(4.7f, fresh.getCustomDistortionFactor(lensId), 0f)
        assertEquals(4.4f, fresh.getDistortionFactor(controlId), 0f)
        assertEquals(LensProfile.CYLINDRICAL, fresh.getLensProfile(controlId))
        assertEquals(OpticalProfileReadResult.Success(fresh.captureOpticalProfile(lensId)!!), roundTrip(fresh.captureOpticalProfile(lensId)!!))
    }

    private fun roundTrip(value: LensOpticalProfile): OpticalProfileReadResult = runBlocking {
        val target = file()
        assertTrue(LensOpticalProfileIo.write(context.contentResolver, Uri.fromFile(target), value))
        LensOpticalProfileIo.read(context.contentResolver, Uri.fromFile(target))
    }

    @Test fun malformedImportLeavesEveryPreferenceUntouched() = runBlocking {
        settings.saveDistortionFactor(lensId, 4.1f)
        val before = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all.toMap()
        val bad = file("""{"format":"lens-optical-profile","version":1,"optical":{"profile":"FISHEYE","distortionFactor":99}}""".toByteArray())
        assertEquals(OpticalProfileReadResult.Malformed, LensOpticalProfileIo.read(context.contentResolver, Uri.fromFile(bad)))
        assertEquals(before, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all)
    }

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val APP_COUNT = 25
        const val CHECK_INDEX = 6
        const val FINGER_OFFSET_PX = 150f
        const val EPSILON = 0.5f
    }
}
