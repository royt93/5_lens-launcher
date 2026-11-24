package com.mckimquyen.util

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tapping an app on the lens goes through UtilApp.launchComponent, which caught only ActivityNotFoundException. An app whose launcher activity is guarded by a
 * permission this app does not hold (the same defect class as the timer quick action that crashed a Pixel) refuses the start with a SecurityException that
 * then escaped to the main thread. A real Activity that refuses or accepts the start stands in for the guarded app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilAppLaunchRefusedTest {

    class GuardedHost : AppCompatActivity() {
        override fun startActivity(intent: Intent?, options: Bundle?) {
            started++
            if (refuse) throw SecurityException("Permission Denial: requires a permission the app does not hold")
        }

        override fun startActivity(intent: Intent?) = startActivity(intent, null)

        companion object {
            var refuse = false
            var started = 0
        }
    }

    private val packageName = "com.example.guarded"
    private val activityName = "com.example.guarded.Main"
    private var snapshotBefore: ArrayList<App>? = null

    @Before fun seed() {
        GuardedHost.started = 0
        snapshotBefore = RAppsSingleton.instance.apps
        // isOpened=true: the app is not biometric-locked, so launchComponent starts it straight away.
        RAppsSingleton.instance.apps = arrayListOf(App(label = "Guarded", packageName = packageName, name = activityName, isOpened = true))
    }

    @After fun restore() {
        RAppsSingleton.instance.apps = snapshotBefore
    }

    private fun host(refuse: Boolean): Activity {
        GuardedHost.refuse = refuse
        return Robolectric.buildActivity(GuardedHost::class.java).setup().get()
    }

    @Test fun anAcceptedLaunchStartsTheActivityOnce() {
        val activity = host(refuse = false)

        UtilApp.launchComponent(activity, packageName, "Guarded", activityName, null, null)

        assertEquals("positive control: the start reached startActivity", 1, GuardedHost.started)
    }

    @Test fun aRefusedLaunchDoesNotThrow() {
        val activity = host(refuse = true)

        UtilApp.launchComponent(activity, packageName, "Guarded", activityName, null, null)

        assertTrue("positive control: the start was attempted", GuardedHost.started >= 1)
    }
}
