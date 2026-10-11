# Mobie performance and capability audit

Reviewed on 2026-10-11. The implementation is based on `604919c`, the head of the existing UI branch. This PR keeps LiteRT-LM Android 0.16.1, its CPU backend and its production thread policy. No new runtime, dependency, permission, network destination or conversion tool was added.

The audit covered runtime, device/model policy, catalog, downloads, credential storage, history, conversion, Compose screens, presentation state, build configuration and CI. Three delegated reviewers checked separate areas, then reviewed the changes independently. The unrelated website and design work in the shared workspace is outside this PR.

## Results and evidence limits

The targets are 1.5x or 2.2x improvement in token efficiency and response speed. Those targets are not validated for native inference on a phone. No physical ARM64 device was attached. An Android 15 x86_64 emulator can establish application behavior and presentation overhead; it cannot establish mobile chip throughput, thermals or image-model support.

The streaming benchmark compares application work for a burst of 2,048 SDK chunks, including the actual reply parser and Markdown parser. It verifies identical final text, warms both cases twice, and alternates their order over seven measured rounds. It measures presentation overhead only. It does not execute a model, measure Compose frame rendering, or prove that a model produces tokens faster. Its raw output includes timing arrays and update counts. Slow streams whose chunks arrive more than 32ms apart will receive little batching benefit, and later output can wait up to 32ms for presentation.

The first two nonempty output events and the first answer event bypass batching. An SDK chunk can contain multiple model tokens. The native adapter records first output, first visible channel output, and second nonempty SDK chunk separately; the second chunk must not be called the second token. Inline reasoning tags can still arrive in the visible channel, so first visible channel output is not an exact final-answer boundary for every artifact. Native latency now starts before locking, memory checks and conversation rebuilding, making the measurement closer to user wait time.

The exported [raw presentation results](benchmarks/2026-10-11-stream-emulator.txt) record a 7,288.82ms baseline median and a 59.31ms candidate median, or 122.88x less wall time for this burst workload. State/parser updates dropped from 2,048 to 16. First-chunk medians were 0.018ms baseline and 0.045ms candidate; second-chunk medians were 0.074ms baseline and 0.105ms candidate. The collector adds small application overhead to those immediate events and no intentional timer wait. These measurements do not meet the phone inference speed targets.

The final full verification command passed on Android 15 x86_64: 308 unit tests and 32 instrumentation cases, with four opt-in benchmarks/native cases skipped and no failures. Lint and APK assembly passed. Separate opt-in runs passed the presentation benchmark and pinned text/vision lifecycle tests. The pinned Qwen text lifecycle passed generation, cancellation recovery, reset and reload. [Its raw metrics](benchmarks/2026-10-11-qwen-emulator.txt) record first/second output chunks at 804/843ms on the first prompt and 556/594ms on the follow-up, with decode throughput of 20.40 and 23.49 tokens/s respectively. These different prompts are not a baseline/candidate comparison. Reported RAM is end-of-generation PSS, not peak memory.

The pinned SmolVLM2 vision lifecycle passed restored-image, replacement-image and text follow-up handling on the emulator. Its [observed response](benchmarks/2026-10-11-vision-emulator.txt) described a blue/yellow fixture as "blue, white, and green". It failed that manual color-quality check despite passing the existing nonblank-output test. This result does not establish reliable image understanding. The PR remains a draft until exact-artifact physical-device performance and capability tests are available.

## Implemented changes

### Preserve useful context without repeating avoidable prefill

The previous policy used UTF-8 transcript size to decide when to discard live context. A transcript larger than the 3 KiB replay allowance at a 4K context could force a native conversation rebuild even when its real token cache had ample room. Rebuilding repeats prefill and removes older context that might help answer the next question.

The pinned SDK already exposes [occupied token count](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt). The underlying [conversation implementation](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/runtime/conversation/conversation.cc) reports the session's current step. The new policy uses that count for a live conversation, including cached reasoning and image tokens omitted from the visible transcript. It retains completed turns while occupancy leaves at least one quarter of context free and also satisfies template/output reserves, a 64-message limit and a 96 KiB application-memory bound.

New prompts still use conservative UTF-8 bounds because the API does not expose a cheap tokenizer for unsent text. Initial messages use [lazy prefill by default](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt), so zero cache occupancy with nonempty restored history must budget the actual pending transcript. Rebuilds select bounded replay history again. Already-cached images use their measured occupancy rather than another image reserve. A newly attached image still receives its separate reserve.

Unit tests cover hidden reasoning occupancy, Unicode, pending replay history, retention/rebuild boundaries, small contexts, image reserves, and bounded replay after a live transcript grows. This is a tested admission and retention change, with a plausible prefill benefit. No native speed multiplier is inferred from those tests.

### Reduce streaming presentation work

The view model previously copied its message list, concatenated the growing reply, published state, and caused Markdown parsing on every chunk. The new collector combines later burst chunks for at most 32ms, with a 2,048-character or 32-segment flush bound. It preserves reasoning/answer order and flushes before statistics, errors, completion and cancellation.

The first two chunks and first answer publish immediately. Stop keeps the current reply placeholder until cancellation has flushed received text, then marks or removes that reply. A captured operation/session prevents an old stream from changing a newly selected conversation. Regression tests cover cancellation in a split reasoning tag, timer-driven flush during upstream idle, terminal order and exact final output. The inline reasoning parser also retains incomplete opening tags after a visible prefix and preserves trailing answer whitespace across chunk boundaries.

### Make actual image capability visible

Model metadata and a loaded image encoder are different facts. A vision model can load as text-only after image initialization fails. The runtime now exposes whether its image encoder actually initialized; the watchdog and thermal guards forward that state. The chat composer offers photo input only when that capability is active, explains a text-only fallback, and distinguishes photo questions from image generation. Emulator tests cover both states.

The [text-only fallback](benchmarks/2026-10-11-text-only-ui.png) and [active image-input screen](benchmarks/2026-10-11-image-input-ui.png) screenshots show instrumentation fixtures. They demonstrate UI behavior for each capability state, not a tested phone/model combination.

Image imports reject files above 20 MiB, 25 million pixels or 16,384 pixels on either side. They validate decoded JPEG/PNG/WebP metadata and a sampled decode, preserve original bytes for inference, and delete failed or cancelled copies. Thumbnail sampling uses the longest dimension so a panorama cannot force a large bitmap allocation. Instrumentation tests cover invalid data, byte/pixel/dimension bounds, wide/tall images, exact input-byte preservation and paths outside the cache directory.

### Avoid a second full checksum read during download recovery

Completed `.part` and installed-file recovery previously verified a trusted checksum and then read the same file again to compute the persisted digest. Verification now returns the digest to atomic finalization. Trusted-hash recovery requires one full read rather than two. This halves bytes read for that recovery step; it is not a measured 2x inference gain.

Checksum-less recovery still computes a local digest. If stored local verification exists but the file fingerprint changes, the helper rehashes and compares before restamping metadata. Tests reject same-length changed content and verify persisted recovery digests. Resume, HTTP range and atomic install behavior remain covered by the existing suites.

### Correct catalog scope and use cached histories

Search and final domain mapping now enforce the repository's v1 `litert-community` publisher rule. Tests reject third-party owners, similar names, malformed repository IDs and search-query parameter injection. Recognized `.litertlm` artifacts still need resolved sizes and compatible execution targets.

History writes and new sessions now use the existing session cache instead of deserializing every saved session again. A persistence test verifies repeated writes preserve inactive sessions across store recreation. Whole-history serialization still runs synchronously and is a remaining improvement.

## Android chip image-model plan

[Mobile image-model research](mobile-image-model-research.md) traces the CPU, GPU and chip-specific NPU paths to official sources. The existing adapter supports image input with text output. Diffusion image generation needs a separate runtime contract, compatible converted assets, verified multi-file installation, a memory policy and exact physical-device validation. No diffusion checkpoint or hardware-specific NPU bundle is enabled by this PR.

Qualcomm and MediaTek NPU deployment requires agreement between the actual SoC, model artifact, dispatch libraries and vendor SDK. ARM64 ABI or the presence of an NPU is insufficient. The smallest next experiment is validating the existing small vision artifact on a physical phone before introducing an image-generation pipeline.

## Remaining opportunities, in priority order

1. Benchmark actual CPU thread counts, text GPU inference and compatible thinking limits on a physical phone. The current benchmark sweeps 1/2/4 threads with deterministic sampling, a fixed checksum/context/output budget, warmups and balanced repeated order. It records callback milestones, sampled PSS and thermals against the shipped two-thread configuration. It refuses emulator results as physical evidence. Output quality and a sustained session are required before changing defaults. The pinned [thinking configuration](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt) can limit reasoning work, but artifact compatibility and answer quality need tests.
2. Evaluate image understanding with expected-color/shape checks, replacement photos, recovery and sustained measurements. This PR pins the text and vision fixtures' revisions, sizes and checksums. Nonblank-answer assertions still cannot establish image comprehension.
3. Move ordered history serialization and persistence off the main thread. A per-session file design may avoid rewriting all 20 sessions, but ownership, process-death recovery and preservation of the user's transcript need design and tests.
4. Store saved chat images durably and clean abandoned attachments. Cache storage can disappear even while saved history references it. Orientation normalization and model-specific input resizing also need a documented policy and image-understanding evaluation.
5. Preserve sanitized catalog distinctions for authentication, gated access, rate limits and network failures. Detail fetch currently collapses some errors to absent metadata. Add explicit private-model access state when the product permits private catalog entries.
6. Recommend quantization by validated quality and compatibility inside the device's memory budget. Current selection often prioritizes estimated memory over quality. Filename guesses alone cannot establish a quality ranking.
7. Measure WorkManager progress/notification overhead during concurrent inference before changing its 250ms interval. Reduced writes may help contention, but this audit measured no decode benefit.
8. Use cancellable HTTP and real server status transitions when enabling conversion. Its endpoint is currently disabled, so there is no reason to add a new backend or polling infrastructure in this performance PR.
9. Align release tag/APK naming with the Gradle version before the next release. The `v0.0.2` release workflow and `0.2.0` app version currently disagree.

## Reproducible commands

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest

# Application presentation benchmark, valid on an emulator but not a native model benchmark.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.ui.StreamingPerformanceBenchmarkTest \
  -Pandroid.testInstrumentationRunnerArguments.streamBenchmark=true
adb pull /sdcard/mobie-stream-benchmark.txt

# Native text lifecycle. Emulator execution is application evidence only.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.LiteRtEndToEndTest \
  -Pandroid.testInstrumentationRunnerArguments.litertE2E=true

# Exact-artifact CPU tuning. Requires a connected physical ARM64 phone.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.LiteRtCpuThreadBenchmarkTest \
  -Pandroid.testInstrumentationRunnerArguments.litertPhysicalBenchmark=true
adb pull /sdcard/mobie-cpu-thread-benchmark.txt
```

For any native gain claim, record the device/SoC, Android version, quantization, exact artifact hash, backend, context, prompt, output budget, measured throughput and first output timings, sampled peak RAM, thermal/throttling notes and quality result. Compare repeated baseline/candidate runs with the same artifact and settings. Keep prompts, images, outputs and derived data local.
