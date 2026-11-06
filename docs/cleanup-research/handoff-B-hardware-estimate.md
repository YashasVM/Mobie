# Hardware detection and decode estimates handoff

Updated by resumed agent B on 2026-10-13. All six review fixes and final verification are complete. B made no commits. The coordinator has since captured work in checkpoints e501f06 and 2242e75; continue editing normally without reset/stash/commit/push.

## Worktree and ownership

- Worktree `/home/yvm/code/Mobie-gpu`, branch `perf/gpu-acceleration`.
- No reset, clean, stash, commit or writes to `/home/yvm/code/Mobie`.
- The six-fix turn edited `core/device`, `ui/ComputeSpeedPresentation.kt`, the compute panel in `ui/ModelScreen.kt`, their tests, and this handoff. The later Advanced task also edits the UI files and narrow compute hooks listed in the section below.
- A owns runtime, data, Gradle and the physical phone. B requested and A implemented settings/ViewModel thread-clamp wiring in those owned files. F owns memory-relief UI.
- No physical-phone access. Emulator-only checks must use `ANDROID_SERIAL=emulator-5554` AND Gradle's `-Pandroid.injected.device.serial=emulator-5554`; every adb command must use `-s emulator-5554`.
- F runs the full emulator suite. B runs only `dev.yashasvm.mobie.ui.ComputeSettingsPanelTest`, after checking `adb -s emulator-5554 shell ps -A | grep -i instrument` and coordinating with F. Never uninstall/wipe the emulator. Use `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
- B's Gradle init script `/tmp/mobie-B.init.gradle` contains `allprojects { layout.buildDirectory.set(file('/tmp/mobie-B-build/' + project.name)) }`. Always pass it.

## Review fixes

1. Missing cpufreq now falls back to SoC/core-count/RAM class assumptions, with wider bandwidth and efficiency ranges. Every known hardware profile with known artifact size gets a CPU estimate. GPU estimates still require GPU information and the OpenCL library hint. `DecodeSpeedConfidence.LOW` renders as `estimate · lower confidence`. Valid measured Stats replace estimates even with missing hardware/size.
2. New `CpuThreadPolicy` clamps explicit threads to `1..min(8, detected cores)` and preserves null as Auto. The panel offers only that many buttons and clamps a restored selection visually, falling back to Android's available processor count before hardware detection. A integrated the same policy into `RuntimeComputeSettings` read/write and ViewModel detection/change/load wiring; restored oversize settings are persisted after sanitation. A owns the corresponding `RuntimeComputeSettingsTest`.
3. Generic ANGLE is treated as hardware. SwiftShader, llvmpipe, softpipe, software rasterizer, swrast and lavapipe remain software, including ANGLE wrapping SwiftShader.
4. History keys include device fingerprint, `BuildConfig.LITERT_LM_VERSION`, schema version and artifact/backend identity. Schema is currently 2. Recreation discards old unversioned or other-generation measurements rather than relabelling them as current.
5. History retains at most 64 artifact/backend entries globally, each with eight samples. A persisted LRU index tracks reads and writes. Recreation recovers unindexed writes after process death, prunes legacy keys and trims oversize stores. Android preference access remains on IO under the existing mutex.
6. Calibration uses bandwidth divided by weight traffic, with no phone/model throughput table. The high hardware class uses 55–80 GB/s theoretical shared bandwidth, 1.2–1.5 package-byte passes per token, CPU effective fractions 5–6%, GPU 20–22%. The fractions include kernel/dequantization/dispatch overhead and are not measured DRAM utilization. For Snapdragon 8 Gen 3, Adreno 750, 8 cores, 3.39 GHz and 12 GiB with the exact Qwen dynamic artifact, the prediction is CPU 5.319–11.605 and GPU 21.276–42.553 tokens/s. Those ranges contain the supplied CPU 6–8 and GPU about 30 measurements. B did not reproduce phone measurements.

## Exact artifact

- Repo `litert-community/Qwen3-0.6B`, revision `a6e678a1d43b3d67452aca8b3f4f9aa96e6c750b`.
- File `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`.
- Size **344,671,744 bytes**.
- SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`.
- Do not substitute the distinct 497,516,544-byte mixed INT4 artifact or the separate Qwen3-0.6B-int4 repository artifacts.

## Files B changed in this resumed turn

Under `app/src/main/java/dev/yashasvm/mobie/`:

- `core/device/HardwareProfile.kt`
- `core/device/CpuThreadPolicy.kt` (new)
- `core/device/DecodeSpeedEstimator.kt`
- `core/device/DecodeSpeedHistory.kt`
- `core/device/DecodeSpeedStore.kt`
- `ui/ComputeSpeedPresentation.kt`
- `ui/ModelScreen.kt` (compute section only)

Under `app/src/test/java/dev/yashasvm/mobie/`:

- `core/device/HardwareProfileParserTest.kt`
- `core/device/CpuThreadPolicyTest.kt` (new)
- `core/device/DecodeSpeedEstimatorTest.kt`
- `core/device/DecodeSpeedHistoryTest.kt`
- `ui/ComputeSpeedPresentationTest.kt`

Instrumentation: `app/src/androidTest/java/dev/yashasvm/mobie/ui/ComputeSettingsPanelTest.kt`, now five tests. Added four-core/one-core limits and lower-confidence/measurement precedence checks.

## Verification

Final full gates passed after A's settings/options/ViewModel integration and the instrumentation-import fix. XML reports **407 unit tests, 0 failures, 0 errors, 0 skips**. Lint passed with **0 errors and 48 warnings**. Debug assembly passed. B's five owned unit-test classes contain **30 tests**, all passed.

The single panel instrumentation class passed on **emulator-5554 only: 5 tests, 0 failures, 0 errors, 0 skips**. Gradle reported `BUILD SUCCESSFUL in 33s`. No UiAutomation conflict, instrumentation overlap, emulator wipe, manual uninstall, or physical-phone access occurred. The first connected attempt failed at compilation due to an invalid `assertDoesNotExist` import; B removed it and reran successfully. No instrumentation started in the failed attempt.

The earlier first full build took 3m25s with 396 unit tests. A's additions were captured by a subsequent full run with 407 tests, completed in 32s. After removing the invalid Android-test import, the final full command passed again in 8s with the same 407-test XML result. F's separate full-suite result is owned/reported by F, not claimed as B's run.

Commands:

```bash
ANDROID_HOME=/home/yvm/Android/Sdk ./gradlew --init-script /tmp/mobie-B.init.gradle testDebugUnitTest lintDebug assembleDebug
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 ./gradlew --init-script /tmp/mobie-B.init.gradle connectedDebugAndroidTest -Pandroid.injected.device.serial=emulator-5554 -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.ui.ComputeSettingsPanelTest
```

Logs: `/tmp/mobie-B-gradle.log`, `/tmp/mobie-B-gradle-final.log`, `/tmp/mobie-B-connected.log`. Outputs: `/tmp/mobie-B-build/app/`. Final `git diff --check` passed. The initial fresh build reports existing Kotlin warnings and incomplete NDK `27.0.12077973` metadata; JNI libraries are packaged without stripping. The connected log has an emulator-console warning, but all five tests passed.

UI evidence, ignored by Git and visually inspected:

- `/home/yvm/code/Mobie-gpu/captures/compute-speed-emulator.png`, refreshed live app capture. Detected `ranchu`, Google SwiftShader, **4 CPU cores**, 2.4 GB RAM; CPU **0.5–7 tokens/s**, labelled **estimate · lower confidence**; GPU is unavailable with the software-renderer reason; thread choices are Auto plus **1–4**. Used actual public Qwen catalog metadata, with no model download or inference.
- `/home/yvm/code/Mobie-gpu/captures/compute-speed-lower-confidence-fixture.png`, pulled from the passing Compose test. This is explicitly a fixture, including a supplied measured GPU sample, not live emulator inference proof.

## Device tools

Current emulator panel launcher:

```bash
/home/yvm/.t3/userdata/device/bin/agent-device <command> \
  --platform android --serial emulator-5554 \
  --config /home/yvm/.t3/userdata/device/hosts/25bf8e1a2393f1108d37029b.json \
  --session t3-230449aa7a9a3ec781f917ec
```

Reopen through `device_list`/`device_open` in a new thread. Do not interact while F instrumentation is active. The refreshed live screenshot in `captures/compute-speed-emulator.png` shows the corrected fallback and four-core picker.

## Requests for A

No remaining request blocks these six fixes. A's settings clamp, ViewModel refresh/change/load clamp and options core-count sanitation are present and covered in the passing 407-test run. A owns final runtime integration, physical-device benchmarks and any later changes. The subsequent Advanced-panel task is implemented below. Inference stays local; B added no network destination, permission, native dependency, or credential handling.


## Advanced panel task, 2026-10-13

Implementation and initial verification are complete. A final repeat of the gates is running against A's newer shared runtime lifecycle edits; fill in the final result below before handing back.

The primary panel shows Auto/CPU/GPU, hardware-derived CPU/GPU speed labels, and the actual runtime fallback reason in plain words. Everything else is under a collapsed Advanced disclosure:

- CPU threads, Auto or 1 through min(8, detected cores).
- Context size, only `ComputeCapabilities.contextChoices()` plus Auto, with the artifact maximum shown. Before the first load, the UI asks the user to run the artifact once to read its limits. It does not infer dynamic context support from a filename.
- Output cap 1–1024 tokens, shared by reasoning and visible output.
- Thinking Model default/Off/On and bounded budget presets, only for metadata-supported models.
- Activation Auto/FP16/FP32. Overrides retain the exact requested experimental warning.
- Speculative decoding Model default/Off/On, only when the runtime metadata advertises a drafter, with the same experimental warning.
- Prefill preface Off/On, with its restored-history behavior explained.
- Sampler temperature 0–2, top-k 1–64, top-p above 0 and at most 1, and 32-bit seed. Each field has an explanation and inline validation.
- Reset to recommended clears the overrides and restores the sampler defaults, including invalid local field text.

Valid edits persist in the existing `runtime_compute`/`sampler_settings` layers but never mutate the loaded engine. The UI tracks a separate load snapshot. A single Apply & reload action appears for pending changes; it is disabled for generation, stopping, loading, absent downloads and invalid fields. The ViewModel independently guards the action before claiming the lifecycle. Explicit apply reloads with the saved conversation. New-chat/history resets retain the currently applied configuration rather than silently applying pending edits. Per-send sampler values come from the applied snapshot.

Chat's run-details area now opens the same compute settings dialog; the prior separate sampler editor was moved into Advanced. All options go through `RuntimeAdapter.configureCompute` and `configureSampler` before load. B added no native-runtime branch or direct SDK call.

The last successfully loaded artifact's capability metadata is cached as exactly one bounded settings entry. Its identity includes the artifact source/checksum/size and pinned runtime version, so the same artifact's settings remain available after process recreation. Other artifacts/runtime versions and incomplete cache records are ignored. Cached metadata is not phone qualification.

### Advanced files

Main UI sources:

- `ui/AdvancedComputePanel.kt`: disclosure, option controls, inline numeric validation, reset and shared chat dialog.
- `ui/AdvancedComputeSettings.kt`: pure codec/sanitization/mapping, existing-settings extensions, bounded capability metadata cache and apply policy.
- `ui/ModelScreen.kt`: simpler primary compute surface, fallback reason, Advanced integration and one apply action.
- `ui/MobieViewModel.kt`: desired/applied option state, persistence, capability handoff, guarded reload and load snapshot mapping.
- `ui/MobieApp.kt`: model callbacks and chat-dialog routing.
- `ui/ChatScreen.kt`: replace the separate sampler editor with the compute-dialog entry point.

Tests:

- `ui/AdvancedComputeSettingsTest.kt`: 8 pure unit tests for complete mapping, malformed/default values, bounds, artifact support/context choices, sampler sanitation, runtime/artifact cache invalidation, busy-state apply guards and reset/pending comparisons.
- `ui/ComputeSettingsPanelTest.kt`: now 10 cases, including the previous hardware/speed/core-count tests and 5 added Advanced cases. Covers collapse/expand, unsupported controls, experimental warnings, fixed/dynamic context choices, explicit reload, generation blocking, invalid sampler input, reset and real settings recreation/read sanitation.

### Runtime edits and requests

**B edited no `core/runtime` file.** B requested a trivial settings preferences accessor; A exposed the existing `RuntimeComputeSettings.preferences` as internal, and B's UI-owned extensions use it. There is no new preferences store or dependency.

A still owns physical-device verification of apply/reload, activation and drafter experiments. The UI cannot mark an experiment phone-qualified without an artifact/backend/device-specific qualification record, which the runtime does not currently expose. Until such a record exists, experimental controls remain labelled untested on this phone. No new speed claim was made by B.

### Advanced verification

Initial final-order gate command passed in 27s: **415 unit tests, 0 failures/errors/skips; lint 0 errors, 48 warnings; assembleDebug passed**.

Then the panel class passed on **emulator-5554 only, 10 tests, 0 failures/errors/skips**, `BUILD SUCCESSFUL in 39s`. No instrumentation conflict occurred. Before the run, B closed its own agent-device session to release its snapshot instrumentation helper; no other agent's runner was stopped. B did not uninstall or wipe the emulator, run the full connected suite, download a model, run inference, or touch the physical phone.

```bash
ANDROID_HOME=/home/yvm/Android/Sdk ./gradlew --init-script /tmp/mobie-B.init.gradle testDebugUnitTest lintDebug assembleDebug
ANDROID_HOME=/home/yvm/Android/Sdk ANDROID_SERIAL=emulator-5554 ./gradlew --init-script /tmp/mobie-B.init.gradle connectedDebugAndroidTest -Pandroid.injected.device.serial=emulator-5554 -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.ui.ComputeSettingsPanelTest
```

Logs: `/tmp/mobie-B-advanced-gates.log`, `/tmp/mobie-B-advanced-connected.log`. Final shared-tree repeat log: `/tmp/mobie-B-advanced-final-gates.log`, pending. Test reports remain under `/tmp/mobie-B-build/app/`. `git diff --check` passed.

### Advanced UI evidence

Screenshots are in `/home/yvm/code/Mobie-research/advanced-ui/`:

- `live-collapsed.png`: real emulator profile, four cores, 0.5–7 CPU tokens/s lower-confidence estimate, software GPU unavailable, Advanced collapsed.
- `live-expanded.png`: real app showing unknown context limits, output cap, activation experimental warning and prefill preface.
- `live-sampler.png`: real app showing the sampler and reset control.
- `collapsed-fixture.png`, `context-fixture.png`, `experiments-fixture.png`, `reload-generating-fixture.png`, `sampler-reset-fixture.png`: captures from the passing Compose fixtures. Mock metadata/active-compute values in these fixtures are UI coverage, not native inference proof.

The collapsed, experiment and generation-blocked fixture screenshots and the live expanded screenshot were visually inspected. The snapshot helper was closed after evidence capture so it cannot conflict with another agent's instrumentation run. The device panel may remain open for viewing.

## H1: estimate unification

Pre-download, compute-panel and pre-load RAM now come from the same estimator as the native load gate, `RuntimeLoadMemoryPolicy.estimate(...)`, including the GPU graphics allowance from A2. `CompatibilityResolver` already imported that policy, so this adds no new package dependency. The legacy `estimateLiteRtRuntimeMemory` is no longer called from `core/device`.

- `CompatibilityResolver.resolveBackend(...)` returns a `BackendAdmission(backend, result)`. `resolve(...)` keeps its old parameters and defaults, adds `allowReclaim` with a default of false, and delegates to it. Its default backend stays CPU, so existing callers keep the same numbers.
- AUTO mirrors the load gate. GPU is chosen only when the device may attempt GPU and GPU is admitted now, or after reclaim when `allowReclaim` is true. Otherwise CPU is chosen and the reason begins `GPU needs about X GB ...so Auto uses CPU.`
- Total RAM decides whether a backend can ever fit. Current RAM decides the warning. Those rules are unchanged.
- `DeviceProfile.gpuAttemptable` is set from `LiteRtVisionBackendPolicy.shouldAttemptGpu`, the same check the runtime uses. The default is false, so Auto never quotes GPU for an unknown device.
- The catalog, installed rows and model verdict use the selected backend, and the compute panel shows RAM for each backend row and an Auto line with its pick.
- Runtime, `RuntimeLoadMemoryPolicy`, and the load gate are unchanged.

Known limit: explicit context overrides still pass through `LiteRtContextWindowPolicy.select` in the resolver, which can shrink an explicit context that the load path would keep. This was not changed here.

Tests: `CompatibilityAutoBackendTest` covers GPU-not-fit/CPU-fits, both fit, neither fits (total and current RAM), accepted reclaim, GPU impossible by total RAM, context 1024 versus 4096, and `gpuAttemptable=false`. `ComputeSpeedPresentationTest` covers the RAM and Auto labels.

## H3: UI review fixes

Code-review findings on the advanced compute UI, checked against the code before fixing. Every finding was confirmed; none was skipped.

1. **No reload prompt with nothing loaded.** `AdvancedComputePolicy.needsApply` returns false when no engine options exist. The panel shows "Saved. Used next time you load a model." and no reload button. "Apply & reload" appears only when an engine is loaded with different options. Test: `saved choices with nothing loaded never ask for a reload`, plus `savedNonDefaultChoicesWithNothingLoadedNeverOfferAReload` in the Compose suite.
2. **Refused Apply leaves consistent state.** The refusal restores chatting, runtime state, active compute and stats to their pre-Apply values, and the error names the engine still running: "Kept running on CPU. <reason>". A failed reload also stays READY when the adapter still reports an active engine. Otherwise it is ERROR as before. Tests: `a refused reload keeps the running engine and names it`, `a refused reload with nothing loaded returns to idle with only the reason`. Runtime hook: this assumes a refused reload leaves `RuntimeAdapter.activeCompute` set to the old engine. The runtime agent should confirm that.
3. **Artifact limits no longer overwrite saved settings.** `updateComputeOptions` saves only the explicit edit. Load success writes nothing back. `MobieUiState.effectiveComputeOptions` is the artifact-limited view, and pending changes compare against it. Tests: `artifact limits are a view over saved choices, so a saved thinking override survives another artifact`.
4. **Prefill preface is labelled experimental.** It carries the same "Experimental — not yet tested on this phone" line. Activation keeps its label and default Auto. A hint sits under activation: "Measured once: FP16 ran about 7% slower than Auto on Qwen3-0.6B on a Galaxy S24 Ultra, with the same output. FP32 is untested." It is scoped to that phone and model. Known gap: at the phone's font size the hint wraps to three lines, so it is not the one-line hint the brief asked for. Shortening it needs a copy decision.
5. **Measurements are keyed by thread count and context size.** `DecodeSpeedHistory` stores settings groups per artifact and backend, up to four groups with eight runs each. CPU groups use thread count and context. GPU groups ignore thread count. Schema is bumped to 3, so schema 2 entries are removed. `matchDecodeMeasurement` returns an exact match, or the nearest same-backend run labelled "measured at different settings", or nothing, which falls back to the estimate. Tests: `DecodeSpeedHistoryTest` settings and matching cases, `measurements from other thread or context settings are labelled as such`, and `speedRowsNameMeasurementsFromOtherSettingsInsteadOfClaimingThisPhone`.
6. **Preference read moved off the main thread.** The view model starts with recommended defaults. Hardware detection and `readAdvancedOptions` run on `Dispatchers.IO` in `init`. A result never overwrites an edit made before it arrives. Not unit-tested: the view model needs an `AppContainer` with a real `Context`. The same init still reads token, sampler and welcome preferences on main; those were out of scope and are unchanged.
7. **Recommendation follows the Auto backend.** `selectBestArtifact` ranks with `resolveBackend(..., AUTO)`. Test: `ArtifactRecommendationBackendTest`. The fixture is one where the CPU RAM ordering and the Auto (GPU) ordering disagree.

Files changed: `core/device/CompatibilityResolver.kt` (one line in `selectBestArtifact`), `core/device/DecodeSpeedHistory.kt`, `core/device/DecodeSpeedStore.kt`, `ui/MobieViewModel.kt`, `ui/ModelScreen.kt`, `ui/AdvancedComputePanel.kt`, `ui/AdvancedComputeSettings.kt`, `ui/ComputeSpeedPresentation.kt`. Tests: `DecodeSpeedHistoryTest`, `AdvancedComputeSettingsTest`, `ComputeSpeedPresentationTest`, new `ArtifactRecommendationBackendTest`, `ComputeSettingsPanelTest`, and `HardwarePerformanceTest` (androidTest, moved to the new store API).

Gates (`/tmp/mobie-H3.init.gradle`, build dir `/tmp/mobie-H3-build`):
- `testDebugUnitTest lintDebug assembleDebug`: BUILD SUCCESSFUL. 468 unit tests, 0 failures, 0 errors. An earlier run had three failures in runtime-owned tests (`CompatibilityAutoBackendTest`, `ComputeBackendPolicyTest`, `RuntimeLoadMemoryPolicyTest`) while the runtime work was in progress. They passed on the final run.
- `connectedDebugAndroidTest` on `emulator-5554` with `ComputeSettingsPanelTest`: BUILD SUCCESSFUL. 13 tests, 0 failures. The physical phone was not used.

Screenshots in `advanced-ui/` refreshed from the emulator run: `collapsed-fixture.png`, `context-fixture.png`, `experiments-fixture.png`, `reload-generating-fixture.png`, `sampler-reset-fixture.png`. Added `compute-speed-panel-fixture.png` and `compute-speed-lower-confidence-fixture.png`. The `live-*.png` captures from a real app run were not refreshed. They predate the prefill label and FP16 hint and need a live capture.

Not changed: "Free up memory" lives only in the non-chat model screen, so the silent return in `freeUpMemory` while chatting is no longer reachable once chat and runtime state agree. The guard was left as is.
