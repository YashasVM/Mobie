# Agent progress

## Major changes completed this week
- Raised normal chat response capacity from 256 to 1024 tokens while preserving per-model context clamping, and prefer 4K-or-better artifacts when they safely fit so small 1280/2048-token packages do not win solely on RAM.
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, storage admission, and rejection of ambiguous resumed ranges without an authoritative total size.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, app-RAM, and cold/reload LiteRT cache-growth telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Hardened LiteRT lifecycle ordering, cancellation serialization, lifecycle-epoch generation admission, and Stop-vs-generation-finish handling.
- Broadened Hugging Face discovery beyond `litert-community`; Featured remains curated while Search accepts directly runnable third-party LiteRT-LM text/vision artifacts, with server-side `litert-lm` filtering.
- Rejected an 8K unknown-context fallback after verifying LiteRT-LM does not expose package max context publicly and current Qwen3-0.6B LiteRT artifacts are published at 2K/4K context; unknown artifacts remain on the conservative 4K fallback.
- Replaced the old 30% first-load LiteRT cache estimate with a measured model-sized allowance plus 10% safety; recommendation storage estimates and runtime admission now use the same policy.

## Important work in progress
- Cache-validity-aware reload admission: added a fail-closed LiteRT cache identity marker design keyed to the exact model path/size/mtime, LiteRT-LM 0.16.1, and a manifest of cache paths/sizes/mtimes. Production admission is not reduced yet; integration waits for the validator tests to pass, then the real Qwen E2E must prove warm reloads remain correct.
- Improve authoritative context-capacity discovery. Current Hugging Face model cards can publish per-artifact context even when filenames do not, while Mobie currently relies on filename inference/fallbacks.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- `c8013686` passed JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E job with the measured cold-load storage policy.
- The successful Qwen E2E measured a 347,251,840-byte artifact, 339,216,776-byte cold cache, and 0-byte cache growth after full unload/reload.
- New `LiteRtCacheState` JVM tests cover unchanged warm-cache reuse, cache mutation invalidation, model replacement invalidation, and refusal to mark an empty cache; exact-tip Android CI is pending.
- Earlier validated lifecycle/runtime changes through `f6811a21`, `e1fa3cad`, `4d181da9`, and `2db41a75` passed the same full Android CI pipeline.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 latest measured run: first prompt 22.09 prefill tok/s, 7.10 decode tok/s, 1.439 s TTFT, 4.060 s total, ~1.02 GiB app RAM.
- Same lifecycle second prompt: 25.25 prefill tok/s, 7.82 decode tok/s, 1.211 s TTFT, 2.783 s total, ~1.02 GiB app RAM.
- Cold load: 2745.6 ms and 339,216,776 bytes of LiteRT cache/filesystem growth; full unload/reload: 1476.3 ms and 0 bytes of additional cache growth.
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Warm-cache identity tracking is not yet wired into runtime storage admission, so reloads still conservatively require cold-cache free space.
- Vision history, thermal/LMK behavior, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only in model metadata cannot yet be inferred when filenames omit them; unknown context remains capped conservatively at 4K.

## Items to inspect before merging
- After cache-validity-aware reload admission lands, verify an initialized model can reload with only filesystem safety reserve free while a cold or mutated-cache model is still blocked.
- Delete/truncate/replace an installed model after cache creation and verify the warm marker is rejected before native initialization.
- Interrupt/resume a large real model download and verify Mobie rejects ambiguous `Content-Range .../*` resumes instead of finalizing an unproven partial artifact.
- Trigger Stop exactly as a real generation completes, then immediately send another prompt; verify the next prompt runs normally.
- Switch directly between installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Exercise a near-4K conversation including vision/history eviction and verify bounded replay/output admission stays stable.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
