package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SEC-001: Automated signing credential security invariants.
 * Prevents re-committing keystores, passwords or plain-text secrets into the repository.
 */
class SigningCredentialSecurityTest {

    private val projectRoot: File
        get() {
            val workingDir = File(System.getProperty("user.dir") ?: ".")
            return if (File(workingDir, "app").isDirectory) workingDir else workingDir.parentFile
        }

    @Test
    fun `no tracked or unignored keystore files exist in repository`() {
        val root = projectRoot
        // Use git ls-files to verify no keystore is tracked by Git
        val process = ProcessBuilder("git", "ls-files", "*.jks", "*.keystore", "keystore.properties")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val tracked = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        assertTrue(
            "No keystore files should be tracked by Git, found: $tracked",
            tracked.isEmpty()
        )
    }

    @Test
    fun `keystore properties is ignored and never committed`() {
        val root = projectRoot
        val committedKeystoreProps = File(root, "keystore.properties")
        assertFalse("keystore.properties must not exist in tracked tree", committedKeystoreProps.exists())

        val gitignore = File(root, ".gitignore").readText()
        assertTrue("gitignore must ignore *.jks", gitignore.contains("*.jks"))
        assertTrue("gitignore must ignore *.keystore", gitignore.contains("*.keystore"))
        assertTrue("gitignore must ignore keystore.properties", gitignore.contains("keystore.properties"))
    }

    @Test
    fun `gradle properties does not contain plaintext release passwords`() {
        val gradleProps = File(projectRoot, "gradle.properties")
        if (gradleProps.exists()) {
            val content = gradleProps.readText()
            assertFalse("gradle.properties must not contain KS_PW", content.contains("KS_PW="))
            assertFalse("gradle.properties must not contain STORE_PASSWORD", content.contains("STORE_PASSWORD="))
            assertFalse("gradle.properties must not contain KEY_PASSWORD", content.contains("KEY_PASSWORD="))
        }
    }

    @Test
    fun `pre-commit secret scanner script exists and is executable`() {
        val script = File(projectRoot, "scripts/check-secrets.sh")
        assertTrue("scripts/check-secrets.sh must exist", script.isFile)
        assertTrue("scripts/check-secrets.sh must be executable", script.canExecute())
    }

    @Test
    fun `gitleaks workflow and config exist`() {
        val workflow = File(projectRoot, ".github/workflows/secret-scan.yml")
        val config = File(projectRoot, ".gitleaks.toml")
        assertTrue("Gitleaks GitHub Actions workflow must exist", workflow.isFile)
        assertTrue("Gitleaks config must exist", config.isFile)
    }

    // ==================================================================== B8 (test-audit)
    // The 5 tests above only prove the gate's *files* exist - none of them prove the gate itself
    // actually catches anything, which is exactly TEST-001's DoD wording ("a deliberately injected
    // representative failure is caught by each gate"). This runs the real script against a real,
    // throwaway git repo with a staged fake secret. No gitleaks dependency: check-secrets.sh is
    // plain bash driven entirely by `git diff --cached`.

    private fun runCheckSecretsAgainstFixture(stagedFileName: String, stagedFileContent: String): Process {
        val fixtureRepo = kotlin.io.path.createTempDirectory("check-secrets-fixture").toFile()
        ProcessBuilder("git", "init", "-q").directory(fixtureRepo).start().waitFor()
        File(fixtureRepo, stagedFileName).writeText(stagedFileContent)
        ProcessBuilder("git", "add", stagedFileName).directory(fixtureRepo).start().waitFor()

        return ProcessBuilder("bash", File(projectRoot, "scripts/check-secrets.sh").absolutePath)
            .directory(fixtureRepo)
            .redirectErrorStream(true)
            .start()
            .also { it.waitFor() }
    }

    @Test
    fun `check-secrets script rejects a staged plaintext keystore password`() {
        // Built via concatenation, not one contiguous literal here - the un-split form of this
        // fixture string is itself what check-secrets.sh's own pattern below looks for, so writing
        // it whole on one line would trip the real gate on THIS file the moment it's staged,
        // permanently blocking every future commit that touches this test (found the hard way:
        // running the real gate against our own staged diff during this round flagged this line).
        val process = runCheckSecretsAgainstFixture("gradle.properties", "KS_" + "PW=fake123injected\n")
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(
            "the gate must exit non-zero when a staged file contains a real secret pattern, output: $output",
            process.exitValue() != 0
        )
        assertTrue("the gate's own SEC-001 error must name the offending pattern, output: $output", output.contains("SEC-001"))
    }

    @Test
    fun `check-secrets script passes a staged file with no secret pattern`() {
        val process = runCheckSecretsAgainstFixture("README.md", "Just a normal doc change, nothing sensitive here.\n")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("an ordinary staged file must not trip the gate, output: $output", 0, process.exitValue())
    }
}
