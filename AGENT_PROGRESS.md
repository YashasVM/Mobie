# Agent progress

## Major changes completed this week
- Hardened Hugging Face downloads against stale/corrupt resume and reuse with immutable-source identity, size/checksum validation, retained partials, cancellation, storage checks, commit-pinned Hub URLs, and per-artifact metadata.
- Made completed installs crash-recoverable from validated per-artifact metadata and made model deletion wait for all WorkManager artifact jobs before removing storage.
- Replaced collision-prone Java `hashCode()` WorkManager identities with SHA-based identities.
- Improved recommendations and runtime context sizing using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, backend support, and hardware targets.
- Added real LiteRT-LM telemetry for TTFT, latency, prefill/decode throughput, token count, app RAM, cold load, and warm-cache load.
- Added thermal protection, inference-stall containment, bounded cancellation/unload, constrained-context replay, and a CI-validated two-thread CPU policy.
- Hardened runtime lifecycle ownership so stale load/unload/reset/delete work cannot unload a newer model or remove files still held by native resources.
- Added fail-closed recovery after stalled native inference so Mobie blocks reuse/deletion over uncertain native state until cleanup succeeds.
- Eliminated worker temp-file collisions when multiple model artifacts publish canonical metadata concurrently.

## Important work in progress
- Metadata repair/verification writes in `ModelDownloadManager` now also use operation-unique temporary files and clean them on failure, preventing concurrent installed-model scans or verification repair from racing through a shared `.part`; exact-tip Android CI is pending.
- Continue the download/install/deletion crash-recovery audit for stale, partially replaced, or concurrently accessed artifacts after this change validates.
- Physical-device validation is still needed for thermal/LMK behavior, long-context pressure, interrupted generation, GPU vision, and CPU thread policy.

## Tests actually performed
- `ef67c99e`: full Android CI passed the concurrent worker metadata-publication fix: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `0fedffd2`: full Android CI passed deterministic stalled-unload recovery coverage: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `ad7e5789`: full Android CI passed native runtime ownership/deletion protection.
- `dee93cce`: full Android CI passed selected-model deletion/runtime serialization.
- `114d45d2`: full Android CI passed stale runtime lifecycle protection.
- `30e8568d`: full Android CI passed repeated-generation-Flow coverage with a real Qwen LiteRT model plus vision E2E.
- Real E2E coverage repeatedly exercised Qwen3-0.6B INT4 LiteRT-LM download/load/generate/recollect/cancel/recover/reset/history/unload/reload and SmolVLM2-500M vision restore/text/replacement-image/text generation.
- JVM coverage asserts distinct metadata operation IDs resolve to distinct sibling temp files; the new manager call site reuses that collision-free policy and is pending exact-tip CI.

## Real benchmarks / performance improvements
- Latest 2-vCPU Android CI, Qwen: runtime default 6.07 decode tok/s and 7.32 prefill tok/s; explicit 2 threads 19.06 decode tok/s and 38.70 prefill tok/s (3.14x decode, 5.29x prefill). CI evidence only, not a physical-phone speedup claim.
- Representative Qwen prompt: 15.85 decode tok/s, 33.01 prefill tok/s, 1.058 s TTFT, 2.033 s total, ~1.02 GiB app RAM.
- Representative cold load: 3263.1 ms with 339,217,207 bytes cache growth; unload/reload: 1695.9 ms with no additional cache growth.

## Known problems / regressions
- The new `ModelDownloadManager` metadata-repair collision fix is not yet CI-validated on the exact branch tip.
- Physical-device thermal/LMK behavior, 32K/64K context pressure, interrupted-generation recovery, GPU vision, and >2 CPU-thread performance remain unvalidated on representative phones.
- Upstream LiteRT-LM streaming can lose terminal callbacks; a truly wedged JNI call may still retain detached native resources until process restart. Mobie now fails closed after watchdog timeout.
- GGUF remains intentionally unavailable for v1; supported published LiteRT-LM artifacts are the priority.
- Main-model GPU/NPU execution remains disabled pending representative handset evidence.

## Items to inspect before merging
- Complete two artifacts for the same model nearly simultaneously and verify both workers succeed, both artifact metadata files remain valid, and canonical metadata replacement never fails from temp-file collision.
- Trigger concurrent installed-model scans/verification repair while an artifact finishes and verify canonical/artifact metadata remains readable with no orphaned shared `.part` files.
- Interrupt checksum-less mutable and commit-pinned Hub downloads and verify mutable sources restart while immutable sources resume/reuse safely.
- Delete a model while multiple downloads are writing and verify all jobs cancel before storage removal with no recreated partials/metadata afterward.
- Rapidly switch/load/delete models and verify stale lifecycle work cannot unload a newer runtime or delete files under uncertain native ownership.
- Force stalled generation/native unload and verify Mobie returns a bounded cleanup error, blocks unsafe reuse/deletion, then recovers after successful cleanup.
- On representative arm64 hardware, test 32K/64K contexts, GPU vision fallback, severe/critical thermal handling, repeated Stop during long generation, and 2-thread vs runtime-default/4+ thread throughput, RAM, battery, and throttling.
