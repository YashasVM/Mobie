# Navigation and chat settings

The drawer lists saved conversations across models. Each conversation keeps its original model association. Models and Settings stay at the bottom while history scrolls. New chat is the only chat action in the drawer.

Models includes installed-model deletion, device RAM and storage, and RAM estimates beside downloadable models. Model details always show the memory breakdown, artifact information, and processing controls. Performance stats control telemetry visibility only.

Chat settings groups display speed, CPU or GPU selection, CPU threads, context, and generation options. Display speed controls how quickly text appears. CPU threads affect CPU inference; the runtime manages GPU threads. Unsupported model options explain their requirements. Reload errors remain visible in the open settings sheet.

OpenCode offerings come from the paired gateway's genuine CLI catalog. The deployed gateway returned 12 models on 2026-10-18. Account access and provider limits still apply. New catalog entries default to text-only unless their image capability has been reviewed.

Local conversations stay on the device. Opening a cloud conversation requires the disclosure and preserves that cloud conversation's own history.

## UI evidence

Screenshots use synthetic model and device fixtures.

- [Drawer](drawer.png)
- [Models and RAM estimates](models.png)
- [Unified chat settings](settings.png)

## Verification

- `./gradlew testDebugUnitTest lintDebug assembleDebug` passed. The unit suite contains 542 tests.
- `python3 -W error::ResourceWarning -m unittest discover -s server/opencode-bridge -p 'test_*.py'` passed all 16 tests.
- The Android 15 emulator passed all 45 focused tests in `MobieSmokeTest`, `ChatDrawerTest`, `ChatComputeSettingsTest`, `ComputeSettingsPanelTest`, `ModelScreensTest`, `CloudRoutingTest`, and `MemoryReliefUiTest`.

The focused run used this command:

```sh
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest --max-workers=2 -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.MobieSmokeTest,dev.yashasvm.mobie.ui.ChatDrawerTest,dev.yashasvm.mobie.ui.ChatComputeSettingsTest,dev.yashasvm.mobie.ui.ComputeSettingsPanelTest,dev.yashasvm.mobie.ui.ModelScreensTest,dev.yashasvm.mobie.ui.CloudRoutingTest,dev.yashasvm.mobie.ui.MemoryReliefUiTest
```

The full emulator suite did not pass. `CompletedInstallRecoveryTest` and `InterruptedDownloadResumeTest` timed out, and the latter's test socket server crashed after its socket closed. Those download tests passed in earlier runs. The focused run above excludes them. Physical ARM64 inference tests remain separate from UI verification.
