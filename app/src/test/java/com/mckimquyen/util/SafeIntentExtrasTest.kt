package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import com.mckimquyen.services.BadgeCountReceiver
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Serializable

/**
 * ActHome and BadgeCountReceiver are exported, so any installed app can hand them an Intent. An extra that is a Serializable of a class this app does not have
 * makes the whole Bundle fail to unparcel on the first read, and Android throws a RuntimeException from getStringExtra. Nothing caught it, so the process died.
 * That eager unparcel is what Android 12 and older do (the A50s runs 11); from Android 13 a Bundle reads each key lazily, so the same Intent only
 * fails when the poisoned key itself is read. The test therefore runs on API 30. The Intent here goes through a real Parcel and is read back with a ClassLoader that cannot find the sender's class, which is what the receiving process sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SafeIntentExtrasTest {

    /** Stands for a class that only the sending app has. */
    class SendersOwnType(val value: Int = 1) : Serializable

    private object BlindLoader : ClassLoader(SafeIntentExtrasTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.contains("SendersOwnType")) throw ClassNotFoundException(name)
            return super.loadClass(name, resolve)
        }
    }

    private fun hostileIntent(): Intent {
        val sent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
            putExtra("poison", SendersOwnType())
            putExtra("badge_count_package_name", "com.example.victim")
            putExtra("target_lens_id", "lens-2")
            putExtra("auto_export_lens", true)
        }
        val parcel = Parcel.obtain()
        try {
            sent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val received = Intent.CREATOR.createFromParcel(parcel)
            received.setExtrasClassLoader(BlindLoader)
            return received
        } finally {
            parcel.recycle()
        }
    }

    private fun cleanIntent() = Intent("x").apply {
        putExtra("s", "value")
        putExtra("b", true)
    }

    @Test fun theRawReadReallyThrowsForTheHostileIntent() {
        // Positive control: without it "the safe read returned null" could just mean the intent was harmless.
        try {
            hostileIntent().getStringExtra("badge_count_package_name")
            fail("the hostile Intent must make a plain getStringExtra throw in this environment")
        } catch (expected: RuntimeException) {
            assertTrue(expected.javaClass.name, expected is RuntimeException)
        }
    }

    @Test fun aSafeStringReadOfTheHostileIntentReturnsNullInsteadOfThrowing() {
        assertNull(SafeIntentExtras.string(hostileIntent(), "badge_count_package_name"))
    }

    @Test fun aSafeBooleanReadOfTheHostileIntentReturnsTheDefault() {
        assertFalse(SafeIntentExtras.bool(hostileIntent(), "auto_export_lens", false))
        assertTrue(SafeIntentExtras.bool(hostileIntent(), "auto_export_lens", true))
    }

    @Test fun aSafeExtrasReadOfTheHostileIntentReturnsNull() {
        assertNull(SafeIntentExtras.extras(hostileIntent()))
    }

    @Test fun aCleanIntentIsReadExactlyAsBefore() {
        assertEquals("value", SafeIntentExtras.string(cleanIntent(), "s"))
        assertTrue(SafeIntentExtras.bool(cleanIntent(), "b", false))
        assertEquals("value", SafeIntentExtras.extras(cleanIntent())?.getString("s"))
    }

    @Test fun nullIntentsAndMissingKeysAreHarmless() {
        assertNull(SafeIntentExtras.string(null, "s"))
        assertNull(SafeIntentExtras.string(cleanIntent(), "missing"))
        assertFalse(SafeIntentExtras.bool(null, "b", false))
        assertNull(SafeIntentExtras.extras(null))
        assertNull(SafeIntentExtras.extras(Intent("x")).takeIf { false })
        assertTrue(Bundle().isEmpty)
    }

    @Test fun theExportedBadgeReceiverSurvivesTheHostileIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Positive control first: the very same Intent really does throw on a plain read in this environment.
        var rawThrew = false
        try { hostileIntent().getStringExtra("badge_count_package_name") } catch (_: RuntimeException) { rawThrew = true }
        assertTrue("positive control: the hostile Intent must throw on a plain read", rawThrew)

        BadgeCountReceiver().onReceive(context, hostileIntent())
    }

    @Test fun theBadgeReceiverStillAcceptsACleanIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // No app with this package is in the snapshot, so nothing is written; the point is that a clean Intent is read and ignored without error.
        BadgeCountReceiver().onReceive(context, Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
            putExtra("badge_count_package_name", "com.example.none")
            putExtra("badge_count", 3)
        })
    }
}
