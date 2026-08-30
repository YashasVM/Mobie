# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Hardened resumable model downloads: strict `Content-Range` validation, server-total size checks, and retries for throttling/transient HTTP failures.
- Enabled the existing Android CI pipeline on `agent-dev` so autonomous branch work is continuously verified without touching `main`.

## In progress
- Continue auditing LiteRT-LM model loading, repeated generation/unload behavior, and measurable inference performance.

## Tests performed
- JVM unit tests, Android lint, and debug APK build passed on `agent-dev`.
- Emulator UI smoke test passed.
- Real LiteRT-LM E2E passed using Qwen3-0.6B: downloaded the actual 344,437,808-byte model, loaded it locally, generated a non-empty response, emitted runtime stats, unloaded cleanly, and uploaded screenshot evidence.

## Benchmarks
- None yet. No performance improvement claim made.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Download resume correctness is unit-tested, but an intentionally interrupted live Hugging Face transfer has not yet been exercised end-to-end.

## Inspect before merging
- Verify model recommendations on devices under real memory pressure and on low-RAM phones.
- Review the stricter resumable-download handling around CDN `206`, `416`, and retryable HTTP responses.
