# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Aligned inferred context windows across recommendation memory estimates, load admission, LiteRT `maxNumTokens`, history replay, and generation-time admission.
- Hardened LiteRT load/reset/generation/unload lifecycle ordering so recoverable native cleanup failures do not hide primary failures or strand stale engine/conversation state.
- Dirty/interrupted conversation rebuild and explicit reset now install replacements before closing stale native conversations.
- Cancellation suppression, Stop-vs-unwind serialization, idle native-cancellation suppression, and lifecycle-epoch generation admission passed full Android CI and real Qwen E2E through `57a4bc18`.

## Important work in progress
- Close a Stop-vs-generation-finish race where Stop could observe an active generation, lose the race to teardown, then arm a stale cancel flag that rejects the next prompt. The cancellation state now makes active-state validation, request arming, and native cancellation one serialized operation; focused regression tests are included pending exact-tip CI.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- `57a4bc18` passed JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E job.
- Earlier validated runtime hardening tips include `a18552b7`, `ec577e6b`, `9608b185`, `0b2d07a7`, `ddbce7f9`, `4e6e511a`, `9458e0d8`, `474269e0`, `9917f16f`, and `07c87115` through the same full Android CI pipeline.
- Current focused tests cover lifecycle permits captured before/during transitions, overlapping lifecycle transitions, transition preservation across generation-state reset, idle cancellation suppression, atomic cancellation request arming, recoverable retry, duplicate suppression, and concurrent cancellation serialization.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Atomic Stop request arming still needs exact-tip full CI and a real Stop-near-generation-completion regression check.
- Vision history, thermal/LMK behavior, first-load cache sizing, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only in model metadata cannot yet be inferred when filenames omit them; unknown context uses a conservative 4K cap.

## Items to inspect before merging
- Trigger Stop exactly as a real generation completes, then immediately send another prompt; verify the completed generation cannot leave a stale cancellation request that rejects the next prompt.
- Queue a generation before reset/load/unload acquires the generation mutex and another while the transition is active; verify both are rejected as stale, then verify a generation submitted after transition completion runs normally.
- Call Stop/reset/load/unload while idle and verify no `cancelProcess()` JNI call occurs.
- Stop an active real generation and force the first cancellation to fail recoverably; verify exactly one cleanup retry occurs and no duplicate successful JNI cancellation reaches LiteRT.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Delete/truncate an installed model or fill storage before first load and verify Mobie blocks before native initialization and recovers after correction.
- Exercise a near-4K conversation including vision and history eviction; verify bounded replay/output admission stays stable.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
- Interrupt/resume a large real download under low storage and verify final checksum/fingerprint behavior.
