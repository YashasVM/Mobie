# Agent progress

## Major changes completed this week
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation, checksum/fingerprint verification, and storage admission.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → reset/history restore → unload/reload → generation.
- Added real TTFT, total latency, prefill/decode throughput, token-count, and app-RAM telemetry.
- Improved device/model recommendations using RAM pressure, storage headroom, quantization, artifact size, context/KV estimates, supported backend, and hardware-target filtering.
- Hardened LiteRT lifecycle ordering, cancellation serialization, lifecycle-epoch generation admission, and Stop-vs-generation-finish handling.
- Broadened Hugging Face discovery beyond `litert-community`; Featured remains curated while Search accepts directly runnable third-party LiteRT-LM text/vision artifacts.

## Important work in progress
- Server-side `litert-lm` search filtering now protects the Hub result window from unrelated PyTorch/GGUF/etc. models while retaining third-party owners; exact-tip CI is pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- `4d181da9` passed JVM tests, lint/debug APK build, emulator smoke, and the real Qwen LiteRT-LM E2E job.
- Earlier validated lifecycle hardening through `2db41a75` passed the same full Android CI pipeline.
- Focused catalog tests cover unrestricted third-party ownership, curated Featured ownership, and server-side LiteRT-LM filtering.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- The new server-side LiteRT-LM search filter still needs exact-tip full CI and an in-app live catalog check against a third-party repository.
- Vision history, thermal/LMK behavior, first-load cache sizing, long-context pressure, and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend/context constraints hidden only in model metadata cannot yet be inferred when filenames omit them; unknown context uses a conservative 4K cap.

## Items to inspect before merging
- Search for a directly runnable third-party `.litertlm` repository and verify Mobie discovers it while Featured remains limited to `litert-community`.
- Trigger Stop exactly as a real generation completes, then immediately send another prompt; verify the next prompt runs normally.
- Queue generation around reset/load/unload transitions and verify stale requests are rejected while post-transition generation runs.
- Switch directly between installed LiteRT models under constrained RAM and verify the old engine is released before next-load admission.
- Delete/truncate an installed model or fill storage before first load and verify Mobie blocks before native initialization and recovers after correction.
- Exercise a near-4K conversation including vision/history eviction and verify bounded replay/output admission stays stable.
- Heat representative phones through MODERATE → SEVERE and verify inference blocks/stops cleanly without ANR or conversation corruption.
- Interrupt/resume a large real download under low storage and verify final checksum/fingerprint behavior.
