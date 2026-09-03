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
- Native LiteRT conversation/replay synchronization after automatic history eviction is exact-tip CI validated.
- Vision initialization fallback retries only recoverable backend exceptions; fatal JVM/runtime errors such as `OutOfMemoryError` no longer trigger additional GPU→CPU→text-only engine initialization attempts.
- Unknown-size LiteRT artifacts remain discoverable with a warning but are excluded from automatic device recommendations until RAM/storage/cache fit can be measured from a concrete artifact size.
- Recheck free storage immediately before LiteRT model initialization so a model downloaded under healthy storage cannot enter native first-load cache generation after other files consume the reserved space.
- LiteRT load/reset and streaming generation boundaries now preserve cancellation and VM-fatal errors instead of converting them into ordinary recoverable failures; exact-tip Android CI including real Qwen E2E passed.
- Fail closed before native LiteRT initialization when the installed model is missing/empty or free storage cannot be measured; exact-tip Android CI including real Qwen E2E passed.

## In progress
- Tighten fatal generation failure ordering so VM-fatal/native-fatal errors are classified before any follow-up JNI `cancelProcess()` cleanup call; recoverable failures still need normal cancellation/history repair.
- Continue auditing runtime cleanup/failure ordering and backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Missing-model/unverifiable-storage preflight tip `787c8753` passed Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Fatal streaming-generation lifecycle tip `abdfaa4d` passed Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Fatal LiteRT load/reset lifecycle tip `0a5bf00a` passed Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- First-load storage admission tip `6f5c2cf5` passed Android CI: JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E path.
- Earlier interruption recovery, vision-history restoration, backend/platform filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, thermal safeguards, context-window wiring, replacement-load memory handling, stop-generation ordering, conversation lifecycle, and context-bound generation fixes passed the same Android CI pipeline at their validated tips where applicable.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Fatal generation failures are rethrown, but the current catch path still invokes native cancellation before classifying the failure; this cleanup ordering should be hardened before relying on fatal-error behavior on physical devices.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only inside model metadata cannot yet be detected when filenames omit them; unknown context therefore uses a conservative 4K runtime cap.
- First-load cache sizing, thermal behavior, LMK admission, image-slot behavior, and long-conversation context pressure still need physical-device validation.

## Inspect before merging
- Force an actual low-memory/native fatal failure during LiteRT generation and verify no additional native cancellation/cleanup call is attempted before the fatal error escapes; repeat during load/reset where applicable.
- Delete or truncate an installed model file and verify Mobie blocks before native LiteRT initialization with a recovery message; repeat with a storage location whose free-space stat cannot be read.
- Fill internal storage after downloading a model but before its first load; verify Mobie blocks before native LiteRT initialization and succeeds after enough storage is freed.
- Force a recoverable vision-backend initialization failure and verify Mobie still falls back GPU → CPU → text-only; under genuine OOM/fatal runtime failure, verify it does not launch further fallback engine attempts.
- Drive a 4K conversation through enough completed turns to trigger replay eviction; verify the next prompt rebuilds from bounded recent history instead of retaining stale native KV context.
- Drive a 4K conversation close to its context limit and verify Mobie clamps the output budget or asks for a shorter/new chat instead of entering native inference at the KV-cache boundary.
- Repeat the near-limit test with vision input; verify the extra media reserve prevents unstable multi-turn behavior without blocking ordinary image prompts.
- Switch directly between two installed LiteRT models under constrained RAM and verify the old engine is released before the next load admission.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or corrupting conversation state.
- Interrupt/resume a large real download under low storage and verify final checksum/local fingerprint behavior.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
