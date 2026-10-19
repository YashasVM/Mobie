"""Check failure-path evidence collection without Gradle or an Android device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


RUNNER = Path(__file__).with_name("run-ui-smoke.sh")


class UiSmokeRunnerTest(unittest.TestCase):
    def run_case(self, test_status, pull_status):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            gradle = root / "gradlew"
            gradle.write_text('#!/bin/sh\nexit "$FAKE_TEST_STATUS"\n')
            adb = root / "adb"
            adb.write_text("""#!/usr/bin/env python3
import os, pathlib, sys
with open(os.environ['FAKE_ADB_LOG'], 'a') as log:
    log.write(sys.argv[2] + '\\n')
status = int(os.environ['FAKE_PULL_STATUS'])
if not status:
    pathlib.Path(sys.argv[3]).write_text('diagnostic fixture')
raise SystemExit(status)
""")
            gradle.chmod(0o755)
            adb.chmod(0o755)
            evidence = root / "evidence"
            log = root / "pulls.txt"
            environment = dict(os.environ, GRADLEW=str(gradle), ADB=str(adb),
                               UI_SMOKE_EVIDENCE_DIR=str(evidence), FAKE_ADB_LOG=str(log),
                               FAKE_TEST_STATUS=str(test_status), FAKE_PULL_STATUS=str(pull_status))
            result = subprocess.run(["bash", str(RUNNER)], env=environment, text=True, capture_output=True)
            self.assertEqual(test_status, result.returncode, result.stderr)
            names = ["memory-system-apps.png", "memory-system-apps-hierarchy.txt"]
            self.assertEqual(["/sdcard/" + name for name in names], log.read_text().splitlines())
            for name in names:
                self.assertEqual(pull_status == 0, (evidence / name).exists())
            if pull_status:
                self.assertEqual(2, result.stderr.count("::warning::Settings diagnostic unavailable"))

    def test_passing_run_collects_evidence(self):
        self.run_case(0, 0)

    def test_failed_run_collects_evidence_and_preserves_failure(self):
        self.run_case(42, 0)

    def test_failed_pulls_preserve_test_failure(self):
        self.run_case(42, 1)

    def test_failed_pulls_do_not_fail_passing_tests(self):
        self.run_case(0, 1)


if __name__ == "__main__":
    unittest.main()
