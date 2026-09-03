# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict range/size checks, retained partials, cancellation, checksum/fingerprint verification, and storage admission after resolved HTTP size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry; conversation reset reuses loaded weights.
- Hardened interrupted generation, stop ordering, conversation replacement, model switching, thermal/memory admission, and persistent LiteRT cache handling.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback.
- Improved recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, supported backend, and platform-target filtering.
- Aligned inferred context windows with recommendation memory estimates, load admission, native LiteRT `EngineConfig.maxNumTokens`, bounded history replay, and generation-time context admission.
- Context-bound generation preflight and native replay synchronization after history eviction are exact-tip CI validated.
- Vision initialization fallback retries only recoverable backend exceptions; fatal JVM/runtime errors no longer trigger additional GPU→CPU→text-only attempts.
- Unknown-size LiteRT artifacts remain discoverable with a warning but are excluded from automatic recommendations until size-dependent RAM/storage/cache fit can be measured.
- Recheck free storage immediately before LiteRT initialization and fail closed when the installed model is missing/empty or storage cannot be measured.
- LiteRT load/reset/generation boundaries preserve cancellation and VM-fatal errors instead of converting them into ordinary recoverable failures.
- Fatal generation, model-load conversation setup, and engine-initialization failures now escape before follow-up JNI cleanup.
- LiteRT unload/model-switch teardown now clears stale references first, continues engine release after recoverable conversation-close failures, preserves suppressed cleanup diagnostics, and stops before further JNI after fatal close failures; exact-tip full Android CI passed at `07c87115`.
- Recoverable LiteRT `cancelProcess()` failure before unload/model switching no longer skips teardown; the old conversation/engine are still released after the generation lock, then the original cancellation error is reported. Exact-tip full Android CI passed at `9917f16f`.
- Reset cancellation hardening at `474269e0` is full-CI validated: recoverable pre-reset `cancelProcess()` failure is deferred until after generation/lifecycle locks and conversation repair/replacement; cancellation and fatal failures preserve their required ordering.
- Generation cleanup ordering at `9458e0d8` is full-CI validated: periodic low-memory/thermal guard failures use the single outer cleanup boundary, recoverable `cancelProcess()` cleanup failures stay suppressed behind the primary failure, and duplicate JNI cancellation is avoided.
- Dirty-conversation rebuild replacement ordering is full-CI validated at `4e6e511a`: replacement is installed before stale `Conversation.close()`, so a recoverable close failure cannot remove the usable conversation.

## In progress
- Apply the same replacement-before-close safety to explicit `resetConversation()`. A recoverable close failure must leave the new conversation installed; if pre-reset cancellation also failed, keep that original cancellation failure primary and attach replacement/close failure diagnostics.
- Continue auditing runtime lifecycle and backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Dirty-conversation rebuild tip `4e6e511a` passed full Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Generation cleanup-ordering tip `9458e0d8`, reset-cancellation code tip `474269e0`, cancellation-safe teardown tip `9917f16f`, and teardown-hardening tip `07c87115` passed the same full Android CI pipeline.
- Fatal load/init cleanup-ordering tip `4c6830a1`, fatal generation cleanup-ordering tip `51c43100`, missing-model/storage preflight tip `787c8753`, streaming fatal lifecycle tip `abdfaa4d`, load/reset fatal lifecycle tip `0a5bf00a`, and first-load storage admission tip `6f5c2cf5` passed the same full Android CI pipeline.
- Resource-replacement policy tests verify create → install → close ordering, replacement survival after recoverable close failure, preservation of the previous resource after replacement-creation failure, and null-previous replacement.
- Generation cleanup-ordering policy tests cover preserving recoverable primary failures, attaching recoverable JNI cleanup failures as suppressed diagnostics, running cleanup for coroutine cancellation, skipping JNI after a fatal primary failure, and fatal cleanup precedence.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Explicit reset replacement ordering is implemented at the current tip but still needs exact-tip full CI validation.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only inside model metadata cannot yet be detected when filenames omit them; unknown context therefore uses a conservative 4K runtime cap.
- First-load cache sizing, thermal behavior, LMK admission, image-slot behavior, and long-conversation context pressure still need physical-device validation.

## Inspect before merging
- Force a recoverable `Conversation.close()` failure during explicit reset and verify the replacement remains installed and usable; if `cancelProcess()` also failed recoverably, verify that cancellation error remains primary and reset/close failure is suppressed.
- Force a recoverable `Conversation.close()` failure while rebuilding after interrupted/cancelled generation; verify the replacement conversation remains installed and usable even though the close error is surfaced, and verify replacement-creation failure leaves the prior reference available for another repair attempt.
- Force a low-memory/thermal stop during generation while making `cancelProcess()` throw a recoverable JNI exception; verify the user-visible failure remains the memory/thermal cause and cancellation is attempted once.
- Force recoverable `cancelProcess()` failure during unload/model switching and verify the old conversation/engine still close after active generation exits; verify fatal cancellation failure triggers no further JNI cleanup.
- Delete/truncate an installed model and fill storage after download but before first load; verify Mobie blocks before native initialization and recovers after the condition is fixed.
- Drive a 4K conversation through replay eviction and near its context boundary, including vision input, and verify bounded history/output admission remains stable.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
- Interrupt/resume a large real download under low storage and verify final checksum/fingerprint behavior.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU→text-only fallback.
