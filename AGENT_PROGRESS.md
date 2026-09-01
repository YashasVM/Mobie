# Agent progress

## Completed this week
- Hardened resumable Hugging Face model downloads with strict Range/size validation, cancellation that closes active HTTP calls, checksum verification, retained partials, trusted post-verification fingerprints, and longer connect/read timeouts for multi-GB transfers.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real inference telemetry: TTFT, total latency, prefill/decode throughput, token counts, and app RAM.
- Reused loaded model weights across new-chat/history switches; bounded restored history and kept conversation replacement transactional/cancellation-safe.
- Added load/decode memory admission, proactive LMK-headroom checks during generation, and severe/critical Android thermal safeguards.
- Improved model compatibility and recommendation using RAM, current memory pressure, storage headroom, quantization, model size, inferred context/cache size, runtime-memory estimate, and supported backend.
- Reserved additional free-storage headroom for LiteRT-LM first-load optimized cache data so models whose weights fit but whose first-load cache likely would not are rejected before download.
- Added per-device LiteRT artifact selection and persisted that exact artifact through UI → download → installed-model restoration → runtime load.
- Excluded Qualcomm/MediaTek/NPU-specific packages from the generic runtime until a matching accelerator path is physically validated; direct compatibility evaluation now rejects them too, not only recommendation filtering.
- Hardened multimodal startup with GPU-first vision, CPU-vision fallback, then text-only fallback while keeping text generation on CPU; explicitly reserves one image slot whenever vision is initialized.
- Improved Hugging Face catalog caching, request cancellation, partial metadata failure handling, and modern LiteRT quantization/context-name parsing.
- Rebuilds LiteRT-LM conversation state from canonical committed turns after interrupted generation; cancellation now stops native decode before releasing the generation mutex, and real-model E2E verifies immediate follow-up recovery.
- Made bounded LiteRT history restoration turn-aware so oversized latest turns do not erase older valid restorable context.

## In progress
- Continue auditing safe runtime/backend choices that improve TTFT/tokens-per-second without increasing crashes, RAM pressure, or thermal load; do not enable main-model GPU/NPU paths without representative physical-device evidence.

## Tests actually performed
- Exact first-load storage-headroom tip `6855974d` passed Android CI: JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E.
- Exact download-timeout hardening tip `c218481c` passed the same full Android CI/E2E pipeline.
- Exact direct hardware-target rejection tip `43a6461c`, turn-aware history tip `cfe09b4e`, and atomic-cancellation tip `005c9643` passed the same full Android CI/E2E pipeline.
- Exact interrupted-generation recovery tip `13dda38d`, image-capacity tip `6b33793a`, GPU-first vision tip `cab21c13`, and proactive generation-memory tip `e0d0fadf` passed the same full Android CI/E2E pipeline.
- Exact checksum-worker reuse tip `0d994054`, direct fingerprint-stamping tip `8ee9de7b`, checksum-caching tip `2dd582ca`, device-selected presentation tip `c8d1e06f`, thermal safeguard tip `a6721f61`, and per-device lifecycle tip `8c4673b5` all passed their relevant JVM/Android/emulator/E2E validation.
- Existing regression coverage includes interrupted download resume, cancellation/socket close, checksum mutation fallback, installed artifact identity, bounded/turn-aware history restoration, transactional reset, load/decode memory admission, hardware-target exclusion, context inference, per-device artifact selection, thermal admission, device-selected artifact presentation, and first-load storage headroom.

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
- Image history currently restores text turns but not prior image media into a recreated native conversation.
- GPU vision, thermal behavior, proactive LMK admission, image-slot behavior, and interrupted-generation recovery still need physical-device validation.
- First-load LiteRT cache size varies by model/device; the storage reserve is conservative until physical-device cache growth is measured.

## Inspect before merging
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones; verify image understanding, repeated image turns, GPU→CPU fallback, TTFT/prefill, RAM, thermals, and crashes.
- Cancel generation after partial output and after memory/thermal interruption, then send another prompt; verify the next response uses the same visible/persisted history rather than stale native context.
- Exercise very long prompts and responses across cancellation/reset; verify oversized latest turns are omitted from native restore without wiping older valid context.
- Verify per-device recommendations and artifact choices on low-RAM phones, under storage pressure, near Android LMK thresholds, and when repositories publish both generic and vendor-targeted LiteRT packages.
- Compare context-aware RAM estimates against real ARM RSS for multiple cache/context variants.
- Measure first-load LiteRT cache growth for several model sizes on physical devices and tune the conservative storage reserve if needed.
- Benchmark multi-GB installed-model discovery/retry paths and confirm file mutation still forces real SHA-256 revalidation.
- Test model downloads over a deliberately stalled/throttled connection and confirm transient gaps longer than 10 s no longer consume retries while genuine dead connections still fail and resume cleanly.
- Treat emulator performance figures as regression baselines only and collect comparable physical-device measurements before merging accelerator/performance claims.
