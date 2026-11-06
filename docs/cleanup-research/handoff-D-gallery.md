# Gallery research handoff

Stopped at the user's request on 2026-10-13.

## Done

- Wrote `/home/yvm/code/Mobie-research/gallery-config.md`, approximately 5,659 words, covering all requested source-research sections.
- Shallow-cloned latest Gallery main and inspected its Android LiteRT-LM setup, defaults, model-specific backend/context selection, conversation reuse/reset, warm-up, and benchmark/live telemetry paths.
- Inspected LiteRT-LM 0.18.0 Kotlin configuration, Engine/Conversation/Session bindings, ModelInfo, experimental flags, and relevant native defaults.
- Compared 0.16.1 configuration definitions with 0.18.0 and reviewed official 0.16.0, 0.16.1, 0.17.0, and 0.18.0 release notes.
- Report contains 11 ranked recommendations, each marked as needing on-device measurement; distinguishes decode throughput from TTFT, load latency, memory, and reduced generation work.
- Validated 212 source citation paths and line bounds against local snapshots. No unresolved citation placeholders.
- No changes to `/home/yvm/code/Mobie-gpu`; no adb, phone, emulator, build, or model inference used.

## Main findings

- Latest Gallery pins LiteRT-LM 0.18.0, so the Mobie upgrade alone establishes runtime parity.
- Gallery uses per-model backend order rather than measured device selection; current Gemma 3n defaults to CPU.
- Gallery leaves CPU thread count unspecified; reviewed native default is four threads.
- Gallery already reuses engine/conversation across ordinary chat turns.
- Normal chat telemetry is hard-coded off in this checkout. Dedicated benchmark remains active.
- Dedicated benchmark TTFT is computed from prefill duration plus average decode-token duration, rather than observed first-token arrival.
- Kotlin benchmark() ignores Backend.CPU threadCount overrides; use a real configured Engine for thread sweeps.
- Most practical extra controls are CPU thread count, independent output/thinking budgets, prefix prefill, per-conversation speculative decoding, and artifact-aware context limits.
- GPU precision often already defaults to FP16; GPU priority and native prefill batch settings require custom native bindings/builds.

## Left / incomplete work

- No requested source-research section is incomplete.
- INCOMPLETE: physical Galaxy S24 Ultra validation and all performance measurements. These were explicitly outside this research task; no speedup is established.
- INCOMPLETE: Mobie implementation, build/test verification, and UI work. None was attempted or requested here.
- Future validation should use the exact same artifact bytes/hash, matching backend/context/sampler/MTP, cold/warm/multi-turn/sustained workloads, actual token counts, app-observed TTFT, peak RAM, and thermal notes. Compare both stock and tuned Gallery baselines.

## Source clones and supporting files

- Gallery shallow clone: `/tmp/gallery-src`
  - Commit: `4928788ef07cdf2194fc435a0f446cf3b997bd5d`
  - Android version: 1.0.20
  - Primary helper: `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`
  - Pin: `Android/src/gradle/libs.versions.toml`
  - Current model defaults: `model_allowlists/1_0_20.json`
- LiteRT-LM 0.18.0 shallow clone: `/tmp/litert-lm-018-src`
  - Commit: `b2f686e2ed4718fb84ec398a61dd59ca0f0aff27`
  - Kotlin API: `kotlin/java/com/google/ai/edge/litertlm/`
  - Native defaults: `runtime/executor/llm_executor_settings.h`, `runtime/executor/llm_executor_settings.cc`
  - Benchmark formulas: `runtime/engine/io_types.cc`
- LiteRT-LM 0.16.1 comparison files: `/tmp/litert-lm-016-api/Config.kt`, `/tmp/litert-lm-016-api/ExperimentalFlags.kt`
- Report generator: `/tmp/write-gallery-research.py`
  - The final report has one small wording correction after generator output: 'backend classes' replaces 'enum'. Prefer the final Markdown file as the authoritative artifact.

Temporary clones/files live under `/tmp` and may be cleared by the system. The report and this handoff live in the requested persistent research directory.
