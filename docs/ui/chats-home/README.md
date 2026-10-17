# Chat-first redesign verification

Mobie opens at chat with the last-used installed model. The drawer searches saved conversations across models and groups them by date. Chats can be renamed or deleted. A conversation remains readable after its model is removed, with an action to find that model again. Models and Settings are secondary destinations.

The terminal palette returns from the original `d22b3f0` theme: near-black surfaces, amber accents, bundled IBM Plex Mono, and small corner radii. Startup goes directly to a quiet greeting and message composer. Model selection and chat controls sit beneath the message field. History, model discovery, and app settings stay in the drawer. Starter prompts and the first-run introduction no longer interrupt chat.

The composer accepts drafts before model selection and during loading. Selecting a model preserves the draft; opening history or starting a new chat clears it. Sending still requires a ready local runtime. Sampling settings appear first in the Controls sheet, with measured diagnostics behind Performance. Reasoning appears only when the model emits it, in an expandable reply section. The runtime has no reasoning-mode switch.

## Checks

Run on 2026-10-17 with JDK 17 and the Android 15 x86_64 `wl35` emulator:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain --max-workers=2
```

Passed: 390 unit tests, lint, APK assembly, and 55 instrumentation tests. After the last draft-restoration adjustment, unit tests, lint, and APK assembly passed again. The final APK also passed all 25 tests in `ChatHomeScreenTest` and `ModelScreensTest` through a direct AndroidJUnitRunner run on `emulator-5554`. Four opt-in tests were skipped: the physical CPU benchmark, native text lifecycle, native vision lifecycle, and streaming benchmark. No physical phone was used. No native inference performance measurements were made.

## UI evidence

These screenshots come from Compose instrumentation fixtures with synthetic conversations and a `Test Model`. They demonstrate UI states, not a loaded model or generated output.

- [Chat home while loading](mobie-chat-home.png)
- [Ready chat with greeting and composer](mobie-chat-ready.png)
- [Conversation in light mode](mobie-chat-light.png)
- [Searchable conversation drawer](mobie-chats-drawer.png)
- [Installed model picker](mobie-model-picker.png)
- [Conversation with its model removed](mobie-read-only-chat.png)
- [Home before downloading a model](mobie-chat-home-empty.png)

The production app still performs inference locally. This change adds no network destination, Android permission, dependency, or native library. Hugging Face credentials remain in Keystore-backed encrypted storage. The catalog's LiteRT-LM restrictions and download checks remain in place.
