# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads with strict Range/size validation, cancellation that closes active calls, checksum verification, retained partials, trusted post-verification fingerprints, longer transfer timeouts, and a second storage-admission check once HTTP response headers reveal the actual remaining transfer size.
- Added installed-model length fingerprints so newly downloaded artifacts are rejected after later truncation/replacement even when Hugging Face publishes no SHA-256; legacy metadata remains readable.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real inference telemetry: TTFT, total latency, prefill/decode throughput, token counts, and app RAM.
- Reused loaded weights across conversation resets; bounded restored history and hardened cancellation/interrupted-turn recovery.
- Added load/decode memory admission, LMK-headroom checks during generation, and severe/critical thermal safeguards.
- Improved model recommendations using RAM, current memory pressure, storage headroom, quantization, model size, inferred context/cache size, runtime-memory estimates, and backend support.
- Reserved first-load LiteRT optimized-cache storage and persisted LiteRT cache artifacts beside each installed model so subsequent loads can reuse them and model deletion removes them.
- Added per-device LiteRT artifact selection and persisted the selected artifact through UI → download → installed-model restoration → runtime load.
- Excluded Qualcomm/MediaTek/NPU-specific packages from the generic runtime until matching accelerator paths are physically validated.
- Hardened multimodal startup with GPU-first vision, CPU-vision fallback, then text-only fallback while keeping text generation on CPU.
- Improved Hugging Face catalog caching, request cancellation, partial metadata failure handling, and LiteRT quantization/context-name parsing.

## In progress
- Stream SHA-256 while model bytes are written so fresh multi-GB downloads avoid a second full-file verification read; resumed transfers hash only the retained prefix before continuing. Persist the streamed digest for checksum-less artifacts so later same-length changes can be revalidated. Exact-tip Android CI/E2E is pending.
- Continue auditing safe runtime/backend choices that improve TTFT/tokens-per-second without increasing crashes, RAM pressure, or thermal load; do not enable main-model GPU/NPU paths without representative physical-device evidence.

## Tests actually performed
- Exact installed-model corruption-guard tip `5f5081a6`, resolved-response download storage tip `88c48aab`, persistent LiteRT cache tip `a9359ae7`, incomplete-turn recovery tip `ce740ee1`, first-load storage-headroom tip `6855974d`, download-timeout tip `c218481c`, direct hardware-target rejection tip `43a6461c`, turn-aware history tip `cfe09b4e`, and atomic-cancellation tip `005c9643` passed the full Android CI pipeline including JVM tests, lint/debug APK build, emulator integration, and real Qwen LiteRT-LM E2E where applicable.
- Focused JVM coverage exists for installed-length truncation, local digest fingerprint reuse/invalidation, interrupted download resume, cancellation/socket close, checksum mutation fallback, installed artifact identity, history recovery, load/decode memory admission, hardware-target exclusion, context inference, per-device artifact selection, thermal admission, and first-load storage headroom. The new streamed-digest exact tip is awaiting CI.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- Fresh downloads now compute SHA-256 inline with the write path instead of rereading the completed artifact solely for checksum verification; no wall-time claim until CI/physical measurements are collected.
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Hardware-targeted LiteRT packages remain excluded from the generic path until exact vendor/runtime combinations are validated.
- Context metadata and restored-history sizing remain conservative where exact tokenizer/runtime metadata is unavailable.
- Image history restores text turns but not prior image media into a recreated native conversation.
- GPU vision, thermal behavior, proactive LMK admission, image-slot behavior, interrupted-generation recovery, and first-load cache sizing still need physical-device validation.

## Inspect before merging
- Interrupt and resume a large real download, verify the final digest/checksum succeeds, then modify a checksum-less installed artifact without changing its length and verify Mobie rehashes/rejects it rather than trusting the length alone.
- Test an artifact whose catalog size is missing but HTTP headers provide a large Content-Length/Content-Range; verify Mobie fails before writing when remaining free storage is insufficient and resumes normally when space is adequate.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones; verify image understanding, repeated image turns, GPU→CPU fallback, TTFT/prefill, RAM, thermals, and crashes.
- Verify per-device recommendations and artifact choices on low-RAM phones, under storage pressure, near Android LMK thresholds, and when repositories publish generic plus vendor-targeted LiteRT packages.
- Measure first/subsequent cold model loads after process restarts and first-load cache growth across several model sizes on physical devices.
- Treat emulator performance figures as regression baselines only and collect comparable physical-device measurements before merging accelerator/performance claims.
