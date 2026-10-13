# Mobie demo: competitor baseline and feature opportunities

Research accessed 2026-10-11. Sources are first-party repositories, release notes, and source code. Advertised competitor features have not been independently loaded or tested on Android here. Absence from reviewed documentation is not proof a feature does not exist.

## Current Google AI Edge Gallery baseline

The current README advertises text chat, supported-model thinking displays, image understanding, audio transcription/translation, prompt templates and sampling controls, downloadable/importable models, benchmarks, Agent Skills, and offline device actions through FunctionGemma. It also advertises natural-language photo search and video moment search. These are poor standalone differentiation claims for Mobie. [Official README](https://github.com/google-ai-edge/gallery/blob/main/README.md)

Release dates were verified through the [official GitHub releases API](https://api.github.com/repos/google-ai-edge/gallery/releases?per_page=4):

| Release | Published UTC | Relevant documented addition |
| --- | --- | --- |
| 1.0.20 | 2026-10-06 22:07:54 | EmbeddingGemma 2, Instant Media Search, Video Moment Finder |
| 1.0.19 | 2026-09-02 23:27:51 | Gated-model permission/license flow improvements; Scrapbook custom task |
| 1.0.18 | 2026-08-10 17:18:42 | Images/audio in AI Chat; importing LiteRT-LM via Hugging Face model-card URLs |
| 1.0.17 | 2026-08-03 17:28:30 | Date verified; no additional feature inference made here |

Sources: [1.0.20](https://github.com/google-ai-edge/gallery/releases/tag/1.0.20), [1.0.19](https://github.com/google-ai-edge/gallery/releases/tag/1.0.19), [1.0.18](https://github.com/google-ai-edge/gallery/releases/tag/1.0.18).

Skills are substantial functionality: text instructions, JavaScript through WebViews, native Android intents, featured skills, URL loading, and local skill import. Some example skills query external services; local inference does not imply every tool interaction is offline. Generic tool calling, personas, or a skill marketplace would enter an already occupied space. [Official skills guide](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md)

Gallery benchmarks already persist multiple runs, filter by model, and compare a selected baseline. Recorded metrics include prefill/decode speed, first-token latency, initialization time, and aggregation statistics. A model-speed comparison screen alone would substantially overlap. The reviewed schema measures performance, not reference-answer correctness or blind user preference. [Benchmark results UI](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/benchmark/BenchmarkResultsViewer.kt), [benchmark schema](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/proto/benchmark.proto)

Gallery also contains a memory warning based on a model's minimum RAM and total/advertised device RAM. Missing thresholds bypass that warning. Therefore, “we check memory” alone is not differentiation. An explainable policy incorporating current free RAM, context-dependent memory, storage, and device-specific observations could be stronger. [MemoryWarning.kt](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/MemoryWarning.kt)

Its source has a PSS memory sampler. Android PowerMonitor deliberately returns empty battery metrics because unprivileged per-turn power attribution is unavailable. Do not invent energy-per-answer figures from battery temperature/current. LiveMetricsBar exists on main, last modified 2026-10-09, but was absent at release tag 1.0.20 when checked: distinguish source/main capabilities from released app behavior. [MemoryMonitor.kt](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/metrics/MemoryMonitor.kt), [PowerMonitor.kt](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/metrics/PowerMonitor.kt), [LiveMetricsBar.kt](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/LiveMetricsBar.kt)

## Other relevant Android competitors

PocketPal advertises GGUF local chat, local neural speech output, named assistants, built-in tools, Hugging Face downloads including gated models, hardware acceleration, and speed/memory benchmarks with an optional leaderboard. It allows retrying an answer with another model. Consequently, local chat, assistant personas, voice, model discovery, and raw throughput comparisons are established features. Latest release verified: v1.19.0, published 2026-10-04 19:38:32 UTC. [README](https://github.com/a-ghorbani/pocketpal-ai/blob/main/README.md), [release](https://github.com/a-ghorbani/pocketpal-ai/releases/tag/v1.19.0), [releases API](https://api.github.com/repos/a-ghorbani/pocketpal-ai/releases?per_page=4)

PocketPal's original benchmarking announcement describes throughput, model size and quantization factors, not measured answer quality. It does not establish that newer releases lack quality evaluation. [Maintainer announcement, 2024-12-31](https://github.com/a-ghorbani/pocketpal-ai/discussions/155)

ChatterUI advertises local GGUF inference, character cards, multiple chats per character, sampling customization, and device TTS; it also offers remote providers. These are useful baselines rather than novel Mobie directions. [README](https://github.com/Vali-98/ChatterUI/blob/master/README.md)

Offline document Q&A is not a first-of-its-kind idea either. PocketSage's README claims Android PDF parsing, local embeddings, LiteRT-LM generation, and displayed source snippets. Airgap's README claims local document Q&A, citations, source checking, and declining unsupported questions. These repository claims were not physically validated here; they still invalidate a broad “nobody does offline document chat” pitch. [PocketSage](https://github.com/umarpazir11/pocketsage), [Airgap](https://github.com/skmdroid/airgap)

Even live diagnostics are already advertised in an Android Gallery fork: app/graphics memory, available RAM, battery temperature, thermal status, and context-budget estimates. Do not call a telemetry dashboard alone unique. [AI Edge Gallery API README](https://github.com/albertosena/edge-galery-api)

## Practical product opportunities: inference from this comparison

**Best demo direction: task-based model audition.** Run two already downloaded, device-compatible text models sequentially against the same small task suite. Hide model names while the user chooses preferred outputs. Show narrowly defined deterministic checks separately: valid JSON, required fields, exact extraction of supplied facts, or a strict word limit. Reveal the models with measured wait time and memory, then let the user choose a default for that task. The distinction is an end-to-end decision about usefulness on this phone, beyond an existing throughput benchmark.

Scope it to one task such as extracting event details from a pasted announcement. Keep reference examples and expected facts visible, record exact artifact/settings/device, and do not present a five-example score as universal model intelligence. Do not use a remote judge. Human preference and schema correctness must remain separate signals. Record thermal state and run order; do not attribute speed differences to throttling without evidence. This combination was not advertised in the reviewed Gallery/PocketPal materials; no world-first claim is justified.

**Strong complementary improvement: explainable device fit.** Explain why an artifact is blocked or warned, distinguish theoretical fit from exact-artifact Android verification, and show how prompt/context size changes estimated memory. Add measured device-specific observations only after real runs. The novelty would be actionable guidance, rather than merely adding RAM figures already found elsewhere.

**Consumer alternative: share text into one useful local workflow.** Android share sheet → extract event details or rewrite a message → review an editable result → copy/share or open a calendar draft. Keep actions predefined and user-reviewed. The useful story is a workflow across apps; generic “agents” already overlaps Gallery.

**Longer alternative: a small source-linked notes workspace.** Start with pasted text or TXT/Markdown, lexical retrieval, capped context, and source passages. Embeddings/PDF/OCR are separate scope additions. Distinguish valid citation IDs from actual semantic support; source links alone do not guarantee truth. A specific study or event-planning workflow can stand out, while generic offline RAG already has competition.

Avoid adding unsupported runtimes or formats to create catalog breadth. Mobie's repository rules keep v1 on compatible `.litertlm` artifacts from `litert-community`, and every demo runtime claim still needs the exact artifact tested on physical ARM64 hardware.
