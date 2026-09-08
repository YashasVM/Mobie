# Agent progress

## Major changes completed this week
- Hardened Hugging Face downloads against stale/corrupt resume and reuse: strict range/size validation, retained partials, cancellation, integrity/storage checks, commit-pinned Hub URLs, revision-aware metadata/cache, and exact per-artifact source identity.
- Checksum-less completed files and interrupted partials are now reusable only from immutable 40-character Hugging Face commit-pinned sources; mutable `/resolve/main/...` and arbitrary mutable URLs restart instead of appending/reusing potentially changed bytes. Trusted expected checksums can still prove safe reuse.
- Made completed installs crash-recoverable from validated per-artifact metadata without re-downloading, while rejecting mismatched revisions.
- Made model deletion wait for cancellation of every WorkManager artifact job before removing model storage, failing closed if cancellation cannot be confirmed.
- Replaced collision-prone Java `hashCode()` WorkManager identities with SHA-based identities.
- Improved recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, backend support, and hardware targets; runtime context sizing now follows the same device constraints.
- Added real LiteRT-LM telemetry for TTFT, latency, prefill/decode throughput, token count, app RAM, cold load, and warm-cache load.
- Added thermal protection, inference-stall containment, bounded cancellation, constrained-context replay, and a CI-validated two-thread CPU policy.
- Bound LiteRT persistent-cache reuse to the selected context/KV capacity so a cache created for one runtime context cannot relax cold-load storage admission for another.
- Completed restored-vision context accounting and replacement-image handling; arm64 may try GPU vision while emulators/x86 skip unsupported OpenCL probing.
- Made LiteRT generation Flow state collection-local so recollecting the same request cannot reuse prior output or contaminate committed conversation history.
- Guarded model load/unload/reset lifecycle work with monotonic operation IDs so stale queued work cannot publish READY or unload a newer model after rapid model switches, history changes, or leave/re-enter transitions.
- Serialized selected-model deletion with the runtime lifecycle so native resources are released before model storage is removed.
- Tracked native runtime ownership independently from UI selection and fail closed while ownership is uncertain, so rapid model switching cannot delete files that a native runtime may still hold.

## Important work in progress
- `9ec0d9b7`: fail-closed recovery after a stalled native inference is pending exact-tip CI. Watchdog timeouts now mark the runtime unsafe, block reuse, and bound native unload so model switching/deletion cannot wait forever behind a wedged generation call; reuse is allowed again only after cleanup actually succeeds.
- Continue the download/install/deletion crash-recovery audit for stale, partially replaced, or concurrently accessed artifacts.
- Physical-device validation is still needed for thermal/LMK behavior, long-context pressure, interrupted generation, GPU vision, and CPU thread policy.

## Tests actually performed
- `ad7e5789`: full Android CI passed native runtime ownership/deletion protection: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `dee93cce`: full Android CI passed selected-model deletion/runtime serialization: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `114d45d2`: full Android CI passed stale runtime lifecycle protection: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `30e8568d`: full Android CI passed the repeated-generation-Flow regression with a real Qwen LiteRT model, plus JVM tests/lint/debug APK, emulator instrumentation, and real LiteRT-LM vision E2E.
- `3ba91da4`: full Android CI passed context-bound LiteRT cache reuse coverage: JVM tests/lint/debug APK, emulator instrumentation, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `42ac2630`: full Android CI passed after correcting emulator-smoke command execution; this validates the mutable-source restart/reuse protection and its regression fixtures.
- `7c032ee4`: full Android CI passed collision-resistant WorkManager identity coverage, including known Java hash collisions (`Aa`/`BB`).
- `51866360`: full Android CI passed installed-metadata crash recovery from per-artifact metadata with canonical metadata missing.
- `44123014`: full Android CI passed independent per-artifact source identity retention/reuse.
- `6127f5ab` / `1d2ee10b`: full Android CI passed cancellation-before-delete handling, including concurrent multi-artifact jobs.
- `42a8bd2a` / `cdfa0b06`: full Android CI passed completed-model identity reconstruction and completed-install crash recovery.
- `8e9afbac`, `64a87e22`, `58e093c`: Android CI passed revision-bound download identity, revision-aware detail cache, and commit-pinned Hugging Face discovery/download coverage.
- Real E2E coverage repeatedly exercised Qwen3-0.6B INT4 LiteRT-LM download/load/generate/recollect/cancel/recover/reset/history/unload/reload and SmolVLM2-500M vision restore/text/replacement-image/text generation.
- `6d969769`: Android CI plus real-model CPU-thread benchmark passed.

## Real benchmarks / performance improvements
- Latest 2-vCPU Android CI, Qwen: runtime default 6.07 decode tok/s and 7.32 prefill tok/s; explicit 2 threads 19.06 decode tok/s and 38.70 prefill tok/s (3.14x decode, 5.29x prefill). This is CI/E2E evidence, not a claimed physical-phone speedup.
- Latest representative Qwen prompt: 15.85 decode tok/s, 33.01 prefill tok/s, 1.058 s TTFT, 2.033 s total, ~1.02 GiB app RAM.
- Latest cold load measured 3263.1 ms with 339,217,207 bytes cache growth; full unload/reload measured 1695.9 ms with 0 additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, 32K/64K context pressure, interrupted-generation recovery, GPU vision, and >2 CPU-thread performance remain unvalidated on representative phones.
- Upstream LiteRT-LM streaming can lose terminal callbacks; a truly wedged JNI call may still retain detached worker/native resources until process restart. The new fail-closed lifecycle protection prevents reuse/deletion over uncertain native state but is pending exact-tip CI.
- GGUF remains intentionally unavailable for v1; supported published LiteRT-LM artifacts are the priority.
- Main-model GPU/NPU execution remains disabled pending representative handset evidence.

## Items to inspect before merging
- Interrupt a checksum-less download from a mutable Hub revision, move that revision to different bytes, and verify Mobie restarts rather than appending/reusing the old partial; repeat with an immutable commit-pinned URL and verify safe resume/reuse.
- Delete a model while multiple real artifact downloads are writing and verify all jobs cancel before storage removal with no recreated `.part` or metadata afterward.
- Load model A, switch to model B, immediately delete A while the native transition is still in flight, and verify A is unloaded before its files are removed without unloading a fully loaded B.
- Delete the currently selected/loaded model and immediately switch/re-enter chat; verify native resources close before storage removal and no stale load can reopen the deleted artifact.
- Force a stalled generation/native unload, then switch or delete the model; verify Mobie returns a bounded cleanup error and does not load over or delete storage under uncertain native ownership. After a successful cleanup, verify model loading is allowed again.
- Kill Mobie immediately after model download completion but before install metadata is committed, relaunch, and verify local recovery without network transfer.
- Verify multiple checksum-less artifacts for one model retain independent source identities across app restart.
- Rapidly switch between two installed models and leave/re-enter chat during model loading; verify stale lifecycle work cannot unload the newly selected runtime or leave old model weights resident.
- On representative arm64 hardware, test 32K/64K contexts, GPU vision fallback, severe/critical thermal handling, repeated Stop during long generation, and 2-thread vs runtime-default/4+ thread throughput, RAM, battery, and throttling.
