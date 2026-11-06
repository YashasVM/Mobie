# Free up memory implementation handoff

Worktree: `/home/yvm/code/Mobie-gpu`, branch `perf/gpu-acceleration`. All work remains uncommitted and unstaged. F resumed and completed the navigation-test fix and verification on 2026-10-13. No reset, clean, stash, commit, or write to `/home/yvm/code/Mobie` was performed.

## Resume outcome, 2026-10-13

The navigator was correct. The old test failed because `findAccessibilityNodeInfosByText("All apps")` returned an empty list on this emulator's Settings accessibility tree, even though recursive traversal found `All apps` and `dumpsys activity activities` showed `com.android.settings/.spa.SpaActivity` as `topResumedActivity`. This was reproduced twice with the actual confirm-button/navigation path. It was not a missing or translated label.

Only `app/src/androidTest/java/dev/yashasvm/mobie/ui/MemoryReliefUiTest.kt` changed in the worktree during this resume. The test now polls the resumed activity from `dumpsys activity activities`, requires its package to be `com.android.settings`, independently checks the active accessibility root's package, and traverses children for visible text before capture. It does not depend on localized Settings labels. A missing launch or wrong activity still fails. Temporary diagnostic traversal/output was removed. No production navigator, UI, minimal hook, core/device, runtime, data, or Gradle source edits were needed.

Verification used only emulator-5554, Android 15/API 35, x86_64. The physical phone `RZCY81Q06XR` was never operated on. Every connected-test Gradle command set both `ANDROID_SERIAL=emulator-5554` and `-Pandroid.injected.device.serial=emulator-5554`; every adb command specified `-s emulator-5554`. The device panel was opened for discovery/inspection and then closed before instrumentation. No UIAutomation errors occurred.

- Original isolated navigation test: 0 passed, 1 failed, 0 errors, 0 skipped. Diagnostic rerun reproduced the same failure and proved root package `com.android.settings`, search count 0, and `All apps` in recursively traversed text.
- Corrected isolated navigation test: 1 passed, 0 failed/errors/skipped; Gradle `BUILD SUCCESSFUL`.
- Full emulator suite: 42 distinct XML test cases, 36 passed, 0 failed, 0 errors, 6 skipped. All three `MemoryReliefUiTest` cases passed. The AGP console printed `Finished 48 tests`; the XML contains 42 unique cases, which are the counts reported here. The six skips are the existing opt-in CPU-thread benchmark, LiteRT E2E, GPU benchmark, GPU context probe, vision E2E, and streaming presentation benchmark. No opt-in flags or exclusions were added.
- Final `testDebugUnitTest`: 406 passed, 0 failed, 0 errors, 0 skipped.
- Final `lintDebug`: passed, 0 errors, 48 warnings.
- Final `assembleDebug`: passed.
- `git diff --check`: passed. The temporary `[DEBUG-mobie-F]` probe is absent.

The first combined command finished the full connected suite successfully but exited 1 because B's `DecodeSpeedHistoryTest` was captured mid-update and did not yet implement `keys`/`remove` or supply `runtimeVersion`. B subsequently updated those owned files. F reran only unit/lint/assemble, which exited 0. F did not edit B's files.

After F's final gates, the coordinator reported a later pass with the latest A settings integration: 407 unit tests, 0 failures/errors/skips, lint and assembly passed. That result was reported by the coordinator and is distinct from F's 406-test XML. B then started only the five ComputeSettingsPanelTest cases with both emulator selectors and `leaveApksInstalledAfterRun=true`. F performed no competing device operations.

The full emulator APK contained the two existing ComputeSettingsPanelTest cases; B's three additional panel cases were added during this run and B owns their separate five-case verification. F announced the emulator free when instrumentation ended and ran no further emulator instrumentation, installation, navigation, or uninstall afterward.

F's init script `/tmp/mobie-F.init.gradle` contains:

```groovy
allprojects { layout.buildDirectory.set(file('/tmp/mobie-F-build/' + project.name)) }
```

Commands, in execution order:

```bash
# Original reproduction, diagnostic probe, then corrected focused verification:
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 \
./gradlew --init-script /tmp/mobie-F.init.gradle connectedDebugAndroidTest \
  -Pandroid.injected.device.serial=emulator-5554 \
  '-Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.ui.MemoryReliefUiTest#systemNavigationOpensTheEmulatorsAvailableSettingsFallback'

# Full connected suite passed; initial unit compile failed during B's update:
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 \
./gradlew --init-script /tmp/mobie-F.init.gradle \
  testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest \
  -Pandroid.injected.device.serial=emulator-5554

# Successful final build checks after B's test update; no device operations:
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 \
./gradlew --init-script /tmp/mobie-F.init.gradle testDebugUnitTest lintDebug assembleDebug
```

Logs: `/tmp/mobie-F-repro.log`, `/tmp/mobie-F-probe.log`, `/tmp/mobie-F-fixed.log`, `/tmp/mobie-F-full.log`, `/tmp/mobie-F-gates.log`. UI dumps: `/tmp/mobie-F-before.xml` and `/tmp/mobie-F-repro.xml`. Reports: `/tmp/mobie-F-build/app/test-results/testDebugUnitTest`, `/tmp/mobie-F-build/app/outputs/androidTest-results/connected/debug`, `/tmp/mobie-F-build/app/reports/lint-results-debug.html`. APK: `/tmp/mobie-F-build/app/outputs/apk/debug/app-debug.apk`, outside Git.

## Design and completed behavior

- Reuse `CompatibilityResolver`, `RuntimeLoadMemoryPolicy`, and `DeviceProfileProvider`; no second RAM estimator. `MemoryReliefDecision` presents the existing fits / reclaim-warning / impossible verdict and computes only extra reclaim headroom as `max(afterReclaim - availableNow, 0)`.
- Model pre-flight shows model needs, free now, and estimated extra reclaimable RAM. The warning remains user-overridable when the existing admission policy says the model can plausibly fit. Impossible models remain blocked.
- A warning exposes **Free up memory**. Its dialog says Android doesn't let Mobie close other apps and asks the user to close apps in Recents or settings, then return. Only the confirm tap starts cleanup/navigation.
- Before navigation, the ViewModel serializes against runtime lifecycle operations, unloads idle native resources, clears runtime ownership, trims the optional catalog metadata cache, and re-reads live device memory. It does not interrupt active chat, loading, generation, or stopping. The primary action is disabled during cleanup.
- `ON_RESUME` re-reads live memory and recomputes selected-artifact compatibility for the current backend. Previous refresh jobs are cancelled so an old reading cannot overwrite a newer refresh. Once memory fits, the run action bypasses the reclaim-warning confirmation.
- Navigation attempts only exported system activities whose permissions the app holds, handles activity disappearance/security rejection, and falls back to a manual-Recents toast if nothing opens.
- No app-killing API, accessibility service, added permission, new network destination, or native-runtime implementation change was introduced by this task. No prompts/inference data are sent anywhere.
- Screenshot review found an existing misleading compatible verdict, changed from `Runs on this phone` to `Expected to fit on this phone`. This is an estimate, not a runtime claim.

## Files created

All source paths below are relative to the worktree.

- `app/src/main/java/dev/yashasvm/mobie/ui/MemoryReliefDecision.kt`: shared-verdict presentation; `from` near line 18.
- `app/src/main/java/dev/yashasvm/mobie/ui/MemoryReliefNavigator.kt`: intent candidates at line 13 and resolution/launch in `open` at line 21.
- `app/src/main/java/dev/yashasvm/mobie/ui/MemoryReliefControls.kt`: dialog/button and reclaim estimate at line 29; lifecycle observer at line 74. The zero-headroom label has a special case at line 36 because existing `formatBytes(0)` means Unknown.
- `app/src/test/java/dev/yashasvm/mobie/ui/MemoryReliefDecisionTest.kt`: six tests for fits, overridable warning, impossible block, improved-after-return, storage block, and pressure/missing state.
- `app/src/test/java/dev/yashasvm/mobie/data/catalog/ModelDetailCacheTrimTest.kt`: cache clear/repopulation behavior.
- `app/src/androidTest/java/dev/yashasvm/mobie/ui/MemoryReliefUiTest.kt`: three emulator tests for the explanation before cleanup, real lifecycle resume with improved memory, and actual system Apps navigation. Screenshot export in `capture` near line 155.

## Existing files changed by this task

- `app/src/main/java/dev/yashasvm/mobie/ui/MobieViewModel.kt`: `memoryReliefInProgress` state near line 98, refresh-job field near line 119, `refreshMemory` near line 158, live snapshot refresh near line 164, `freeUpMemory` near line 178. Other hardware/speed edits in this file belong to another agent; preserve them.
- `app/src/main/java/dev/yashasvm/mobie/ui/ModelScreen.kt`: two callback hooks near lines 91-96; memory-panel integration near line 114; run-warning decision near line 146; truthful verdict near line 277; model-needs row near line 339; relief controls near line 370; primary-action guard near line 637. Backend-picker/speed area belongs to another agent; do not overwrite it.
- `app/src/main/java/dev/yashasvm/mobie/ui/MobieApp.kt`: callback wiring at lines 169-170.
- `app/src/main/java/dev/yashasvm/mobie/data/catalog/ModelDetailCache.kt`: synchronized `clear` near line 41, a three-line hook.
- `app/src/main/java/dev/yashasvm/mobie/data/catalog/HuggingFaceCatalogRepository.kt`: `trimMemory` near line 77, a three-line hook. Other data changes belong to the runtime/data owner.

## Intents and observed resolution

Order:

1. `android.intent.action.SHOW_RECENTS`, best-effort OEM action, not a portable public Android API. No handler on this emulator.
2. `android.settings.MEMORY_SETTINGS`, best-effort OEM memory action. No handler on this emulator.
3. `Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS`, public Apps-management fallback. Resolved to `com.android.settings/.Settings$ManageApplicationsActivity` on emulator-5554 and opened from the app in the instrumentation test.
4. `Settings.ACTION_APPLICATION_SETTINGS`, public alternative. Also resolved to the ManageApplicationsActivity.
5. `Settings.ACTION_SETTINGS`, generic final fallback. Resolved to `com.android.settings/.homepage.SettingsHomepageActivity`.

Do not use `ACTION_MEMORY_CARD_SETTINGS`; it describes storage, not RAM. Public settings actions are documented at https://developer.android.com/reference/android/provider/Settings . Android's app-process restriction is documented at https://developer.android.com/about/versions/14/behavior-changes-all . The navigator never uses hidden privileged APIs, injected keys, or accessibility to open Recents or close apps.

## Previous verification history and emulator state

- Read worktree AGENTS.md first. Used only emulator-5554, Android 15/API 35, x86_64. **Never operated on physical phone RZCY81Q06XR.** Native model loading/performance remains the other agent's responsibility; no inference success is claimed here.
- A first build without ANDROID_HOME failed because this worktree has no local.properties. With `ANDROID_HOME=/home/yvm/Android/Sdk`, `./gradlew testDebugUnitTest lintDebug assembleDebug` passed. Added seven unit tests also passed.
- Explicit app install used `/home/yvm/Android/Sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`, success. Never ran installDebug.
- Initial connected run failed with a UIAutomation connection conflict, affecting new and pre-existing screenshot tests. Closed this thread's live device panel with device_close and force-stopped only the emulator's `com.callstack.agentdevice.snapshothelper` test helper. The next full connected suite passed.
- Another combined run hit a shared Gradle output collision: compressed license asset JAR already contained its entry. Avoid deleting or modifying another agent's outputs. An external init script now redirects generated build directories to `/tmp/mobie-memory-verification/<project-name>`.
- `/tmp/mobie-memory-verification.init.gradle` contains `allprojects { layout.buildDirectory.set(file('/tmp/mobie-memory-verification/' + project.name)) }`.
- Isolated combined verification before the final screenshot/copy refinements passed: all 387 unit tests, lint, APK build, and the full emulator suite. Physical/benchmark tests were skipped by their existing opt-in guards.
- **Previous handoff status, superseded by the resume outcome above:** the latest run has finished producing XML. Unit tests: 387, zero failures/errors/skips. Emulator tests: 42, **one failure**, zero errors, six opt-in skips. No matching Gradle wrapper process remains. The failure is `MemoryReliefUiTest.systemNavigationOpensTheEmulatorsAvailableSettingsFallback`, assertion `A system settings screen must open on this emulator`, at `MemoryReliefUiTest.kt:126`, after the newly added `All apps` text wait. Prior actual navigation had passed with the looser package check. Determine whether the populated Apps screen uses another accessible label or navigation failed; do not assume the text check proves intent failure. The final zero-label and lifecycle-return tests passed. The exec session ID was `41967`; do not launch another run before work resumes.

```bash
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 \
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest \
  -Pandroid.injected.device.serial=emulator-5554 \
  --init-script /tmp/mobie-memory-verification.init.gradle
```

Reports are under `/tmp/mobie-memory-verification/app/test-results/testDebugUnitTest`, `/tmp/mobie-memory-verification/app/reports/lint-results-debug.html`, and `/tmp/mobie-memory-verification/app/outputs/androidTest-results/connected/debug`.

UI screenshots use deterministic memory fixtures, not actual model loads: 8 GiB total, initially 512 MiB available / 2 GiB after reclaim, then 3 GiB available on resume. The Apps navigation test uses the emulator's real intent handler. The production ViewModel uses live DeviceProfileProvider snapshots.

The device panel remains closed to avoid competing UIAutomation clients during instrumentation. Reopen emulator-5554 with device_open after the test runner is finished if desired. Do not use the physical phone.

## Refreshed screenshots

All four host copies were pulled from the successful full F emulator run and visually inspected on 2026-10-13:

- `/home/yvm/code/Mobie-research/memory-ui/memory-warning.png`: memory gap and Free up memory action.
- `/home/yvm/code/Mobie-research/memory-ui/memory-explanation.png`: honest explanation and explicit confirm button.
- `/home/yvm/code/Mobie-research/memory-ui/memory-after-return.png`: `0 MB` estimated additional reclaimable RAM, `3.0 GB` free now, `Expected to fit on this phone`, no Free up memory action.
- `/home/yvm/code/Mobie-research/memory-ui/memory-system-apps.png`: populated real Settings `All apps` screen, not a splash.

These are deterministic UI fixtures plus real Settings navigation. They do not establish model/runtime support or physical-phone inference performance. No production UI changed during this resume; the stale previous screenshot evidence was replaced.

F's outstanding navigation-test failure and stale screenshot follow-ups are resolved. Keep the emulator free for B's separately coordinated panel tests. Runtime/device claims and final integration review remain with their owners.
