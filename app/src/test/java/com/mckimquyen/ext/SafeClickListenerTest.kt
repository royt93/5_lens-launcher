package com.mckimquyen.ext

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Coverage gap found by whole-codebase audit sweep: SafeClickListener's own doc comment calls
 * out "critical actions (e.g. submit, purchase)" as an intended use, but it had zero test at
 * any tier before this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SafeClickListenerTest {

    private val view get() = View(RuntimeEnvironment.getApplication())

    @Before
    fun advancePastBoot() {
        // Robolectric's fake elapsedRealtime() starts at 0 - SafeClickListener's own
        // lastTimeClicked also starts at 0, so an unadjusted clock makes the very first click
        // of a test look like it's "inside the debounce window" of an imaginary click at t=0.
        // Advance well past any interval used below so every test starts from a clean baseline.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun `first click always fires`() {
        var clicks = 0
        val listener = SafeClickListener(defaultInterval = 600) { clicks++ }

        listener.onClick(view)

        assertEquals(1, clicks)
    }

    @Test
    fun `second click inside the debounce window is suppressed`() {
        var clicks = 0
        val listener = SafeClickListener(defaultInterval = 600) { clicks++ }

        listener.onClick(view)
        listener.onClick(view)

        assertEquals(1, clicks)
    }

    @Test
    fun `click exactly at the interval boundary fires (strictly-less-than suppression)`() {
        var clicks = 0
        val listener = SafeClickListener(defaultInterval = 600) { clicks++ }

        listener.onClick(view)
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        listener.onClick(view)

        assertEquals(2, clicks)
    }

    @Test
    fun `click after the debounce window elapses fires again`() {
        var clicks = 0
        val listener = SafeClickListener(defaultInterval = 600) { clicks++ }

        listener.onClick(view)
        ShadowSystemClock.advanceBy(Duration.ofMillis(601))
        listener.onClick(view)

        assertEquals(2, clicks)
    }

    @Test
    fun `a shorter custom interval debounces on its own schedule`() {
        var clicks = 0
        val listener = SafeClickListener(defaultInterval = 100) { clicks++ }

        listener.onClick(view)
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        listener.onClick(view) // still inside 100ms window
        ShadowSystemClock.advanceBy(Duration.ofMillis(60))
        listener.onClick(view) // 110ms since first click, past the window

        assertEquals(2, clicks)
    }

    @Test
    fun `View setSafeOnClickListener wires a debounced listener end to end`() {
        var clicks = 0
        val target = view
        target.setSafeOnClickListener { clicks++ }

        target.performClick()
        target.performClick()

        assertEquals(1, clicks)
    }
}
