# Agent progress

## Completed this week
- Created the long-running `agent-dev` branch from current `main`.
- Made device recommendations aware of Android's real low-memory state and LMK threshold instead of relying only on raw free RAM.
- Reserved memory headroom before marking LiteRT-LM models compatible, reducing risky recommendations that can lead to OOMs or process kills.
- Hardened resumable model downloads: strict `Content-Range` validation, server-total size checks, and retries for throttling/transient HTTP failures.
- Added an Android integration test that deliberately drops a model transfer mid-stream, verifies WorkManager preserves the `.part` file, resumes with the exact HTTP `Range` offset, validates SHA-256, and produces the original bytes.
- Enabled the existing Android CI pipeline on `agent-dev` so autonomous branch work is continuously verified without touching `main`.
- Verified real Qwen3-0.6B INT4 local execution on Android: download/checksum → load → first generation → second generation on the same model → unload → reload → another generation.
- Added TTFT and total-generation latency measurement to LiteRT inference stats and persist real E2E performance evidence alongside the screenshot artifact.
- Expanded device profiling with Android low-RAM classification, device manufacturer/model, SoC manufacturer/model, and media performance class; low-RAM devices now get stricter model-fit headroom instead of the same thresholds as normal devices.

## In progress
- Re-validate the low-RAM recommendation changes after correcting a boundary-case unit test exposed by CI.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use the measured baseline to identify runtime changes that materially improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- JVM unit tests, Android lint, debug APK build, emulator smoke/integration, interrupted-transfer resume, and real LiteRT-LM E2E all passed before the latest low-RAM hardware-profile change.
- Real LiteRT-LM E2E passed on API 35 x86_64 using the published 347,251,840-byte Qwen3-0.6B INT4 no-think artifact, including repeated prompting and unload/reload generation.
- Real E2E evidence artifact contains both screenshot and machine-readable runtime metrics.
- Latest low-RAM CI correctly failed one boundary test: 3 GiB available RAM still left ~1.875 GiB safe headroom, enough for the test model's ~1.75 GiB estimate. The test input was corrected to 2.5 GiB rather than weakening the implementation. Full CI re-validation is pending.

## Benchmarks
- CPU emulator baseline, Qwen3-0.6B INT4: first prompt 8.56 tokens/s, 1.536 s TTFT, 3.661 s total generation, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 9.81 tokens/s, 1.352 s TTFT, 3.414 s total, ~1.02 GiB app RAM.
- After unload/reload: 6.98 tokens/s, 1.455 s TTFT, 2.168 s total, ~0.94 GiB app RAM.
- These are measured baselines, not a before/after speed-improvement claim.

## Known problems / regressions
- The current branch tip awaits a green full CI run after the corrected low-RAM boundary test; do not treat the latest hardware-profile work as merge-ready yet.
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- The complete Android resume path is tested with a forced HTTP disconnect, but a deliberately interrupted live Hugging Face CDN transfer has not been run because it is nondeterministic and would duplicate the same recovery path without stable CI behavior.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance or accelerator behavior.
- GPU is not enabled automatically yet: current LiteRT-LM guidance supports GPU fallback, but real-device OpenCL support/performance varies and recent model-specific GPU regressions mean automatic selection needs physical-device evidence first. Kotlin NPU selection is also not mature enough to advertise safely.

## Inspect before merging
- Confirm the latest low-RAM CI re-validation is green before merging.
- Verify model recommendations on devices under real memory pressure and Android-classified low-RAM phones.
- Review captured SoC/performance-class data before using it to make accelerator claims; detection is evidence input, not proof that a backend works.
- Review the stricter resumable-download handling around CDN `206`, `416`, throttling/retry responses, and the forced-disconnect integration test.
- Treat emulator performance figures as regression baselines only; collect comparable measurements on ARM hardware before making device-performance claims.
