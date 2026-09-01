# Agent progress

## Completed this week
- Hardened resumable Hugging Face model downloads with strict Range/size validation, cancellation that closes active HTTP calls, checksum verification, retained partials, and trusted post-verification fingerprints to avoid repeated multi-GB SHA-256 reads.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real inference telemetry: TTFT, total latency, prefill/decode throughput, token counts, and app RAM.
- Reused loaded model weights across new-chat/history switches; bounded restored history and kept conversation replacement transactional/cancellation-safe.
- Added load/decode memory admission, proactive LMK-headroom checks during generation, and severe/critical Android thermal safeguards.
- Improved model compatibility and recommendation using RAM, current memory pressure, storage headroom, quantization, model size, inferred context/cache size, runtime-memory estimate, and supported backend.
- Added per-device LiteRT artifact selection and persisted that exact artifact through UI → download → installed-model restoration → runtime load.
- Excluded Qualcomm/MediaTek/NPU-specific packages from the generic runtime until a matching accelerator path is physically validated.
- Hardened multimodal startup with GPU-first vision, CPU-vision fallback, then text-only fallback while keeping text generation on CPU.
- Improved Hugging Face catalog caching, request cancellation, partial metadata failure handling, and modern LiteRT quantization/context-name parsing.

## In progress
- Explicitly reserve one LiteRT image slot (`maxNumImages = 1`) whenever a vision executor is initialized so multimodal loads cannot silently lack image capacity; text-only loads remain unchanged.
- Continue auditing safe runtime/backend choices that improve TTFT/tokens-per-second without increasing crashes, RAM pressure, or thermal load.

## Tests actually performed
- Exact GPU-first vision tip `cab21c13` passed Android CI: JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E.
- Exact proactive generation-memory tip `e0d0fadf` passed the same full Android CI/E2E pipeline.
- Exact checksum-worker reuse tip `0d994054`, direct fingerprint-stamping tip `8ee9de7b`, checksum-caching tip `2dd582ca`, device-selected presentation tip `c8d1e06f`, thermal safeguard tip `a6721f61`, and per-device lifecycle tip `8c4673b5` all passed their relevant JVM/Android/emulator/E2E validation.
- Existing regression coverage includes interrupted download resume, cancellation/socket close, checksum mutation fallback, installed artifact identity, bounded history restoration, transactional reset, load/decode memory admission, hardware-target exclusion, context inference, per-device artifact selection, thermal admission, and device-selected artifact presentation.
- The new explicit LiteRT image-capacity change is committed but not yet marked validated until its exact-tip CI finishes.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset with restored history: 26.56 prefill tok/s, 7.80 decode tok/s, 2.264 s TTFT, 3.214 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Hardware-targeted LiteRT packages may require exact vendor dispatch libraries/SoC/runtime combinations and remain excluded from the generic path.
- Context metadata is inferred from artifact naming when available; unknown packages use a conservative 4096-token estimate.
- Restored-history sizing uses a conservative character budget because a cheap pre-conversation tokenizer count is not exposed.
- GPU vision, thermal behavior, proactive LMK admission, and image-slot behavior still need physical-device validation.

## Inspect before merging
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones; verify image understanding (not hallucinated text-only behavior), repeated image turns, GPU→CPU fallback, TTFT/prefill, RAM, thermals, and crashes.
- Verify per-device recommendations and artifact choices on low-RAM phones, under storage pressure, and near Android LMK thresholds.
- Compare context-aware RAM estimates against real ARM RSS for multiple cache/context variants.
- Benchmark multi-GB installed-model discovery/retry paths and confirm file mutation still forces real SHA-256 revalidation.
- Treat emulator performance figures as regression baselines only and collect comparable physical-device measurements before merging accelerator/performance claims.
