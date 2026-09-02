# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict range/size checks, retained partials, cancellation, checksum/fingerprint verification, and storage admission after resolved HTTP size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry; conversation reset reuses loaded weights.
- Hardened interrupted generation so cancelled/failed partial turns remain visible but never re-enter native context; serialized recovery prevents prompt races inside the runtime.
- Serialized stop-generation at the UI boundary: chat stays non-sendable in STOPPING until the active inference coroutine has cancelled and joined; exact-tip CI passed unit/lint/build, emulator smoke, and real Qwen E2E.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback.
- Improved recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, and supported backend.
- Excluded vendor/backend-constrained LiteRT packages from the generic CPU path and prevented unknown-size warning artifacts from outranking measurable alternatives.
- Added load/generation memory admission, SEVERE thermal safeguards, first-load LiteRT cache storage headroom, persistent optimized-cache reuse, and per-device artifact selection.
- Aligned inferred c32k/c64k/c128k-style context with both final RAM admission and native LiteRT `EngineConfig.maxNumTokens`.
- Replacement model loads now release the previous LiteRT engine before RAM admission; exact-tip Android CI passed.

## In progress
- Reject desktop/web/iOS-targeted `.litertlm` variants from Android generic recommendations; current code and focused JVM coverage are committed, exact-tip CI pending.
- Fix LiteRT conversation replacement ordering: reset/recovery currently creates a replacement before closing the existing conversation, conflicting with LiteRT-LM's one-session-per-engine lifecycle.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Latest validated tip `ba9ebf77` passed JVM tests, lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E, including replacement-load memory handling.
- Earlier interruption recovery, vision-history restoration, backend-target filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, streamed verification, thermal safeguards, context-window wiring, recommendation changes, and stop-generation ordering each passed the same Android CI pipeline at their validated tips where applicable.
- Focused JVM coverage includes context parsing/runtime context selection, per-device artifact choice, measurable-over-unknown warning preference, hardware-target exclusion, load/decode memory admission, thermal admission, interrupted-turn replay exclusion, vision history, download resume/cancellation, checksums/fingerprints, and storage headroom.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- LiteRT conversation reset/recovery replacement ordering needs correction and revalidation against the one-session-per-engine lifecycle.
- Desktop/web/iOS artifact filtering is not yet exact-tip CI validated.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only inside model metadata cannot yet be detected when filenames omit them; unknown context therefore uses a conservative 4K runtime cap.
- Unknown artifact size remains warning-only until Hugging Face or the resolved response supplies a byte length.
- First-load cache sizing, thermal behavior, LMK admission, and image-slot behavior still need physical-device validation.

## Inspect before merging
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before the next load admission; a rejected replacement must not leave stale model RAM resident.
- Reset/recover a loaded conversation repeatedly and after cancellation; verify the previous native conversation is closed before replacement and no `A session already exists` failure occurs.
- Rapidly stop a generation after visible output and immediately try to send another prompt; verify send stays disabled during STOPPING and the replacement prompt is never cancelled by the stale stop request.
- Heat a representative phone into MODERATE then SEVERE thermal status; verify local inference remains available at MODERATE and stops/blocks at SEVERE without ANR or corrupting conversation state.
- Test c32k/c64k/c128k LiteRT artifacts and verify configured native context, actual RAM use, and recommendation/load admission remain consistent on memory-constrained devices.
- Interrupt/resume a large real download and verify final checksum/local fingerprint behavior under low storage.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
- Verify recommendations on low-RAM phones, storage pressure, near LMK thresholds, missing artifact sizes, and repositories publishing generic plus vendor/backend/platform-targeted packages.
