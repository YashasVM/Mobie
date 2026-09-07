# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads with strict range/size validation, retained partials, cancellation, integrity checks, storage admission, and revision-bound reuse.
- Pinned Hugging Face model-card and `.litertlm` artifact requests to the exact discovered Hub commit SHA, made model-detail caching revision-aware, and bound completed unverified files plus resumable `.part` files to their exact source URL so mutable `main` updates cannot reuse or append stale bytes across revisions.
- Made completed model installs crash-recoverable: exact-source sidecars now survive until installed metadata is atomically committed, so a process death after the final model move can repair metadata locally without re-downloading; mismatched revisions remain untrusted.
- Enforced exact source identity when reusing completed checksum-less models through `ModelDownloadManager.completedFile()`, and restored persisted source URLs when reconstructing installed artifacts so legitimate same-revision models remain reusable after app restart.
- Preserved independent source/checksum identity for every artifact sharing a model directory, so installing or downloading one artifact no longer overwrites another artifact's revision metadata or forces valid checksum-less files to re-download.
- Recovered interrupted installs from validated per-artifact metadata when canonical `.model.properties` is missing or unusable, recreating canonical metadata locally without network transfer while preserving exact source identity.
- Made model deletion wait for WorkManager download cancellation before removing model storage, and extended that cancellation to every artifact download sharing the model directory so concurrent multi-artifact writes cannot race recursive deletion; deletion fails closed if cancellation cannot be confirmed.
- Verified real Qwen3-0.6B INT4 LiteRT-LM download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, latency, prefill/decode throughput, token-count, app-RAM, cold-load, and warm-cache telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Added device-aware LiteRT context sizing, constrained-context replay, thermal protection, inference-stall containment, and a CI-validated two-thread CPU policy.
- Completed restored-vision context accounting, single-image-slot replacement handling, and real SmolVLM2-500M restore → text → replacement-image → text validation.
- Added device-aware vision backend selection: real arm64 devices may try GPU first; emulators/x86 skip unsupported OpenCL probing and use CPU vision.
- Isolated watchdog and explicit Stop cancellation from potentially non-cooperative native cancellation calls.
- Aligned recommendation RAM/KV estimates with the exact device-bounded context production passes to LiteRT.
- Made explicit LiteRT context metadata a hard upper bound; packages below Mobie's 1,024-token practical minimum are rejected consistently.

## Important work in progress
- Continue auditing download/install identity, deletion, and crash-recovery paths for stale, partially replaced, or concurrently accessed artifacts.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second without enabling unvalidated main-model GPU/NPU execution.
- Thermal protection, long-context pressure, interrupted-generation recovery, and GPU vision still need representative physical-device testing.

## Tests actually performed
- `51866360`: full Android CI passed installed-metadata crash recovery after instrumentation compile fix: JVM tests/lint/debug APK, emulator smoke covering recovery from per-artifact metadata with canonical metadata missing, real LiteRT-LM text E2E, and real LiteRT-LM vision E2E.
- `44123014`: full Android CI passed per-artifact source identity retention: JVM tests/lint/debug APK, emulator smoke covering two checksum-less artifacts retaining independent exact-source identity and reuse, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `6127f5ab`: full Android CI passed multi-artifact cancellation-before-delete handling: JVM tests/lint/debug APK, emulator smoke with both artifact jobs reaching cancellation before model storage removal, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `1d2ee10b`: full Android CI passed cancellation-before-delete handling: JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `42a8bd2a`: full Android CI passed completed-model source-identity reconstruction and reuse after restart: JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `cdfa0b06`: full Android CI passed completed-install crash recovery: JVM tests/lint/debug APK, emulator smoke with a no-network recovery test for a fully moved model lacking final metadata, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `8e9afbac`: full Android CI passed revision-bound completed-file/partial-download identity coverage, JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- Revision-bound download identity regression tests cover same-source reuse, cross-revision rejection, legacy metadata rejection, and checksum-proven completed-file reuse.
- `64a87e22`: Android CI passed revision-aware Hugging Face detail-cache coverage on top of commit-pinned Hub URLs.
- `58e093c`: Android CI passed commit-pinned Hugging Face discovery/download coverage.
- `6e7c6e8b`: full Android CI passed: JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `72c9f3fe`: Android CI passed recommendation/runtime context-parity coverage, including constrained-RAM context sizing.
- `0f2fc453`: Android CI passed bounded native-cancellation tests with deliberately blocked fake native cancellation.
- `83db83db` / `9f06f837`: full Android CI passed real Qwen text and SmolVLM2 vision flows.
- `ef276beb` / `0437aa22`: Android CI passed constrained-context replay coverage at 1K/2K/4K.
- `6d969769`: Android CI plus real-model CPU-thread benchmark passed.

## Real benchmarks / performance improvements
- Real-Qwen CPU benchmark on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s (2.39x decode, 2.02x prefill).
- Production now requests up to two LiteRT CPU threads; this is CI/E2E validated but not claimed as a physical-phone speedup.
- Representative normal Qwen prompt: 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM.
- Cold load measured 2745.6 ms with 339,216,776 bytes cache growth; full unload/reload measured 1476.3 ms with 0 additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, long-context pressure, interrupted-generation recovery, and GPU vision need representative handset testing.
- Upstream LiteRT-LM streaming can lose terminal callbacks; Mobie contains the caller/UI path, but a truly wedged native call may retain detached worker/native resources until process restart.
- Representative 32K/64K handset validation is still needed before claiming measured RAM/OOM improvements.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU and more than two CPU inference threads remain disabled pending representative handset evidence.

## Items to inspect before merging
- Delete a model while multiple real artifact downloads are actively writing, and verify every job is cancelled before storage removal with no re-created `.part`/metadata files afterward.
- Force-stop/kill Mobie immediately after a model finishes downloading but before installation state appears, relaunch it, and confirm the complete model is recovered without network transfer and the sidecar is removed only after metadata exists.
- Publish a new Hugging Face repo revision with the same model ID/file name/size and confirm Mobie refuses to reuse the older completed file or `.part` bytes unless a checksum independently proves the completed artifact.
- Verify multiple checksum-less artifacts for one model retain independent persisted source URLs after app restart and each remains reusable only for its own revision.
- Verify a checksum-less installed model still resolves to the persisted exact source URL after app restart, and that `completedFile()` accepts only the same revision while rejecting an otherwise-identical different revision.
- Verify newly discovered Hugging Face artifacts use commit-pinned `/resolve/<sha>/...` URLs and metadata/detail caching switches atomically to new SHAs.
- On representative arm64 hardware, repeat the vision restore → text → replacement-image → text flow and verify GPU initialization/fallback plus memory/thermal behavior.
- During a long real generation, press Stop repeatedly and verify UI responsiveness, cancellation latency, post-cancel recovery, and native-resource behavior.
- Compare recommendation/runtime selected context for 32K/64K artifacts on a RAM-constrained phone and measure app RAM, TTFT, decode and prefill throughput.
- Heat a phone to SEVERE and CRITICAL and verify throttling/cancellation without ANR.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, including throughput, battery drain, and thermal throttling.
