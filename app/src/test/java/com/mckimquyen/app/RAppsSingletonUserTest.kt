package com.mckimquyen.app

import com.mckimquyen.model.App
import com.mckimquyen.model.AppIdentity
import com.mckimquyen.model.PinnedZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FEAT-012: the same package/component in two profiles are two different apps in the snapshot. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RAppsSingletonUserTest {

    private val personal = App(label = "Chat", packageName = "com.chat", name = "Main", userId = 0)
    private val work = App(label = "Chat", packageName = "com.chat", name = "Main", userId = 10, isWorkProfile = true)

    @Before
    fun setUp() {
        RAppsSingleton.instance.apps = arrayListOf(personal, work)
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.apps = null
    }

    @Test
    fun `findApp without a user keeps meaning the primary user`() {
        assertEquals(0, RAppsSingleton.instance.findApp("com.chat", "Main")?.userId)
    }

    @Test
    fun `findApp with a user returns that profile's copy`() {
        assertEquals(10, RAppsSingleton.instance.findApp("com.chat", "Main", 10)?.userId)
        assertTrue(RAppsSingleton.instance.findApp("com.chat", "Main", 10)!!.isWorkProfile)
        assertNull(RAppsSingleton.instance.findApp("com.chat", "Main", 11))
    }

    @Test
    fun `updating the work copy never touches the personal copy`() {
        RAppsSingleton.instance.updateAppState("com.chat", "Main", userId = 10, openCount = 7L, isVisible = false)

        assertEquals(0L, RAppsSingleton.instance.findApp("com.chat", "Main", 0)?.openCount)
        assertTrue(RAppsSingleton.instance.findApp("com.chat", "Main", 0)!!.isVisible)
        assertEquals(7L, RAppsSingleton.instance.findApp("com.chat", "Main", 10)?.openCount)
        assertFalse(RAppsSingleton.instance.findApp("com.chat", "Main", 10)!!.isVisible)
    }

    @Test
    fun `updating without a user still only changes the personal copy`() {
        RAppsSingleton.instance.updateAppState("com.chat", "Main", openCount = 3L)

        assertEquals(3L, RAppsSingleton.instance.findApp("com.chat", "Main", 0)?.openCount)
        assertEquals(0L, RAppsSingleton.instance.findApp("com.chat", "Main", 10)?.openCount)
    }

    @Test
    fun `organization and order updates are per user`() {
        RAppsSingleton.instance.updateOrganization("com.chat", "Main", true, "Work", PinnedZone.START, userId = 10)
        RAppsSingleton.instance.updateAppOrder("com.chat", "Main", 4, userId = 10)

        val p = RAppsSingleton.instance.findApp("com.chat", "Main", 0)!!
        val w = RAppsSingleton.instance.findApp("com.chat", "Main", 10)!!
        assertFalse(p.isFavorite)
        assertEquals(-1, p.orderNumber)
        assertTrue(w.isFavorite)
        assertEquals("Work", w.folderName)
        assertEquals(4, w.orderNumber)
    }

    @Test
    fun `an app exposes its identity key including the user`() {
        assertEquals("com.chat-Main", personal.identifier)
        assertEquals("com.chat-Main@u10", work.identifier)
        assertEquals(AppIdentity.identifier("com.chat", "Main", 10), work.identifier)
        assertNotEquals(personal.identifier, work.identifier)
    }

    @Test
    fun `a default app is a primary user app so old call sites are unaffected`() {
        val legacy = App(label = "X", packageName = "p", name = "n")

        assertEquals(0, legacy.userId)
        assertFalse(legacy.isWorkProfile)
        assertEquals("p-n", legacy.identifier)
    }
}
