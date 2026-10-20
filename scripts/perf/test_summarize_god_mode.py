import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import summarize_god_mode as summary


def fixture():
    identity = dict(artifact_sha256="model", artifact_bytes=2588147712, app_apk_sha256="app",
                    test_apk_sha256="test", split_apks=[], device_fingerprint="phone/build",
                    runtime_version="0.18.0", build_revision="revision", with_vision=False,
                    output_budget=256, benchmark_protocol="mobie-gallery-phone-v1")
    prompts = [dict(id=category, text=category, sha256=hashlib.sha256(category.encode()).hexdigest())
               for category in summary.CATEGORIES]
    rows = []
    for condition in summary.CONDITIONS:
        for r in range(2):
            for prompt, warmup, rep in [(prompts[0], True, -1)] + [(p, False, 0) for p in prompts]:
                rows.append(dict(identity, condition=condition, round=r, prompt_id=prompt["id"],
                                 prompt_category=prompt["id"], prompt_sha256=prompt["sha256"],
                                 warmup=warmup, repetition=rep, status="complete", decode_tps=999 if warmup else 20,
                                 first_chunk_ms=100, ms_per_output_token=50, generation_ms=1000,
                                 peak_pss_bytes=1024 * 1024, visible_length=30, visible_sha256="output"))
    return dict(identity, status="complete", rounds=2, repetitions=1, prompts=prompts, samples=rows)


class SummaryTest(unittest.TestCase):
    def test_happy_path_excludes_warmups_and_reports_all_comparisons(self):
        report = fixture()
        result = summary.summarize(report)
        self.assertEqual(len(summary.validate(report)), 32)
        self.assertIn("20.00 [20.00, 20.00]", result)
        self.assertNotIn("999.00", result)
        for candidate in summary.CONDITIONS[2:]:
            for baseline in summary.CONDITIONS[:2]:
                self.assertIn(f"| {candidate} | {baseline} | 1.000x", result)
        self.assertIn("Results apply only to this device/build", result)
        self.assertIn("Differing samplers change outputs", result)
        for category in summary.CATEGORIES:
            self.assertIn(f"### {category}", result)

    def test_failed_row_including_warmup_rejected(self):
        for index in (0, 1):
            report = fixture()
            report["samples"][index]["error"] = "native load failed"
            with self.assertRaisesRegex(ValueError, "failure"):
                summary.summarize(report)

    def test_speedup_direction_and_prompt_group_medians(self):
        report = fixture()
        for row in report["samples"]:
            if not row["warmup"] and row["condition"] == "mobie_default":
                row.update(decode_tps=40, first_chunk_ms=50, ms_per_output_token=25, generation_ms=500)
        result = summary.summarize(report)
        self.assertIn("| mobie_default | gallery_default | 2.000x | 2.000x | 2.000x | 2.000x |", result)
        # Two rounds provide values on either side of the expected median for this category.
        for row in report["samples"]:
            if not row["warmup"] and row["condition"] == "mobie_default" and row["prompt_id"] == "code_snippet":
                row["decode_tps"] = 30 if row["round"] == 0 else 50
        code_table = summary.summarize(report).split("### code_snippet\n", 1)[1].split("### rewrite_tone", 1)[0]
        self.assertIn("40.00 [30.00, 50.00]", code_table)

    def test_mixed_identity_rejected(self):
        for field in summary.IDENTITY:
            report = fixture()
            report["samples"][-1][field] = "other"
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "Mixed identity"):
                summary.summarize(report)

    def test_missing_condition_rejected(self):
        report = fixture()
        report["samples"] = [row for row in report["samples"] if row["condition"] != "gallery_mtp"]
        with self.assertRaisesRegex(ValueError, "Missing conditions"):
            summary.summarize(report)

    def test_missing_or_duplicate_sample_rejected(self):
        report = fixture()
        report["samples"].pop()
        with self.assertRaisesRegex(ValueError, "Missing measured"):
            summary.summarize(report)
        report = fixture()
        report["samples"].append(copy.deepcopy(report["samples"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            summary.summarize(report)

    def test_nonfinite_metric_and_incomplete_report_rejected(self):
        report = fixture()
        report["samples"][1]["decode_tps"] = float("nan")
        with self.assertRaisesRegex(ValueError, "Invalid metric"):
            summary.summarize(report)
        report = fixture()
        report["status"] = "failed"
        with self.assertRaisesRegex(ValueError, "failure"):
            summary.summarize(report)

    def test_prompt_bytes_must_match_recorded_hash(self):
        report = fixture()
        report["prompts"][0]["text"] += "\n"
        with self.assertRaisesRegex(ValueError, "prompt identity"):
            summary.summarize(report)

    def test_cli_reads_json_and_exits_nonzero_for_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic-report.json"
            report = fixture()
            path.write_text(json.dumps(report), encoding="utf-8")
            command = [sys.executable, str(Path(summary.__file__)), str(path)]
            success = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertEqual(success.returncode, 0, success.stderr)
            self.assertIn("| mobie_default | gallery_mtp |", success.stdout)
            report["samples"][1]["status"] = "failed"
            path.write_text(json.dumps(report), encoding="utf-8")
            failure = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertNotEqual(failure.returncode, 0)
            self.assertIn("Rejected benchmark", failure.stderr)
            self.assertEqual(failure.stdout, "")


if __name__ == "__main__":
    unittest.main()
