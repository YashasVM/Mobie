# Agent progress

## Completed this week
- Hardened resumable Hugging Face downloads: strict range/size validation, retained partials, cancellation that closes active calls, checksum/local-digest verification, inline SHA-256 for fresh downloads, installed-file fingerprints, and a second storage-admission check after resolved HTTP length is known.
- Verified real Qwen3-0.6B INT4 LiteRT-LM execution through Mobie: download → load → repeated generation → conversation reset/history restore → unload/reload → generation.
- Added real inference telemetry for TTFT, total latency, prefill/decode throughput, token counts, and app RAM; conversation reset reuses loaded weights.
- Hardened interrupted generation across persistence and same-process replay so cancelled/failed partial assistant turns remain visible to the user but do not re-enter native context; serialized recovery to prevent prompt races.
- Preserved compatible vision history through LiteRT recreation with newest-readable-image restoration and safe text-only fallback for missing files or unavailable vision initialization.
- Improved model recommendations using RAM, current pressure, storage headroom, quantization, artifact size, inferred context/KV cache, runtime-memory estimates, and backend support.
- Excluded vendor/backend-constrained LiteRT packages from the generic CPU path until matching accelerator paths are physically validated.
- Prevented unknown-size warning artifacts from outranking measurable warning-state alternatives solely because missing publisher metadata produced zero RAM/storage estimates; exact-tip CI passed unit/lint/build, emulator smoke, and real Qwen E2E.
- Added load/generation memory admission, thermal safeguards, first-load LiteRT cache storage headroom, persistent optimized-cache reuse, and per-device artifact selection.

## In progress
- Make final native LiteRT load admission context-aware. Recommendation estimates already scale KV cache using artifact names such as `c64k`; the runtime preflight previously assumed 4K/256 MiB. Runtime admission now accepts context size, and the LiteRT adapter infers the context from the installed filename before engine initialization. Focused regression coverage is pushed; exact-tip CI is pending.
- Continue auditing runtime/backend choices for reliable TTFT/tokens-per-second improvements without enabling unvalidated main-model GPU/NPU execution.

## Tests actually performed
- Latest validated tip `853c1c0e` passed JVM tests, lint/debug APK build, emulator smoke, and real Qwen LiteRT-LM E2E.
- Earlier interruption recovery, vision-history restoration, backend-target filtering, persistent LiteRT cache, resumable download, storage admission, corruption detection, and streamed verification changes each passed the same Android CI pipeline at their exact code tips where applicable.
- Focused JVM coverage includes context parsing, per-device artifact choice, measurable-over-unknown warning preference, hardware-target exclusion, load/decode memory admission, thermal admission, interrupted-turn replay exclusion, vision history, download resume/cancellation, checksums/fingerprints, and storage headroom.

## Real benchmarks / performance improvements
- CPU-emulator Qwen3-0.6B INT4 baseline: 20.64 prefill tok/s, 7.51 decode tok/s, 1.468 s TTFT, 3.955 s total, ~1.02 GiB app RAM.
- Same loaded conversation, second prompt: 21.41 prefill tok/s, 7.81 decode tok/s, 1.375 s TTFT, 2.965 s total, ~1.02 GiB app RAM.
- Conversation-only reset setup: 2.95 ms versus 1524.24 ms for full unload/reload on the CI CPU emulator (~517× less setup wall time).
- No physical-device speed claim yet; emulator numbers are regression baselines only.

## Known problems / regressions
- Context-aware native load admission is not yet CI-validated at the newest tip.
- Vision history and interrupted-generation recovery still need representative physical-device testing.
- GGUF remains intentionally unavailable; v1 currently relies on published LiteRT-LM artifacts.
- Main-model GPU/NPU execution remains disabled until representative phones show a reliable net benefit.
- Backend constraints hidden only inside model metadata cannot yet be detected by filename classification.
- Unknown artifact size remains warning-only until Hugging Face or the resolved response supplies a byte length.
- First-load cache sizing, thermal behavior, LMK admission, and image-slot behavior still need physical-device validation.

## Inspect before merging
- Test `c64k`/`c128k` LiteRT artifacts on memory-constrained devices and confirm native load admission matches the recommendation RAM estimate rather than assuming 4K context.
- Cancel generation after visible tokens, immediately send another prompt, then restart and send another prompt; verify the interrupted partial turn enters neither same-process nor restored context.
- Interrupt/resume a large real download and verify final checksum/local fingerprint behavior under low storage.
- Run a real vision-capable `.litertlm` model on representative Adreno/Mali/Tensor phones and verify repeated/restored image turns plus GPU→CPU/text-only fallback.
- Verify recommendations on low-RAM phones, storage pressure, near LMK thresholds, missing artifact sizes, and repositories publishing generic plus vendor/backend-targeted packages.
- Measure first/subsequent cold loads and LiteRT cache growth across several model sizes on physical devices.
