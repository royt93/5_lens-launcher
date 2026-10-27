package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** BUILD-003: freeze the owner-approved safe scope; deferred permissions/logs/heap must not drift. */
class Build003SafeScopeContractTest {

    private val projectRoot: File
        get() {
            val wd = File(System.getProperty("user.dir") ?: ".")
            return if (File(wd, "app").isDirectory) wd else wd.parentFile
        }

    private fun command(vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(*args)
            .directory(projectRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("command must finish", process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue() to output
    }

    @Test
    fun `Jetifier is disabled and no support library remains declared or verified`() {
        val properties = File(projectRoot, "gradle.properties").readLines()
            .map(String::trim)
            .filter { it.startsWith("android.enableJetifier=") }
        assertEquals(listOf("android.enableJetifier=false"), properties)

        val buildFiles = listOf(File(projectRoot, "build.gradle"), File(projectRoot, "app/build.gradle"))
        buildFiles.forEach { file -> assertFalse("legacy support dependency in ${file.name}", file.readText().contains("com.android.support:")) }
        assertFalse(File(projectRoot, "gradle/verification-metadata.xml").readText().contains("com.android.support:"))
    }

    @Test
    fun `the two reviewed manifest permissions remain unchanged and explicit`() {
        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.REQUEST_DELETE_PACKAGES\" />"))
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />"))
    }

    @Test
    fun `local properties stays in the ignored local file and out of the git index`() {
        val tracked = command("git", "ls-files", "--error-unmatch", "local.properties")
        assertFalse("local.properties must not be tracked", tracked.first == 0)
        val ignored = command("git", "check-ignore", "--no-index", "local.properties")
        assertEquals(".gitignore must ignore local.properties", 0, ignored.first)
        assertTrue("developer's local SDK path file must remain on disk", File(projectRoot, "local.properties").isFile)
    }

    @Test
    fun `the safe-scope task explicitly records deferred permissions crash logs and heap`() {
        val story = File(projectRoot, "doc/task/done/p3-build-build-003-repo-hygiene-quick-wins.md").readText()
        assertTrue(story.contains("Giữ nguyên 2 manifest permissions"))
        assertTrue(story.contains("7 JVM crash logs"))
        assertTrue(story.contains("-Xmx2048m"))
    }
}
