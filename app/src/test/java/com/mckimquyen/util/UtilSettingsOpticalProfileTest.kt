package com.mckimquyen.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensOpticalProfile
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.OpticalCustomPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsOpticalProfileTest {
    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val work = "work-lens"
    private val other = "other-lens"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun raw() = PreferenceManager.getDefaultSharedPreferences(context)

    private fun sample(custom: OpticalCustomPreset? = null) = LensOpticalProfile(
        LensProfile.ANAMORPHIC, 3.3f, 1.3f, 22f, 250L, 0.45f, true, custom
    )

    private fun seedTarget() {
        settings.saveDistortionFactor(work, 4.1f)
        settings.saveIconSize(work, 30f)
        settings.saveSmartFocusBias(work, false)
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        settings.saveLensHood(work, 0.7f)
        raw().edit().putFloat(UtilSettings.KEY_SCALE_FACTOR, 2f)
            .putLong(UtilSettings.KEY_ANIMATION_TIME, 300L).commit()
    }

    @Test fun captureIsReadOnlyAndResolvesInheritedValues() {
        raw().edit().putFloat(UtilSettings.KEY_DISTORTION_FACTOR, 4.1f)
            .putFloat(UtilSettings.KEY_ICON_SIZE, 33f)
            .putBoolean(UtilSettings.KEY_SMART_FOCUS_BIAS, true)
            .putFloat(UtilSettings.KEY_SCALE_FACTOR, 2.2f)
            .putLong(UtilSettings.KEY_ANIMATION_TIME, 250L).commit()
        val before = raw().all.toMap()

        val value = requireNotNull(settings.captureOpticalProfile(work))

        assertEquals(4.1f, value.distortionFactor, 0f)
        assertEquals(33f, value.iconSize, 0f)
        assertTrue(value.smartFocusBias)
        assertEquals(LensProfile.FISHEYE, value.profile)
        assertEquals(LensHood.FULL, value.hood, 0f)
        assertEquals(2.2f, value.scaleFactor, 0f)
        assertEquals(250L, value.animationTimeMs)
        assertEquals(before, raw().all)
    }

    @Test fun captureRejectsWrongTypedOrOutOfRangeValuesWithoutRepairingPrefs() {
        raw().edit().putString(UtilSettings.KEY_DISTORTION_FACTOR, "bad").commit()
        var before = raw().all.toMap()
        assertNull(settings.captureOpticalProfile(work))
        assertEquals(before, raw().all)

        raw().edit().clear().putFloat(UtilSettings.KEY_DISTORTION_FACTOR, 9f).commit()
        before = raw().all.toMap()
        assertNull(settings.captureOpticalProfile(work))
        assertEquals(before, raw().all)
    }

    @Test fun captureIncludesCustomOnlyWhenPresetExists() {
        assertNull(requireNotNull(settings.captureOpticalProfile(work)).custom)
        settings.saveCustomDistortionFactor(work, 4.2f)
        settings.saveCustomScaleFactor(1.6f)
        settings.saveCustomAnimationTime(320L)

        assertEquals(OpticalCustomPreset(4.2f, 1.6f, 320L), requireNotNull(settings.captureOpticalProfile(work)).custom)
    }

    @Test fun touchedKeysCoverOnlyOpticalFieldsAndOptionalCustom() {
        assertEquals(
            setOf(
                "${UtilSettings.KEY_DISTORTION_FACTOR}_$work", "${UtilSettings.KEY_ICON_SIZE}_$work",
                "${UtilSettings.KEY_SMART_FOCUS_BIAS}_$work", "${UtilSettings.KEY_LENS_PROFILE}_$work",
                "${UtilSettings.KEY_LENS_HOOD}_$work", "${UtilSettings.KEY_PENDING_DISTORTION_FACTOR}_$work",
                UtilSettings.KEY_SCALE_FACTOR, UtilSettings.KEY_ANIMATION_TIME
            ),
            settings.opticalProfileKeys(work, includesCustom = false)
        )
        assertEquals(
            setOf("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_$work", UtilSettings.KEY_CUSTOM_SCALE_FACTOR,
                UtilSettings.KEY_CUSTOM_ANIMATION_TIME),
            settings.opticalProfileKeys(work, true) - settings.opticalProfileKeys(work, false)
        )
    }

    @Test fun applyWritesTargetAndGlobalFieldsAndPreservesUnrelatedState() {
        seedTarget()
        settings.saveDistortionFactor(other, 4.4f)
        settings.saveCustomDistortionFactor(other, 3.9f)
        settings.saveCustomScaleFactor(1.6f)
        settings.saveCustomAnimationTime(310L)
        settings.savePendingDistortionFactor(work, 4.8f)
        raw().edit().putString(UtilSettings.KEY_ACTIVE_LENS_ID, other).commit()

        assertEquals(OpticalProfileApplyResult.APPLIED, settings.applyOpticalProfile(work, sample()))

        assertEquals(3.3f, settings.getDistortionFactor(work), 0f)
        assertEquals(22f, settings.getIconSize(work), 0f)
        assertTrue(settings.isSmartFocusBias(work))
        assertEquals(LensProfile.ANAMORPHIC, settings.getLensProfile(work))
        assertEquals(0.45f, settings.getLensHood(work), 0f)
        assertEquals(1.3f, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR), 0f)
        assertEquals(250L, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))
        assertNull(settings.getPendingDistortionFactor(work))
        assertEquals(4.4f, settings.getDistortionFactor(other), 0f)
        assertEquals(3.9f, settings.getCustomDistortionFactor(other), 0f)
        assertEquals(1.6f, settings.getCustomScaleFactor(), 0f)
        assertEquals(310L, settings.getCustomAnimationTime())
        assertEquals(other, raw().getString(UtilSettings.KEY_ACTIVE_LENS_ID, null))
    }

    @Test fun applyCustomUpdatesTargetDistortionAndGlobalScaleTime() {
        settings.saveCustomDistortionFactor(other, 3.9f)

        assertEquals(
            OpticalProfileApplyResult.APPLIED,
            settings.applyOpticalProfile(work, sample(OpticalCustomPreset(4.7f, 1.8f, 330L)))
        )

        assertEquals(4.7f, settings.getCustomDistortionFactor(work), 0f)
        assertEquals(1.8f, settings.getCustomScaleFactor(), 0f)
        assertEquals(330L, settings.getCustomAnimationTime())
        assertEquals(3.9f, settings.getCustomDistortionFactor(other), 0f)
        assertTrue(settings.hasCustomPreset())
    }

    @Test fun defaultLensUsesSharedKeysAndDoesNotChangeSecondaryOverride() {
        settings.saveDistortionFactor(other, 4.4f)

        assertEquals(OpticalProfileApplyResult.APPLIED, settings.applyOpticalProfile(LensWorkspace.DEFAULT_LENS_ID, sample()))

        assertEquals(3.3f, raw().getFloat(UtilSettings.KEY_DISTORTION_FACTOR, 0f), 0f)
        assertEquals(22f, raw().getFloat(UtilSettings.KEY_ICON_SIZE, 0f), 0f)
        assertEquals(4.4f, settings.getDistortionFactor(other), 0f)
    }

    @Test fun invalidInputRejectsWithoutOpeningAnEditor() {
        val before = raw().all.toMap()

        assertEquals(OpticalProfileApplyResult.FAILED_RESTORED, settings.applyOpticalProfile("", sample()))
        assertEquals(OpticalProfileApplyResult.FAILED_RESTORED, settings.applyOpticalProfile(work, sample().copy(hood = 0f)))

        assertEquals(before, raw().all)
    }

    @Test fun failedCommitRestoresExactTouchedKeysAndKeepsUnrelatedValues() {
        seedTarget()
        settings.savePendingDistortionFactor(work, 4.8f)
        raw().edit().putString("unrelated", "keep").commit()
        val failing = FailingCommitPrefs(raw(), failFirstCommit = true)
        val subject = UtilSettings(contextWith(failing))
        val before = raw().all.toMap()

        assertEquals(OpticalProfileApplyResult.FAILED_RESTORED, subject.applyOpticalProfile(work, sample()))

        assertEquals(before, raw().all)
        assertEquals(2, failing.commitCalls)
    }

    @Test fun failedRollbackIsReportedSeparately() {
        seedTarget()
        val failing = FailingCommitPrefs(raw(), failFirstCommit = true, failSecondCommit = true)
        val subject = UtilSettings(contextWith(failing))

        assertEquals(OpticalProfileApplyResult.RESTORE_FAILED, subject.applyOpticalProfile(work, sample()))
        assertEquals(2, failing.commitCalls)
    }

    @Test fun successfulApplyUsesExactlyOneCommit() {
        val counting = FailingCommitPrefs(raw())
        val subject = UtilSettings(contextWith(counting))

        assertEquals(OpticalProfileApplyResult.APPLIED, subject.applyOpticalProfile(work, sample()))

        assertEquals(1, counting.commitCalls)
    }

    @Test fun absentKeysBecomeAbsentAfterRollback() {
        val failing = FailingCommitPrefs(raw(), failFirstCommit = true)
        val subject = UtilSettings(contextWith(failing))

        assertEquals(OpticalProfileApplyResult.FAILED_RESTORED, subject.applyOpticalProfile(work, sample(OpticalCustomPreset(4f, 1f, 200L))))

        assertFalse(raw().contains("${UtilSettings.KEY_DISTORTION_FACTOR}_$work"))
        assertFalse(raw().contains(UtilSettings.KEY_CUSTOM_SCALE_FACTOR))
    }

    private fun contextWith(prefs: SharedPreferences): Context {
        val appContext = mock(Context::class.java)
        `when`(appContext.applicationContext).thenReturn(appContext)
        `when`(appContext.resources).thenReturn(context.resources)
        `when`(appContext.packageName).thenReturn(context.packageName)
        `when`(appContext.getSharedPreferences(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(prefs)
        return appContext
    }

    /** Delegates to real prefs but makes selected Editor.commit calls fail after applying no value. */
    private class FailingCommitPrefs(
        private val delegate: SharedPreferences,
        private val failFirstCommit: Boolean = false,
        private val failSecondCommit: Boolean = false
    ) : SharedPreferences by delegate {
        var commitCalls = 0
            private set

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by delegate.edit() {
            private val real = delegate.edit()
            override fun putString(key: String, value: String?) = apply { real.putString(key, value) }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { real.putStringSet(key, values) }
            override fun putInt(key: String, value: Int) = apply { real.putInt(key, value) }
            override fun putLong(key: String, value: Long) = apply { real.putLong(key, value) }
            override fun putFloat(key: String, value: Float) = apply { real.putFloat(key, value) }
            override fun putBoolean(key: String, value: Boolean) = apply { real.putBoolean(key, value) }
            override fun remove(key: String) = apply { real.remove(key) }
            override fun clear() = apply { real.clear() }
            override fun apply() = real.apply()
            override fun commit(): Boolean {
                commitCalls++
                val shouldFail = (commitCalls == 1 && failFirstCommit) || (commitCalls == 2 && failSecondCommit)
                // Một lần ghi hỏng một phần được mô phỏng bằng commit thật rồi báo false để thử rollback.
                val committed = real.commit()
                return committed && !shouldFail
            }
        }
    }
}
