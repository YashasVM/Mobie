# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict range/size checks, retained partials, cancellation, checksum/fingerprint verification, and storage admission after resolved HTTP size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry; conversation reset reuses loaded weights.
- Hardened interrupted generation so cancelled/failed partial turns remain visible but never re-enter native context; serialized recovery prevents prompt races.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback.
- Improved recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, and supported backend.
- Excluded vendor/backend-constrained LiteRT packages from the generic CPU path and prevented unknown-size warning artifacts from outranking measurable alternatives.
- Added load/generation memory admission, thermal safeguards, first-load LiteRT cache storage headroom, persistent optimized-cache reuse, and per-device artifact selection.
- Made final LiteRT load admission context-aware so c64k/c128k-style artifacts no longer pass native preflight under a 4K KV-cache assumption; exact-tip CI passed unit/lint/build, emulator smoke, and real Qwen E2E.

## In progress
- Align runtime thermal admission with Android's SEVERE thermal level. Recommendations already warn at SEVERE, but model load/generation previously continued until CRITICAL. Runtime policy and regression coverage now block new/continued local inference at SEVERE while still allowing MODERATE; exact-tip CI is pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Latest validated tip `8abbad3c` passed JVM tests, lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E.
- Earlier interruption recovery, vision-history restoration, backend-target filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, streamed verification, and recommendation changes each passed the same Android CI pipeline at their validated tips where applicable.
- Focused JVM coverage includes context parsing, per-device artifact choice, measurable-over-unknown warning preference, hardware-target exclusion, load/decode memory admission, thermal admission, interrupted-turn replay exclusion, vision history, download resume/cancellation, checksums/fingerprints, and storage headroom.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- SEVERE-level runtime thermal blocking is not yet CI-validated at the newest tip.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend constraints hidden only inside model metadata cannot yet be detected by filename classification.
- Unknown artifact size remains warning-only until Hugging Face or the resolved response supplies a byte length.
- First-load cache sizing, thermal behavior, LMK admission, and image-slot behavior still need physical-device validation.

## Inspect before merging
- Heat a representative phone into MODERATE then SEVERE thermal status; verify local inference remains available at MODERATE and stops/blocks at SEVERE without ANR or corrupting conversation state.
- Test c64k/c128k LiteRT artifacts on memory-constrained devices and confirm native load admission matches recommendation RAM estimates.
- Cancel generation after visible tokens, immediately send another prompt, then restart and verify the interrupted partial turn never re-enters context.
- Interrupt/resume a large real download and verify final checksum/local fingerprint behavior under low storage.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
- Verify recommendations on low-RAM phones, storage pressure, near LMK thresholds, missing artifact sizes, and repositories publishing generic plus vendor/backend-targeted packages.
