package com.mckimquyen.util

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
}
