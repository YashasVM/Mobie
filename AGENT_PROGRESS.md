# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Hardened resumable model downloads: strict `Content-Range` validation, server-total size checks, and retries for throttling/transient HTTP failures.
- Enabled the existing Android CI pipeline on `agent-dev` so autonomous branch work is continuously verified without touching `main`.
- Verified real Qwen3-0.6B INT4 local execution on Android: download/checksum → load → first generation → second generation on the same model → unload → reload → another generation.
- Added TTFT and total-generation latency measurement to LiteRT inference stats and persist real E2E performance evidence alongside the screenshot artifact.

## In progress
- Continue auditing intentionally interrupted live Hugging Face transfers and real-device performance constraints.
- Use the new measured baseline to identify runtime changes that materially improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- JVM unit tests, Android lint, and debug APK build passed on `agent-dev`.
- Emulator UI smoke test passed.
- Real LiteRT-LM E2E passed on API 35 x86_64 using the published 347,251,840-byte Qwen3-0.6B INT4 no-think artifact, including repeated prompting and unload/reload generation.
- Real E2E evidence artifact contains both screenshot and machine-readable runtime metrics.
- Added unit coverage for memory-pressure compatibility decisions and download response/range handling.

## Benchmarks
- CPU emulator baseline, Qwen3-0.6B INT4: first prompt 8.56 tokens/s, 1.536 s TTFT, 3.661 s total generation, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 9.81 tokens/s, 1.352 s TTFT, 3.414 s total, ~1.02 GiB app RAM.
- After unload/reload: 6.98 tokens/s, 1.455 s TTFT, 2.168 s total, ~0.94 GiB app RAM.
- These are measured baselines, not a before/after speed-improvement claim.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Download resume correctness is unit-tested, but an intentionally interrupted live Hugging Face transfer has not yet been exercised end-to-end.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.

## Inspect before merging
- Verify model recommendations on devices under real memory pressure and on low-RAM phones.
- Review the stricter resumable-download handling around CDN `206`, `416`, and retryable HTTP responses.
- Treat emulator performance figures as regression baselines only; collect comparable measurements on ARM hardware before making device-performance claims.
