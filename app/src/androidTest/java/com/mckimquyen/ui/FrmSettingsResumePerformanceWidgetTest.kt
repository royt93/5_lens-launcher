package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PERF-008: Settings avoids repeating package-manager discovery for unchanged values. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsResumePerformanceWidgetTest {
    @get:Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun repeatedResumeWithUnchangedSettings_reusesLauncherAndIconPackValues() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            var initialLauncherResolveCount = 0
            scenario.onFragment { fragment ->
                initialLauncherResolveCount = fragment.launcherResolveCountForTest
            }
            repeat(RESUME_COUNT) {
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
            scenario.onFragment { fragment ->
                assertNotNull(fragment.requireView().findViewById<android.view.View>(R.id.tvSelectedHomeLauncher))
                assertEquals(
                    "unchanged launcher state should not resolve again during resume binds",
                    initialLauncherResolveCount,
                    fragment.launcherResolveCountForTest
                )
            }
        }
    }

    @Test
    fun onValuesUpdated_rebindsButKeepsValidLauncherLabelCache() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val before = fragment.launcherResolveCountForTest
                (fragment as com.mckimquyen.itf.SettingsInterface).onValuesUpdated()
                assertEquals("settings values update should reuse still-valid launcher cache", before, fragment.launcherResolveCountForTest)
            }
        }
    }

    @Test
    fun homeLauncherChooserInvalidatesCacheForNextResume() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            var before = 0
            scenario.onFragment { fragment ->
                before = fragment.launcherResolveCountForTest
                fragment.onHomeLauncherChooserStarted()
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onFragment { fragment ->
                assertEquals(before + 1, fragment.launcherResolveCountForTest)
            }
        }
    }

    @Test
    fun returningFromStoppedStateRefreshesExternalLauncherCache() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            var before = 0
            scenario.onFragment { before = it.launcherResolveCountForTest }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onFragment { fragment ->
                assertEquals("quay lại từ ngoài app phải làm mới launcher", before + 1,
                    fragment.launcherResolveCountForTest)
            }
        }
    }

    @Test
    fun unchangedValuesDoNotRewriteTextOrRecreateColorDrawables() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                val highlight = root.findViewById<android.widget.ImageView>(R.id.ivSelectedHighlightColor)
                val originalDrawable = highlight.drawable
                val text = root.findViewById<android.widget.TextView>(R.id.tvSelectedSearchHint)
                val label = root.findViewById<android.widget.TextView>(R.id.tvSelectedHomeLauncher)
                var writes = 0
                val watcher = object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { writes++ }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                }
                text.addTextChangedListener(watcher)
                label.addTextChangedListener(watcher)
                try {
                    repeat(RESUME_COUNT) { fragment.onValuesUpdated() }
                    assertEquals("nội dung không đổi không được gán text lại", 0, writes)
                    org.junit.Assert.assertSame("màu không đổi phải giữ drawable", originalDrawable, highlight.drawable)
                } finally {
                    text.removeTextChangedListener(watcher)
                    label.removeTextChangedListener(watcher)
                }
            }
        }
    }

    @Test
    fun changedPreferenceUpdatesVisibleHintWithoutResolvingLauncherAgain() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val before = fragment.launcherResolveCountForTest
                com.mckimquyen.util.UtilSettings(context).save(
                    com.mckimquyen.util.UtilSettings.KEY_SEARCH_HINT_TEXT, "PERF-008 hint")
                fragment.onValuesUpdated()
                val text = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvSelectedSearchHint)
                assertEquals("PERF-008 hint", text.text.toString())
                assertEquals(before, fragment.launcherResolveCountForTest)
            }
        }
    }

    @Test
    fun measureRepeatedSettingsBinding() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                repeat(WARM_UP_COUNT) { fragment.onValuesUpdated() }
                val samples = LongArray(SAMPLE_COUNT) {
                    val start = android.os.SystemClock.elapsedRealtimeNanos()
                    fragment.onValuesUpdated()
                    android.os.SystemClock.elapsedRealtimeNanos() - start
                }.sorted()
                val result = "bind p50Ns=${samples[SAMPLE_COUNT / 2]} p95Ns=${samples[(SAMPLE_COUNT - 1) * 95 / 100]} samples=$SAMPLE_COUNT"
                java.io.File(context.cacheDir, TIMING_FILE).writeText(result)
            }
        }
    }

    private companion object {
        const val RESUME_COUNT = 3
        const val WARM_UP_COUNT = 5
        const val SAMPLE_COUNT = 30
        const val TIMING_FILE = "perf008-binding-timing.txt"
    }
}
