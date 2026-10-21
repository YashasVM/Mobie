"""Exercise runner argument boundaries without a device or an Android installation."""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


RUNNER = Path(__file__).with_name("run-gpu-benchmark.sh")


class RunnerTest(unittest.TestCase):
    def run_case(self, model_path, revision, minimal_path=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            log = root / "calls.jsonl"
            adb = root / "fake-adb"
            adb.write_text("""#!/usr/bin/env python3
import json, os, sys
with open(os.environ['FAKE_ADB_LOG'], 'a') as stream:
    stream.write(json.dumps(sys.argv[1:]) + '\\n')
if 'instrument' in ' '.join(sys.argv[1:]):
    print('OK (1 test)')
""")
            adb.chmod(0o755)
            environment = dict(os.environ, ADB=str(adb), FAKE_ADB_LOG=str(log), BUILD_REVISION=revision)
            if minimal_path:
                binaries = root / "bin"
                binaries.mkdir()
                for name in ("bash", "python3", "mkdir", "tee", "grep"):
                    resolved = shutil.which(name)
                    self.assertIsNotNone(resolved, f"Required host tool missing: {name}")
                    (binaries / name).symlink_to(resolved)
                environment["PATH"] = str(binaries)
            # Do not inherit a caller's experiment overrides into these boundary tests.
            for key in ("CPU_THREADS", "MIN_DECODE_TOKENS", "CONTEXT_TOKENS", "SPECULATIVE_DECODING",
                        "THINKING_BUDGET", "OUTPUT_TOKENS", "PROMPT_FIXTURE", "SUSTAINED_SECONDS"):
                environment.pop(key, None)
            completed = subprocess.run(["bash", str(RUNNER), "serial", model_path, "CPU", str(root / "output"), "1", "4"],
                                       env=environment, text=True, capture_output=True)
            self.assertEqual(0, completed.returncode, completed.stderr)
            calls = [json.loads(line) for line in log.read_text().splitlines()]
            invocation = next(call for call in calls if "instrument" in " ".join(call))
            # ADB executes the joined command through the device shell, not via argv.
            command = " ".join(invocation[3:])
            arguments = shlex.split(command)
            self.assertEqual(["am", "instrument"], arguments[:2])
            extras = {arguments[index + 1]: arguments[index + 2]
                      for index, value in enumerate(arguments) if value == "-e"}
            self.assertEqual(model_path, extras["modelPath"])
            self.assertEqual(revision, extras["buildRevision"])
            self.assertEqual("dev.yashasvm.mobie.LiteRtGpuBenchmarkTest#measuresProductionBackendOnPhysicalArm64",
                             extras["class"])
            self.assertEqual((root / "output/run-id.txt").read_text().strip(), extras["benchmarkRunId"])
            self.assertEqual("4", extras["repetitions"])
            self.assertEqual("1", extras["cpuThreads"])

    def run_failure_case(self, instrument_status=0, success_marker=True, failed_collection=None, tee_status=0):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            log = root / "calls.jsonl"
            adb = root / "fake-adb"
            adb.write_text("""#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
with open(os.environ['FAKE_ADB_LOG'], 'a') as stream:
    stream.write(json.dumps(args) + '\\n')
if 'instrument' in ' '.join(args):
    print('OK (1 test)' if os.environ['SUCCESS_MARKER'] == 'true' else 'FAILURES!!!')
    sys.exit(int(os.environ['INSTRUMENT_STATUS']))
if '-d' in args:
    stage = 'logcat'
elif 'exec-out' in args:
    stage = 'results'
elif 'thermalservice' in args:
    # Only the second thermal sample belongs to post-run collection.
    with open(os.environ['FAKE_ADB_LOG']) as stream:
        stage = 'thermal' if sum('thermalservice' in line for line in stream) == 2 else 'before'
else:
    stage = 'other'
if stage == os.environ['FAILED_COLLECTION']:
    sys.exit(23)
print(stage)
""")
            adb.chmod(0o755)
            environment = dict(os.environ, ADB=str(adb), FAKE_ADB_LOG=str(log),
                               INSTRUMENT_STATUS=str(instrument_status),
                               SUCCESS_MARKER=str(success_marker).lower(),
                               FAILED_COLLECTION=failed_collection or "none")
            if tee_status:
                tee = root / "tee"
                tee.write_text("#!/usr/bin/env python3\nimport sys\ndata = sys.stdin.read()\nopen(sys.argv[1], 'w').write(data)\nprint(data, end='')\nsys.exit(" + str(tee_status) + ")\n")
                tee.chmod(0o755)
                environment["PATH"] = str(root) + os.pathsep + environment["PATH"]
            completed = subprocess.run(["bash", str(RUNNER), "serial", "model.litertlm", "CPU", str(root / "output")],
                                       env=environment, text=True, capture_output=True)
            expected = tee_status or instrument_status or (1 if not success_marker else (23 if failed_collection else 0))
            self.assertEqual(expected, completed.returncode, completed.stderr)
            calls = [json.loads(line) for line in log.read_text().splitlines()]
            self.assertTrue(any("-d" in call for call in calls))
            self.assertTrue(any("exec-out" in call for call in calls))
            self.assertEqual(2, sum("thermalservice" in call for call in calls))
            for name in ("instrumentation.txt", "native-logcat.txt", "cumulative-results.jsonl", "thermal-after.txt"):
                self.assertTrue((root / "output" / name).exists(), name)

    def test_tee_failure_still_collects_all_evidence(self):
        self.run_failure_case(tee_status=29)

    def test_instrument_failure_still_collects_all_evidence(self):
        self.run_failure_case(instrument_status=17)

    def test_collection_failures_preserve_instrument_failure(self):
        for stage in ("logcat", "results", "thermal"):
            with self.subTest(stage=stage):
                self.run_failure_case(instrument_status=17, failed_collection=stage)

    def test_collection_failures_are_not_reported_as_success(self):
        for stage in ("logcat", "results", "thermal"):
            with self.subTest(stage=stage):
                self.run_failure_case(failed_collection=stage)

    def test_missing_success_marker_preserves_test_failure(self):
        self.run_failure_case(success_marker=False, failed_collection="logcat")

    def test_plain_model_path(self):
        self.run_case("models/model.litertlm", "abc123")

    def test_runs_without_ripgrep(self):
        self.run_case("models/model.litertlm", "abc123", minimal_path=True)

    def test_spaces_quotes_and_shell_metacharacters_remain_literal(self):
        self.run_case("models/owner's model; $(echo unexpected).litertlm", "revision with spaces ' ; # literal")


if __name__ == "__main__":
    unittest.main()
