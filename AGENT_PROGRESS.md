# Agent progress

## Completed this week
- Created and maintained the long-running `agent-dev` branch without modifying `main`.
- Hardened resumable model downloads with strict range/server-size validation, retry handling, SHA-256 verification, cancellation that closes the active HTTP call, and retained partial files for resume.
- Verified real Qwen3-0.6B INT4 local execution through Mobie: download → load → repeated generation → conversation reset → generation → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token counts, and app RAM measurements for LiteRT-LM runs.
- Reused loaded model weights for new-chat/history switching, bounded restored context, preserved cancellation, and made conversation replacement transactional.
- Added Android low-memory admission checks before load/generation plus bounded checks during active decode to reduce OOM/LMK failures.
- Hardened LiteRT multimodal initialization with a text-only fallback when the vision executor cannot initialize.
- Improved Hugging Face catalog caching, request cancellation, and partial metadata failure handling.
- Recognize current LiteRT quantization naming including `q4_block32`, `mixed_int4`, `dynamic_wi4b32`, and `channelwise_int8`.
- Exclude MediaTek/Qualcomm/NPU-specific LiteRT bundles from the generic CPU runtime until a matching accelerator path is physically validated.
- Infer published LiteRT context/cache markers including `ekv1280`, `ekv2048`, `ctx4096`, `context4096`, `c1024`, `c32k`, and `c64k`; scale conservative KV-cache RAM estimates accordingly.
- Rank generic LiteRT artifacts using estimated weights + runtime overhead + context-dependent KV cache so long-context variants cannot hide safer lower-memory alternatives.
- Added device-specific artifact selection using current RAM, Android memory pressure, storage headroom, runtime-memory estimate, context, and supported generic LiteRT backend.
- Threaded the chosen device-specific artifact through compatibility state, download identity, cancellation, completed-file lookup, runtime adapter selection, chat resets/history switching, and installed-model restoration. Installed metadata now preserves the original Hugging Face artifact filename so context/quantization identity survives app restarts.

## In progress
- Added Android thermal-state awareness: severe thermal pressure downgrades model recommendations to a warning, while critical thermal pressure blocks new model loads/generation and is rechecked during active decode. Exact-tip CI is pending.
- Make catalog/model-detail presentation use the same device-selected artifact as the functional lifecycle; a few UI-only `bestArtifact` references can still show a different size/status/file than Mobie will actually download.
- Continue auditing real-device performance constraints and safe accelerator/backend selection.
- Use measured CPU prefill/decode baselines to identify changes that improve TTFT/tokens-per-second without increasing RAM or instability.

## Tests performed
- Exact per-device lifecycle tip `8c4673b5` passed JVM unit tests, Android lint, debug APK build, emulator smoke/integration tests, and the real LiteRT-LM Qwen E2E workflow.
- Added Android persistence coverage proving an installed `Qwen3-0.6B-int4-ekv2048.litertlm` keeps its original artifact identity, restores the 2048-token context hint/INT4 metadata, and resolves the hashed local file correctly after reconstruction.
- Added JVM coverage for thermal recommendation behavior and runtime admission: severe thermal pressure warns; critical thermal pressure blocks model load and generation; severe-but-not-critical pressure does not hard-stop an already usable runtime.
- Existing validation covers interrupted-transfer resume, explicit download cancellation/socket close, bounded history restoration, cancellation-safe inference, transactional conversation reset, load/decode memory admission, hardware-target exclusion, context inference, and per-device artifact selection.

## Benchmarks
- Current CPU-emulator Qwen3-0.6B INT4 baseline: first prompt 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- After conversation-only reset with restored history: 26.56 prefill tok/s, 7.80 decode tok/s, 2.264 s TTFT, 3.214 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup measured 2.95 ms versus 1524.24 ms for full unload + reload on the CI CPU emulator (~517x less setup wall time). Emulator regression baseline only.
- No physical-device performance claim yet.

## Known problems / regressions
- GGUF remains intentionally unavailable; Mobie v1 currently relies on published LiteRT-LM artifacts.
- Emulator validation proves CPU LiteRT-LM execution but not real ARM phone performance, thermal/battery behavior, or accelerator behavior.
- GPU/NPU selection remains disabled until representative physical-device evidence shows it is safe and beneficial.
- Hardware-targeted LiteRT bundles remain deliberately unsupported by the generic runtime even on apparently matching SoCs; current LiteRT NPU artifacts can require vendor dispatch libraries and exact SoC/runtime combinations.
- Catalog/model-detail UI still has a few display-only `bestArtifact` references; functional download/load state is already pinned to the device-selected artifact, but presentation should be aligned next.
- Artifacts without recognized context metadata still use a conservative 4096-token estimate because LiteRT-LM does not expose a cheap pre-load package metadata API.
- Restored-context sizing still uses a conservative character budget because LiteRT-LM does not expose a cheap pre-conversation token-count API.
- Text-only fallback preserves chat when vision initialization fails but cannot make vision work on unsupported hardware.

## Inspect before merging
- Verify recommendations and selected variants on real low-RAM phones and under deliberate memory pressure.
- Verify severe/critical thermal behavior on a physical phone under sustained local inference; emulator/JVM tests cannot validate OEM thermal reporting or real throttling behavior.
- Review LiteRT artifact ranking against current repositories containing multiple quantization/context variants and SoC/NPU-specific bundles.
- Compare context-aware RAM estimates against real ARM-device RSS for 1K/2K/4K/32K/64K cache variants.
- Verify the displayed model package exactly matches the device-selected/downloaded artifact after the remaining UI consistency work.
- Treat emulator performance figures as regression baselines only; collect comparable physical-device measurements before making performance claims.
- Verify conversation reuse, long-history switching, memory guards, and a real vision-capable `.litertlm` model on representative phones.