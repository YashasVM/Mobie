# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads with strict range/size validation, retained partials, cancellation, integrity checks, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, latency, prefill/decode throughput, token-count, app-RAM, cold-load, and warm-cache telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Added device-aware LiteRT context sizing, constrained-context replay, thermal protection, inference-stall containment, and a CI-validated two-thread CPU policy.
- Completed restored-vision context accounting, single-image-slot replacement handling, and real SmolVLM2-500M restore → text → replacement-image → text validation.
- Added device-aware vision backend selection: real arm64 devices may try GPU first; emulators/x86 skip unsupported OpenCL probing and use CPU vision.
- Isolated watchdog and explicit Stop cancellation from potentially non-cooperative native `cancelProcess()` calls so callers can regain control instead of hanging indefinitely.
- Aligned recommendation RAM/KV estimates with the exact device-bounded context production passes to LiteRT, preventing 32K/64K artifacts from being rejected solely because their advertised maximum exceeds the usable runtime context.
- Made explicit LiteRT context metadata a hard upper bound: packages below Mobie's 1,024-token practical minimum are rejected consistently instead of being silently expanded beyond the artifact's advertised capacity.

## Important work in progress
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second without enabling unvalidated main-model GPU/NPU execution.
- Thermal protection, long-context pressure, and GPU vision still need representative physical-device testing.

## Tests actually performed
- `6e7c6e8b`: full exact-tip Android CI passed: JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E. Regression coverage verifies `c512` context preservation/rejection and selection of a valid 2K alternative.
- `72c9f3fe`: exact-tip Android CI passed after recommendation/runtime context-parity coverage. Tests verify long-context down-sizing, the 1,024-token minimum under constrained RAM, safe selection of a long-context artifact, and equality between the recommendation-reported context and production runtime policy.
- `0f2fc453`: exact-tip Android CI passed after bounded native-cancellation containment; JVM regressions deliberately block fake native cancellation for 750 ms and require watchdog and explicit Stop paths to regain control within a 50 ms configured bound.
- `83db83db` / `9f06f837`: full Android CI passed: JVM tests/lint/debug APK, emulator smoke, real Qwen LiteRT-LM text E2E, and real SmolVLM2-500M vision E2E.
- `ef276beb` / `0437aa22`: exact-tip Android CI passed constrained-context replay coverage at 1K/2K/4K.
- `6d969769`: Android CI plus real-model CPU-thread benchmark passed.

## Real benchmarks / performance improvements
- Real-Qwen CPU-thread comparison on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s (2.39x decode, 2.02x prefill).
- Production now requests up to two LiteRT CPU threads; this is CI/E2E validated but not claimed as a physical-phone speedup.
- Latest benchmark-run normal Qwen prompt before the production thread change: 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM.
- Cold load measured 2745.6 ms with 339,216,776 bytes cache growth; full unload/reload measured 1476.3 ms with 0 additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, long-context pressure, interrupted-generation recovery, and GPU vision need representative handset testing.
- GPU vision on real arm64 remains enabled first but needs handset validation; CPU fallback is retained when GPU initialization fails.
- Upstream LiteRT-LM streaming can lose terminal callbacks. Mobie returns an error even if collection or cancellation ignores coroutine cancellation, but a truly wedged native call may retain detached worker/native resources until process restart.
- Representative 32K/64K handset validation is still needed before claiming measured RAM/OOM improvement from context sizing or recommendation/runtime alignment.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU and more than two CPU inference threads remain disabled pending representative handset evidence.

## Items to inspect before merging
- On representative arm64 hardware, repeat the vision restore → text → replacement-image → text flow and verify GPU initialization/fallback plus memory/thermal behavior.
- During a long real generation, press Stop repeatedly and verify UI responsiveness, cancellation latency, post-cancel recovery, and process/native-resource behavior if the runtime itself wedges.
- On a RAM-constrained phone, compare Mobie's recommendation result with the actual selected LiteRT context for 32K/64K artifacts and verify both report the same bounded usable context.
- Compare a 32K/64K LiteRT artifact before/after context-budget wiring: load/OOM behavior, selected usable context, app RAM, TTFT, decode and prefill throughput.
- Heat a phone to SEVERE and CRITICAL and verify 256-token throttling, generation cancellation, and no ANR.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, measuring throughput, battery drain, and thermal throttling.
- Interrupt/resume a large real model download and verify ambiguous `Content-Range .../*` resumes are rejected.
