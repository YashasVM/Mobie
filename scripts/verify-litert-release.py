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
# Collect blocks first: modern R8 marks generated classes with class-level metadata
# instead of always putting ExternalSyntheticLambda in their names. Member-level
# synthesized markers must never exempt an actual SDK class from name checks.
blocks = []
for line in args.mapping.read_text().splitlines():
    match = re.fullmatch(r"(\S+) -> (\S+):", line)
    if match:
        blocks.append((match.group(1), match.group(2), []))
    elif blocks:
        blocks[-1][2].append(line)
for original, renamed, lines in blocks:
    if not original.startswith(prefix):
        continue
    seen.add(original)
    metadata = [line for line in lines if line.startswith("# ")]
    generated_by_r8 = (
        "$$ExternalSyntheticLambda" in original or
        (any('"fileName":"R8$$SyntheticClass"' in line for line in metadata) and
         any('"id":"com.android.tools.r8.synthesized"' in line for line in metadata))
    )
    # Required JNI entry points are never exempt, even with synthetic metadata.
    if original != renamed and (original in required or not generated_by_r8):
        problems.append(f"renamed SDK class: {original} -> {renamed}")
    for line in lines:
        match = re.search(r"\b(onMessage|onDone|onError|onNext)\(.*\).* -> (\S+)$", line)
        if match:
            callback, mapped = match.groups()
            callbacks.add(callback)
            if callback != mapped:
                problems.append(f"renamed JNI callback: {callback} -> {mapped}")
problems.extend(f"missing JNI class: {name}" for name in sorted(required - seen))
problems.extend(f"missing JNI callback: {name}" for name in sorted({"onMessage", "onDone", "onError", "onNext"} - callbacks))
if problems:
    raise SystemExit("\n".join(problems))
print(f"Verified {len(seen)} LiteRT-LM mapping entries and all four JNI callback names.")
