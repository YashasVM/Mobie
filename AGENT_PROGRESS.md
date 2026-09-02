# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict range/size checks, retained partials, cancellation, checksum/fingerprint verification, and storage admission after resolved HTTP size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry; conversation reset reuses loaded weights.
- Hardened interrupted generation and stop ordering so cancelled/failed partial turns never re-enter native context and replacement prompts cannot be hit by stale cancellation.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback.
- Improved recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, and supported backend; excluded explicit vendor/backend/desktop/web/iOS artifacts from Android generic CPU recommendations.
- Added load/generation memory admission, SEVERE thermal safeguards, first-load LiteRT cache storage headroom, persistent optimized-cache reuse, and per-device artifact selection.
- Aligned inferred c32k/c64k/c128k-style context with both final RAM admission and native LiteRT `EngineConfig.maxNumTokens`.
- Replacement model loads release the previous LiteRT engine before RAM admission; conversation reset/recovery closes the prior native conversation before replacement.
- Restored LiteRT history is UTF-8 bounded and scales with the configured context window while remaining capped to avoid unbounded reset prefill/TTFT cost; exact-tip Android CI passed.

## In progress
- Recognize explicit K-suffixed context/KV filename markers such as `ctx32k`, `context-64k`, `kv128k`, and `ekv2k` so these artifacts do not silently fall back to a 4K runtime/memory estimate. Focused JVM coverage added; exact-tip CI pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Latest validated tip `d5c5be75` passed Android CI; the pipeline includes JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Earlier interruption recovery, vision-history restoration, backend/platform filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, thermal safeguards, context-window wiring, recommendation changes, replacement-load memory handling, stop-generation ordering, and conversation lifecycle fixes passed the same Android CI pipeline at their validated tips where applicable.
- New JVM coverage verifies `ctx32k`, `context-64k`, `kv128k`, and `ekv2k` resolve to their real context sizes; exact-tip CI is pending.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- K-suffixed explicit context-marker inference is committed but not yet exact-tip CI validated.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only inside model metadata cannot yet be detected when filenames omit them; unknown context therefore uses a conservative 4K runtime cap.
- First-load cache sizing, thermal behavior, LMK admission, image-slot behavior, and long-conversation context pressure still need physical-device validation.

## Inspect before merging
- Reopen/reset long chats on 4K and explicit 32K/64K models; verify larger contexts retain more useful recent turns without unacceptable reset TTFT or memory growth.
- Verify K-suffixed context artifacts are recommended/admitted with the same context and KV-cache sizing used by native LiteRT initialization.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before the next load admission.
- Reset/recover a loaded conversation repeatedly and after cancellation; verify no native session-lifecycle failures occur.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or corrupting conversation state.
- Test c32k/c64k/c128k and ctx/context/kv K-suffixed artifacts and compare configured native context, RAM use, recommendation/load admission, and restored-history behavior on memory-constrained devices.
- Interrupt/resume a large real download under low storage and verify final checksum/local fingerprint behavior.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
