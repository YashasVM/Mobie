# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android low-memory state/LMK headroom and added stricter fit limits for Android-classified low-RAM devices.
- Expanded device profiling with manufacturer/model, SoC manufacturer/model, ABI, and media performance class without making unverified accelerator claims.
- Hardened resumable model downloads with strict `Content-Range`/server-size validation, transient HTTP retries, SHA-256 validation, and an Android forced-disconnect resume integration test.
- Verified real Qwen3-0.6B INT4 local execution: download/checksum → load → repeated generation → unload → reload → generation, with screenshot and machine-readable runtime evidence.
- Added measured TTFT, total generation latency, tokens/sec, and app RAM reporting for real LiteRT-LM runs.
- Reduced Hugging Face catalog request fan-out with a bounded 10-minute/64-entry detail cache; complete file metadata from the initial response now skips the extra detail request entirely.
- Made catalog loading tolerant of individual model-detail request failures so one transient metadata failure no longer aborts the whole model list.
- Made Hugging Face catalog HTTP calls coroutine-cancellable so obsolete searches cancel their underlying OkHttp calls instead of continuing to consume network/battery in the background.
- Made user-cancelled WorkManager model downloads immediately cancel the active OkHttp call instead of allowing a blocked/read-in-progress transfer to continue until timeout; partial bytes are retained for resume.

## In progress
- Validate the new active-download cancellation path through the full Android CI pipeline, including the deterministic localhost cancellation test.
- Replace unnecessary full model unload/reload cycles when starting a new chat or switching history with safe conversation-only reset/reuse if LiteRT lifecycle testing supports it.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use the measured CPU baseline to identify runtime changes that materially improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- Cancellable catalog networking is fully validated: JVM unit tests, Android lint, debug APK build, emulator smoke/integration tests, interrupted-transfer resume, and real LiteRT-LM Qwen E2E all passed on the previous branch tip.
- Low-RAM recommendation boundary fix is fully re-validated after the earlier test-expectation error.
- Added JVM coverage for catalog metadata-cache TTL/LRU behavior and for cancellation propagating to the active catalog OkHttp call.
- Added an Android integration test that starts a slow model transfer, cancels it through `ModelDownloadManager`, requires WorkManager to reach `CANCELLED`, verifies the server observes the socket closing promptly, and checks that a resumable `.part` file remains.
- Full CI for the newest model-download cancellation change is currently pending; no pass claim is made yet.

## Benchmarks
- CPU emulator baseline, Qwen3-0.6B INT4: first prompt 8.56 tokens/s, 1.536 s TTFT, 3.661 s total generation, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 9.81 tokens/s, 1.352 s TTFT, 3.414 s total, ~1.02 GiB app RAM.
- After unload/reload: 6.98 tokens/s, 1.455 s TTFT, 2.168 s total, ~0.94 GiB app RAM.
- No catalog-latency or runtime speed-improvement claim yet; catalog changes reduce avoidable/stale requests but have not been benchmarked on a phone network.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.
- GPU/NPU selection remains disabled until physical-device evidence shows it is safe and beneficial.
- The complete resume path is deterministically tested with a forced HTTP disconnect; a live Hugging Face CDN interruption is intentionally not used as a flaky CI dependency.
- New chat/history selection still reinitializes the whole model today; this is a likely avoidable latency/energy cost and is the next runtime lifecycle target.

## Inspect before merging
- Verify model recommendations on real low-RAM phones and devices under memory pressure.
- Review captured SoC/performance-class data before using it for accelerator claims; detection is evidence input, not proof a backend works.
- Review resumable-download handling around `206`, `416`, throttling/retry responses, forced disconnects, and explicit user cancellation.
- Treat emulator performance figures as regression baselines only; collect comparable ARM-device measurements before making performance claims.
- Review catalog caching/failure fallback and cancellation behavior for freshness, API traffic, and mobile-network efficiency.
