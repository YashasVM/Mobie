import copy
import unittest

from compare_gallery import CONDITIONS, compare


class GalleryComparisonTest(unittest.TestCase):
    def setUp(self):
        conditions = dict.fromkeys(CONDITIONS, "fixture")
        conditions.update(artifact_sha256="a" * 64, prompt_sha256="b" * 64,
                          reply_pacing="off", timing_boundary="send_to_first_displayed_answer")
        self.mobie = dict(package="dev.yashasvm.mobie", source="actual_app", device_type="physical",
                          abi="arm64-v8a", build_type="release", debuggable=False,
                          apk_sha256="c" * 64, warmup_completed=True, conditions=conditions,
                          samples=[dict(status="complete", quality_passed=True, native_decode_tps=30,
                                        first_displayed_answer_ms=100, decode_token_count=128,
                                        thermal_start=0, thermal_max=0, thermal_end=0) for _ in range(3)])
        self.gallery = copy.deepcopy(self.mobie)
        self.gallery["package"] = "com.google.ai.edge.gallery"
        self.gallery["apk_sha256"] = "d" * 64
        for row in self.gallery["samples"]:
            row.update(native_decode_tps=20, first_displayed_answer_ms=150)

    def test_exact_threshold_passes(self):
        result = compare(self.mobie, self.gallery)
        self.assertTrue(result["passed"])
        self.assertEqual(result["decode_tps_speedup"], 1.5)
        self.assertEqual(result["displayed_answer_ttft_speedup"], 1.5)

    def test_either_target_miss_fails(self):
        for key, value in (("native_decode_tps", 29), ("first_displayed_answer_ms", 101)):
            with self.subTest(key=key):
                candidate = copy.deepcopy(self.mobie)
                for row in candidate["samples"]:
                    row[key] = value
                self.assertFalse(compare(candidate, self.gallery)["passed"])

    def test_every_condition_must_be_present_and_match(self):
        for key in CONDITIONS:
            with self.subTest(key=key):
                candidate = copy.deepcopy(self.mobie)
                del candidate["conditions"][key]
                with self.assertRaises(ValueError):
                    compare(candidate, self.gallery)
                candidate = copy.deepcopy(self.mobie)
                candidate["conditions"][key] = "different"
                with self.assertRaises(ValueError):
                    compare(candidate, self.gallery)

    def test_invalid_evidence_is_rejected(self):
        mutations = [lambda e: e.update(source="gallery_sampler_only"),
                     lambda e: e.update(device_type="emulator"),
                     lambda e: e.update(debuggable=True),
                     lambda e: e.update(warmup_completed=False),
                     lambda e: e.update(apk_sha256="unknown"),
                     lambda e: e.update(samples=e["samples"][:2]),
                     lambda e: e["samples"][0].update(error="failure"),
                     lambda e: e["samples"][0].update(quality_passed=False),
                     lambda e: e["samples"][0].update(native_decode_tps=float("nan")),
                     lambda e: e["samples"][0].update(first_displayed_answer_ms=0),
                     lambda e: e["samples"][0].update(thermal_max=1),
                     lambda e: e["samples"][0].update(decode_token_count=127)]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                candidate = copy.deepcopy(self.mobie)
                mutation(candidate)
                with self.assertRaises(ValueError):
                    compare(candidate, self.gallery)


if __name__ == "__main__":
    unittest.main()
