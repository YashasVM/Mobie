# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads with strict range/size validation, retained partials, cancellation, integrity checks, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, latency, prefill/decode throughput, token-count, app-RAM, cold-load, and warm-cache telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Broadened Hugging Face discovery to directly runnable third-party LiteRT-LM artifacts while keeping Featured curated.
- Unified recommendation/runtime context metadata and cold/warm LiteRT cache storage admission, including fail-closed cache identity checks.
- Hardened LiteRT lifecycle ordering, cancellation serialization, interrupted-turn recovery, Stop-vs-completion behavior, and active low-memory checks.
- Added thermal protection around real LiteRT inference: SEVERE remains usable with new output capped to 256 tokens; CRITICAL+ rejects new work and cancels active/stalled generation through an independent 500 ms monitor.
- Aligned device recommendations with that thermal policy: SEVERE warns about the 256-token throttle while CRITICAL+ explains that local model loading is blocked until the phone cools.
- Benchmarked LiteRT CPU threading with the real Qwen model and enabled a conservative two-thread production policy after exact-tip E2E validation.
- Added an inference-stall watchdog around production LiteRT streaming: 120 s initial prefill allowance, 30 s active-stream idle timeout, bounded cancellation, and explicit failure for streams that disappear without a terminal callback.
- Wired device-aware LiteRT KV/context sizing into production and scaled conversation replay below 4K so constrained contexts stay within the selected native context budget.
- Hardened the inference-stall watchdog so Mobie returns control even when a native LiteRT collector ignores coroutine cancellation.
- Validated restored-vision context accounting and completed single-image-slot replacement handling so an old history image is replayed as text before a new image is submitted.

## Important work in progress
- Real multimodal LiteRT-LM E2E uses `litert-community/SmolVLM2-500M`: restore historical image → text follow-up → replacement image → text follow-up. The isolated `23ce401e` run proved normal verification, emulator smoke, and the real Qwen text E2E are green while only the vision job fails. `149569be` now makes vision backend selection device-aware: real arm64 devices may try GPU first, while emulators/x86 skip the unsupported OpenCL probe and go directly to CPU vision. Exact-tip multimodal CI is pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second without unvalidated main-model GPU/NPU execution.
- Thermal protection still needs representative physical-device sustained-heat testing.

## Tests actually performed
- `23ce401e`: JVM tests/lint/debug APK, emulator smoke, and the isolated real Qwen LiteRT-LM text E2E passed; only the isolated SmolVLM2 vision E2E failed during real model execution.
- `149569be`: exact-tip Android CI is in progress. New JVM coverage verifies GPU vision is allowed for a real arm64 profile and skipped for x86 and arm64 emulator profiles.
- `badaeec1`: JVM tests/lint/debug APK and emulator smoke passed. The combined real-runtime job failed during LiteRT model execution after JUnit discovery was fixed; therefore the multimodal replacement path is still not claimed validated.
- `67d2642b` passed JVM tests/lint/debug APK verification, but the first vision test attempt failed during JUnit discovery because its expression body inferred a non-void return type.
- `982f6ec7` passed exact-tip Android CI after single-image replacement handling: JVM tests/lint/debug APK, emulator smoke, and the real Qwen LiteRT-LM E2E all completed successfully.
- `1a2675f7` passed exact-tip Android CI after restored-vision context accounting, including JVM tests/lint/debug APK, emulator smoke, and the real Qwen LiteRT-LM E2E.
- `eadd96ff` and `5656f810` passed exact-tip Android CI after non-cooperative stall containment, including real Qwen LiteRT-LM E2E.
- `ef276beb` and `0437aa22` passed exact-tip Android CI after constrained-context replay; JVM coverage compares 1K, 2K, and 4K replay budgets.
- `c79df688` passed JVM tests/lint/debug APK, emulator smoke, and real Qwen LiteRT-LM E2E with production device-aware context sizing.
- `4bc03ac9`, `1268c26a`, `615fe48d`, and `563aab15` passed the Android CI pipeline for thermal policy, stall guarding, and the two-thread CPU policy.
- `6d969769` passed the same pipeline plus the real-model CPU-thread benchmark.

## Real benchmarks / performance improvements
- Real-Qwen CPU-thread comparison on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s (2.39x decode, 2.02x prefill).
- Production now requests up to two LiteRT CPU threads; this is CI/E2E validated but not claimed as a physical-phone speedup.
- Latest benchmark-run normal Qwen prompt before the production thread change: 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM.
- Cold load measured 2745.6 ms with 339,216,776 bytes cache growth; full unload/reload measured 1476.3 ms with 0 additional cache growth.
- Device-aware context sizing and constrained-context replay are CI/E2E validated for correctness, but no handset RAM/OOM improvement is claimed until representative 32K/64K physical-device testing is performed.

## Known problems / regressions
- Physical-device thermal/LMK behavior, vision history, long-context pressure, and interrupted-generation recovery still need representative handset testing.
- LiteRT-LM `maxNumImages` remains intentionally configured to one image. Replacement handling is text-path CI validated; the real SmolVLM2 multimodal replacement gate is still pending exact-tip runtime validation.
- Vision GPU selection now avoids emulator/x86 OpenCL probing and falls back directly to CPU there. GPU vision on real arm64 hardware remains intentionally enabled first but still needs representative handset validation.
- Upstream LiteRT-LM Android streaming can lose terminal callbacks. Mobie returns an error even if the collector ignores coroutine cancellation, but a truly wedged native call may retain native resources until process restart.
- Representative 32K/64K handset validation is still needed before claiming measured RAM/OOM improvement from device-aware context sizing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU and more than two CPU inference threads remain disabled pending representative handset evidence.

## Items to inspect before merging
- On a real vision model, restore a chat containing an image, send a text-only follow-up, then send a replacement image; verify the restored image receives context reserve, the old image is replayed as text only before replacement, and the new image does not exceed the one-image engine slot.
- On representative arm64 hardware, verify LiteRT GPU vision initializes only when a usable OpenCL stack exists and that CPU fallback remains clean when GPU initialization fails.
- Reproduce a genuinely non-cooperative LiteRT stream and verify the UI regains control after the watchdog timeout; if native resources remain wedged, verify the restart guidance is clear and no ANR occurs.
- On a RAM-constrained phone where context sizing drops below 4K, restore a long conversation and verify replay remains bounded and native context rebuilds cleanly when older turns are evicted.
- On a representative low/free-RAM phone, compare a 32K/64K LiteRT artifact before/after context-budget wiring: verify load success/OOM behavior, selected usable context, app RAM, TTFT, and decode/prefill throughput.
- Heat a representative phone to SEVERE before starting a prompt and verify Mobie still generates with the 256-token cap; at CRITICAL verify new work is blocked and active generation is cancelled.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, measuring TTFT, decode/prefill throughput, battery drain, and thermal throttling.
- Interrupt/resume a large real model download and verify ambiguous `Content-Range .../*` resumes are rejected.
