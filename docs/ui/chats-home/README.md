# Chat-first redesign verification

Mobie opens at chat with the last-used installed model. The drawer searches saved conversations across models and groups them by date. Chats can be renamed or deleted. A conversation remains readable after its model is removed, with an action to find that model again. Models and Settings are secondary destinations.

The shared theme uses proportional body text, rounded controls, warm neutral colors, and the new `m.` mark. Markdown parsing lives separately from chat presentation. History serialization and encrypted credential access run on IO; captured writes retain their session identity and finish their disk commit before returning. Scrolling preserves the reader's position and resumes following when they send another prompt.

## Checks

Run on 2026-10-17 with JDK 17 and the Android 15 x86_64 `wl35` emulator:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain --max-workers=2
```

Passed: 390 unit tests, lint, APK assembly, and 48 instrumentation tests. Four opt-in tests were skipped: the physical CPU benchmark, native text lifecycle, native vision lifecycle, and streaming benchmark. No physical phone was used. No native inference performance measurements were made.

## UI evidence

These screenshots come from Compose instrumentation fixtures with synthetic conversations and a `Test Model`. They demonstrate UI states, not a loaded model or generated output.

- [Chat home while loading](mobie-chat-home.png)
- [Searchable conversation drawer](mobie-chats-drawer.png)
- [Conversation with its model removed](mobie-read-only-chat.png)
- [Home before downloading a model](mobie-chat-home-empty.png)

The production app still performs inference locally. This change adds no network destination, Android permission, dependency, or native library. Hugging Face credentials remain in Keystore-backed encrypted storage. The catalog's LiteRT-LM restrictions and download checks remain in place.
