# Agent progress

## Major changes completed this week
- Raised normal chat response capacity from 256 to 1024 tokens while preserving per-model context clamping, and prefer 4K-or-better artifacts when they safely fit so small 1280/2048-token packages do not win solely on RAM.
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, storage admission, and rejection of ambiguous resumed ranges without an authoritative total size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Hardened LiteRT lifecycle ordering, cancellation serialization, lifecycle-epoch generation admission, and Stop-vs-generation-finish handling.
- Broadened Hugging Face discovery beyond `litert-community`; Featured remains curated while Search accepts directly runnable third-party LiteRT-LM text/vision artifacts, with server-side `litert-lm` filtering.
- Rejected an 8K unknown-context fallback after verifying LiteRT-LM does not expose package max context publicly and current Qwen3-0.6B LiteRT artifacts are published at 2K/4K context; unknown artifacts remain on the conservative 4K fallback.

## Important work in progress
- Audit first-load LiteRT cache admission so Mobie reserves enough storage for initial optimization without falsely blocking later reloads after a valid per-model cache already exists. Cache growth is now measured inside the same real Qwen E2E lifecycle that downloads and verifies the model, avoiding a second instrumentation reinstall that discarded app-internal model state. Use the resulting CI evidence before reducing reload headroom.
- Improve authoritative context-capacity discovery. Current Hugging Face model cards can publish per-artifact context even when filenames do not (for example Qwen3-0.6B mixed INT4 at 2048 tokens), while Mobie currently relies on filename inference/fallbacks.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- `f6811a21` passed JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E job.
- `e1fa3cad` and `4d181da9` passed the same full Android CI pipeline.
- Earlier validated lifecycle hardening through `2db41a75` passed the same full Android CI pipeline.
- Focused catalog tests cover unrestricted third-party ownership, curated Featured ownership, and server-side LiteRT-LM filtering.
- Focused download policy tests cover exact resume offsets, expected-size agreement, and rejection of `Content-Range` resumes whose total is `*`.
- The first standalone cache-growth CI attempt failed because a second `connectedDebugAndroidTest` invocation reinstalled/reset the app and lost the model downloaded by the preceding E2E test. The measurement has been integrated into `LiteRtEndToEndTest`; the replacement CI run is pending, so no cache-size result is claimed yet.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Vision history, thermal/LMK behavior, first-load cache sizing, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only in model metadata cannot yet be inferred when filenames omit them; unknown context therefore remains capped conservatively at 4K.

## Items to inspect before merging
- Search for a directly runnable third-party `.litertlm` repository and verify Mobie discovers it while Featured remains limited to `litert-community`.
- Interrupt/resume a large real model download and verify Mobie rejects ambiguous `Content-Range .../*` resumes instead of finalizing an unproven partial artifact.
- Trigger Stop exactly as a real generation completes, then immediately send another prompt; verify the next prompt runs normally.
- Queue generation around reset/load/unload transitions and verify stale requests are rejected while post-transition generation runs.
- Switch directly between installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Delete/truncate an installed model or fill storage before first load and verify Mobie blocks before native initialization and recovers after correction.
- Review the integrated Qwen cache-growth evidence; only reduce reload storage headroom if subsequent loads show negligible cache growth versus the cold first load.
- Exercise a near-4K conversation including vision/history eviction and verify bounded replay/output admission stays stable.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
