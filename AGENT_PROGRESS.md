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
- Wired device-aware LiteRT KV/context sizing into production: Mobie selects the largest context fitting the current RAM budget and consistently applies it to memory admission, native KV allocation, history trimming, and generation output budgeting while preserving full advertised context when safe.
- Scaled conversation-history replay below 4K so 1K-3K constrained contexts no longer inherit the old 4K replay floor; exact-tip Android CI validated the fix.
- Hardened the inference-stall watchdog so Mobie returns control even when a native LiteRT collector ignores coroutine cancellation; exact-tip CI validated the non-cooperative regression coverage and real-Qwen E2E.
- Validated restored-vision output budgeting so a restored history image reserves the same context as a new image without double-reserving Mobie's single vision slot.
- Completed single-image-slot replacement handling: when a new image arrives after a restored image, Mobie rebuilds native history with the older image downgraded to text before submitting the replacement; exact-tip Android CI is green.

## Important work in progress
- Real multimodal LiteRT-LM E2E is now wired into Android CI using `litert-community/SmolVLM2-500M`: restore historical image → text follow-up → replacement image → text follow-up. Exact-tip CI is pending before this is marked validated.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second without unvalidated main-model GPU/NPU execution.
- Thermal protection still needs representative physical-device sustained-heat testing.

## Tests actually performed
- `982f6ec7` passed exact-tip Android CI after single-image replacement handling: JVM tests/lint/debug APK, emulator smoke, and the real Qwen LiteRT-LM E2E all completed successfully.
- `1a2675f7` passed exact-tip Android CI after restored-vision context accounting, including JVM tests/lint/debug APK, emulator smoke, and the real Qwen LiteRT-LM E2E.
- Restored-vision context-budget JVM coverage checks that a history image consumes the same reserve as a new image and that the single image slot is not double-counted when both flags are present.
- `eadd96ff` passed exact-tip Android CI after non-cooperative stall containment, including JVM tests/lint/debug APK, emulator smoke, and real Qwen LiteRT-LM E2E.
- `5656f810` passed exact-tip Android CI: JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E after non-cooperative inference-stall containment.
- Non-cooperative stall regression coverage deliberately blocks the fake native producer after emitting a token and verifies the public generation flow returns promptly instead of waiting for the blocked producer.
- `ef276beb` passed exact-tip Android CI after the constrained-context replay fix.
- `0437aa22` passed exact-tip Android CI after the constrained-context replay fix; JVM coverage compares 1K, 2K, and 4K replay budgets and asserts constrained contexts stay below proportional UTF-8 history limits.
- `c79df688` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with production device-aware context sizing wired into runtime.
- `d8dd78f8` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with the device-aware context sizing policy present and its JVM coverage green before production wiring.
- Device-aware context sizing JVM coverage checks full-context retention when RAM is sufficient, context reduction under current free-RAM pressure, stricter low-RAM-device behavior, stable 256-token sizing, and telemetry-unavailable fallback.
- `4bc03ac9` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with recommendation behavior aligned to the validated SEVERE/CRITICAL runtime boundary.
- `1268c26a` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with SEVERE thermal admission aligned to the existing 256-token throttle while CRITICAL+ still blocks model load/generation.
- `615fe48d` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with the inference-stall guard wired around production LiteRT.
- `563aab15` passed JVM tests/lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E with the production two-thread CPU policy.
- `6d969769` passed the same pipeline plus the real-model CPU-thread benchmark.

## Real benchmarks / performance improvements
- Real-Qwen CPU-thread comparison on the 2-vCPU Android runner: runtime default 8.02 decode tok/s and 19.32 prefill tok/s; explicit 2 threads 19.19 decode tok/s and 39.01 prefill tok/s (2.39x decode, 2.02x prefill).
- Production now requests up to two LiteRT CPU threads; this is CI/E2E validated but not claimed as a physical-phone speedup.
- Latest benchmark-run normal Qwen prompt before the production thread change: 7.16 decode tok/s, 16.37 prefill tok/s, 1.820 s TTFT, 4.369 s total, ~1.02 GiB app RAM.
- Cold load measured 2745.6 ms with 339,216,776 bytes cache growth; full unload/reload measured 1476.3 ms with 0 additional cache growth.
- Device-aware context sizing and constrained-context replay are exact-tip CI/E2E validated for correctness, but no handset RAM/OOM improvement is claimed until representative 32K/64K physical-device testing is performed.

## Known problems / regressions
- Physical-device thermal/LMK behavior, vision history, long-context pressure, and interrupted-generation recovery still need representative handset testing.
- LiteRT-LM `maxNumImages` remains intentionally configured to one image. Replacement handling is text-path CI validated; the newly added real SmolVLM2 multimodal replacement gate is still awaiting exact-tip CI.
- Upstream LiteRT-LM Android streaming can lose terminal callbacks. Mobie now returns an error even if the collector ignores coroutine cancellation, but a truly wedged native call may still retain native resources until the app process restarts.
- Representative 32K/64K handset validation is still needed before claiming a measured RAM/OOM improvement from device-aware context sizing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU and more than two CPU inference threads remain disabled pending representative handset evidence.

## Items to inspect before merging
- On a real vision model, restore a chat containing an image, send a text-only follow-up, then send a replacement image; verify the restored image receives context reserve, the old image is replayed as text only before replacement, and the new image does not exceed the one-image engine slot.
- Reproduce a genuinely non-cooperative LiteRT stream and verify the UI regains control after the watchdog timeout; if native resources remain wedged, verify the restart guidance is clear and no ANR occurs.
- On a RAM-constrained phone where context sizing drops below 4K, restore a long conversation and verify replay remains bounded, generation still has usable prompt/output headroom, and native context rebuilds cleanly when older turns are evicted.
- On a representative low/free-RAM phone, compare a 32K/64K LiteRT artifact before/after context-budget wiring: verify load success/OOM behavior, selected usable context, app RAM, TTFT, and decode/prefill throughput.
- Heat a representative phone to SEVERE before starting a prompt and verify recommendations mention throttling and Mobie still generates with the 256-token cap; at CRITICAL verify recommendations say loading is blocked, new generation is rejected, and active generation is cancelled.
- Compare 2-thread production against runtime-default and 4+ threads on representative big.LITTLE phones, measuring TTFT, decode/prefill throughput, battery drain, and thermal throttling.
- Interrupt/resume a large real model download and verify ambiguous `Content-Range .../*` resumes are rejected.
