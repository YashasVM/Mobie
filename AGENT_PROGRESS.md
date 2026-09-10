# Agent progress

## Major changes completed this week
- Hardened Hugging Face downloads and installs with immutable source identity, commit-pinned URLs, size/checksum validation, resumable partials, cancellation, storage checks, per-artifact metadata, crash recovery, and SHA-based WorkManager identities.
- Made completed installs and deletion recover safely from corrupt or stale canonical/per-artifact metadata, including fallback to matching canonical metadata when a parseable stale artifact sidecar would otherwise force a redownload, while keeping ownership checks fail-closed.
- Applied the same reusable-metadata selection rule inside `ModelDownloadWorker`, preventing a stale per-artifact sidecar from deleting and redownloading a valid installed file when matching canonical metadata proves the requested immutable source.
- Made present-but-malformed `installedLength` metadata fail closed instead of being treated like legacy metadata where the property is absent; genuine legacy metadata remains readable.
- Improved recommendations/runtime sizing using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, backend support, and hardware targets; portable LiteRT-LM `gpu`/`opencl` bundles remain eligible while vendor/platform-specific bundles remain excluded.
- Added LiteRT-LM telemetry for TTFT, latency, prefill/decode throughput, token count, app RAM, cold load, and warm-cache load.
- Added thermal protection, inference-stall containment, bounded cancellation/unload, constrained-context replay, and a CI-validated two-thread CPU policy.
- Hardened critical thermal escalation so generation collection still stops promptly when native runtime cancellation throws, surfacing recovery guidance instead of allowing generation to continue.
- Fail closed after explicit/native cancellation failure: the stall guard blocks further generation until a successful unload clears uncertain runtime ownership, recovery errors carry `requiresReload=true`, and the UI remains in `ERROR` instead of re-enabling prompts until reload.
- Hardened runtime ownership so stale lifecycle work cannot unload a newer model or delete files still held by native resources; uncertain native state now fails closed until cleanup succeeds.
- Made active download/checksum verification cancellation-aware and removed metadata temp-file collision risks.

## Important work in progress
- Continue the download/install/deletion crash-recovery audit for stale, partially replaced, or concurrently accessed artifacts; no speculative changes without a reproducible defect.
- Continue runtime/recommendation failure-mode audit, especially Stop/reload ownership and low-memory transitions.
- Physical-device validation is still needed for thermal/LMK behavior, long-context pressure, interrupted generation, GPU vision, and CPU thread policy.

## Tests actually performed
- `cad32a57`: full Android CI passed the recovery-required UI fail-closed change; prompts stay blocked when an inference error requires reload.
- `0e03ffe7`: full Android CI passed fail-closed cancellation recovery, including JVM coverage proving a failed explicit cancel blocks subsequent generation and successful unload restores execution.
- `5422a236`: full Android CI passed critical thermal cancellation-failure hardening, including the regression where native cancel throws while generation is stalled.
- `56542570`: full Android CI passed malformed `installedLength` validation: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `b0191ba8` and `5de23bb5`: full Android CI passed worker/completed-file stale-metadata fallback across the same four gates.
- `5b6018e5`, `9d9ae1fe`, `29a68e25`, `0de8146f`, `8ca93542`, and `25c8e489`: full Android CI validated corrupt metadata recovery, portable LiteRT recommendation selection, bounded native cancellation, resolved-length crash recovery, deletion recovery, and cancellation-aware SHA-256 verification.
- Real E2E coverage repeatedly exercised Qwen3-0.6B INT4 LiteRT-LM download/load/generate/recollect/cancel/recover/reset/history/unload/reload plus SmolVLM2-500M vision restore/text/replacement-image/text generation.

## Real benchmarks / performance improvements
- Latest 2-vCPU Android CI, Qwen: runtime default 6.07 decode tok/s and 7.32 prefill tok/s; explicit 2 threads 19.06 decode tok/s and 38.70 prefill tok/s (3.14x decode, 5.29x prefill). CI evidence only.
- Representative Qwen prompt: 15.85 decode tok/s, 33.01 prefill tok/s, 1.058 s TTFT, 2.033 s total, ~1.02 GiB app RAM.
- Representative cold load: 3263.1 ms with 339,217,207 bytes cache growth; unload/reload: 1695.9 ms with no additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, 32K/64K context pressure, interrupted-generation recovery, GPU vision, and >2 CPU-thread performance remain unvalidated on representative phones.
- Upstream LiteRT-LM streaming can lose terminal callbacks; a truly wedged JNI call may retain detached native resources until process restart. Mobie fails closed after watchdog timeout.
- GGUF remains intentionally unavailable for v1; supported published LiteRT-LM artifacts are the priority.
- Main-model GPU/NPU execution remains disabled pending representative handset evidence.

## Items to inspect before merging
- Force explicit/native cancellation failure and verify the runtime rejects another generation until unload succeeds; the UI should remain non-READY when the resulting error carries `requiresReload=true`.
- Force critical thermal escalation while runtime cancellation fails; generation collection should stop promptly and surface a recovery-oriented error.
- Corrupt `installedLength` metadata should fail closed while legacy metadata with no `installedLength` remains readable.
- Verify stale/corrupt per-artifact metadata cannot block reuse when matching canonical metadata validates the requested immutable source.
- Repeatedly stop long generation and verify bounded native cancellation plus fail-closed cleanup behavior.
- Interrupt commit-pinned and mutable downloads at resume/finalization boundaries; immutable sources should recover safely while mutable sources restart.
- Rapidly switch/load/delete models and verify stale lifecycle work cannot unload newer runtime state.
- On representative arm64 hardware, test 32K/64K contexts, GPU vision fallback, severe/critical thermal handling, repeated Stop, and 2-thread vs runtime-default/4+ thread throughput, RAM, battery, and throttling.