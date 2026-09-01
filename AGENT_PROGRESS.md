# Agent progress

## Completed this week
- Created and maintained the long-running `agent-dev` branch without modifying `main`.
- Hardened resumable model downloads with strict range/server-size validation, retry handling, SHA-256 verification, cancellation that closes the active HTTP call, and retained partial files for resume.
- Verified real Qwen3-0.6B INT4 local execution through Mobie: download → load → repeated generation → conversation reset → generation → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token counts, and app RAM measurements for LiteRT-LM runs.
- Reused loaded model weights for new-chat/history switching, bounded restored context, preserved cancellation, and made conversation replacement transactional.
- Added Android low-memory admission checks before load/generation plus bounded checks during active decode to reduce OOM/LMK failures.
- Added proactive generation headroom checks using current available RAM, Android's LMK threshold, and a small device-scaled reserve so long decodes can stop before the coarse `lowMemory` flag is raised.
- Hardened LiteRT multimodal initialization with a text-only fallback when the vision executor cannot initialize.
- Improved Hugging Face catalog caching, request cancellation, and partial metadata failure handling.
- Recognize current LiteRT quantization naming including `q4_block32`, `mixed_int4`, `dynamic_wi4b32`, and `channelwise_int8`.
- Exclude MediaTek/Qualcomm/NPU-specific LiteRT bundles from the generic CPU runtime until a matching accelerator path is physically validated.
- Infer published LiteRT context/cache markers including `ekv1280`, `ekv2048`, `ctx4096`, `context4096`, `c1024`, `c32k`, and `c64k`; scale conservative KV-cache RAM estimates accordingly.
- Rank generic LiteRT artifacts using estimated weights + runtime overhead + context-dependent KV cache so long-context variants cannot hide safer lower-memory alternatives.
- Added device-specific artifact selection using current RAM, Android memory pressure, storage headroom, runtime-memory estimate, context, and supported generic LiteRT backend.
- Threaded the chosen device-specific artifact through compatibility state, download identity, cancellation, completed-file lookup, runtime adapter selection, chat resets/history switching, installed-model restoration, and catalog/model-detail presentation.
- Added Android thermal-state awareness: severe thermal pressure downgrades recommendations to a warning, while critical thermal pressure blocks new model loads/generation and is rechecked during active decode.
- Avoid repeated full-file SHA-256 reads for unchanged installed models by caching the exact verified filename/length/mtime fingerprint; changed files still force a real checksum pass.
- Persist the checksum-verification fingerprint directly at successful download completion, and reuse it inside the download worker itself when WorkManager restarts/retries an already verified destination.

## In progress
- Validate GPU-first LiteRT vision initialization: keep text inference on CPU, prefer GPU only for the vision encoder, fall back to CPU vision, then text-only if neither vision backend initializes.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use measured CPU prefill/decode baselines to identify changes that improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- Exact proactive generation-memory headroom tip `e0d0fadf` passed Android CI after JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E validation.
- Exact worker-side checksum-fingerprint tip `0d994054` passed Android CI after JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E validation.
- Exact direct-fingerprint-stamping tip `8ee9de7b` passed Android CI after JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E validation.
- Exact checksum-caching tip `2dd582ca` passed Android CI after JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E validation.
- Exact device-selected presentation tip `c8d1e06f` passed Android CI: JVM tests, lint/debug APK build, emulator integration, and the real Qwen LiteRT-LM E2E workflow.
- Exact thermal-safeguard tip `a6721f61` passed Android CI after JVM tests, lint/debug APK build, emulator integration, and the real Qwen LiteRT-LM E2E workflow.
- Exact per-device lifecycle tip `8c4673b5` passed JVM unit tests, Android lint, debug APK build, emulator smoke/integration tests, and the real LiteRT-LM Qwen E2E workflow.
- Focused checksum-verification coverage requires the exact checksum, stored filename, length, and last-modified fingerprint; changed files or incomplete metadata force real re-verification.
- Emulator coverage verifies a checksum-validated resumed download persists its verified length/mtime fingerprint at completion.
- Focused JVM coverage verifies generation is blocked before crossing Android's low-memory threshold while healthy RAM headroom remains allowed.
- Existing validation covers interrupted-transfer resume, explicit download cancellation/socket close, installed artifact identity, bounded history restoration, cancellation-safe inference, transactional conversation reset, load/decode memory admission, hardware-target exclusion, context inference, per-device artifact selection, thermal admission, and device-selected artifact presentation.

## Benchmarks
- Current CPU-emulator Qwen3-0.6B INT4 baseline: first prompt 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- After conversation-only reset with restored history: 26.56 prefill tok/s, 7.80 decode tok/s, 2.264 s TTFT, 3.214 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup measured 2.95 ms versus 1524.24 ms for full unload + reload on the CI CPU emulator (~517x less setup wall time). Emulator regression baseline only.
- No physical-device performance claim yet; checksum-read savings, proactive memory admission, and GPU vision acceleration still require representative-phone measurements.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance, thermal/battery behavior, or accelerator behavior.
- Main-model GPU/NPU selection remains disabled until representative physical-device evidence shows it is safe and beneficial.
- Hardware-targeted LiteRT bundles remain deliberately unsupported by the generic runtime even on apparently matching SoCs; current LiteRT NPU artifacts can require vendor dispatch libraries and exact SoC/runtime combinations.
- Artifacts without recognized context metadata still use a conservative 4096-token estimate because LiteRT-LM does not expose a cheap pre-load package metadata API.
- Restored-context sizing still uses a conservative character budget because LiteRT-LM does not expose a cheap pre-conversation token-count API.
- Text-only fallback preserves chat when vision initialization fails but cannot make vision work on unsupported hardware.
- GPU-first vision initialization still needs physical-device validation across Adreno/Mali/Tensor devices; CPU vision remains the fallback when the GPU delegate cannot initialize.
- Proactive generation-memory headroom is deliberately conservative and still needs physical-device validation against OEM LMK behavior.

## Inspect before merging
- Verify recommendations and selected variants on real low-RAM phones and under deliberate memory pressure.
- Verify severe/critical thermal behavior on a physical phone under sustained local inference; emulator/JVM tests cannot validate OEM thermal reporting or real throttling behavior.
- Review LiteRT artifact ranking against current repositories containing multiple quantization/context variants and SoC/NPU-specific bundles.
- Compare context-aware RAM estimates against real ARM-device RSS for 1K/2K/4K/32K/64K cache variants.
- Verify catalog cards and model details show the same package size/status/file that Mobie actually downloads.
- Benchmark installed-model discovery, immediate post-download lookup, and WorkManager restart/retry with multi-GB files; modifying the file must still force SHA-256 revalidation.
- Exercise sustained generation while forcing available RAM toward the LMK threshold; confirm Mobie cancels before an OS kill without aborting healthy generations too early.
- Test a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones; compare GPU-vision initialization, image TTFT/prefill, repeated image turns, CPU fallback, RAM, thermals, and crashes.
- Treat emulator performance figures as regression baselines only; collect comparable physical-device measurements before making performance claims.
- Verify conversation reuse, long-history switching, memory guards, and multimodal fallback behavior on representative phones.
