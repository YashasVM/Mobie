# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Aligned inferred context windows across recommendation memory estimates, load admission, LiteRT `maxNumTokens`, history replay, and generation-time admission.
- Hardened LiteRT load/reset/generation/unload lifecycle ordering so recoverable native cleanup failures do not hide primary failures or strand stale engine/conversation state.
- Dirty/interrupted conversation rebuild and explicit reset now install replacements before closing stale native conversations.
- Cancellation suppression and its Stop-vs-unwind concurrency race are validated through full Android CI and real Qwen E2E.

## Important work in progress
- Skip `cancelProcess()` entirely when no generation is active, while preserving lifecycle-transition gating so a generation cannot slip in between the idle-state check and load/reset/unload. Implementation and focused state tests are in the current commit pending exact-tip CI.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- `ec577e6b` passed JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E job.
- Earlier validated runtime hardening tips include `9608b185`, `0b2d07a7`, `ddbce7f9`, `4e6e511a`, `9458e0d8`, `474269e0`, `9917f16f`, and `07c87115` through the same full Android CI pipeline.
- Current focused tests cover idle cancellation suppression, generation begin/end state, recoverable retry, duplicate suppression, fatal/coroutine failure, and concurrent cancellation serialization.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Idle-cancellation suppression still needs exact-tip full CI and a real idle reset/load/unload regression check.
- Vision history, thermal/LMK behavior, first-load cache sizing, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only in model metadata cannot yet be inferred when filenames omit them; unknown context uses a conservative 4K cap.

## Items to inspect before merging
- Call Stop/reset/load/unload while idle and verify no `cancelProcess()` JNI call occurs; then race a new generation against reset/load/unload and verify the generation is gated rather than escaping the lifecycle transition.
- Stop an active real generation and force the first cancellation to fail recoverably; verify exactly one cleanup retry occurs and no duplicate successful JNI cancellation reaches LiteRT.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Delete/truncate an installed model or fill storage before first load and verify Mobie blocks before native initialization and recovers after correction.
- Exercise a near-4K conversation including vision and history eviction; verify bounded replay/output admission stays stable.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
- Interrupt/resume a large real download under low storage and verify final checksum/fingerprint behavior.
