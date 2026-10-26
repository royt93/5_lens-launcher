package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BUILD-004: the R8 rules file must say what is true and keep only what reflection actually needs.
 * R8 (not ProGuard) shrinks this app; flags R8 ignores are noise that misleads the next reader.
 */
class ProguardRulesContractTest {

    private val projectRoot: File
        get() {
            val workingDir = File(System.getProperty("user.dir") ?: ".")
            return if (File(workingDir, "app").isDirectory) workingDir else workingDir.parentFile
        }

    private val rules: List<String>
        get() = File(projectRoot, "app/proguard-rules.pro").readLines().map { it.trim() }

    private val activeRules: List<String>
        get() = rules.filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test
    fun `header does not claim a full-mode flag that does not exist`() {
        assertFalse(
            "R8 full mode is the AGP default; the comment must not imply a flag enables it",
            rules.any { it.startsWith("#") && it.contains("R8 full mode enabled", ignoreCase = true) }
        )
    }

    @Test
    fun `no legacy ProGuard-only flags that R8 ignores`() {
        val ignoredByR8 = listOf("-optimizationpasses", "-dontpreverify", "-dontusemixedcaseclassnames", "-verbose")
        val present = activeRules.filter { rule -> ignoredByR8.any { rule.startsWith(it) } }
        assertEquals("R8 ignores these flags: $present", emptyList<String>(), present)
    }

    @Test
    fun `model package is not kept wholesale`() {
        val wholesale = activeRules.filter { it.startsWith("-keep class com.mckimquyen.model.**") }
        assertEquals("keeping the whole model package defeats shrinking: $wholesale", emptyList<String>(), wholesale)
    }

    @Test
    fun `room entities and daos are still kept`() {
        val joined = activeRules.joinToString("\n")
        assertTrue("@Entity classes must be kept for Room", joined.contains("@androidx.room.Entity"))
        assertTrue("@Dao types must be kept for Room", joined.contains("@androidx.room.Dao"))
    }

    @Test
    fun `release logging strip still covers the levels Logger relies on`() {
        val joined = activeRules.joinToString("\n")
        assertTrue(joined.contains("-assumenosideeffects class android.util.Log"))
    }
}

/**
 * BUILD-004: the startup profile is a different artifact from the baseline profile. It drives DEX
 * layout for classes needed before first frame, so it must not carry ad SDK code that initialises
 * on a background thread, while the baseline profile (AOT compile set) keeps everything.
 */
class BaselineProfileContractTest {

    private val dir: File
        get() {
            val workingDir = File(System.getProperty("user.dir") ?: ".")
            val root = if (File(workingDir, "app").isDirectory) workingDir else workingDir.parentFile
            return File(root, "app/src/main/generated/baselineProfiles")
        }

    private fun lines(name: String) = File(dir, name).readLines().filter { it.isNotBlank() }

    @Test
    fun `startup profile is narrower than the baseline profile`() {
        assertTrue(
            "startup-prof.txt must not be a copy of baseline-prof.txt",
            lines("startup-prof.txt").size < lines("baseline-prof.txt").size
        )
    }

    @Test
    fun `startup profile excludes ad sdk code but keeps the app launch path`() {
        val startup = lines("startup-prof.txt")
        assertEquals("ad SDK must stay out of the startup profile", 0, startup.count { it.contains("Lcom/applovin/") })
        assertTrue("ActHome must be in the startup profile", startup.any { it.contains("Lcom/mckimquyen/ui/ActHome;") })
        assertTrue("LensView must be in the startup profile", startup.any { it.contains("Lcom/mckimquyen/views/LensView;") })
    }

    @Test
    fun `baseline profile still carries the full set including ad sdk`() {
        val baseline = lines("baseline-prof.txt")
        assertTrue(baseline.any { it.contains("Lcom/applovin/") })
        assertTrue(baseline.any { it.contains("Lcom/mckimquyen/ui/ActHome;") })
    }

    @Test
    fun `every startup rule is also in the baseline profile`() {
        val baseline = lines("baseline-prof.txt").toSet()
        val missing = lines("startup-prof.txt").filterNot { it in baseline }
        assertEquals("startup rules absent from baseline: ${missing.take(3)}", 0, missing.size)
    }
}
