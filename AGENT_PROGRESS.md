# Agent progress

## Major changes completed this week
- Hardened Hugging Face downloads against stale/corrupt resume and reuse with immutable-source identity, size/checksum validation, retained partials, cancellation, storage checks, commit-pinned Hub URLs, per-artifact metadata, and crash recovery for commit-pinned artifacts whose catalog size/checksum is unknown by persisting the resolved HTTP transfer length only for the same immutable source.
- Made completed installs crash-recoverable from validated per-artifact metadata and made model deletion coordinate with WorkManager artifact jobs before removing storage.
- Replaced collision-prone Java `hashCode()` WorkManager identities with SHA-based identities.
- Improved recommendations and runtime context sizing using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, backend support, and hardware targets.
- Added real LiteRT-LM telemetry for TTFT, latency, prefill/decode throughput, token count, app RAM, cold load, and warm-cache load.
- Added thermal protection, inference-stall containment, bounded cancellation/unload, constrained-context replay, and a CI-validated two-thread CPU policy.
- Hardened runtime lifecycle ownership so stale load/unload/reset/delete work cannot unload a newer model or remove files still held by native resources.
- Added fail-closed recovery after stalled native inference so Mobie blocks reuse/deletion over uncertain native state until cleanup succeeds.
- Bounded native cancellation when callers stop guarded generation; unsuccessful or timed-out native cancellation now marks runtime ownership uncertain and blocks reuse until cleanup succeeds.
- Eliminated temp-file collisions in both worker metadata publication and manager-side metadata repair/verification.
- Made active model transfer, worker checksum loops, and manager-side completed-file verification cooperatively observe coroutine cancellation so cancelled work does not keep consuming network/CPU until an unrelated suspend point.
- Made deletion recover ownership from valid per-artifact metadata when canonical install metadata is missing/corrupt, while still failing closed on absent/conflicting ownership.

## Important work in progress
- Correct automatic recommendations that wrongly excluded portable LiteRT-LM bundles merely because their filenames contain generic `gpu`/`opencl` backend hints; official CPU-runnable Llama 3.2 1B is the regression case. Exact-tip Android CI is pending.
- Continue the download/install/deletion crash-recovery audit for stale, partially replaced, or concurrently accessed artifacts; no new code change is being made without a reproducible correctness/reliability defect.
- Continue auditing runtime/recommendation failure modes after validating stopped-generation native cancellation; prioritize reproducible failures over speculative refactors.
- Physical-device validation is still needed for thermal/LMK behavior, long-context pressure, interrupted generation, GPU vision, and CPU thread policy.

## Tests actually performed
- Added JVM regression coverage that keeps portable `gpu`/`opencl` LiteRT-LM bundles eligible while retaining fail-closed classification for MediaTek/QNN/Adreno and desktop/web-specific packages; exact-tip CI is pending.
- `29a68e25`: full Android CI passed stopped-generation bounded native cancellation: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- Added deterministic JVM coverage for caller cancellation, bounded native-cancel failure, and malformed generation ending without a terminal callback.
- `0de8146f`: full Android CI passed resolved-length crash recovery: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `19ecbc71`: JVM tests/lint/debug APK, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E passed resolved-length crash recovery. Emulator smoke did not reach Mobie tests because GitHub Android SDK provisioning failed with `Intel x86_64 Atom System Image: Error on ZipFile unknown archive`; the subsequent exact-tip run passed emulator smoke.
- `652173df`: JVM tests/lint/debug APK, emulator instrumentation, and real LiteRT-LM text E2E passed resolved-length crash recovery. Vision E2E reached the 55-minute job timeout and was cancelled without reporting a test failure; the subsequent exact-tip run passed vision.
- `8ca93542`: full Android CI passed artifact-metadata deletion recovery: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- Added JVM coverage for resolved transfer-length resume metadata and immutable-source identity matching.
- `25c8e489`: full Android CI passed manager-side cancellation-aware completed-file SHA-256 verification: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E. The preceding run's only failure was transient debug-APK artifact upload infrastructure.
- Added a deterministic JVM regression that aborts manager SHA-256 verification on the third cancellation check instead of reading the full file.
- `4e32fc92`: full Android CI passed cooperative worker download/checksum cancellation.
- `18d4561f`: full Android CI passed manager-side metadata repair/verification collision protection.
- `ef67c99e`: full Android CI passed the concurrent worker metadata-publication fix.
- `0fedffd2`: full Android CI passed deterministic stalled-unload recovery coverage.
- `ad7e5789`: full Android CI passed native runtime ownership/deletion protection.
- `dee93cce`: full Android CI passed selected-model deletion/runtime serialization.
- `114d45d2`: full Android CI passed stale runtime lifecycle protection.
- `30e8568d`: full Android CI passed repeated-generation-Flow coverage with a real Qwen LiteRT model plus vision E2E.
- Real E2E coverage repeatedly exercised Qwen3-0.6B INT4 LiteRT-LM download/load/generate/recollect/cancel/recover/reset/history/unload/reload and SmolVLM2-500M vision restore/text/replacement-image/text generation.

## Real benchmarks / performance improvements
- Latest 2-vCPU Android CI, Qwen: runtime default 6.07 decode tok/s and 7.32 prefill tok/s; explicit 2 threads 19.06 decode tok/s and 38.70 prefill tok/s (3.14x decode, 5.29x prefill). CI evidence only, not a physical-phone speedup claim.
- Representative Qwen prompt: 15.85 decode tok/s, 33.01 prefill tok/s, 1.058 s TTFT, 2.033 s total, ~1.02 GiB app RAM.
- Representative cold load: 3263.1 ms with 339,217,207 bytes cache growth; unload/reload: 1695.9 ms with no additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, 32K/64K context pressure, interrupted-generation recovery, GPU vision, and >2 CPU-thread performance remain unvalidated on representative phones.
- Upstream LiteRT-LM streaming can lose terminal callbacks; a truly wedged JNI call may still retain detached native resources until process restart. Mobie now fails closed after watchdog timeout.
- GGUF remains intentionally unavailable for v1; supported published LiteRT-LM artifacts are the priority.
- Main-model GPU/NPU execution remains disabled pending representative handset evidence.

## Items to inspect before merging
- Confirm official portable LiteRT-LM bundles such as `llama3_2_1b_mixed_int4_gpu.litertlm` remain eligible for CPU-backed recommendation, while MediaTek/QNN/Adreno and desktop/web-specific bundles remain excluded from automatic selection.
- Stop an active long generation repeatedly and verify native cancellation returns promptly; if native cancellation wedges, verify Mobie blocks runtime reuse until unload succeeds instead of returning to unsafe reuse.
- Interrupt a commit-pinned download with no catalog size/checksum after final-file promotion but before install metadata publication; verify restart recovers the completed file from the matching immutable-source sidecar without re-downloading it.
- Verify stale/mutable resume metadata never supplies a recovered length to a fresh download.
- Delete an install after removing/corrupting canonical `.model.properties` while valid per-artifact metadata remains; verify deletion still succeeds, but conflicting/absent ownership metadata fails closed.
- Cancel/delete a large active model download and verify network/CPU activity stops promptly while the resumable partial remains reusable.
- Cancel during checksum verification of a large completed/partial file and verify cancellation returns promptly without finalizing stale metadata.
- Complete two artifacts for the same model nearly simultaneously and verify both workers succeed and canonical/artifact metadata remains valid.
- Trigger concurrent installed-model scans/verification repair while an artifact finishes and verify metadata remains readable with no shared-temp collisions.
- Interrupt checksum-less mutable and commit-pinned Hub downloads and verify mutable sources restart while immutable sources resume/reuse safely.
- Delete a model while multiple downloads are writing and verify cancellation prevents storage from being republished afterward.
- Rapidly switch/load/delete models and verify stale lifecycle work cannot unload a newer runtime or delete files under uncertain native ownership.
- Force stalled generation/native unload and verify Mobie returns a bounded cleanup error, blocks unsafe reuse/deletion, then recovers after successful cleanup.
- On representative arm64 hardware, test 32K/64K contexts, GPU vision fallback, severe/critical thermal handling, repeated Stop during long generation, and 2-thread vs runtime-default/4+ thread throughput, RAM, battery, and throttling.