# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict range/size checks, retained partials, cancellation, checksum/fingerprint verification, and storage admission after resolved HTTP size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry; conversation reset reuses loaded weights.
- Hardened interrupted generation, stop ordering, conversation replacement, model switching, thermal/memory admission, and persistent LiteRT cache handling.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback.
- Improved recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, supported backend, and platform-target filtering.
- Aligned inferred context windows with recommendation memory estimates, load admission, native LiteRT `EngineConfig.maxNumTokens`, bounded history replay, and generation-time context admission.
- K-suffixed context/KV markers, large explicit 256K/512K/1M windows, and million-token aliases are exact-tip CI validated.
- Context-bound generation preflight is exact-tip CI validated: restored text, prompt, template/vision reserve, and requested output are budgeted before native inference.

## In progress
- Keep the live LiteRT conversation synchronized with bounded replay history: when a successful turn forces old history eviction, rebuild the native conversation before the next prompt so KV-cache contents and context admission cannot drift apart.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Latest validated tip `45a109cb` passed Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Earlier interruption recovery, vision-history restoration, backend/platform filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, thermal safeguards, context-window wiring, replacement-load memory handling, stop-generation ordering, and conversation lifecycle fixes passed the same Android CI pipeline at their validated tips where applicable.
- Focused JVM coverage for native/replay synchronization verifies short conversations stay live without needless recreation and history eviction explicitly requests a native rebuild; exact-tip CI is pending.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Native/replay synchronization after automatic history eviction is committed but not yet exact-tip CI validated.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only inside model metadata cannot yet be detected when filenames omit them; unknown context therefore uses a conservative 4K runtime cap.
- First-load cache sizing, thermal behavior, LMK admission, image-slot behavior, and long-conversation context pressure still need physical-device validation.

## Inspect before merging
- Drive a 4K conversation through enough completed turns to trigger replay eviction; verify the next prompt rebuilds from bounded recent history instead of retaining stale native KV context.
- Drive a 4K conversation close to its context limit and verify Mobie clamps the output budget or asks for a shorter/new chat instead of entering native inference at the KV-cache boundary.
- Repeat the near-limit test with vision input; verify the extra media reserve prevents unstable multi-turn behavior without blocking ordinary image prompts.
- Reopen/reset long chats on 4K and explicit 32K/64K models; verify larger contexts retain more useful recent turns without unacceptable reset TTFT or memory growth.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before the next load admission.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or corrupting conversation state.
- Interrupt/resume a large real download under low storage and verify final checksum/local fingerprint behavior.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
