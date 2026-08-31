# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android low-memory state/LMK headroom and added stricter fit limits for Android-classified low-RAM devices.
- Expanded device profiling with manufacturer/model, SoC manufacturer/model, ABI, and media performance class without making unverified accelerator claims.
- Hardened resumable model downloads with strict `Content-Range`/server-size validation, transient HTTP retries, SHA-256 validation, and an Android forced-disconnect resume integration test.
- Verified real Qwen3-0.6B INT4 local execution: download/checksum → load → repeated generation → conversation-only reset → generation → unload/reload → generation, with screenshot and machine-readable runtime evidence.
- Added measured TTFT, total generation latency, decode tokens/sec, prefill tokens/sec/count, decode token count, and app RAM reporting for real LiteRT-LM runs; the exact instrumentation tip passed full Android CI and real Qwen E2E.
- New Chat and history switching now reuse the initialized LiteRT engine by replacing only the conversation; if reuse is unavailable, Mobie safely falls back to a full model load.
- Measured conversation-only reset against full LiteRT unload/reload: on the CI CPU emulator, reset took 3.98 ms versus 1509.73 ms for full unload/reload (~379x less setup wall time). Treat this as an emulator regression baseline, not a physical-device performance claim.
- Bound LiteRT conversation restoration by both message count and payload size, prevent restoration from starting on an orphan assistant message, and fully validated the change in Android CI.
- Reduced Hugging Face catalog request fan-out with a bounded 10-minute/64-entry detail cache; complete file metadata from the initial response now skips the extra detail request entirely.
- Made catalog loading tolerant of individual model-detail request failures so one transient metadata failure no longer aborts the whole model list.
- Made Hugging Face catalog HTTP calls coroutine-cancellable so obsolete searches cancel their underlying OkHttp calls instead of continuing to consume network/battery in the background.
- Made user-cancelled WorkManager model downloads cancel the underlying OkHttp call through coroutine cancellation while retaining partial bytes for resume; the deterministic Android cancellation test is fully green.
- Preserved coroutine cancellation through LiteRT load/reset/generation so user/system cancellation is no longer converted into a false inference failure; the exact branch tip passed Android CI.
- Made conversation-only reset transactional so a failed replacement conversation does not destroy the still-usable current conversation; the corrected implementation passed full Android CI.
- Re-check live Android RAM/LMK state immediately before LiteRT engine initialization and reject unsafe loads before tearing down an already-resident runtime; the exact branch tip passed full Android CI.
- Prevent new LiteRT decodes from starting while Android reports active low-memory/LMK pressure; the exact branch tip passed full Android CI.
- Re-check Android low-memory state during active LiteRT generation at a bounded 500 ms cadence and cancel native decode if LMK pressure appears after generation has started; the exact branch tip passed full Android CI.

## In progress
- Harden LiteRT multimodal loading: try the requested CPU vision executor first, fall back to a text-only CPU engine when vision initialization is unavailable, reject image sends clearly in fallback mode, and pass multimodal content in LiteRT-LM's documented text-before-media order. Android CI validation is pending.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use the measured CPU prefill/decode baseline to identify runtime changes that materially improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- Latest validated `agent-dev` runtime tip passed JVM unit tests, Android lint, debug APK build, emulator smoke/integration tests, explicit active-download cancellation/socket-close validation, interrupted-transfer resume, bounded restored-history tests, cancellation-safe runtime handling, transactional conversation reset, load-time memory admission, pre-generation memory admission, mid-generation memory-pressure handling, and real LiteRT-LM Qwen E2E.
- Focused JVM coverage verifies restored-history message limits, character budget, blank entries, oversized history entries, and user-led turn boundaries; the exact bounded-history branch tip passed full Android CI.
- Real Qwen E2E verifies repeated prompts, conversation-only reset with restored history, successful generation after reset, full unload/reload, successful generation after reload, and records reset/reload wall time plus native prefill/decode benchmark metrics.
- Active-download cancellation initially failed to compile because `CoroutineWorker.onStopped()` is final in the current WorkManager API; the implementation was corrected to use the existing coroutine-aware OkHttp bridge so WorkManager cancellation propagates directly to `Call.cancel()`.
- Low-RAM recommendation boundary fix is fully re-validated after the earlier test-expectation error.
- Added JVM coverage for catalog metadata-cache TTL/LRU behavior and for cancellation propagating to the active catalog OkHttp call.
- Added focused JVM coverage for LiteRT load-memory admission and decode admission: active Android low-memory state, insufficient current load headroom, healthy small-model load, decode blocked under low-memory pressure, decode allowed when Android is healthy, and the 500 ms mid-generation memory-check cadence.

## Benchmarks
- Current CPU-emulator Qwen3-0.6B INT4 baseline with native LiteRT prefill/decode counters: first prompt 20.64 prefill tok/s over 21 tokens, 7.51 decode tok/s over 22 tokens, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s over 25 tokens, 7.81 decode tok/s over 14 tokens, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- After conversation-only reset with restored history: 26.56 prefill tok/s over 50 tokens, 7.80 decode tok/s over 9 tokens, 2.264 s TTFT, 3.214 s total, ~1.02 GiB app RAM.
- After full unload/reload: 24.98 prefill tok/s over 24 tokens, 6.62 decode tok/s over 8 tokens, 1.386 s TTFT, 2.173 s total, ~0.94 GiB app RAM.
- Conversation-only reset setup in the latest measured run: 2.95 ms; full unload + reload setup: 1524.24 ms on the CI CPU emulator (~517x less setup wall time by keeping model weights resident). This is a CI-emulator regression baseline, not a physical-device claim.
- Earlier measured reset/reload run was 3.98 ms versus 1509.73 ms (~379x), showing the qualitative advantage is repeatable even though exact timing varies between CI runs.
- No catalog-latency speed-improvement claim yet; catalog changes reduce avoidable/stale requests but have not been benchmarked on a phone network.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.
- GPU/NPU selection remains disabled until physical-device evidence shows it is safe and beneficial.
- LiteRT-LM currently does not expose a public API for reading a `.litertlm` package's maximum context capacity before engine creation, so Mobie should not hardcode larger KV-cache/context settings from model-name guesses.
- The complete resume path is deterministically tested with a forced HTTP disconnect; a live Hugging Face CDN interruption is intentionally not used as a flaky CI dependency.
- Restored-context sizing still uses a conservative character budget because LiteRT-LM does not expose a cheap pre-conversation token-count API; the full transcript remains stored and visible in the UI.
- Text-only fallback for a failed vision executor preserves local chat availability but cannot make vision work on an unsupported/incompatible device; representative physical-device multimodal validation is still required.

## Inspect before merging
- Verify model recommendations on real low-RAM phones and devices under memory pressure.
- Review captured SoC/performance-class data before using it for accelerator claims; detection is evidence input, not proof a backend works.
- Review resumable-download handling around `206`, `416`, throttling/retry responses, forced disconnects, and explicit user cancellation.
- Treat emulator performance figures as regression baselines only; collect comparable ARM-device measurements before making performance claims.
- Review conversation reuse/fallback behavior when switching histories and creating chats, especially around cancellation, failed conversation replacement, and restored context; verify the setup-time reduction on representative physical phones.
- Verify long-history switching preserves useful recent context without context-limit failures or excessive prefill on real models.
- Review catalog caching/failure fallback and cancellation behavior for freshness, API traffic, and mobile-network efficiency.
- Verify load-time, pre-generation, and mid-generation memory guards on real low-RAM phones under deliberate memory pressure; unsafe work should be rejected or cancelled before native memory growth reaches LMK/OOM territory without destroying an already-resident runtime.
- Verify a real vision-capable `.litertlm` model on representative phones: multimodal engine initialization, text-before-image inference, and text-only fallback behavior when the vision executor cannot initialize.
