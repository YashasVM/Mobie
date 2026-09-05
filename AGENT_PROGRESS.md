# Agent progress

## Major changes completed this week
- Raised normal chat response capacity from 256 to 1024 tokens while preserving per-model context clamping, and prefer 4K-or-better artifacts when they safely fit so small 1280/2048-token packages do not win solely on RAM.
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, storage admission, and rejection of ambiguous resumed ranges without an authoritative total size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, app-RAM, and cold/reload LiteRT cache-growth telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Hardened LiteRT lifecycle ordering, cancellation serialization, lifecycle-epoch generation admission, and Stop-vs-generation-finish handling.
- Broadened Hugging Face discovery beyond `litert-community`; Featured remains curated while Search accepts directly runnable third-party LiteRT-LM text/vision artifacts, with server-side `litert-lm` filtering.
- Rejected an 8K unknown-context fallback after verifying LiteRT-LM does not expose package max context publicly and current Qwen3-0.6B LiteRT artifacts are published at 2K/4K context; unknown artifacts remain on the conservative 4K fallback.
- Replaced the old 30% first-load LiteRT cache estimate with a measured model-sized allowance plus 10% safety; recommendation storage estimates and runtime cold-load admission use the same policy.
- Added fail-closed warm-cache identity tracking keyed to exact model path/size/mtime, LiteRT-LM version, and cache manifest; validated warm reloads now use reduced storage headroom while stale/missing/mutated caches fall back to cold-load admission.
- Added artifact-specific context metadata from Hugging Face model-card tables for `.litertlm` filenames that omit context, including compact `4K`/`8K` values, so KV/RAM recommendations use publisher capacity when available.
- Hardened model-card parsing across Markdown table/prose boundaries so unrelated storage/benchmark values cannot leak into context capacity.
- Validated model-card context propagation end to end through catalog selection → runtime-aware filename → hashed on-disk filename → LiteRT runtime context parsing, so recommendation memory estimates and EngineConfig stay aligned after download/restart.
- Added thermal inference protection around the real LiteRT runtime: SEVERE caps new responses to 256 tokens; CRITICAL/EMERGENCY/SHUTDOWN reject new work and cancel active generations before stale output is forwarded.
- Validated independent 500 ms thermal monitoring during active generation, including stalled native inference, so CRITICAL+ escalation can cancel LiteRT even when no further token callbacks arrive.
- Benchmarked LiteRT-LM CPU threading with the real Qwen3-0.6B INT4 artifact and promoted the measured win into production with a conservative two-thread cap.

## Important work in progress
- Validate the production two-thread LiteRT CPU policy through exact-tip Android CI and real-model E2E; keep the two-thread cap until representative phones justify using more heterogeneous cores.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.
- Thermal protection still needs representative physical-device heat testing across sustained SEVERE/CRITICAL transitions; CI validates control flow, not real handset throttling behavior.

## Tests actually performed
- `6d969769` passed Android CI including JVM tests/lint/debug APK build, emulator smoke, real Qwen LiteRT-LM E2E, and the new CPU-thread benchmark artifact.
- The CPU-thread benchmark used the same Qwen3-0.6B INT4 model and measured runtime-default CPU execution against explicit two-thread execution on the 2-vCPU Android runner.
- `def170ec` passed Android CI after preserving thermal-guard constructor call-site compatibility: JVM tests/lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E all passed.
- The stalled-inference thermal regression simulates a token followed by CRITICAL thermal escalation while native inference stops producing output; the guard independently cancels the delegate instead of waiting for another inference event.
- `455d3ca9` passed Android CI after adding active-generation CRITICAL+ cancellation, validating the existing unit/lint/APK/emulator/real-Qwen pipeline for that path.
- `b939096a` passed Android CI after fixing the thermal-guard JVM test coroutine runner; this validates new-generation thermal admission through the existing CI pipeline.
- `25f60ace` passed JVM tests, lint/debug APK build, emulator smoke, and the full real Qwen LiteRT-LM E2E with cross-layer stored-context propagation coverage.
- `404fb573` passed JVM tests, lint/debug APK build, emulator smoke, and the full real Qwen LiteRT-LM E2E with model-card table-boundary hardening.
- `2d938534` passed JVM tests, lint/debug APK build, emulator smoke, and the full real Qwen LiteRT-LM E2E after fixing compact `4K`/`8K` context parsing.
- `e3ec8758` passed JVM tests, lint/debug APK build, emulator smoke, and the full real Qwen LiteRT-LM E2E lifecycle with validated warm-cache runtime integration.
- `c8013686` passed the same full pipeline with the measured cold-load storage policy.
- The successful Qwen E2E measured a 347,251,840-byte artifact, 339,216,776-byte cold cache, and 0-byte cache growth after full unload/reload.
- `1fa77181` passed Android CI with `LiteRtCacheState` JVM coverage for unchanged warm-cache reuse, cache mutation invalidation, model replacement invalidation, and refusal to mark an empty cache.

## Real benchmarks / performance improvements
- Real-Qwen CPU-thread comparison on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s, a 2.39x decode and 2.02x prefill speedup.
- Production now requests up to two LiteRT CPU threads rather than the runtime default; this is benchmark-backed on CI but not yet claimed as a physical-phone speedup.
- Latest normal Qwen E2E on the same run: first prompt 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM before the production thread-policy change.
- Cold load: 2745.6 ms and 339,216,776 bytes of LiteRT cache/filesystem growth; full unload/reload: 1476.3 ms and 0 bytes of additional cache growth.
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Thermal/LMK behavior, vision history, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- More than two CPU inference threads remain intentionally disabled until phone benchmarks show a sustained gain without unacceptable heat/battery cost.

## Items to inspect before merging
- Compare the two-thread production policy against runtime-default and 4+ threads on representative big.LITTLE phones, measuring TTFT, decode/prefill throughput, battery drain, and sustained thermal throttling before raising the cap.
- Heat representative phones through MODERATE → SEVERE → CRITICAL during both normal streaming and an intentionally stalled/slow response; verify active inference cancels within roughly the monitor interval without ANR, stale tokens, or conversation corruption.
- Verify a `.litertlm` artifact whose publisher filename omits context but whose model card says 2048 installs with a local `ctx2048` runtime marker, while the Hugging Face URL still targets the original filename and LiteRT uses 2048 rather than the 4K fallback.
- Verify Qwen3-0.6B artifacts whose filenames omit context show their published 2048/4096-token capacities and corresponding KV-memory recommendation impact, while unrelated model-card tables cannot override them.
- Verify an initialized model can reload with only filesystem safety reserve free while a cold or mutated-cache model is still blocked.
- Delete/truncate/replace an installed model after cache creation and verify the warm marker is rejected before native initialization.
- Interrupt/resume a large real model download and verify Mobie rejects ambiguous `Content-Range .../*` resumes instead of finalizing an unproven partial artifact.
- Trigger Stop exactly as a real generation completes, then immediately send another prompt; verify the next prompt runs normally.
- Switch directly between installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Exercise a near-4K conversation including vision/history eviction and verify bounded replay/output admission stays stable.
