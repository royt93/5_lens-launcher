#!/usr/bin/env python3
"""Contract tests for repository-safe Android release signing configuration."""

from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ReleaseSigningContractTest(unittest.TestCase):
    def test_no_keystore_is_tracked(self) -> None:
        tracked = subprocess.run(
            ["git", "ls-files"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.splitlines()

        forbidden_suffixes = {".jks", ".keystore", ".p12", ".pfx"}
        offenders = [path for path in tracked if Path(path).suffix.lower() in forbidden_suffixes]
        self.assertEqual([], offenders, f"Tracked signing artifacts: {offenders}")

    def test_tracked_gradle_properties_have_no_signing_secrets(self) -> None:
        content = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        self.assertIsNone(
            re.search(r"(?m)^(KS_ALIAS|KS_PW|storePassword|keyPassword)\s*=", content),
            "Signing credential remains in gradle.properties",
        )

    def test_build_uses_external_signing_inputs(self) -> None:
        build_script = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
        required_environment_names = {
            "ANDROID_RELEASE_STORE_FILE",
            "ANDROID_RELEASE_STORE_PASSWORD",
            "ANDROID_RELEASE_KEY_ALIAS",
            "ANDROID_RELEASE_KEY_PASSWORD",
        }
        for name in required_environment_names:
            self.assertIn(name, build_script)
        self.assertIn("keystore.properties", build_script)
        self.assertIn("gradle.taskGraph.whenReady", build_script)
        self.assertIn("createsReleaseArtifact", build_script)
        self.assertNotIn('file("keystore.jks")', build_script)
        self.assertNotIn("KS_PW", build_script)
        self.assertNotIn("KS_ALIAS", build_script)

    def test_local_signing_files_are_ignored(self) -> None:
        for path in (
            "keystore.properties",
            "gradle.properties.bak",
            "private-release-key.jks",
            "private-release-key.keystore",
        ):
            ignored = subprocess.run(
                ["git", "check-ignore", "--quiet", path],
                cwd=ROOT,
                check=False,
            )
            self.assertEqual(0, ignored.returncode, f"Local signing file is not ignored: {path}")


if __name__ == "__main__":
    unittest.main()
