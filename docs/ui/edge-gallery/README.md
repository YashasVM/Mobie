# Gallery-inspired Mobie experience

Worktree: `/home/yvm/code/Mobie-edge-gallery`  
Branch: `feat/edge-gallery-experience`

This worktree began with a copy of the original checkout's uncommitted changes. The implementation below adds to that state. The original checkout was not edited.

## User-visible changes

Explore gives each supported local task a named entry point. Prompt Lab starts a new saved conversation for every run and offers freeform, summarize, rewrite, code and translation templates. Language practice starts a text tutor with the user's language, level and topic. Existing chats remain in history. Task drafts survive navigating away and returning, so a failed model load does not discard the original input.

Generation settings reuse the existing sampler and compute controls. Opening settings from a task saves choices for the next local model load; it does not reload the current chat. Model performance uses a fixed comparison prompt and displays stored speed ranges for the active artifact, backend and settings. This is a manual comparison, not a controlled benchmark.

Model discovery adds All, Fits now, Photos and Text filters, result counts, reset and query-preserving retry. Local discovery appears before cloud models. Installed and discovery rows use separate list keys, so the same model can appear in both. Vision estimates include encoder memory.

Completed replies can be read aloud with an installed offline Android voice. Dictation only uses Android's dedicated on-device recognizer on Android 12 or newer and leaves a draft for the user to review. Both stop when the app backgrounds or the chat changes. The UI explains missing services or voices.

Vision chats can ask a camera app to take a photo. Only its output file receives a temporary URI grant. The existing image validator rejects unsupported content, oversized files and excessive dimensions. Late captures cannot enter another chat. One image per message remains the runtime limit.

## Privacy and dependencies

- New permission: `RECORD_AUDIO`, requested only when the user taps dictation on a phone with an available on-device speech service.
- New provider: non-exported AndroidX FileProvider exposing only `cache/camera/` for delegated camera capture. No CAMERA or broad media/storage permission was added.
- New package-visibility queries: Android TTS and speech-recognition services.
- No new dependency, native library, model format or network destination. Existing explicit cloud selection and disclosure remain in place. These task launches choose installed local models only.
- No local chat history is copied into a task or sent to cloud by these additions.

## Remaining parity work

See [the feature inventory](../../edge-gallery-parity.md). Audio Scribe, embedding/media/video search, skills/MCP, model-driven device actions, Tiny Garden, custom model import, segmentation/Scrapbook and complete XR/vocabulary learning are not implemented. No new native inference capability is claimed.

## Verification

The root agent ran the repository's build, lint, unit and emulator checks serially. The existing interrupted-download fixture now budgets Android scheduling separately from retry completion, captures server-thread failures, cancels outstanding work and joins its server during cleanup. Its payload, range and atomic-finalization assertions remain intact. Final results are recorded below. UI screenshot capture remains blocked by the emulator failure described below. Physical speech-engine behavior and exact-model ARM64 inference are separate checks; the emulator cannot establish them.


### Results

- `./gradlew testDebugUnitTest lintDebug assembleDebug`: passed. All 552 unit tests pass.
- `ANDROID_SERIAL=emulator-5556 ./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest`: ran against the isolated Android 15 emulator. The final XML contains 85 cases: 77 passed, 7 skipped, 1 failed. The skipped cases are opt-in native-model/performance tests.
- The remaining failure is `MemoryReliefUiTest.systemNavigationOpensTheEmulatorsAvailableSettingsFallback`. Android reports Settings as resumed, but a System UI ANR dialog covers the screen. Device inspection confirmed the loaded All apps page behind “System UI isn't responding”. This is not a passing UI suite, and that assertion was not weakened.
- All new Explore, Prompt Lab, camera-import and catalog tests pass. The task settings/draft-retention smoke test and the existing chat/compute tests pass in the final emulator run.
- The earlier shared-emulator run encountered `UiAutomationService ... already registered` and `Not connected`; it was stopped, and checks moved to the isolated `cd-beta-test` emulator. The original emulator was not closed or reconfigured.
- Device automation could not dismiss the system dialog. `press` returned `Application Not Responding: com.android.systemui`; the advised recovery attempt returned `alert accept found an alert but no button that clearly matches the action`. Device interaction stopped after that failed recovery. New-screen screenshots and further visual QA are therefore incomplete.
- `git diff --check`: passed.

No physical ARM64 model, camera-app capture, installed offline voice or on-device recognizer was validated. Unit and emulator tests do not establish support for an exact model artifact on a phone. No new runtime capability was enabled.
