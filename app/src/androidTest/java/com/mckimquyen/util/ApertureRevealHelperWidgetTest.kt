package com.mckimquyen.util

import android.app.Dialog
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.ui.ActHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * B5 (test-audit): [ApertureRevealHelper.dismissWithReveal] guards its own `afterDismiss` behind
 * a `completed` flag (both [android.animation.Animator.AnimatorListenerAdapter.onAnimationEnd]
 * and `onAnimationCancel` call `complete()`), but nothing in the test tree ever exercised that
 * guard - `ApertureRevealHelperTest` only covers the two pure geometry functions. This locks the
 * "exactly once" invariant in on a real [Dialog]/[android.view.Window], both on the normal
 * animated path and the reduced-motion immediate path.
 */
@RunWith(AndroidJUnit4::class)
class ApertureRevealHelperWidgetTest {

    private fun runShell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)))
            .use { it.readText() }
    }

    private fun showRealDialog(scenario: ActivityScenario<ActHome>): Dialog {
        var dialog: Dialog? = null
        scenario.onActivity { activity ->
            dialog = Dialog(activity).apply {
                setContentView(FrameLayout(activity).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(200, 200)
                })
                ApertureRevealHelper.prepareDialog(this)
                show()
            }
        }
        return dialog!!
    }

    @Test
    fun dismissWithReveal_normalAnimatedPath_callsAfterDismissExactlyOnce() {
        val original = runShell("settings get global animator_duration_scale").trim()
        try {
            runShell("settings put global animator_duration_scale 1")
            val scenario = ActivityScenario.launch(ActHome::class.java)
            val dialog = showRealDialog(scenario)

            val callCount = AtomicInteger(0)
            val latch = CountDownLatch(1)
            scenario.onActivity {
                ApertureRevealHelper.dismissWithReveal(dialog, null) {
                    callCount.incrementAndGet()
                    latch.countDown()
                }
            }

            assertTrue(
                "afterDismiss must fire within 2s of a ${ApertureRevealHelper.DEFAULT_DURATION_MS}ms reveal animation",
                latch.await(2, TimeUnit.SECONDS)
            )
            // The latch only proves "at least once, in time" - proving "exactly once" (the actual
            // guard being tested) requires waiting past the animation's own duration so a second,
            // buggy onAnimationEnd/onAnimationCancel firing would have had its chance to land
            // before we read the counter. This is a deliberate wait-for-an-absence, not the
            // sleep-instead-of-synchronizing anti-pattern this test suite otherwise avoids.
            Thread.sleep(ApertureRevealHelper.DEFAULT_DURATION_MS + 300)
            assertEquals("afterDismiss must run exactly once, not on every animator callback", 1, callCount.get())
            assertFalse("the dialog must actually be dismissed once afterDismiss ran", dialog.isShowing)
            scenario.close()
        } finally {
            val restoreValue = if (original.isBlank() || original == "null") "1" else original
            runShell("settings put global animator_duration_scale $restoreValue")
        }
    }

    @Test
    fun dismissWithReveal_reducedMotion_dismissesImmediatelyAndCallsAfterDismissExactlyOnce() {
        val original = runShell("settings get global animator_duration_scale").trim()
        try {
            runShell("settings put global animator_duration_scale 0")
            val scenario = ActivityScenario.launch(ActHome::class.java)
            val dialog = showRealDialog(scenario)

            val callCount = AtomicInteger(0)
            scenario.onActivity {
                ApertureRevealHelper.dismissWithReveal(dialog, null) { callCount.incrementAndGet() }
                // Reduced motion skips the animator entirely (see shouldAnimate), so the dismiss
                // and the callback must already be done by the time dismissWithReveal returns -
                // no latch/wait needed here, unlike the animated path above.
                assertEquals("reduced motion must call afterDismiss exactly once, synchronously", 1, callCount.get())
                assertFalse(dialog.isShowing)
            }
            scenario.close()
        } finally {
            val restoreValue = if (original.isBlank() || original == "null") "1" else original
            runShell("settings put global animator_duration_scale $restoreValue")
        }
    }
}
