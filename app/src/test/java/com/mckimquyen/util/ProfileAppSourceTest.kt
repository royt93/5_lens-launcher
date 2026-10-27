package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FEAT-012: merging apps from every profile, deciding who gets a badge, and which user launches. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ProfileAppSourceTest {

    private fun entry(pkg: String, name: String, user: Int, label: String = pkg, work: Boolean = user != 0) =
        ProfileAppEntry(pkg, name, label, user, work)

    private class FakeSource(
        val profiles: List<Int>,
        val apps: Map<Int, List<ProfileAppEntry>>,
        val failing: Set<Int> = emptySet(),
        val allowedProfiles: Set<Int> = profiles.toSet()
    ) : ProfileDirectory {
        override fun profileUserIds(): List<Int> = profiles
        override fun appsOf(userId: Int): List<ProfileAppEntry> {
            if (userId in failing) throw SecurityException("profile locked")
            return apps[userId].orEmpty()
        }
        override fun isManagedProfile(userId: Int): Boolean = userId != 0
        override fun isUserUnlocked(userId: Int): Boolean = userId in allowedProfiles
    }

    @Test
    fun `a device with only the primary user returns exactly its own apps and no work apps`() {
        val source = FakeSource(listOf(0), mapOf(0 to listOf(entry("com.a", "A", 0))))

        val result = ProfileAppSource.collect(source)

        assertEquals(listOf("com.a"), result.map { it.packageName })
        assertTrue(result.none { it.isWorkProfile })
    }

    @Test
    fun `apps from every profile are merged with the primary user first`() {
        val source = FakeSource(
            listOf(10, 0),
            mapOf(
                0 to listOf(entry("com.a", "A", 0)),
                10 to listOf(entry("com.w", "W", 10))
            )
        )

        val result = ProfileAppSource.collect(source)

        assertEquals(listOf(0, 10), result.map { it.userId })
    }

    @Test
    fun `the same app in both profiles yields two entries with different identities`() {
        val source = FakeSource(
            listOf(0, 10),
            mapOf(
                0 to listOf(entry("com.chat", "Main", 0)),
                10 to listOf(entry("com.chat", "Main", 10))
            )
        )

        val result = ProfileAppSource.collect(source)

        assertEquals(2, result.size)
        assertEquals(2, result.map { it.identifier }.toSet().size)
        assertTrue(result.single { it.userId == 10 }.identifier.endsWith("@u10"))
        assertFalse(result.single { it.userId == 0 }.identifier.contains("@u"))
    }

    @Test
    fun `a locked or failing work profile is skipped without losing the primary apps`() {
        val source = FakeSource(
            listOf(0, 10),
            mapOf(0 to listOf(entry("com.a", "A", 0)), 10 to listOf(entry("com.w", "W", 10))),
            failing = setOf(10)
        )

        val result = ProfileAppSource.collect(source)

        assertEquals(listOf("com.a"), result.map { it.packageName })
    }

    @Test
    fun `a stopped work profile that is not unlocked contributes nothing`() {
        val source = FakeSource(
            listOf(0, 10),
            mapOf(0 to listOf(entry("com.a", "A", 0)), 10 to listOf(entry("com.w", "W", 10))),
            allowedProfiles = setOf(0)
        )

        assertEquals(listOf("com.a"), ProfileAppSource.collect(source).map { it.packageName })
    }

    @Test
    fun `only apps from a managed profile are flagged for a badge`() {
        val source = FakeSource(
            listOf(0, 10),
            mapOf(0 to listOf(entry("com.a", "A", 0)), 10 to listOf(entry("com.w", "W", 10)))
        )

        val result = ProfileAppSource.collect(source)

        assertFalse(result.single { it.userId == 0 }.isWorkProfile)
        assertTrue(result.single { it.userId == 10 }.isWorkProfile)
    }

    @Test
    fun `no profiles at all falls back to an empty list instead of throwing`() {
        assertTrue(ProfileAppSource.collect(FakeSource(emptyList(), emptyMap())).isEmpty())
    }

    @Test
    fun `an app whose component name is blank is dropped`() {
        val source = FakeSource(listOf(0), mapOf(0 to listOf(entry("com.a", "", 0))))

        assertTrue(ProfileAppSource.collect(source).isEmpty())
    }

    @Test
    fun `launch target resolves to the profile of the app and falls back to the primary user when it is gone`() {
        assertEquals(10, ProfileAppSource.launchUserId(10, listOf(0, 10)))
        assertEquals(0, ProfileAppSource.launchUserId(0, listOf(0, 10)))
        assertEquals("a removed profile must not launch into another user", -1, ProfileAppSource.launchUserId(11, listOf(0, 10)))
    }

    @Test
    fun `profile change decides refresh only when the set of profiles actually changed`() {
        assertTrue(ProfileAppSource.profilesChanged(listOf(0), listOf(0, 10)))
        assertTrue(ProfileAppSource.profilesChanged(listOf(0, 10), listOf(0)))
        assertFalse(ProfileAppSource.profilesChanged(listOf(0, 10), listOf(10, 0)))
    }

    @Test
    fun `rows of a profile that no longer exists are the orphans to clean up`() {
        val orphans = ProfileAppSource.orphanedIdentifiers(
            storedIdentifiers = listOf("a-A", "w-W@u10", "x-X@u11"),
            liveUserIds = listOf(0, 10)
        )

        assertEquals(listOf("x-X@u11"), orphans)
    }

    // ---- turning profile entries into App shells ----

    private fun icon() = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)

    @Test
    fun `shells for work apps carry the user, the work flag and a user specific identity`() {
        val shells = ProfileAppSource.toShells(
            entries = listOf(entry("com.chat", "Main", 10, label = "Chat")),
            firstId = 100,
            iconFor = { icon() },
            cacheKeyFor = { "key-${it.packageName}" },
            paletteFor = { _, _ -> 7 }
        )

        val shell = shells.single()
        assertEquals(10, shell.userId)
        assertTrue(shell.isWorkProfile)
        assertEquals("com.chat-Main@u10", shell.identifier)
        assertEquals("Chat", shell.label.toString())
        assertEquals(100, shell.id)
        assertEquals(7, shell.paletteColor)
    }

    @Test
    fun `the work copy's icon cache key never equals the personal copy's`() {
        val shells = ProfileAppSource.toShells(
            entries = listOf(entry("com.chat", "Main", 10), entry("com.chat", "Main", 11)),
            firstId = 0,
            iconFor = { icon() },
            cacheKeyFor = { "com.chat/Main#1#system" },
            paletteFor = { _, _ -> 0 }
        )

        assertEquals(2, shells.map { it.iconCacheKey }.toSet().size)
        assertTrue(shells.all { it.iconCacheKey.contains("@u") })
        assertNotEqualsKey("com.chat/Main#1#system", shells.first().iconCacheKey)
    }

    @Test
    fun `an app whose icon cannot be loaded is still listed without a bitmap`() {
        val shells = ProfileAppSource.toShells(
            entries = listOf(entry("com.w", "W", 10)),
            firstId = 0,
            iconFor = { null },
            cacheKeyFor = { "k" },
            paletteFor = { _, _ -> 0 }
        )

        assertEquals(1, shells.size)
        assertTrue(shells.single().icon == null)
    }

    @Test
    fun `ids continue after the primary user's apps so they never repeat`() {
        val shells = ProfileAppSource.toShells(
            entries = listOf(entry("a", "A", 10), entry("b", "B", 10)),
            firstId = 5,
            iconFor = { null },
            cacheKeyFor = { "k" },
            paletteFor = { _, _ -> 0 }
        )

        assertEquals(listOf(5, 6), shells.map { it.id })
    }

    private fun assertNotEqualsKey(a: String, b: String) = org.junit.Assert.assertNotEquals(a, b)

    // ---- launch target ----

    @Test
    fun `a primary user app launches through the plain intent path`() {
        assertEquals(ProfileAppSource.LaunchRoute.PLAIN_INTENT, ProfileAppSource.routeFor(appUserId = 0, liveUserIds = listOf(0, 10)))
    }

    @Test
    fun `a work app launches through LauncherApps into its own profile`() {
        assertEquals(ProfileAppSource.LaunchRoute.LAUNCHER_APPS, ProfileAppSource.routeFor(appUserId = 10, liveUserIds = listOf(0, 10)))
    }

    @Test
    fun `an app of a profile that was removed is not launchable anywhere`() {
        assertEquals(ProfileAppSource.LaunchRoute.PROFILE_GONE, ProfileAppSource.routeFor(appUserId = 11, liveUserIds = listOf(0, 10)))
    }

    @Test
    fun `with no profile information a primary user app still launches`() {
        assertEquals(ProfileAppSource.LaunchRoute.PLAIN_INTENT, ProfileAppSource.routeFor(appUserId = 0, liveUserIds = emptyList()))
    }

    @Test
    fun `a work app's install date reaches its shell so it can be listed as recently installed`() {
        val installed = 1_800_000_000_000L
        val shells = ProfileAppSource.toShells(
            entries = listOf(ProfileAppEntry("com.chat", "Main", "Chat", 10, true, installDate = installed)),
            firstId = 0,
            iconFor = { null },
            cacheKeyFor = { "k" },
            paletteFor = { _, _ -> 0 }
        )

        assertEquals(installed, shells.single().installDate)
    }

    @Test
    fun `an entry without an install date keeps the unknown value zero`() {
        val shells = ProfileAppSource.toShells(
            entries = listOf(entry("com.w", "W", 10)),
            firstId = 0,
            iconFor = { null },
            cacheKeyFor = { "k" },
            paletteFor = { _, _ -> 0 }
        )

        assertEquals(0L, shells.single().installDate)
    }
}
