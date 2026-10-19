# GOD MODE implementation checkpoint

## Scope

One open PR: #36, titled GOD MODE. Existing terminal UI/GPU work is preserved. Main 67ed794's download cancellation, empty-draft retention and release JNI fixes were integrated with a real merge parent rather than discarded or duplicated. No branch was deleted.

## Current implementation

- A conservative runtime conversation-reconfiguration API can retain initialized model weights when only sampler/output/thinking/conversation controls change. Backend, threads, context, activation and artifact identity remain engine-level gates. The API closes the old conversation before allocating a replacement; failure blocks generation until recovery.
- First PSS/battery sampling no longer suffers signed timestamp overflow.
- Benchmark peak sampling uses one monitor instead of two simultaneous PSS scans.
- Benchmark rows identify APKs, model, prompt, runtime, device and configuration. Summary scripts refuse failed or mixed-setting invocations instead of resurrecting earlier successful results.
- Speed-history schema distinguishes activation/speculation and actual resolved CPU/context settings.

## Not complete

Presentation wiring implemented by Claude Opus with an Opus test-design subagent; local build/test passed, exact-head CI and independent review pending:

- Apply calls `reconfigureConversation` only when the ViewModel's lock-guarded record proves the READY engine has the same artifact identity, file and vision mode, and `RuntimeConversationReconfigurationPolicy` admits the change. Thread counts are normalized as the runtime normalizes them. Engine-level changes keep the preflighted full reload with refusal preservation. Settings publish only on success. Any failure unloads the engine and shows an error; READY is never restored.
- Reopening the loaded model's chat (`openLastChat`, `startInstalledChat`) keeps the engine until `openChat` proves an exact match (artifact, file, vision, every setting, sampler). It then only resets history; otherwise it unloads and fully loads, as before.
- Speed history records `DecodeRunSettings.fromApplied`. Model-page lookup includes activation/speculation and uses resolved CPU/context only for the running backend's row while the pending engine settings equal the applied ones.

These avoid reload work. No end-to-end speed gain is claimed until it is measured on a physical ARM64 device.

Independent Claude review started but did not complete before a provider session limit. It must resume before this checkpoint is represented as reviewed. No alternative model is being represented as Claude.

No exact-build physical ARM64 result exists for this checkpoint. Neither a Kotlin unit test nor an x86 emulator establishes superiority over Google AI Edge Gallery, native GPU correctness, thermal behavior, or measured token throughput.

## Regression coverage

- Runtime engine-identity/conversation-option policy.
- Cancellation ordering, generation drain, resource-close/replace behavior, and fail-closed settings transition.
- Wrapper forwarding with zero model load/unload calls, timeout and caller cancellation.
- Telemetry first-sample/cadence boundaries.
- Distinct activation/speculation/resolved settings in measurement history.
- Python benchmark-summary failure, identity, warm-up and numeric validation.

Test results must be tied to their exact commit in PR/CI. Historical pre-GOD-MODE benchmark values are not measurements of these changes.

## Build toolchain compatibility

The inherited Kotlin 2.3.0 / AGP 8.7.3 combination emitted R8 Kotlin metadata parse warnings. The catalog now pairs Kotlin 2.3.10 with AGP 8.13.2 (R8 8.13.19), retaining Gradle 8.13 and JDK 17. Both are within the published compatibility matrices:

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://kotlinlang.org/docs/gradle-configure-project.html

Inspection of resolved release inputs then identified Kotlin 2.4 metadata in LiteRT-LM 0.18.0's own Engine, EngineConfig and Conversation classes, and its required reflect/stdlib 2.4.0 dependencies. R8 8.13.19 therefore still emitted 100 metadata warnings. Coroutines 1.11.0 was not the source (its metadata is 2.2).

The targeted candidate is the official R8 9.1.29 override, the documented minimum for Kotlin 2.4, while retaining AGP 8.13.2. Because Google Maven did not serve this version, the build uses Google's official R8 stable archive, scoped to the R8 module only. This does not add an application network destination. Validation must identify R8 9.1.29 in mapping output and show the warnings are absent; no broad AGP migration or runtime dependency downgrade is intended.

- https://developer.android.com/build/kotlin-support
- https://r8.googlesource.com/r8/+/refs/heads/main/README.md#replacing-r8-in-android-gradle-plugin

This is not a demonstrated inference-speed improvement. Warnings alone did not prove the previous APK was miscompiled. The optimized APK still requires release JNI preservation checks and exact-build device validation.

### Validated optimizer checkpoint

The targeted R8 9.1.29 build completed all 135 tasks: unit tests, lint, debug APK, instrumentation APK and optimized release APK. Mapping identifies R8 9.1.29; metadata parse warnings fell from 100 to zero. The release verifier checks 110 SDK mapping entries and all four JNI callbacks; 10 verifier fixtures and six benchmark-summary fixtures pass. The verifier now recognizes R8-generated classes by class-level source and synthesized metadata, without exempting required JNI classes or accepting member-level synthetic markers. No keep rule was weakened. CI rejects recurrence of metadata parse warnings.

The new opt-in LiteRtConversationReconfigurationTest compiles and is included in runtime E2E CI. It verifies actual engine-object retention, changed conversation/sampler/history, fail-closed rejection and full-reload recovery using the existing pinned Qwen fixture. At core checkpoint 0fd68c1, the test executed and passed in CI (JUnit verified), alongside text lifecycle, vision and UI smoke checks. It is not a timing benchmark or physical ARM64 evidence.

### Presentation checkpoint validation

Local testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest and assembleRelease passed after the Opus presentation changes: 572 unit tests, zero failures/errors/skips. Release JNI verification again checked 110 SDK mapping entries and all four callbacks. The focused tests exercise reuse admission, safe error states and speed-history identity. Repeated/cancelled Apply are not yet covered by an actual ViewModel integration harness; policy-level tests alone do not establish that end-to-end behavior. Exact-head emulator CI and independent Claude review remain gates.
