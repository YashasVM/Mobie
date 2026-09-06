# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads with strict range/size validation, retained partials, cancellation, integrity checks, and storage admission.
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
- Pin Hugging Face model-card and artifact requests to the exact Hub revision returned by discovery so resumable downloads cannot silently cross mutable `main` revisions; exact-tip Android CI is pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second without enabling unvalidated main-model GPU/NPU execution.
- Thermal protection, long-context pressure, and GPU vision still need representative physical-device testing.

## Tests actually performed
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
- Verify newly discovered Hugging Face artifacts use commit-pinned `/resolve/<sha>/...` URLs and that an interrupted large download resumes the same immutable revision.
- On representative arm64 hardware, repeat the vision restore → text → replacement-image → text flow and verify GPU initialization/fallback plus memory/thermal behavior.
- During a long real generation, press Stop repeatedly and verify UI responsiveness, cancellation latency, post-cancel recovery, and native-resource behavior.
- Compare recommendation/runtime selected context for 32K/64K artifacts on a RAM-constrained phone and measure app RAM, TTFT, decode and prefill throughput.
- Heat a phone to SEVERE and CRITICAL and verify throttling/cancellation without ANR.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, including throughput, battery drain, and thermal throttling.
