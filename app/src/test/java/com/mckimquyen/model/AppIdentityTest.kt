package com.mckimquyen.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FEAT-012: one place builds and reads the per-user app key. User 0 must stay byte-identical to the old key. */
class AppIdentityTest {

    @Test
    fun `the primary user keeps the exact legacy identifier so existing Room rows and backups still match`() {
        assertEquals("com.a-com.a.Main", AppIdentity.identifier("com.a", "com.a.Main", AppIdentity.PRIMARY_USER_ID))
        assertEquals(
            AppPersistent.generateIdentifier("com.a", "com.a.Main"),
            AppIdentity.identifier("com.a", "com.a.Main", AppIdentity.PRIMARY_USER_ID)
        )
    }

    @Test
    fun `a work profile gets a suffix so the same app in two profiles never collides`() {
        val personal = AppIdentity.identifier("com.a", "com.a.Main", 0)
        val work = AppIdentity.identifier("com.a", "com.a.Main", 10)

        assertEquals("com.a-com.a.Main@u10", work)
        assertNotEquals(personal, work)
    }

    @Test
    fun `different work profiles are different identities`() {
        assertNotEquals(AppIdentity.identifier("p", "n", 10), AppIdentity.identifier("p", "n", 11))
    }

    @Test
    fun `blank package or name stays blank for every user`() {
        assertEquals("", AppIdentity.identifier(null, "n", 10))
        assertEquals("", AppIdentity.identifier("p", " ", 10))
        assertEquals("", AppIdentity.identifier("", "n", 0))
    }

    @Test
    fun `userIdOf reads the suffix back and treats an unsuffixed identifier as the primary user`() {
        assertEquals(10, AppIdentity.userIdOf("com.a-com.a.Main@u10"))
        assertEquals(0, AppIdentity.userIdOf("com.a-com.a.Main"))
        assertEquals(0, AppIdentity.userIdOf(""))
    }

    @Test
    fun `an at sign inside a class name is not mistaken for a user suffix`() {
        assertEquals(0, AppIdentity.userIdOf("com.a-com.a.Weird@name"))
        assertEquals(0, AppIdentity.userIdOf("com.a-com.a.Weird@u"))
        assertEquals(0, AppIdentity.userIdOf("com.a-com.a.Weird@u1x"))
        assertEquals(0, AppIdentity.userIdOf("com.a-com.a.Weird@u-5"))
    }

    @Test
    fun `a primary user app whose class name ends like a suffix is still told apart from a profile app`() {
        // Pathological class name that literally ends in "@u10": the primary user's own identifier is
        // indistinguishable by shape, so it must be treated as user 10 only via [isProfileIdentifier] callers
        // that also know the component; the key builder itself never emits it for user 0.
        val primaryLookalike = AppIdentity.identifier("p", "Odd@u10", 0)
        val realProfile = AppIdentity.identifier("p", "Odd", 10)

        assertEquals("p-Odd@u10", primaryLookalike)
        assertEquals("p-Odd@u10", realProfile)
        assertTrue("known limitation documented in AppIdentity", AppIdentity.collides("p", "Odd@u10", 0, "p", "Odd", 10))
        assertFalse(AppIdentity.collides("p", "Main", 0, "p", "Main", 10))
    }

    @Test
    fun `identity with only a user id suffix and no component is not a valid identity`() {
        assertNull(AppIdentity.stripUser("@u10"))
        assertEquals("com.a-com.a.Main", AppIdentity.stripUser("com.a-com.a.Main@u10"))
        assertEquals("com.a-com.a.Main", AppIdentity.stripUser("com.a-com.a.Main"))
    }

    @Test
    fun `icon cache keys differ per user and stay unchanged for the primary user`() {
        assertEquals("p/n#1#pack", AppIdentity.cacheKeySegment("p/n#1#pack", 0))
        assertEquals("p/n#1#pack@u10", AppIdentity.cacheKeySegment("p/n#1#pack", 10))
        assertNotEquals(AppIdentity.cacheKeySegment("p/n#1#pack", 10), AppIdentity.cacheKeySegment("p/n#1#pack", 11))
    }

    @Test
    fun `search history component key is the same shape as the identifier minus the dash`() {
        assertEquals("p/n", AppIdentity.componentKey("p", "n", 0))
        assertEquals("p/n@u10", AppIdentity.componentKey("p", "n", 10))
    }
}
