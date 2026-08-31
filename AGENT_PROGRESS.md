# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android low-memory state/LMK headroom and added stricter fit limits for Android-classified low-RAM devices.
- Expanded device profiling with manufacturer/model, SoC manufacturer/model, ABI, and media performance class without making unverified accelerator claims.
- Hardened resumable model downloads with strict `Content-Range`/server-size validation, transient HTTP retries, SHA-256 validation, and an Android forced-disconnect resume integration test.
- Verified real Qwen3-0.6B INT4 local execution: download/checksum → load → repeated generation → conversation-only reset → generation → unload/reload → generation, with screenshot and machine-readable runtime evidence.
- Added measured TTFT, total generation latency, decode tokens/sec, prefill tokens/sec/count, decode token count, and app RAM reporting for real LiteRT-LM runs; the exact instrumentation tip passed full Android CI and real Qwen E2E.
- New Chat and history switching now reuse the initialized LiteRT engine by replacing only the conversation; if reuse is unavailable, Mobie safely falls back to a full model load.
- Bound LiteRT conversation restoration by both message count and payload size and prevent restoration from starting on an orphan assistant message.
- Reduced Hugging Face catalog request fan-out with a bounded detail cache, failure isolation, and coroutine-cancellable requests.
- Made user-cancelled WorkManager model downloads cancel the underlying OkHttp call while retaining partial bytes for resume.
- Preserved coroutine cancellation through LiteRT load/reset/generation and made conversation reset transactional.
- Added load-time, pre-generation, and bounded mid-generation Android memory-pressure guards.
- Hardened LiteRT multimodal loading with CPU vision initialization, safe text-only fallback, explicit image rejection in fallback mode, and text-before-media ordering.
- Recognize current LiteRT Community quantization naming (`q4_block32`, `mixed_int4`, `dynamic_wi4b32`, `channelwise_int8`) so recommendations use real quantization metadata.
- Hardware-specific MediaTek/Qualcomm/NPU LiteRT bundles are represented explicitly and excluded from Mobie's generic CPU artifact selection; the exact tip passed full Android CI.

## In progress
- Make compatibility estimates context-aware. Mobie now infers published context hints such as `ekv1280`, `ekv2048`, `ctx4096`, and `context4096` from artifact filenames and scales its conservative KV-cache RAM allowance accordingly instead of reporting every LiteRT artifact as 4096-token. Focused JVM coverage is added; exact-tip Android CI is pending.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use the measured CPU prefill/decode baseline to identify runtime changes that materially improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- Post-merge `main` passed the full Android CI pipeline on the exact merged tree, including JVM tests, lint/debug APK build, emulator smoke/integration, and real LiteRT-LM Qwen E2E.
- Hardware-target filtering exact tip passed JVM tests, Android lint/debug APK build, emulator smoke/integration, and real LiteRT-LM Qwen E2E.
- Existing runtime validation covers interrupted-transfer resume, explicit download cancellation/socket close, bounded history restoration, cancellation-safe inference, transactional conversation reset, load/decode memory admission, and real Qwen E2E.
- Focused JVM cases cover current LiteRT quantization filename patterns, INT4-vs-INT8 ordering, hardware-target exclusion, filename context inference, and context-aware KV-cache estimates.

## Benchmarks
- Current CPU-emulator Qwen3-0.6B INT4 baseline: first prompt 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- After conversation-only reset with restored history: 26.56 prefill tok/s, 7.80 decode tok/s, 2.264 s TTFT, 3.214 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup measured 2.95 ms versus 1524.24 ms for full unload + reload on the CI CPU emulator (~517x less setup wall time). Emulator regression baseline only.
- No physical-device performance claim yet.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.
- GPU/NPU selection remains disabled until physical-device evidence shows it is safe and beneficial.
- Hardware-targeted LiteRT bundles are deliberately unsupported by the generic runtime even on apparently matching SoCs until Mobie has a validated accelerator path.
- Context inference currently uses reliable filename hints when present; artifacts without explicit context metadata still fall back to a conservative 4096-token estimate because LiteRT-LM does not expose a cheap pre-load package metadata API.
- Restored-context sizing still uses a conservative character budget because LiteRT-LM does not expose a cheap pre-conversation token-count API.
- Text-only fallback for a failed vision executor preserves chat but cannot make vision work on unsupported hardware.

## Inspect before merging
- Verify model recommendations on real low-RAM phones and devices under memory pressure.
- Review captured SoC/performance-class data before using it for accelerator claims.
- Review LiteRT artifact ranking against current community repositories, especially generic CPU bundles versus SoC/NPU-specific files.
- Review context-aware RAM estimates against artifacts with published 1280/2048/4096-token limits and real device RSS measurements.
- Review resumable-download handling around `206`, `416`, throttling/retry responses, forced disconnects, and explicit cancellation.
- Treat emulator performance figures as regression baselines only; collect comparable ARM-device measurements before making performance claims.
- Verify conversation reuse, long-history switching, and memory guards on representative phones.
- Verify a real vision-capable `.litertlm` model on representative phones.
