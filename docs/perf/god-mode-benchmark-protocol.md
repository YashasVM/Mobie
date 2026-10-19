# GOD MODE performance measurement protocol

Reviewed 2026-10-19 against Mobie's restored terminal branch and official LiteRT-LM 0.18.0 sources. This document describes a reproducible test plan, not measured superiority over Google AI Edge Gallery. No physical ARM64 performance result was produced for these changes.

## What the comparison must establish

Keep three outcomes separate:

1. **Model work:** prompt prefill tokens/s and native decode tokens/s.
2. **User wait:** launch-to-ready, send-to-first-native-chunk, send-to-first-displayed-answer, and total response time.
3. **Cost and quality:** sampled peak memory, thermals, sustained performance, and whether the answer passes the same task checks.

A callback chunk can contain multiple tokens. Never present callback rate as model tokens/s. A faster first reasoning chunk is not necessarily a faster useful answer. Reducing thinking or output limits changes the workload; report that as a latency/quality tradeoff, not an equivalent-compute speedup.

## Verified comparison baseline

- [Gallery's current dependency catalog](https://github.com/google-ai-edge/gallery/blob/main/Android/src/gradle/libs.versions.toml) pins LiteRT-LM 0.18.0, the same version as this Mobie branch. Capture a release/commit when performing a comparison because `main` can change.
- [Gallery's LlmChatModelHelper](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt) retains an initialized engine and normal conversation, supports GPU/CPU selection and configurable speculation, and uses the SDK's model-directory cache default for normal model paths. Mobie's persistent cache and retained conversation are not unique acceleration techniques.
- [LiteRT-LM 0.18.0 EngineConfig and ConversationConfig](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt) distinguish engine context/backend/activation configuration from conversation settings. Eager preface prefill moves work into conversation creation. Conversation-level speculation can lazily initialize a drafter. Therefore capture initialization and first-request latency separately rather than claiming shifted work disappeared.
- [The engine's experimental flags](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt) define artifact-default speculative behavior and engine-initialization-only benchmark selection. Include both flags in an experiment's setup.
- [Google's official performance table](https://developers.google.com/edge/litert-lm/overview) shows that backend rankings differ by model and phase. GPU can improve prefill without improving decode. Do not extrapolate its published hardware results to an untested phone.

## Identity and conditions to preserve

Use the same physical phone, OS build, exact artifact SHA-256, quantization, runtime version, model context, sampler/seed, output cap, thinking setting/budget, speculative setting, activation override, image configuration and prompt bytes. Match actual backend and resolved CPU threads/context, not only a requested Auto label. Record an unknown runtime default as unknown, not as guessed FP16, FP32 or speculation state.

Use matching optimized build types for a product-speed comparison. A debuggable instrumentation build can establish behavior or an internally matched A/B result, but is not automatically comparable to Gallery's release APK. Record Gallery's exact APK/version/hash and settings separately; Mobie's test cannot certify them.

The production-adapter benchmark now records:

- model filename, byte count, SHA-256 and inferred quantization;
- LiteRT-LM version, app ID/version/build type/debug status, target and test APK SHA-256, and split APK hashes;
- optional `buildRevision` supplied by the operator, plus device build fingerprint and ABIs;
- protocol ID, fixed prompt ID/SHA-256, sampler values, requested/active backend, requested/actual context and thread counts, activation and speculative choices;
- native token counts/rates, first output timings, response duration, sampled PSS and thermals, output hash and errors.

APK checksums identify exact binary inputs even when a Git revision was not provided. An operator-supplied revision is provenance, not independently verified source identity. Hashing happens before timed regions, but reads model pages into the OS cache. Consequently these runs do **not** measure cold-storage I/O. Rows declare `os_page_cache_controlled=false`; optional `cacheState` is an operator label, not verification of cache deletion. A new engine does not imply an empty persistent or operating-system cache.

The legacy `galleryEquivalent=true` argument only selects a sampler preset. Rows also include `gallery_sampler_only` and `comparison_target=mobie_adapter_only`. This is not a direct Gallery benchmark and does not prove all settings match. The fixed prompt contains `/no_think`; do not use that prompt as an unrestricted-thinking comparison without defining and recording a different fixture.

## Run matrix

Keep independent groups for:

1. Engine initialization with no pre-existing compiled cache, explicitly established outside the measured run.
2. New engine initialization with verified reusable compiled cache.
3. First generation from an empty conversation.
4. The same prompt sequence in a retained live conversation, measuring turn two and later.
5. Restored history with lazy versus eager preface prefill; report both restoration time and next-send latency.
6. Conversation-only settings updates versus full engine-settings reloads.
7. Sustained repeated prompts, with starting/max/ending thermal state and quality results.

Warm up before steady-state measurements, then alternate or randomize baseline/candidate order. Use multiple matched repetitions and publish individual rows as well as median and dispersion. Keep cold and warm groups separate. Record failures and refusals; never omit slow or failed runs merely to improve a summary. Include output length and quality checks, because early stopping or a failed answer can make latency appear better.

Run CPU thread choices, GPU, activation, speculation and thinking as separate controlled experiments. Do not enable multiple untested changes and assign the entire gain to one feature. Never bypass memory, thermal, cancellation or artifact-compatibility safeguards for a speed result.

## Measurement overhead and limits

### Sustained native decode fixture

Protocol `mobie-production-adapter-v3` distinguishes two fixed prompt fixtures. `short-latency` preserves the original `local-ai-benefits-v1` prompt and remains the default. A short response can measure latency but cannot establish sustained decode throughput. Older rows without a fixture are treated as short latency probes.

`sustained-decode` uses the fixed `phone-care-twelve-tips-v1` prompt. Rows record its ID, exact UTF-8 byte count, and SHA-256. The default eligibility threshold is 64 native decoded tokens. SDK chunk counts are recorded separately and never substitute for native token counts. Match the prompt, output budget, thinking settings, and threshold in both comparison conditions.

For example, after installing the benchmark APKs and placing the same artifact on a physical ARM64 phone, run:

```bash
PROMPT_FIXTURE=sustained-decode OUTPUT_TOKENS=256 MIN_DECODE_TOKENS=64 \
	SUSTAINED_SECONDS=120 BUILD_REVISION=$(git rev-parse HEAD) \
	scripts/perf/run-gpu-benchmark.sh SERIAL APP_PRIVATE_MODEL_PATH CPU OUTPUT_DIR 1 4
python3 docs/perf/summarize-gpu-results.py OUTPUT_DIR/cumulative-results.jsonl \
	--fixture sustained-decode --backend CPU --cpu-threads 1
```

`SERIAL`, `APP_PRIVATE_MODEL_PATH`, and `OUTPUT_DIR` are operator inputs. The model path is relative to the app's private files directory. This command measures Mobie only. It does not run Gallery or verify matched Gallery settings.

With `SUSTAINED_SECONDS` greater than zero, each outer repetition loads an engine, performs one warm-up generation, and collects measured samples. The summary aggregates those samples, including samples from outer iteration zero, and excludes each in-row warm-up. With no sustained interval, the summary excludes outer iteration zero and uses subsequent generations.

Short outputs remain in raw rows with their native token count and eligibility reason. An ineligible generation makes the sustained fixture fail and prevents a success-only summary. The summary rejects missing, failed, short, and nonfinite measured samples and mixed prompt or configuration identities. It reports individual token counts through their range, median, standard deviation, and total, alongside decode-rate dispersion. These checks establish workload eligibility, not answer quality, physical-phone superiority, or a 2× speedup. Review outputs against the same task checks in both apps.

The benchmark uses one 100 ms PSS/thermal monitor feeding both whole-run and per-generation peaks, with endpoint samples for short generations. This avoids two simultaneous page-map scans. PSS is still a sampled process-memory metric: it is not an exact peak, all device memory, or a complete GPU allocation accounting. The monitor itself can perturb the workload; keep its cadence matched and assess observer overhead separately.

Adapter timings start within the adapter and do not include every ViewModel, Compose or displayed-frame delay. Add a send-click timestamp and a first-rendered-answer marker to an end-to-end trace when evaluating perceived responsiveness. Keep native decode rate separate from these app timings. APK/model checksums and input setup must remain outside the reported timed region, with their cache effects disclosed.

The UI's measured-speed history is not a controlled benchmark. Its versioned keys now distinguish requested activation/speculation and actual reported CPU/context resolutions. Different activation/speculation modes are not substituted for one another. Unknown Auto resolution is only an approximate match to a known resolution. Runtime/artifact/device changes invalidate old groups. Do not use its recent min/max range as evidence of an optimization's causal effect.

## Available validation without a phone

Host unit tests can establish sampling boundary correctness, configuration-key separation, old-schema invalidation, lifecycle call counts and streaming output integrity. Emulator tests can establish UI behavior and presentation overhead. Neither establishes native ARM64 GPU throughput, thermal behavior, compatible exact artifacts or superiority over Gallery.

The repository still requires its complete test/lint/build gates and physical exact-artifact runtime validation before a phone-native performance claim. Report passed, failed and never-run stages separately.

## Bounded x86 retained-engine mechanism probe

`LiteRtConversationReconfigureBenchmarkTest` is a separate opt-in native API-path experiment, not a phone or Gallery benchmark and not tap-to-pixel UI latency. It compares the old full-load Apply operation with `reconfigureConversation` on the same CPU-1, INT4 Qwen fixture. Both conditions start from identical restored history and seed 0, then change to seed 7 while retaining greedy sampling and output cap 24. No extra baseline unload is timed. Model/APK/test-APK/prompt/history hashes and actual runtime settings are recorded.

One warmup per condition is followed by four pairs with alternating order. Exclude warmups; require top-level `status=complete`, cleanup success, `paired_comparison_valid=true`, all eight measured rows successful, matching settings and fixture identity, and expected engine identity changes. Report raw rows and medians separately for setup, runtime first SDK chunk, setup plus that chunk, and native decode. A fast setup path does not imply faster native decode or physical-phone performance. OS page cache is not controlled; both warmups and fixture hashing happen before measurement.

Run on a debug x86 emulator using `connectedDebugAndroidTest` with runner class `dev.yashasvm.mobie.LiteRtConversationReconfigureBenchmarkTest` and arguments `litertReconfigureBenchmark=true`, optionally `buildRevision=<source SHA>`. The existing runtime E2E CI invokes it and uploads `mobie-conversation-reconfigure-benchmark.json` and `.csv` exported to `/sdcard` by the instrumentation before target-app teardown (the original working copies live under external-files `benchmarks`). There are no timing thresholds in assertions; this avoids turning shared-runner variance into a flaky correctness gate. Physical-device comparisons remain deferred to owner testing and use the stricter protocol above.
