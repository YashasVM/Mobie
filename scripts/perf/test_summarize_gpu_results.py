import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("summary", Path(__file__).resolve().parents[2] / "docs/perf/summarize-gpu-results.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.args = SimpleNamespace(runtime="0.18.0", backend=None, context=None, cpu_threads=None, activation="AUTO",
                                    gallery=False, fixture=None)
    def run_record(self, iteration, **extras):
        record = dict(iteration=iteration, runtime_version="0.18.0", decode_tps=10, first_chunk_ms=100, peak_pss_bytes=1000, load_ms=200)
        record.update(extras)
        return record
    def sample(self, tps, tokens, **extras):
        sample = dict(decode_tps=tps, native_decode_tokens=tokens, decode_tokens=tokens, sdk_chunks=max(1, tokens // 4),
                      first_chunk_ms=100, peak_pss_bytes=1000, min_native_decode_tokens=64, throughput_eligible=True,
                      throughput_ineligible_reason=None, thermal_start=0, thermal_max=0, thermal_end=0)
        sample.update(extras)
        return sample
    def sustained_row(self, iteration, samples, warmup=None, **extras):
        """A sustainedSeconds>0 row: top-level fields duplicate the in-row warm-up, as the test writes them."""
        warmup = warmup or self.sample(99.0, 200)
        row = dict(warmup)
        row.update(iteration=iteration, runtime_version="0.18.0", load_ms=200, prompt_fixture="sustained-decode",
                   fixture_role="sustained_decode", prompt_id="phone-care-twelve-tips-v1", prompt_sha256="abc",
                   sustained_seconds=30, thinking_enabled_request=False, benchmark_protocol="mobie-production-adapter-v3",
                   all_generations_throughput_eligible=all(g["throughput_eligible"] for g in [warmup, *samples]),
                   warmup=warmup, samples=samples)
        row.update(extras)
        return row
    def repetition_row(self, iteration, tps, tokens, **extras):
        """A sustainedSeconds=0 row: one generation per outer repetition."""
        row = self.sample(tps, tokens)
        row.update(iteration=iteration, runtime_version="0.18.0", load_ms=200, prompt_fixture="sustained-decode",
                   fixture_role="sustained_decode", prompt_id="phone-care-twelve-tips-v1", prompt_sha256="abc",
                   sustained_seconds=0, all_generations_throughput_eligible=True)
        row.update(extras)
        return row
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

    # Short / legacy compatibility.
    def test_legacy_short_records_are_a_latency_probe_without_throughput_claim(self):
        records = [self.run_record(0, decode_tps=50), self.run_record(1, decode_tps=8), self.run_record(2, decode_tps=12)]
        result = module.summarize(records, self.args)
        self.assertEqual(result["summary_kind"], "short_latency_probe")
        self.assertTrue(result["prompt_fixture_inferred"])
        self.assertFalse(result["sustained_throughput_established"])
        self.assertEqual(result["measured_repetitions"], 2)
        self.assertEqual(result["decode_tps_median"], 10)
        self.assertFalse(result["gallery_executed"])
    def test_short_fixture_sustained_rows_are_not_summarized_from_their_warmup(self):
        records = [self.run_record(0, sustained_seconds=30, samples=[self.sample(10, 2)]),
                   self.run_record(1, sustained_seconds=30, samples=[self.sample(10, 2)])]
        with self.assertRaisesRegex(ValueError, "sustained-decode fixture"):
            module.summarize(records, self.args)

    # Sustained-decode fixture.
    def test_sustained_summary_uses_samples_after_in_row_warmup(self):
        records = [self.sustained_row(0, [self.sample(10, 80), self.sample(14, 120)]),
                   self.sustained_row(1, [self.sample(12, 100)])]
        result = module.summarize(records, self.args)
        self.assertEqual(result["summary_kind"], "sustained_decode")
        self.assertEqual(result["sample_source"], "in_row_samples_after_warmup")
        self.assertTrue(result["sustained_throughput_established"])
        self.assertEqual(result["measured_samples"], 3)
        self.assertEqual(result["samples_per_iteration"], {0: 2, 1: 1})
        # The 99 tok/s, 200-token warm-ups must not appear anywhere in the aggregate.
        self.assertEqual(result["decode_tps"], {"median": 12, "min": 10, "max": 14, "stdev": 2.0})
        self.assertEqual(result["native_decode_tokens"], {"median": 100, "min": 80, "max": 120, "stdev": 20.0, "total": 300})
        self.assertEqual(result["min_native_decode_tokens"], 64)
        self.assertFalse(result["gallery_executed"])
    def test_sustained_repetitions_exclude_iteration_zero(self):
        records = [self.repetition_row(0, 50, 300), self.repetition_row(1, 20, 90),
                   self.repetition_row(2, 30, 110), self.repetition_row(3, 25, 100)]
        result = module.summarize(records, self.args)
        self.assertEqual(result["sample_source"], "repetitions_after_iteration_zero")
        self.assertEqual(result["measured_samples"], 3)
        self.assertEqual(result["decode_tps"]["median"], 25)
        self.assertEqual(result["decode_tps"]["max"], 30)
        self.assertEqual(result["native_decode_tokens"]["median"], 100)
        self.assertEqual(result["native_decode_tokens"]["total"], 300)
    def test_short_eos_rejected_without_falling_back_to_older_success(self):
        older = [self.sustained_row(0, [self.sample(10, 80)]), self.sustained_row(1, [self.sample(12, 100)])]
        self.assertEqual(module.summarize(older, self.args)["measured_samples"], 2)
        early_eos = self.sample(40, 2, throughput_eligible=False, short_output=True,
                                throughput_ineligible_reason="native_decode_tokens_below_minimum")
        latest = [self.sustained_row(0, [self.sample(12, 100), early_eos])]
        with self.assertRaisesRegex(ValueError, "ineligible.*native_decode_tokens_below_minimum"):
            module.summarize(older + latest, self.args)
    def test_short_sample_mislabelled_eligible_still_rejected(self):
        records = [self.sustained_row(0, [self.sample(40, 2)])]
        with self.assertRaisesRegex(ValueError, "decoded 2 native tokens, below the minimum 64"):
            module.summarize(records, self.args)
    def test_short_warmup_rejects_invocation(self):
        warmup = self.sample(40, 2, throughput_eligible=False, throughput_ineligible_reason="native_decode_tokens_below_minimum")
        with self.assertRaisesRegex(ValueError, "ineligible"):
            module.summarize([self.sustained_row(0, [self.sample(12, 100)], warmup=warmup)], self.args)
    def test_missing_native_token_count_rejected(self):
        sample = self.sample(12, 100)
        del sample["native_decode_tokens"]
        with self.assertRaisesRegex(ValueError, "native_decode_tokens"):
            module.summarize([self.sustained_row(0, [sample])], self.args)
    def test_nonfinite_sample_rate_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid or missing decode_tps"):
            module.summarize([self.sustained_row(0, [self.sample(float("inf"), 100)])], self.args)
    def test_nonfinite_warmup_rate_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid or missing decode_tps"):
            module.summarize([self.sustained_row(0, [self.sample(12, 100)],
                                               warmup=self.sample(float("nan"), 100))], self.args)
    def test_two_token_threshold_cannot_certify_sustained_output(self):
        sample = self.sample(40, 2, min_native_decode_tokens=2)
        with self.assertRaisesRegex(ValueError, "at least 64"):
            module.summarize([self.sustained_row(0, [sample], warmup=sample)], self.args)
    def test_mixed_resolved_cpu_threads_rejected(self):
        records = [self.sustained_row(0, [self.sample(12, 100)], active_cpu_threads=1),
                   self.sustained_row(1, [self.sample(12, 100)], active_cpu_threads=4)]
        with self.assertRaisesRegex(ValueError, "mixes active_cpu_threads"):
            module.summarize(records, self.args)
    def test_failed_sample_rejected(self):
        with self.assertRaisesRegex(ValueError, "failure"):
            module.summarize([self.sustained_row(0, [self.sample(12, 100, generation_error="cancelled")])], self.args)
    def test_row_without_measured_samples_rejected(self):
        with self.assertRaisesRegex(ValueError, "no measured samples"):
            module.summarize([self.sustained_row(0, [self.sample(12, 100)]), self.sustained_row(1, [])], self.args)
    def test_mixed_prompt_rejected(self):
        records = [self.sustained_row(0, [self.sample(12, 100)]), self.sustained_row(1, [self.sample(12, 100)], prompt_sha256="def")]
        with self.assertRaisesRegex(ValueError, "mixes prompt_sha256"):
            module.summarize(records, self.args)
    def test_mixed_minimum_tokens_rejected(self):
        records = [self.sustained_row(0, [self.sample(12, 100)]),
                   self.sustained_row(1, [self.sample(12, 100)], min_native_decode_tokens=32)]
        with self.assertRaisesRegex(ValueError, "mixes min_native_decode_tokens"):
            module.summarize(records, self.args)
    def test_mixed_thinking_request_rejected(self):
        records = [self.sustained_row(0, [self.sample(12, 100)]),
                   self.sustained_row(1, [self.sample(12, 100)], thinking_enabled_request=None)]
        with self.assertRaisesRegex(ValueError, "mixes thinking_enabled_request"):
            module.summarize(records, self.args)
    def test_fixture_filter_selects_sustained_over_newer_short_probe(self):
        records = [self.sustained_row(0, [self.sample(12, 100)]), self.run_record(0), self.run_record(1)]
        self.assertEqual(module.summarize(records, self.args)["summary_kind"], "short_latency_probe")
        self.args.fixture = "sustained-decode"
        self.assertEqual(module.summarize(records, self.args)["decode_tps"]["median"], 12)

if __name__ == '__main__':
    unittest.main()
