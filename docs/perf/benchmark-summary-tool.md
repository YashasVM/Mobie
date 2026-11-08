# Validate saved benchmark summaries

This host-only tool summarizes saved JSONL captures. It does not run a model, change the Android app, or establish physical-device performance.

```sh
python3 docs/perf/summarize-gpu-results.py capture.jsonl --runtime 0.18.0 --run-id CAPTURE_RUN_ID
python3 -m unittest discover -s scripts/perf -p 'test_*.py'
```

Use the runtime version and run ID recorded by the capture producer. Captures come from separately instrumented builds; the current app is not modified to produce new benchmark rows by this change. Without a run ID, the tool cannot exclude a newer invocation that died before writing its first row.

The validator refuses mixed identities, incomplete invocations, failed warmups, generation or teardown failures, invalid numeric values, and sustained-throughput claims from short replies. Sustained rows must meet their native-token floor. Short latency probes remain labelled as latency probes. A sampler preset does not establish a comparison against the Gallery app.

The tool checks consistency of supplied data. It does not authenticate the capture, prove hardware execution, or replace physical ARM64 validation for native runtime changes.

The broader GPU/runtime/UI work previously carried by PR36 and the original PR39 is retained in the repository under `archive/cleanup-2026-11-09/pr-36` and `archive/cleanup-2026-11-09/pr-39`. Those changes are excluded here because their physical-device gate remains unmet.
