# Terminal UI restoration

This branch restores the terminal chat from the PR #18 development line instead of the chat-home redesign in PR #35. It starts at `3972679`, the newer local version of the GPU branch rebased onto current `main` (`382b61c`), and restores the catalog and scanlines directly from `origin/perf/gpu-acceleration@5a765cb`. No remote branch history was rewritten.

The app keeps the existing model catalog, library, history sheet, terminal composer, and in-chat run settings. Auto, CPU, and GPU remain explicit choices. The GPU SDK pin is LiteRT-LM 0.18.0, with its bundled license notices.

Two corrections from the later integration survive the UI restoration:

- Runtime loading waits for saved compute preferences and hardware detection.
- A failed history reset or replacement load cannot enable prompts against the previous conversation. Only an explicit settings reload may retain the current engine after admission refuses the reload.

The chat screenshot is a synthetic UI fixture captured by `ModelScreensTest.terminalChatKeepsConversationVisibleAndOpensRunSettings`. Its GPU label is fixture state, not evidence that inference ran on the emulator.

## Chat-first launch

The one UX behaviour kept from PR #35: Mobie opens straight into chat with the last-used installed model and its current conversation, falling back to the first installed model, or to the catalog when nothing is installed. The welcome screen and starter prompts are gone. The chat's menu button opens a terminal-style drawer with new chat, chat history, models, library, and settings; back from the catalog returns to the chat. `mobie-launch-into-chat.png` and `mobie-chat-drawer.png` were captured on the emulator with a dummy model file, so the engine error in them is expected.

## Verification

JDK 17, Android 15 x86_64 emulator `emulator-5558`:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain --max-workers=2
ANDROID_SERIAL=emulator-5558 ./gradlew connectedDebugAndroidTest --console=plain --max-workers=2
```

On the final tree, unit tests, lint, debug and release assembly passed, and the emulator suite completed 62 tests with no failures; the opt-in runtime/performance probes were skipped.

The release mapping check is reproducible with:

```bash
./gradlew assembleRelease
python3 scripts/verify-litert-release.py app/build/outputs/mapping/release/mapping.txt
```

The check failed against the original release mapping because JNI classes and callbacks were removed or renamed. The new keep rule preserves the SDK package. R8-synthesized `$$ExternalSyntheticLambda` classes are always renamed and are exempt, since JNI never resolves them by name. Android CI now builds release and runs this check.

An earlier `assembleRelease` passed. R8 emitted Kotlin metadata compatibility warnings with the repository-pinned Kotlin/AGP versions. The release APK is unsigned. The installable handoff is the debug-signed APK, not a production-signed release.

## Remaining release gate

This restoration has not been qualified on a physical ARM64 phone. The historical measurements in `docs/perf/gpu-benchmark.md` describe an earlier build and cannot certify this APK. Before merging the runtime upgrade, run the exact model artifact on a physical phone and record its checksum, quantization, device, Android version, peak RAM, first-token latency, tokens per second, thermal behavior, cancellation, reset, reload, and CPU/GPU switching. Repeat the sustained reset loop after the conversation-close fix, and test the R8 build on the phone.

No new network destination, Android permission, or credential handling is introduced. Prompts and inference data stay on device. Unsupported formats remain blocked from the runtime.
