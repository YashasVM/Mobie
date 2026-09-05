# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads with strict range/size validation, retained partials, cancellation, integrity checks, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, latency, prefill/decode throughput, token-count, app-RAM, cold-load, and warm-cache telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Broadened Hugging Face discovery to directly runnable third-party LiteRT-LM artifacts while keeping Featured curated.
- Unified recommendation/runtime context metadata and cold/warm LiteRT cache storage admission, including fail-closed cache identity checks.
- Hardened LiteRT lifecycle ordering, cancellation serialization, interrupted-turn recovery, Stop-vs-completion behavior, and active low-memory checks.
- Added thermal protection around real LiteRT inference: SEVERE caps new output to 256 tokens; CRITICAL+ rejects new work and cancels active/stalled generation through an independent 500 ms monitor.
- Benchmarked LiteRT CPU threading with the real Qwen model and enabled a conservative two-thread production policy after exact-tip E2E validation.
- Added an inference-stall watchdog around production LiteRT streaming: 120 s initial prefill allowance, 30 s active-stream idle timeout, bounded cancellation, and explicit failure for streams that disappear without a terminal callback.

## Important work in progress
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.
- Thermal protection still needs representative physical-device sustained-heat testing.

## Tests actually performed
- `e7368396` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with the inference-stall guard wired around production LiteRT.
- Inference-stall JVM coverage exercises stalled prefill, mid-stream stalls after token output, healthy Token → Stats → Complete pass-through, and streams ending without a terminal event.
- `563aab15` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with the production two-thread CPU policy.
- `6d969769` passed the same pipeline plus the real-model CPU-thread benchmark.
- `def170ec` passed JVM/lint/APK/emulator/real-Qwen E2E for independent stalled-inference thermal cancellation.
- `455d3ca9`, `b939096a`, `25f60ace`, `404fb573`, `2d938534`, `e3ec8758`, `c8013686`, and `1fa77181` passed their respective lifecycle, thermal, context, cache, and runtime regression pipelines.

## Real benchmarks / performance improvements
- Real-Qwen CPU-thread comparison on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s (2.39x decode, 2.02x prefill).
- Production now requests up to two LiteRT CPU threads; this is CI/E2E validated but not claimed as a physical-phone speedup.
- Latest benchmark-run normal Qwen prompt before the production thread change: 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM.
- Cold load measured 2745.6 ms with 339,216,776 bytes cache growth; full unload/reload measured 1476.3 ms with 0 additional cache growth.

## Known problems / regressions
- Physical-device thermal/LMK behavior, vision history, long-context pressure, and interrupted-generation recovery still need representative handset testing.
- Upstream LiteRT-LM Android streaming has open reports of missing terminal callbacks; Mobie now fails and cancels stalled streams instead of waiting forever, but recovery from a native deadlock that ignores cancellation still needs a reproducible device case.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU and more than two CPU inference threads remain disabled pending representative handset evidence.

## Items to inspect before merging
- Reproduce a real LiteRT stream whose terminal callback disappears and verify Mobie returns control, reports the stall, and can recover/reload without ANR or stale tokens.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, measuring TTFT, decode/prefill throughput, battery drain, and thermal throttling.
- Heat representative phones through MODERATE → SEVERE → CRITICAL during normal and intentionally slow/stalled responses; verify cancellation timing and conversation integrity.
- Interrupt/resume a large real model download and verify ambiguous `Content-Range .../*` resumes are rejected.
- Switch installed LiteRT models under constrained RAM and exercise a near-4K conversation including vision/history eviction.
