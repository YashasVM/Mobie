#!/usr/bin/env python3
"""Validate one physical-phone invocation, then summarize measured rows only (stdlib, Python 3.10+)."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics
import sys

CONDITIONS = ("gallery_default", "gallery_mtp", "mobie_default", "mobie_gallery_sampler")
CATEGORIES = ("summarize_text", "code_snippet", "rewrite_tone", "free_form")
IDENTITY = ("artifact_sha256", "artifact_bytes", "app_apk_sha256", "test_apk_sha256",
            "split_apks", "device_fingerprint", "runtime_version", "build_revision",
            "with_vision", "output_budget", "benchmark_protocol")
METRICS = (("decode_tps", "Decode tok/s", 1), ("first_chunk_ms", "First chunk ms", 1),
           ("ms_per_output_token", "ms/output token", 1), ("generation_ms", "Generation ms", 1),
           ("peak_pss_bytes", "Peak PSS MiB", 1024 * 1024))


def validate(report):
    if report.get("status") != "complete" or report.get("error"):
        raise ValueError("Report has a failure or is incomplete")
    rows = report.get("samples")
    if not isinstance(rows, list) or not rows:
        raise ValueError("Missing sample rows")
    for row in rows:
        if row.get("status") != "complete" or row.get("error"):
            raise ValueError("Sample failure: " + str(row.get("error", "incomplete row")))
    for field in IDENTITY:
        if field not in report or report[field] is None or report[field] == "":
            raise ValueError(f"Missing identity: {field}")
        if any(field not in row or row[field] != report[field] for row in rows):
            raise ValueError(f"Mixed identity: {field}")
    if report["benchmark_protocol"] != "mobie-gallery-phone-v1" or report["runtime_version"] != "0.18.0":
        raise ValueError("Unsupported benchmark protocol or LiteRT version")
    if {row.get("condition") for row in rows} != set(CONDITIONS):
        raise ValueError("Missing conditions or unexpected condition")
    rounds, repetitions = report.get("rounds"), report.get("repetitions")
    if type(rounds) is not int or rounds < 2 or rounds % 2 or type(repetitions) is not int or repetitions < 1:
        raise ValueError("Invalid round/repetition counts")
    prompts = report.get("prompts", [])
    prompt_hashes = {}
    for prompt in prompts:
        digest = hashlib.sha256(prompt["text"].encode("utf-8")).hexdigest()
        if prompt.get("sha256") != digest or prompt["id"] in prompt_hashes:
            raise ValueError("Invalid prompt identity")
        prompt_hashes[prompt["id"]] = digest
    if set(prompt_hashes) != set(CATEGORIES):
        raise ValueError("Missing prompt categories")
    expected = {(condition, r, category, rep, False)
                for condition in CONDITIONS for r in range(rounds)
                for category in CATEGORIES for rep in range(repetitions)}
    expected |= {(condition, r, CATEGORIES[0], -1, True) for condition in CONDITIONS for r in range(rounds)}
    seen = set()
    for row in rows:
        if type(row.get("warmup")) is not bool:
            raise ValueError("Missing warmup marker")
        key = (row["condition"], row.get("round"), row.get("prompt_id"), row.get("repetition"), row["warmup"])
        if key not in expected or key in seen:
            raise ValueError("Unexpected or duplicate sample")
        seen.add(key)
        if row.get("prompt_category") != row["prompt_id"] or row.get("prompt_sha256") != prompt_hashes[row["prompt_id"]]:
            raise ValueError("Mixed prompt identity")
        if row.get("visible_length", 0) <= 0 or not row.get("visible_sha256"):
            raise ValueError("Missing visible output")
        for field, _, _ in METRICS:
            value = row.get(field)
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
                raise ValueError(f"Invalid metric: {field}")
        if row["decode_tps"] <= 0 or row["ms_per_output_token"] <= 0 or row["peak_pss_bytes"] <= 0:
            raise ValueError("Invalid zero rate, duration per token, or PSS")
    if seen != expected:
        raise ValueError("Missing measured rows or warmups")
    return [row for row in rows if not row["warmup"]]


def summarize(report):
    rows = validate(report)
    lines = ["Results apply only to this device/build and exact artifact/APKs. Differing samplers change outputs; inspect output lengths and quality before interpreting speedups.",
             "", f"Device fingerprint: `{report['device_fingerprint']}`",
             f"Build revision: `{report['build_revision']}`",
             f"Artifact SHA-256: `{report['artifact_sha256']}`",
             f"App APK SHA-256: `{report['app_apk_sha256']}`",
             f"Test APK SHA-256: `{report['test_apk_sha256']}`", "",
             "Warmups excluded. Cells show median [min, max]; PSS is sampled process memory, not total GPU memory.",
             "OS and persistent caches are uncontrolled. Raw SDK conditions approximate Gallery configuration; Gallery's app was not executed."]
    timed_out = sum(bool(row.get("cool_start_timed_out")) for row in rows)
    lines += [f"Cool-start timeouts affected {timed_out} measured rows."]
    for category in (None, *CATEGORIES):
        selected = [row for row in rows if category is None or row["prompt_category"] == category]
        lines += ["", f"### {category or 'All prompt categories'}", "",
                  "| Condition | n | " + " | ".join(label for _, label, _ in METRICS) + " |",
                  "|---|---:|" + "---:|" * len(METRICS)]
        medians = {}
        for condition in CONDITIONS:
            samples = [row for row in selected if row["condition"] == condition]
            cells = []
            medians[condition] = {}
            for field, _, scale in METRICS:
                values = [row[field] / scale for row in samples]
                middle = statistics.median(values)
                medians[condition][field] = middle
                cells.append(f"{middle:.2f} [{min(values):.2f}, {max(values):.2f}]")
            lines.append(f"| {condition} | {len(samples)} | " + " | ".join(cells) + " |")
        lines += ["", "Speedup ratios use medians. Decode is candidate/baseline; latency is baseline/candidate. Values above 1 favor Mobie.", "",
                  "| Candidate | Baseline | Decode | First chunk | ms/output token | Generation |",
                  "|---|---|---:|---:|---:|---:|"]
        for candidate in CONDITIONS[2:]:
            for baseline in CONDITIONS[:2]:
                ratios = []
                for field in ("decode_tps", "first_chunk_ms", "ms_per_output_token", "generation_ms"):
                    numerator, denominator = medians[candidate][field], medians[baseline][field]
                    if field != "decode_tps":
                        numerator, denominator = denominator, numerator
                    ratios.append(f"{numerator / denominator:.3f}x" if denominator else "n/a")
                lines.append(f"| {candidate} | {baseline} | " + " | ".join(ratios) + " |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        print(summarize(json.loads(args.report.read_text(encoding="utf-8"))), end="")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f"Rejected benchmark: {error}\n")


if __name__ == "__main__":
    main()
