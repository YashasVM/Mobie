import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("summary", Path(__file__).resolve().parents[2] / "docs/perf/summarize-gpu-results.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.args = SimpleNamespace(runtime="0.18.0", backend=None, context=None, cpu_threads=None, activation="AUTO", gallery=False)
    def run_record(self, iteration, **extras):
        return dict(iteration=iteration, runtime_version="0.18.0", decode_tps=10, first_chunk_ms=100, peak_pss_bytes=1000, load_ms=200, **extras)
    def test_latest_failure_does_not_resurrect_previous_success(self):
        records = [self.run_record(0), self.run_record(1), self.run_record(0, error="load failed")]
        with self.assertRaisesRegex(ValueError, "failure"):
            module.summarize(records, self.args)
    def test_failed_measured_repetition_not_discarded(self):
        with self.assertRaisesRegex(ValueError, "failure"):
            module.summarize([self.run_record(0), self.run_record(1), self.run_record(2, error="cancelled")], self.args)
    def test_latest_invocation_only_and_not_a_gallery_claim(self):
        result = module.summarize([self.run_record(0), self.run_record(1), self.run_record(0), self.run_record(1), self.run_record(2)], self.args)
        self.assertEqual(result["measured_repetitions"], 2)
        self.assertFalse(result["gallery_executed"])
    def test_missing_warmup_rejected(self):
        with self.assertRaisesRegex(ValueError, "warm-up"):
            module.summarize([self.run_record(1)], self.args)
    def test_mixed_identity_rejected(self):
        with self.assertRaisesRegex(ValueError, "mixes artifact_sha256"):
            module.summarize([self.run_record(0, artifact_sha256="a"), self.run_record(1, artifact_sha256="b")], self.args)
    def test_nonfinite_rate_rejected(self):
        bad = self.run_record(1); bad["decode_tps"] = float("nan")
        with self.assertRaisesRegex(ValueError, "Invalid"):
            module.summarize([self.run_record(0), bad], self.args)

if __name__ == '__main__':
    unittest.main()
