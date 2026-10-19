#!/usr/bin/env python3
"""Check that an R8 mapping preserves LiteRT-LM's JNI class and callback names."""
import argparse
import re
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("mapping", type=Path)
args = parser.parse_args()
prefix = "com.google.ai.edge.litertlm."
required = {
    prefix + name
    for name in (
        "BenchmarkInfo", "InputData$Text", "InputData$Image", "InputData$Audio",
        "LiteRtLmJniException", "LiteRtLmJni$JniMessageCallback",
        "LiteRtLmJni$JniInferenceCallback",
    )
}
seen = set()
callbacks = set()
problems = []
in_sdk = False
for line in args.mapping.read_text().splitlines():
    match = re.fullmatch(r"(\S+) -> (\S+):", line)
    if match:
        original, renamed = match.groups()
        in_sdk = original.startswith(prefix)
        if in_sdk:
            seen.add(original)
            # R8 synthesizes lambda classes itself; JNI never resolves them by name.
            if original != renamed and "$$ExternalSyntheticLambda" not in original:
                problems.append(f"renamed SDK class: {original} -> {renamed}")
    elif in_sdk:
        match = re.search(r"\b(onMessage|onDone|onError|onNext)\(.*\).* -> (\S+)$", line)
        if match:
            original, renamed = match.groups()
            callbacks.add(original)
            if original != renamed:
                problems.append(f"renamed JNI callback: {original} -> {renamed}")
problems.extend(f"missing JNI class: {name}" for name in sorted(required - seen))
problems.extend(f"missing JNI callback: {name}" for name in sorted({"onMessage", "onDone", "onError", "onNext"} - callbacks))
if problems:
    raise SystemExit("\n".join(problems))
print(f"Preserved {len(seen)} LiteRT-LM classes and all four JNI callback names.")
