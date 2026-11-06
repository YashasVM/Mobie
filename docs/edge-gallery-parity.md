# Google AI Edge Gallery parity audit

Audited 2026-11-03 against upstream commit `e0bb6c1fd3e34a7ba749a6d2f9acde1b7be06b56`. Mobie findings describe the copied starting state in `feat/edge-gallery-experience`, before this branch's additions. This is a source audit, not a claim that every referenced model works on Android.

The comparison uses the [official feature list](https://github.com/google-ai-edge/gallery/blob/e0bb6c1fd3e34a7ba749a6d2f9acde1b7be06b56/README.md), the [Android source tree](https://github.com/google-ai-edge/gallery/tree/e0bb6c1fd3e34a7ba749a6d2f9acde1b7be06b56/Android/src/app/src/main/java/com/google/ai/edge/gallery), and Mobie source. Older 2025 feature summaries omit several current upstream tasks.

## Implemented in this worktree

- Explore gives local chat, photo questions, Prompt Lab, language practice and model performance their own entry points. Existing saved chats remain available through the drawer.
- Prompt Lab has freeform, summarize, rewrite, code and translation templates. Each run creates a new local session before generation. Generation settings include the existing sampler controls and save for the next task load without reloading an unrelated chat.
- Language practice starts a new text tutoring conversation for the chosen language, level and topic. This does not include XR, vocabulary storage or Gallery's learning games.
- Model performance provides a fixed comparison prompt and shows stored, artifact-specific CPU/GPU speed ranges for the active model. It is a manually repeated chat workload, not a controlled benchmark runner.
- Ask a photo filters the installed library to vision models. Chat supports camera-app capture through a private FileProvider and the existing bounded JPEG/PNG/WebP validator. The runtime still accepts one image per message.
- Completed replies have read-aloud/stop controls using installed, non-network Android TTS voices for the system language. Dictation requires Android 12 or newer and Android's dedicated on-device speech recognizer. Neither path falls back to a network speech service.
- Model browsing has All, Fits now, Photos and Text filters, result counts, filter reset and query-preserving retry. Local models appear before cloud models. Distinct list keys prevent installed/discovered duplicates from crashing the list. Vision estimates include the encoder.

The original checkout had uncommitted cloud and navigation changes. Those changes were copied into this worktree before implementation; they are not Gallery additions. The original checkout remains separate.

Full upstream parity is **not implemented**. The inventory below records the remaining work rather than treating missing tasks as working features. No new native runtime or model format was enabled.

## Feature inventory

| Upstream feature | Mobie starting state | Work needed for parity |
| --- | --- | --- |
| Agent Skills | Missing | A constrained tool-execution contract, skill management, imports, permission decisions and visible results. Upstream has URL/local skill import and MCP server/tool management. External tools need destination-specific disclosure; local inference must never silently become network execution. |
| AI Chat | Present in source | Streaming text, saved per-model sessions, cancellation, Markdown, copy and inference controls exist. Verify current complete flows on Android. |
| Speech input | Missing | On-device recognition, explicit microphone permission, language/engine availability checks, cancellation and useful unavailable states. Never fall back to a network recognizer without disclosure. |
| Spoken replies | Missing | An offline voice, speak/stop controls, lifecycle cleanup, engine/language/voice settings and optional autoplay. Android TTS is the smallest initial implementation if network-required voices are excluded. |
| XR Language Teacher | Missing | Camera lessons, selected learning language, speech, structured vocabulary cards, persistent study material and XR device integration. Plain chat translation is only a subset. |
| Instant Media Search | Missing | A reviewed embedding runtime and exact artifact, local indexing and retrieval, photo access, index lifecycle/deletion, natural-language and image queries. A text filter over filenames does not provide semantic search. |
| Video Moment Finder | Missing | Video decoding, time-indexed embeddings, search results linked to timestamps, playback, clip trimming/export and persistent projects. Requires the embedding foundation first. |
| Ask Image | Partial | Gallery image attachment and a single-image runtime path exist. Add camera capture and task discovery. Upstream supports a broader image workflow; Mobie initializes only one image slot. Do not expose arbitrary multi-image input without runtime/memory validation. |
| Audio Scribe | Missing | Audio-bearing runtime messages, supported-model capability mapping, capture/import, validated conversion/limits, transcription/translation UI and cancellation. Speech keyboard dictation is not model audio inference. |
| Prompt Lab | Missing as a dedicated flow | Existing sampler/compute controls can be reused. Add task templates, isolated single-turn execution, editable inputs and copyable outputs with real inference measurements. |
| Mobile Actions | Missing | Model-generated structured calls mapped to a fixed allowlist, parameter validation and user-visible action previews. Upstream includes flashlight, contacts, email, maps, Wi-Fi settings and calendar. Generated text must never become arbitrary Android intents. |
| Tiny Garden | Missing | Persistent game state and a validated FunctionGemma tool-call loop. A deterministic phrase parser would be a different feature. |
| Model discovery/download/library | Present for a deliberately narrow format set | Existing Hugging Face catalog, resumable WorkManager transfers, private storage, checksum validation and deletion should be retained. Broader upstream model support requires additional reviewed adapters and capability mapping. |
| Custom model import | Missing | Storage Access Framework import with bounded copying to private storage, checksum/source identity, metadata validation, compatibility review and atomic finalization. Only approved formats may reach their runtime. |
| Benchmarks | Partial | Per-run telemetry and JSON copying exist, plus opt-in instrumentation benchmarks. Missing a user-facing repeatable benchmark flow with warm-up, fixed workload/settings and saved comparable results. Live SDK chunks must remain distinct from tokenizer tokens. |
| Local inference privacy | Present as architecture/policy | Keep local conversations local. Mobie additionally offers explicitly selected OpenCode cloud chat through the paired gateway; that is an intentional product difference and must remain disclosed. |

## Additional Android tasks beyond the README

The upstream [Scrapbook implementation](https://github.com/google-ai-edge/gallery/blob/e0bb6c1fd3e34a7ba749a6d2f9acde1b7be06b56/Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/scrapbook/ScrapbookTask.kt) initializes an interactive segmentation model. Its editor includes cutouts, brushes, backgrounds and collages. Mobie has no segmentation contract, artifact support, editor or export flow. Matching it requires a reviewed segmentation adapter and physical-device validation, not another chat prompt.

The upstream language teacher source also includes saved lessons, vocabulary images/cutouts and the Vocab Popper/Vocab Runner study games. These are absent from Mobie. The source tree includes an Example Custom Task for developer integration; that is an extension example rather than a consumer parity requirement.

## Existing code to reuse

- `ui/MobieApp.kt` owns navigation and the searchable history drawer. Add task entry points here without bypassing model selection or cloud consent.
- `ui/ChatScreen.kt` owns the composer, validated image import, Markdown/copy and run details. Its image picker uses `GetContent`; there is no camera or audio flow in the starting state.
- `ui/MobieViewModel.kt` owns model selection, download admission, runtime lifecycle, generation and history writes. Task screens must call this layer rather than native APIs.
- `core/runtime/RuntimeAdapter.kt` accepts text and an optional image path. `NativeRuntimeAdapters.kt` sets `maxNumImages` to one for the initialized vision backend. Neither currently supports audio, embeddings, segmentation or tool results.
- `core/runtime/SamplerSettings.kt`, `ComputeOptions.kt` and `ui/AdvancedComputePanel.kt` already provide sampler and compute configuration. Reuse them in Prompt Lab and benchmarks.
- `data/catalog/HuggingFaceCatalogRepository.kt` restricts runnable catalog models to text/vision `.litertlm` artifacts with known size and a generic execution target. Adding task tiles must not loosen these filters accidentally.
- `data/download/ModelDownloadWorker.kt` and `ModelDownloadManager.kt` already handle private storage, resume metadata, verification and completed-file identity. Imports must reuse equivalent validation/finalization rules.

## Delivery order

1. Add a task-oriented entry screen and a real Prompt Lab using the installed local model, isolated conversation state and existing controls. Keep unavailable capabilities explained rather than presenting dead launch buttons.
2. Complete Ask Image with camera capture through a bounded private image flow. Add offline reply playback with explicit availability and stop controls. Add on-device dictation only where Android reports an offline recognizer.
3. Add repeatable in-app benchmarks and carefully validated local model import. Retain per-run artifact/settings identity so comparisons mean something.
4. Extend the runtime contract and download metadata for audio and structured tool calls. Prove exact model behavior before enabling Audio Scribe, Mobile Actions and Tiny Garden.
5. Introduce reviewed embedding and segmentation adapters, then implement media search, video moments, Scrapbook and the full language-teacher study flow. XR support needs supported hardware and its own verification.

These are separate deliverables. Shipping steps 1 or 2 does not establish complete Gallery parity.

## Verification and release constraints

Repository `AGENTS.md` requires `./gradlew testDebugUnitTest lintDebug assembleDebug`, plus `connectedDebugAndroidTest` for navigation/UI changes. This audit itself makes no runtime or UI changes and runs no Gradle tests.

For every newly enabled native capability, record the exact artifact/hash, quantization, physical ARM64 device, Android version, peak RAM, first-token latency, tokens per second, thermal behavior and observed result. Existing emulator lifecycle tests and old phone measurements do not validate a new artifact or capability. `docs/performance-audit.md` also records a vision fixture whose nonblank response passed automation while its color description failed manual quality review. Do not treat nonempty output as image-understanding proof.

Do not add `.task`, GGUF, generic Transformers, unknown-size, embedding or audio artifacts to the existing runnable catalog to make the inventory look complete. The current product rules explicitly exclude those until their required reviewed adapters, capability policy and tests exist. New network tools, microphone/camera/media access and external speech engines each need accurate disclosure and corresponding tests.
