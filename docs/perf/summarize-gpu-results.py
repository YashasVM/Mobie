#!/usr/bin/env python3
"""Summarize the latest matching invocation, excluding only iteration-zero warm-up.

Failures are never filtered out before selecting an invocation: doing that could silently
report an older successful run when the newest warm-up failed.
"""
import argparse
import json
import math
import statistics
from pathlib import Path


def summarize(records, args):
    records = [r for r in records if r.get("runtime_version") == args.runtime
               and (args.backend is None or r.get("requested_backend", r.get("active_backend")) == args.backend)
               and (args.context is None or r.get("requested_context_tokens", r.get("context_tokens")) == args.context)
               and (args.cpu_threads is None or r.get("cpu_threads") == args.cpu_threads)
               and r.get("activation_type", "AUTO") == args.activation
               and r.get("gallery_equivalent", False) == args.gallery]
    starts = [i for i, r in enumerate(records) if r.get("iteration") == 0]
    if not starts:
        raise ValueError("No matching invocation with iteration-zero warm-up was found.")
    records = records[starts[-1]:]
    if any(r.get("error") or r.get("generation_error") for r in records):
        raise ValueError("Latest matching invocation contains a failure; refusing a success-only summary.")
    measured = [r for r in records if r.get("iteration", 0) > 0]
    if not measured:
        raise ValueError("No measured repetitions after warm-up match this case.")
    # Mixing devices, models, settings or binaries would make a median meaningless.
    identity_fields = ("artifact_sha256", "device", "android", "runtime_version", "active_backend",
                       "context_tokens", "cpu_threads", "activation_type", "speculative_decoding",
                       "temperature", "top_k", "top_p", "output_budget", "thinking_off",
                       "sustained_seconds", "prompt_sha256", "device_fingerprint", "app_version_code",
                       "app_apk_sha256", "test_apk_sha256", "split_apks", "seed",
                       "thinking_requested", "thinking_budget", "prefill_preface_on_init", "benchmark_protocol")
    for key in identity_fields:
        if len({json.dumps(r.get(key), sort_keys=True) for r in records}) > 1:
            raise ValueError(f"Latest invocation mixes {key}; compare separate homogeneous runs.")
    numeric = ("decode_tps", "first_chunk_ms", "peak_pss_bytes", "load_ms")
    for record in measured:
        for key in numeric:
            value = record.get(key)
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
                raise ValueError(f"Invalid or missing {key} in measured repetition.")
        if record["decode_tps"] <= 0:
            raise ValueError("Measured repetition has no positive native decode throughput.")
    def median(key):
        return statistics.median(r[key] for r in measured)
    return {
        "measured_repetitions": len(measured),
        "decode_tps_median": median("decode_tps"),
        "first_chunk_ms_median": median("first_chunk_ms"),
        "peak_pss_bytes_median": median("peak_pss_bytes"),
        "peak_pss_bytes_max": max(r["peak_pss_bytes"] for r in measured),
        "load_ms_median": median("load_ms"),
        "thermal": [[r.get("thermal_start"), r.get("thermal_max"), r.get("thermal_end")] for r in measured],
        "artifact_sha256": records[-1].get("artifact_sha256", "unknown"),
        "visible_sha256": sorted({r["visible_sha256"] for r in measured if "visible_sha256" in r}),
        "gallery_executed": False,
        "note": "This measures Mobie only. Gallery-equivalent selects a sampler, not a Gallery app comparison.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("--backend")
    parser.add_argument("--context", type=int)
    parser.add_argument("--runtime", default="0.18.0")
    parser.add_argument("--cpu-threads", type=int)
    parser.add_argument("--activation", default="AUTO")
    parser.add_argument("--gallery", action="store_true", help="Select Gallery-style sampler runs; does not execute Gallery")
    args = parser.parse_args()
    records = [json.loads(line) for line in args.path.read_text().splitlines() if line.strip()]
    try:
        print(json.dumps(summarize(records, args), indent=2))
    except ValueError as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
