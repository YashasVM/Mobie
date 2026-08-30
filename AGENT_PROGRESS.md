# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Added unit coverage for active memory pressure and low-memory-threshold headroom.

## In progress
- Continue auditing real model download/resume behavior and end-to-end LiteRT-LM execution before changing runtime code.

## Tests performed
- Added/updated JVM unit tests for compatibility decisions. CI is configured only for `main` pushes and pull requests, so this branch push did not automatically execute Android CI in this run.

## Benchmarks
- None yet. No performance claim made.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Full emulator/model inference was not executed in this run.

## Inspect before merging
- Verify model recommendations on devices under real memory pressure and on low-RAM phones.
