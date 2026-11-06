# Advanced runtime controls

Agent A, 2026-10-13. `ComputeOptions` is in `core/runtime/ComputeOptions.kt`. Apply options with `RuntimeAdapter.configureCompute` before load. A changed context, activation, backend, or conversation option needs unload and reload. Options are captured at load and do not mutate a running engine.

| Field | Range or choices | Default | SDK mapping and restriction |
| --- | --- | --- | --- |
| `backend` | AUTO, CPU, GPU | AUTO | GPU first when plausible and memory permits; CPU fallback reason remains in `activeCompute.fallbackReason`. NPU remains unavailable. |
| `cpuThreads` | null or 1..min(detected cores, 8) | null | `Backend.CPU.threadCount`; null selects topology policy, not all cores. Persisted 0 means Auto. Settings sanitize on read, write, and hardware detection. |
| `contextTokens` | null or 1024..4096; suggested 1024, 2048, 4096 | null | `EngineConfig.maxNumTokens`. The actual limit comes from local `ModelInfo`; static artifacts require their fixed capacity. Dynamic choices must not exceed the artifact maximum. Automatic retains existing RAM policy pending repeat measurements. |
| `maxOutputTokens` | 1..1024; suggested 64, 128, 256, 512, 1024 | 1024 | Conversation cap and maximum per-send cap. Actual send uses the smaller of this, `GenerationConfig.maxNewTokens`, and available context. This cap includes reasoning plus visible output. |
| `thinkingEnabled` | null, false, true | null | `ThinkingConfig.enableThinking`. Null preserves model default or legacy `configureThinking`. True requires artifact support. |
| `thinkingTokenBudget` | null or 0..1024; suggested 64, 128, 256 | null | `ThinkingConfig.thinkingTokenBudget`; null maps to SDK -1. Supported models only. A budget does not reserve extra visible output tokens outside the shared cap. |
| `activationType` | AUTO, FP16, FP32 | AUTO | Nullable `EngineConfig.activationDataType`. An override may fail initialization; the error is surfaced. FP16 and FP32 are experimental until the exact artifact/backend passes a phone trial. No INT8 or INT16 choices. This changes activation precision, not weight quantization. |
| `speculativeDecoding` | null, false, true | null | `ConversationConfig.enableSpeculativeDecoding`. True is rejected unless the artifact advertises a drafter. First use can add memory and latency. Metadata support is not measured compatibility. |
| `prefillPrefaceOnInit` | false, true | false | `ConversationConfig.prefillPrefaceOnInit`, applied only with restored history. Moves prefill into conversation creation; it does not remove that work. |

`RuntimeAdapter.computeCapabilities` returns metadata after load. `contextChoices()` provides bounded presets. Capability metadata is available through the thermal and stall wrappers. The UI must distinguish metadata-supported experiments from configurations qualified on the phone.

Existing `SamplerSettings` remains the sampler interface, supplied through `configureSampler` and `GenerationConfig.sampler`. Temperature is 0..2, topK is 1..200, topP is greater than 0 and at most 1, and seed is an Int. For the Advanced UI, use topK 1..64 until larger generic-GPU values pass device tests. Greedy benchmark settings are temperature 0, topK 1, topP 1, seed 0. These settings affect output and must match across a speed comparison.

The stock SDK does not expose GPU queue priority, shader options, prefill graph batch sizes, KV dtype, or KV growth increments. Do not add controls for them. Vision thread count is not forwarded by the reviewed JNI binding; audio is outside Mobie's v1 chat runtime. Gemma 4 visual token budgets and reasoning-KV filtering are process-global experimental flags requiring a separate qualified integration. Arbitrary templates, LoRA files, constraints, tool execution, and token suppression are behavior features, not safe generic speed switches.

API evidence is the pinned 0.18.0 `Config.kt`, `ModelInfo.kt`, and `Conversation.kt` in `/tmp/litert-lm-018-src`, plus the cached Maven AAR. See `gallery-config.md` and `litertlm-0.18-review.md` for immutable upstream source links. No default speed claim follows from API availability.

Phone update: the exact 344671744-byte Qwen artifact reports fixed context 4096 and no speculative decoding support. Direct SDK lower-context trials are exploratory and do not override the production restriction. Auto/GPU with default activation settings is measured; explicit FP16 and FP32 still await trials. `RuntimeComputeSettings.preferences` is internal for the UI-owned advanced settings codec.
