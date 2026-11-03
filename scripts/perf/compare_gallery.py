#!/usr/bin/env python3
"""Fail-closed 1.5x TPS and TTFT gate for normalized, actual-app evidence."""
import argparse
import json
import math
import statistics
from pathlib import Path

# Normalize these from captured settings; never infer unknown runtime defaults.
CONDITIONS = (
    "artifact_sha256", "quantization", "device_serial", "device_fingerprint",
    "android", "runtime_version", "active_backend", "cpu_threads", "context_tokens",
    "output_budget", "prompt_sha256", "history_sha256", "temperature", "top_k",
    "top_p", "seed", "thinking_requested", "thinking_budget", "speculative_decoding",
    "activation_type", "image_sha256", "cache_state", "benchmark_protocol",
    "timing_boundary", "measurement_overhead", "reply_pacing", "conversation_state",
    "quality_protocol",
)


def positive(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        raise ValueError(f"{label} must be a finite positive number")
    return value


def validate(evidence, package):
    if not isinstance(evidence, dict):
        raise ValueError("Evidence must be a JSON object")
    if evidence.get("package") != package or evidence.get("source") != "actual_app":
        raise ValueError(f"Expected captured actual app {package}; sampler reconstructions are insufficient")
    if evidence.get("device_type") != "physical" or evidence.get("abi") != "arm64-v8a":
        raise ValueError("Physical ARM64 evidence is required")
    if evidence.get("build_type") != "release" or evidence.get("debuggable") is not False:
        raise ValueError("Matched optimized release builds are required")
    digest = evidence.get("apk_sha256", "")
    if not isinstance(digest, str) or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
        raise ValueError("Missing or invalid exact APK SHA-256")
    conditions = evidence.get("conditions", {})
    if not isinstance(conditions, dict):
        raise ValueError("conditions must be a JSON object")
    for key in CONDITIONS:
        value = conditions.get(key)
        if type(value) not in (str, int, float, bool) or value == "" or value == "unknown" or (isinstance(value, float) and not math.isfinite(value)):
            raise ValueError(f"Missing or unknown condition {key}; use explicit 'none' for absent images/history")
    for key in ("artifact_sha256", "prompt_sha256"):
        digest = conditions[key]
        if not isinstance(digest, str) or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise ValueError(f"Invalid {key}")
    if conditions["reply_pacing"] != "off":
        raise ValueError("Disable reply pacing before measuring unrestricted performance")
    if conditions["timing_boundary"] != "send_to_first_displayed_answer":
        raise ValueError("TTFT must measure send to first displayed answer in both actual apps")
    rows = evidence.get("samples", [])
    if not isinstance(rows, list) or len(rows) < 3:
        raise ValueError("At least three measured samples after warm-up are required")
    if evidence.get("warmup_completed") is not True:
        raise ValueError("A completed warm-up is required")
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("Each sample must be a JSON object")
        if row.get("status") != "complete" or row.get("quality_passed") is not True or row.get("error"):
            raise ValueError("Every measured sample must complete and pass the same quality check")
        for key in ("native_decode_tps", "first_displayed_answer_ms", "decode_token_count"):
            positive(row.get(key), key)
        for key in ("thermal_start", "thermal_max", "thermal_end"):
            if type(row.get(key)) is not int or not 0 <= row[key] <= 6:
                raise ValueError(f"Missing or invalid {key}")
    return conditions, rows


def compare(mobie, gallery):
    left, mrows = validate(mobie, "dev.yashasvm.mobie")
    right, grows = validate(gallery, "com.google.ai.edge.gallery")
    for key in CONDITIONS:
        if type(left[key]) is not type(right[key]) or left[key] != right[key]:
            raise ValueError(f"Mismatched {key}")
    if len(mrows) != len(grows):
        raise ValueError("Measured sample counts must match")
    for mrow, grow in zip(mrows, grows):
        for key in ("thermal_start", "thermal_max", "thermal_end", "decode_token_count"):
            if mrow[key] != grow[key]:
                raise ValueError(f"Paired samples mismatch {key}")
    median = lambda rows, key: statistics.median(row[key] for row in rows)
    tps = median(mrows, "native_decode_tps") / median(grows, "native_decode_tps")
    ttft = median(grows, "first_displayed_answer_ms") / median(mrows, "first_displayed_answer_ms")
    return {"passed": tps >= 1.5 and ttft >= 1.5, "target_speedup": 1.5,
            "decode_tps_speedup": tps, "displayed_answer_ttft_speedup": ttft,
            "measured_pairs": len(mrows),
            "note": "Operator-normalized actual-app evidence; this tool does not authenticate captures or establish sustained performance."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mobie", type=Path)
    parser.add_argument("gallery", type=Path)
    args = parser.parse_args()
    try:
        result = compare(json.loads(args.mobie.read_text()), json.loads(args.gallery.read_text()))
    except (ValueError, TypeError, KeyError, OSError) as error:
        print(json.dumps({"passed": False, "invalid_evidence": str(error)}))
        raise SystemExit(2) from error
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
