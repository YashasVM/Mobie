# Mobie reliability fixes, 2026-10-19

Baseline: `main` at `382b61c08f5da6f3f8f20c99b9f92d7418956571`.
Local branch: `fix/download-cancellation-release-jni`.

## Changes

1. Cancel a stalled model download promptly even after response headers arrived. The old cancellable header wait detached cancellation from a blocking body read, leaving the socket active until its read timeout. `useDownloadResponse` keeps a cancellation watcher attached for the entire response lifetime and closes it on every exit. The worker uses this helper without changing download/resume policy.
2. Preserve LiteRT-LM JNI classes and callbacks in minified release APKs. The pinned 0.16.1 AAR supplies no consumer ProGuard rules. Its baseline release mapping removes required JNI classes and callback names. Backported only the isolated package keep rule and mapping verifier from existing PR #36, not that branch's UI/runtime upgrade. CI now builds release and validates the mapping; six Python fixture tests exercise the verifier.

No UI edits, runtime/dependency upgrade, network destination, permission, or credential handling change. Inference remains on-device. Unsupported model gating is unchanged. PRs #18, #36, and #37 were not merged or adopted.

## Verification

Toolchain: existing Android SDK 35, JDK 17, Gradle 8.13.

```
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease \
  --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
python3 scripts/verify-litert-release.py app/build/outputs/mapping/release/mapping.txt
python3 scripts/test_verify_litert_release.py
git diff --check
```

- Aggregate build passed; 363 JVM tests, zero failures/errors/skips.
- Lint: zero errors, 25 warnings on the final combined run (initial reliability run reported 16; repeated dependency-version warnings vary).
- Debug APK and unsigned release APK built.
- Release mapping: 74 LiteRT-LM classes and all four JNI callback names preserved.
- Verifier fixtures: six passed.
- Cancellation regression: replacing only the helper with the old `awaitResponse().use(block)` behavior makes the stalled-body test fail with `Cancellation did not close the active call`; restoring the fix passes the complete suite.
- Release regression: baseline release APK builds, but mapping verification fails for renamed/removed JNI classes and missing callbacks. The keep rule makes the same check pass.
- Initial combined build lost its daemon during R8; reduced-memory single-worker retry and final aggregate build succeeded.

## Remaining gates

- Independent Claude review is pending; do not describe these changes as reviewed or production-ready yet.
- No physical ARM64 inference or emulator instrumentation was run for this patch. Build and mapping success do not prove model loading on a phone. No model compatibility/speed claim is made.
- Existing Kotlin 2.3.0 / AGP 8.7.3 combination produces R8 Kotlin metadata warnings. Build succeeds, but toolchain alignment and physical release inference validation remain release-readiness concerns.
- This checkpoint is being submitted as a focused draft PR. Merge remains gated on CI and independent review. Generated APKs/local configuration remain ignored by Git.

## Follow-on streaming overhead optimization

After the reliability checkpoint, a singleton-channel fast path avoids redundant StringBuilder allocations and copying. Three more tests pass, for 363 total. The complete unit/lint/debug/release gate was rerun successfully on the combined code, and the JNI verifier still preserves 74 classes and four callback names. See `../perf/stream-channel-overhead-2026-10-19.md` for reproducible host-only measurements and limitations. Claude review and physical-device validation remain pending.
