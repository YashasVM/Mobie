#!/usr/bin/env python3
"""Summarize the last four samples of one benchmark case, excluding iteration-zero warm-up."""
import argparse
import json
import statistics
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("path", type=Path)
parser.add_argument("--backend")
parser.add_argument("--context", type=int)
parser.add_argument("--runtime", default="0.18.0")
parser.add_argument("--cpu-threads", type=int)
parser.add_argument("--activation", default="AUTO")
parser.add_argument("--gallery", action="store_true")
args = parser.parse_args()
records = [json.loads(line) for line in args.path.read_text().splitlines() if line.strip()]
records = [r for r in records if r.get("runtime_version") == args.runtime and not r.get("error")
           and r.get("decode_tps", 0) > 0
           and (args.backend is None or r.get("requested_backend", r.get("active_backend")) == args.backend)
           and (args.context is None or r.get("context_tokens") == args.context)
           and (args.cpu_threads is None or r.get("cpu_threads") == args.cpu_threads)
           and r.get("activation_type", "AUTO") == args.activation
           and r.get("gallery_equivalent", False) == args.gallery]
# Cumulative snapshots contain previous invocations. Select the last invocation, then its measured reps.
start = max((i for i, r in enumerate(records) if r.get("iteration") == 0), default=0)
records = records[start:]
records = [r for r in records if r.get("iteration", 0) > 0]
if not records:
    raise SystemExit("No measured repetitions after warm-up match this case.")

def median(key):
    return statistics.median(r[key] for r in records)

print(json.dumps({
    "measured_repetitions": len(records),
    "decode_tps_median": median("decode_tps"),
    "first_chunk_ms_median": median("first_chunk_ms"),
    "peak_pss_bytes_median": median("peak_pss_bytes"),
    "peak_pss_bytes_max": max(r["peak_pss_bytes"] for r in records),
    "load_ms_median": median("load_ms"),
    "thermal": [[r["thermal_start"], r["thermal_max"], r["thermal_end"]] for r in records],
    "artifact_sha256": records[-1].get("artifact_sha256", "see original handoff identity"),
    "visible_sha256": sorted({r["visible_sha256"] for r in records if "visible_sha256" in r}),
}, indent=2))
