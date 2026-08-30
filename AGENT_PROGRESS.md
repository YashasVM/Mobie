# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Hardened resumable model downloads: strict `Content-Range` validation, server-total size checks, and retries for throttling/transient HTTP failures.
- Enabled the existing Android CI pipeline on `agent-dev` so autonomous branch work is continuously verified without touching `main`.
- Verified the real 344,437,808-byte Qwen3-0.6B LiteRT-LM model downloads through Mobie, passes checksum validation, loads locally, generates a non-empty response, emits runtime stats, unloads cleanly, and produces screenshot evidence on an Android emulator.

## In progress
- Strengthened the real-model E2E test to exercise a second prompt on the same loaded model plus unload/reload and another local generation. Latest CI validation is pending.
- Continue auditing measurable inference performance and intentionally interrupted live Hugging Face transfers.

## Tests performed
- JVM unit tests, Android lint, and debug APK build passed on `agent-dev`.
- Emulator UI smoke test passed.
- Real LiteRT-LM E2E passed on API 35 x86_64 using Qwen3-0.6B: download → checksum validation → load → local generation → runtime stats → unload; screenshot artifact uploaded by CI.
- Added unit coverage for memory-pressure compatibility decisions and download response/range handling.

## Benchmarks
- No trustworthy before/after performance benchmark yet. No speed improvement claim made.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Download resume correctness is unit-tested, but an intentionally interrupted live Hugging Face transfer has not yet been exercised end-to-end.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.

## Inspect before merging
- Verify model recommendations on devices under real memory pressure and on low-RAM phones.
- Review the stricter resumable-download handling around CDN `206`, `416`, and retryable HTTP responses.
- Review repeated generation and unload/reload E2E results once the latest CI run completes.
