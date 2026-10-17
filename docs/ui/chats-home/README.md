# Chat-first redesign verification

Mobie opens at chat with the last-used installed model. The drawer searches saved conversations across models and groups them by date. Chats can be renamed or deleted. A conversation remains readable after its model is removed, with an action to find that model again. Models and Settings are secondary destinations.

The terminal palette returns from the original `d22b3f0` theme: near-black surfaces, amber accents, bundled IBM Plex Mono, and small corner radii. Startup goes directly to a quiet greeting and message composer. Model selection and chat controls sit beneath the message field. History, model discovery, and app settings stay in the drawer. Starter prompts and the first-run introduction no longer interrupt chat.

The composer accepts drafts before model selection and during loading. Selecting a model preserves the draft; opening history or starting a new chat clears it. Sending still requires a ready local runtime. Controls now includes Auto, CPU, and GPU selection, the actual active processor, fallback reasons, and an explicit Apply action. Advanced settings include CPU threads, sampling, and capability-gated thinking controls. Saving a choice does not interrupt generation; Apply is disabled during generation, loading, and stopping. Reasoning emitted by the model remains expandable in its reply.

## Checks

Run on 2026-10-17 with JDK 17 and the isolated Android 15 x86_64 `ghw_demo` emulator (`emulator-5558`):

```bash
ANDROID_SERIAL=emulator-5558 ./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain --max-workers=2
```

Passed: 509 unit tests, lint, APK assembly, and 76 instrumentation tests. Seven opt-in tests were skipped: CPU and GPU benchmarks, native text and vision lifecycle tests, GPU context and lifecycle probes, and the streaming benchmark. No physical phone was attached. No new native inference performance measurements were made.

An earlier run on the shared `wl35` emulator hit three `UiAutomation` registration/connection failures in system-settings tests. The complete suite passed on the isolated emulator without those failures.

## UI evidence

These screenshots come from Compose instrumentation fixtures with synthetic conversations and a `Test Model`. They demonstrate UI states, not a loaded model or generated output.

- [CPU and GPU selection in chat Controls](mobie-chat-controls.png)
- [Chat home while loading](mobie-chat-home.png)
- [Ready chat with greeting and composer](mobie-chat-ready.png)
- [Conversation in light mode](mobie-chat-light.png)
- [Searchable conversation drawer](mobie-chats-drawer.png)
- [Installed model picker](mobie-model-picker.png)
- [Conversation with its model removed](mobie-read-only-chat.png)
- [Home before downloading a model](mobie-chat-home-empty.png)

The GPU integration includes draft PR #18 and upgrades the pinned LiteRT-LM Android SDK from 0.16.1 to 0.18.0, including its matching license notices. The historical [GPU benchmark report](../../perf/gpu-benchmark.md) records the original phone measurements; this integration has not been benchmarked on a physical phone. No new performance claim is made.

The production app still performs inference locally. This change adds no network destination or Android permission. Hugging Face credentials remain in Keystore-backed encrypted storage. The catalog's LiteRT-LM restrictions and download checks remain in place.
