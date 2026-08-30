# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Hardened resumable model downloads: strict `Content-Range` validation, server-total size checks, and retries for throttling/transient HTTP failures.
- Enabled the existing Android CI pipeline on `agent-dev` so autonomous branch work is continuously verified without touching `main`.
- Verified real Qwen3-0.6B INT4 local execution on Android: download/checksum → load → first generation → second generation on the same model → unload → reload → another generation; screenshot evidence uploaded by CI.

## In progress
- Added runtime measurement for time-to-first-token and total generation latency alongside decode tokens/sec and RAM; full real-model CI validation is running.
- Continue auditing intentionally interrupted live Hugging Face transfers and real-device performance constraints.

## Tests performed
- JVM unit tests, Android lint, and debug APK build passed on `agent-dev`.
- Emulator UI smoke test passed.
- Real LiteRT-LM E2E passed on API 35 x86_64 using the published 347,251,840-byte Qwen3-0.6B INT4 no-think artifact, including repeated prompting and unload/reload generation.
- Added unit coverage for memory-pressure compatibility decisions and download response/range handling.

## Benchmarks
- No trustworthy before/after speed improvement claim yet.
- Runtime now records TTFT and total generation latency so future optimizations can be compared against real measurements instead of only decode tokens/sec.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Download resume correctness is unit-tested, but an intentionally interrupted live Hugging Face transfer has not yet been exercised end-to-end.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.

## Inspect before merging
- Verify model recommendations on devices under real memory pressure and on low-RAM phones.
- Review the stricter resumable-download handling around CDN `206`, `416`, and retryable HTTP responses.
- Review TTFT/total-latency instrumentation after its real-model CI run completes.
