#!/usr/bin/env python3
"""Summarize the latest matching benchmark invocation.

Failures are never filtered out before selecting an invocation: doing that could silently
report an older successful run when the newest warm-up failed.

Two fixtures are summarized differently:

* ``short-latency`` (also every record written before fixtures existed): a latency probe.
  Iteration zero is excluded as warm-up. Its decode rate may come from a reply that hit EOS
  after a couple of tokens, so the summary never claims sustained throughput.
* ``sustained-decode``: native decode throughput. With ``sustained_seconds > 0`` only the
  ``samples`` measured after each row's in-row warm-up are used; otherwise repetitions after
  iteration zero. Every generation must be throughput-eligible with at least
  ``min_native_decode_tokens`` native decoded tokens, or the whole invocation is rejected.
"""
import argparse
import json
import math
import statistics
from pathlib import Path

SHORT_FIXTURE = "short-latency"
SUSTAINED_FIXTURE = "sustained-decode"
LEGACY_SHORT_PROMPT_ID = "local-ai-benefits-v1"
NOTE = "This measures Mobie only. Gallery-equivalent selects a sampler, not a Gallery app comparison."

# Mixing devices, models, settings, prompts or binaries would make a median meaningless.
IDENTITY_FIELDS = ("artifact_sha256", "device", "android", "runtime_version", "requested_backend", "active_backend",
                   "requested_context_tokens", "context_tokens", "cpu_threads", "active_cpu_threads", "activation_type",
                   "speculative_decoding", "temperature", "top_k", "top_p", "output_budget", "thinking_off",
                   "sustained_seconds", "prompt_id", "prompt_sha256", "prompt_bytes", "fixture_role",
                   "min_native_decode_tokens", "device_fingerprint", "app_version_code", "build_type",
                   "app_apk_sha256", "test_apk_sha256", "split_apks", "seed", "quantization",
                   "thinking_enabled_request", "thinking_budget", "prefill_preface_on_init", "benchmark_protocol",
                   "gallery_equivalent", "persistent_cache_state", "run_id", "expected_repetitions")


def fixture_of(record):
    """Rows written before fixtures existed only ever used the original short prompt."""
    if "prompt_fixture" in record:
        return record["prompt_fixture"]
    return SHORT_FIXTURE if record.get("prompt_id", LEGACY_SHORT_PROMPT_ID) == LEGACY_SHORT_PROMPT_ID else "unknown"


def _is_finite_number(value):
    return not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value)


def _require_number(record, key, where, positive=False):
    value = record.get(key)
    if not _is_finite_number(value) or value < 0 or (positive and value <= 0):
        raise ValueError(f"Invalid or missing {key} in {where}.")
    return value


def _dispersion(values):
    return {
        "median": statistics.median(values),
        "min": min(values),
        "max": max(values),
        "stdev": statistics.stdev(values) if len(values) > 1 else None,
    }


def summarize(records, args):
    fixture = getattr(args, "fixture", None)
    run_id = getattr(args, "run_id", None)
    if run_id is not None:
        records = [r for r in records if r.get("run_id") == run_id]
    records = [r for r in records if r.get("runtime_version") == args.runtime
               and (args.backend is None or r.get("requested_backend", r.get("active_backend")) == args.backend)
               and (args.context is None or r.get("requested_context_tokens", r.get("context_tokens")) == args.context)
               and (args.cpu_threads is None or r.get("cpu_threads") == args.cpu_threads)
               and r.get("activation_type", "AUTO") == args.activation
               and r.get("gallery_equivalent", False) == args.gallery
               and (fixture is None or fixture_of(r) == fixture)]
    starts = [i for i, r in enumerate(records) if r.get("iteration") == 0]
    if not starts:
        raise ValueError("No matching invocation with iteration-zero warm-up was found.")
    records = records[starts[-1]:]
    expected = records[0].get("expected_repetitions")
    if expected is not None:
        if isinstance(expected, bool) or not isinstance(expected, int) or not 1 <= expected <= 20:
            raise ValueError("Invalid expected_repetitions in invocation.")
        if [r.get("iteration") for r in records] != list(range(expected)):
            raise ValueError("Invocation is incomplete or has nonconsecutive repetitions.")
    generations = [g for r in records for g in [r, r.get("warmup") or {}, *(r.get("samples") or [])]]
    if any(g.get("error") or g.get("generation_error") for g in generations):
        raise ValueError("Latest matching invocation contains a failure; refusing a success-only summary.")
    for key in IDENTITY_FIELDS:
        if len({json.dumps(r.get(key), sort_keys=True) for r in records}) > 1:
            raise ValueError(f"Latest invocation mixes {key}; compare separate homogeneous runs.")
    fixtures = {fixture_of(r) for r in records}
    if len(fixtures) > 1:
        raise ValueError("Latest invocation mixes prompt_fixture; compare separate homogeneous runs.")
    fixture = fixtures.pop()
    if fixture == SUSTAINED_FIXTURE:
        result = _summarize_sustained(records)
    elif fixture == SHORT_FIXTURE:
        if any((r.get("sustained_seconds") or 0) > 0 for r in records):
            raise ValueError("Short-latency rows with sustained samples cannot establish sustained throughput; "
                             "rerun with the sustained-decode fixture.")
        result = _summarize_latency_probe(records)
    else:
        raise ValueError(f"Unknown prompt fixture {fixture!r}; refusing to guess its workload.")
    result["run_id"] = records[0].get("run_id")
    result["requested_run_id_matched"] = run_id is not None
    if run_id is None:
        result["note"] += " Without --run-id, cumulative results cannot exclude a newer invocation that died before its first row."
    return result


def _summarize_latency_probe(records):
    measured = [r for r in records if r.get("iteration", 0) > 0]
    if not measured:
        raise ValueError("No measured repetitions after warm-up match this case.")
    for record in measured:
        for key in ("decode_tps", "first_chunk_ms", "peak_pss_bytes", "load_ms"):
            _require_number(record, key, "measured repetition")
        if record["decode_tps"] <= 0:
            raise ValueError("Measured repetition has no positive native decode throughput.")
    def median(key):
        return statistics.median(r[key] for r in measured)
    # Key names are kept for compatibility with summaries of existing short records.
    return {
        "summary_kind": "short_latency_probe",
        "prompt_fixture": SHORT_FIXTURE,
        "prompt_fixture_inferred": any("prompt_fixture" not in r for r in records),
        "sustained_throughput_established": False,
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
        "note": NOTE + " Short-latency probe: the reply may end after a few tokens, so decode_tps_median is "
                       "not sustained decode throughput.",
    }


def _summarize_sustained(records):
    for record in records:
        if not record.get("prompt_id") or not record.get("prompt_sha256"):
            raise ValueError("Sustained-decode rows must record prompt_id and prompt_sha256.")
    minimum = records[0].get("min_native_decode_tokens")
    if isinstance(minimum, bool) or not isinstance(minimum, int) or minimum < 64:
        raise ValueError("Sustained-decode rows must record an integer min_native_decode_tokens of at least 64.")
    sustained = (records[0].get("sustained_seconds") or 0) > 0
    if sustained:
        for record in records:
            if not isinstance(record.get("warmup"), dict):
                raise ValueError(f"Iteration {record.get('iteration')} has no in-row warm-up; refusing to guess.")
            if not record.get("samples"):
                raise ValueError(f"Iteration {record.get('iteration')} has no measured samples after warm-up.")
        measured = [s for r in records for s in r["samples"]]
        source = "in_row_samples_after_warmup"
    else:
        measured = [r for r in records if r.get("iteration", 0) > 0]
        source = "repetitions_after_iteration_zero"
    if not measured:
        raise ValueError("No measured samples after warm-up match this case.")
    # Warm-ups are checked too: the workload is fixed, so an early EOS anywhere disqualifies it.
    checked = list(records) + [r["warmup"] for r in records if sustained] + (measured if sustained else [])
    for generation in checked:
        _require_number(generation, "decode_tps", "generation", positive=True)
        if generation.get("throughput_eligible") is not True:
            raise ValueError("Generation is throughput-ineligible "
                             f"({generation.get('throughput_ineligible_reason')}); refusing a sustained summary.")
        if generation.get("min_native_decode_tokens", minimum) != minimum:
            raise ValueError("Generation mixes min_native_decode_tokens; compare separate homogeneous runs.")
        tokens = generation.get("native_decode_tokens")
        if isinstance(tokens, bool) or not isinstance(tokens, int):
            raise ValueError("Generation is missing an integer native_decode_tokens count.")
        if tokens < minimum:
            raise ValueError(f"Generation decoded {tokens} native tokens, below the minimum {minimum}.")
    for record in records:
        if record.get("all_generations_throughput_eligible") is not True:
            raise ValueError(f"Iteration {record.get('iteration')} is not marked all-generations throughput-eligible; "
                             "refusing a sustained summary.")
    for sample in measured:
        _require_number(sample, "decode_tps", "measured sample", positive=True)
        for key in ("first_chunk_ms", "peak_pss_bytes"):
            _require_number(sample, key, "measured sample")
    tokens = [s["native_decode_tokens"] for s in measured]
    return {
        "summary_kind": "sustained_decode",
        "sample_source": source,
        "prompt_fixture": SUSTAINED_FIXTURE,
        "prompt_id": records[0]["prompt_id"],
        "prompt_sha256": records[0]["prompt_sha256"],
        "min_native_decode_tokens": minimum,
        "sustained_throughput_established": True,
        "measured_samples": len(measured),
        "samples_per_iteration": {r.get("iteration"): len(r["samples"]) for r in records} if sustained else None,
        "decode_tps": _dispersion([s["decode_tps"] for s in measured]),
        "native_decode_tokens": {**_dispersion(tokens), "total": sum(tokens)},
        "first_chunk_ms_median": statistics.median(s["first_chunk_ms"] for s in measured),
        "peak_pss_bytes_median": statistics.median(s["peak_pss_bytes"] for s in measured),
        "peak_pss_bytes_max": max(s["peak_pss_bytes"] for s in measured),
        "load_ms_by_iteration": {r.get("iteration"): r.get("load_ms") for r in records},
        "thermal": [[s.get("thermal_start"), s.get("thermal_max"), s.get("thermal_end")] for s in measured],
        "artifact_sha256": records[-1].get("artifact_sha256", "unknown"),
        "visible_sha256": sorted({s["visible_sha256"] for s in measured if "visible_sha256" in s}),
        "gallery_executed": False,
        "note": NOTE + " Native decode token counts come from LiteRT-LM benchmark info, not SDK chunk counts.",
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
    parser.add_argument("--fixture", choices=(SHORT_FIXTURE, SUSTAINED_FIXTURE),
                        help="Select one prompt fixture; rows without a fixture are the legacy short prompt")
    parser.add_argument("--run-id", help="Require the exact run ID saved by the runner before instrumentation starts")
    args = parser.parse_args()
    records = [json.loads(line) for line in args.path.read_text().splitlines() if line.strip()]
    try:
        print(json.dumps(summarize(records, args), indent=2))
    except ValueError as error:
        raise SystemExit(str(error)) from error


if __name__ == "__main__":
    main()
