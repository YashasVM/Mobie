# Mobie developer-demo feature plan

Prepared 2026-10-11. Budget: about one week. These are proposals, not implemented or device-validated features. Estimates assume one developer familiar with the repository.

## Recommendation

Build task-based model auditions, then add a text share target. Position Mobie as the app that helps someone choose a local model for their task on their phone.

Using LiteRT-LM is compatible with making a substantial engineering contribution. Mobie already contains device-fit policies, authenticated artifact discovery, verified resumable downloads, history restoration, lifecycle recovery, and thermal protection. Those are visible in the repository; physical handset results are still needed before making performance claims.

## Current competitor baseline

Google AI Edge Gallery advertises chat, image/audio tasks, prompt templates, agents, device actions, model import, and performance benchmarking in its [official README](https://github.com/google-ai-edge/gallery). Its [1.0.20 release](https://github.com/google-ai-edge/gallery/releases/tag/1.0.20), published 2026-10-06, adds local media search and video moment finding. Adding one of these alone is a weak differentiation claim.

[PocketPal](https://github.com/a-ghorbani/pocketpal-ai) also documents Hugging Face discovery, tools, configurable assistants, and speed/memory benchmarks. [PocketSage](https://github.com/umarpazir11/pocketsage) and [Airgap](https://github.com/skmdroid/airgap) advertise local document retrieval and citations. These are first-party product claims, not features we tested. No proposal here is claimed to be globally unique.

See [competitor research](demo-competitor-research.md) for the broader source comparison.

## 1. Task-based model auditions

Estimated feature effort: 3 developer days, followed by device validation. Best fit for a developer audience and the current model-browser mission.

User flow:

1. Pick a task and two installed, compatible text models.
2. Use the same input, task instructions, fresh conversation, and output budget for each model. Load and unload sequentially; two native engines need not coexist.
3. Show anonymous answers A and B. Let the user choose the more useful answer before revealing model names.
4. Reveal explicit task-check results alongside measured first-token time, generation time, output token count, and sampled app memory.
5. Save the user's preferred model for that task locally.

Start with a small, versioned suite using synthetic event notes:

- Extract a title, room, and start time into a specified JSON structure. Check valid JSON, required keys, and exact expected values.
- Answer a question whose answer is explicitly present in a note. Check the expected fact with a documented normalization rule.
- Answer a question the note cannot resolve. Show the expected abstention behavior and evaluate it explicitly.
- Summarize the note. Let the user assess usefulness; do not score prose quality with word count or an unvalidated heuristic.

The contribution is evaluation design, transparent checkers, sequential runtime orchestration, local task preferences, and reproducible run records. Gallery already has benchmarks; task correctness plus blind preference is the proposed product angle, not raw tokens per second.

Record model artifact identity/checksum when available, quantization, runtime/app version, task-suite version, input identity, settings, device/Android version, and thermal state. Keep cold load separate from generation. Treat the existing `InferenceStats.ramBytes` as sampled app memory, not peak RAM or model-only memory. Collect peak memory separately for physical-device reports.

Repeat runs during development and alternate model order to inspect warm-cache and heat effects. Fixed settings do not guarantee deterministic outputs. A tiny suite only supports conclusions about those tasks. Let users rerun or override a preference. Do not automatically recommend an untested artifact before download.

MVP additions should sit above `RuntimeAdapter`; UI must not call LiteRT directly. Keep run records app-private, avoid uploading inputs/outputs, and do not create a public leaderboard in this version.

Demo: extract event details from a note, compare anonymous answers, reveal which checks passed and how each model performed, then use the chosen model for another note. Use actual outcomes; do not script a supposedly better model winning.

## 2. Share text to Mobie

Estimated effort: 1 developer day. Recommended companion to auditions.

Register a text share target, preview incoming text, then offer a bounded action such as extracting event details or rewriting a message. Use the locally saved task preference when available, otherwise let the user choose an installed compatible model. Show structured output as an editable card with copy/share controls.

This connects model selection to daily use. Prompt templates alone are already common. The Android integration, input handling, result validation, and task preference are the contribution. Follow [Android's receiving shared data contract](https://developer.android.com/develop/ui/compose/sharing/receive).

For this week, accept actual shared text only. A shared URL is not the article body; display that limitation instead of silently fetching pages. No clipboard monitoring or automatic sending of generated messages. If load/generation fails, retain the draft and offer recovery.

## 3. A cited local notes workspace

Estimated effort: 3 to 5 developer days. Alternative main feature for a consumer audience, not an addition to the entire audition plan.

Users save a few text/Markdown notes, ask questions, and tap citations to inspect the original passages. Start with bounded lexical retrieval and source identifiers owned by the app. This avoids adding an embedding runtime that the v1 rules currently exclude. Label weak matches; show retrieved passages even when the model cannot produce a reliable answer.

Your contribution is note ingestion, retrieval, source mapping, context budgeting, and delete/export controls. Citations must resolve to real passages, but citation existence alone does not prove an answer is supported. Test unsupported questions and conflicting notes; do not promise hallucination-free answers.

Demo: answer where an event session takes place, open the supporting passage, then ask about a fact absent from the notes. This is a practical workflow, not a claim to invent offline RAG. Defer PDF parsing, OCR, whole-device search, and arbitrary file formats.

## 4. Memory users can inspect and control

Estimated effort: 2 to 3 developer days. Alternative smaller main feature.

Expose which prior turns will be restored, which were omitted, and the approximate context budget. Let users save a small editable fact card for a workspace, preview it before a new chat, and explicitly include or remove it. Budget that card with the prompt and history; rebuild conversations through the runtime interface when needed.

Mobie already bounds history in `ConversationHistoryPolicy` and prompt/output admission in `GenerationContextPolicy`. This turns that hidden policy into user control. A saved transcript and the native model's active context are different states; make that difference visible. Avoid automatic, hidden memory summaries for the first version.

Demo: save an event-room preference, restart the app, show exactly what is included, remove the preference, then start a fresh conversation. Test influence with suitable tasks instead of promising reliable long-term model memory.

## Improve the current foundation

- Explain compatibility in actionable copy: whether the model can fit at all, whether closing other apps might help, and what consumes storage.
- Make existing thermal restrictions visible: explain when heat shortens an answer or pauses generation. Surface OS thermal state rather than inventing a temperature or battery-saving percentage.
- Preserve prompts across failed load/generation and clearly show Stop, retry, and reload behavior.

## One-week sequence

- Day 1: validate two exact compatible text artifacts on the physical demo phone. Define the task suite and baseline results. If this gate fails, use the smaller share-to-Mobie workflow with one validated model.
- Days 2 to 4: implement auditions, anonymous answers, explicit checkers, and local task preferences. Record enough metadata to reproduce each run.
- Day 5: add the bounded text share flow and editable result card.
- Day 6: run regression checks and physical-device testing. Cover cancellation between models, unload/load failure, low memory, heat, malformed output, share cold start, and restored drafts.
- Day 7: fix only demo blockers and rehearse. Predownload the artifacts and prepare a clearly labeled recording of an actual device run as a fallback.

Required implementation checks: `./gradlew testDebugUnitTest lintDebug assembleDebug`, plus `connectedDebugAndroidTest` for UI/navigation changes. These were not run for this research-only task. Record the exact model, quantization, device, Android version, peak RAM, first-token latency, throughput, thermal notes, and result for physical ARM64 runtime claims.

## Demo pitch

"Mobie helps you choose a local model for a job. It tests the models on your phone, shows which task checks passed, and lets you use your preferred model on text shared from other apps."

After downloading, demonstrate the workflow offline. Every prompt and output remains local. Keep the current LiteRT-LM artifact restrictions; new runtime support and a conversion backend are separate projects with much larger validation scope.
