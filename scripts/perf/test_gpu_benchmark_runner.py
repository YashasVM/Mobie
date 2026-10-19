"""Exercise runner argument boundaries without a device or an Android installation."""
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest


RUNNER = Path(__file__).with_name("run-gpu-benchmark.sh")


class RunnerTest(unittest.TestCase):
    def run_case(self, model_path, revision):
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

    def test_plain_model_path(self):
        self.run_case("models/model.litertlm", "abc123")

    def test_spaces_quotes_and_shell_metacharacters_remain_literal(self):
        self.run_case("models/owner's model; $(echo unexpected).litertlm", "revision with spaces ' ; # literal")


if __name__ == "__main__":
    unittest.main()
