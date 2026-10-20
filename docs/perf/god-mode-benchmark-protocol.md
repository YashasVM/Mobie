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

## Phone comparison against Gallery

`GodModeSpeedBenchmarkTest` is an opt-in physical ARM64 comparison using LiteRT-LM 0.18.0. Compilation verifies the test code, but cannot establish model compatibility, native throughput, or superiority over Gallery. No timing threshold is asserted. Successful generations must complete and produce visible output. A failed load, generation, reset, or cleanup invalidates the report.

Select a phone explicitly, then run from the repository:

```bash
ANDROID_SERIAL=<physical-phone-serial> scripts/perf/god-mode-benchmark.sh
# Optional settings, with defaults shown:
ANDROID_SERIAL=<physical-phone-serial> REPETITIONS=3 ROUNDS=2 OUTPUT_TOKENS=256 \
  WITH_VISION=false COOL_START=false scripts/perf/god-mode-benchmark.sh
```

The script rejects emulator serials, `ro.kernel.qemu=1`, and non-ARM64 primary ABIs before installing anything. It downloads once into `${XDG_CACHE_HOME:-$HOME/.cache}/mobie-bench`, resumes a `.part` download with curl, and checks SHA-256 before atomic finalization. A corrupt cached file or completed partial file requires removal before retrying. It builds both debug APKs, installs each only through `adb -s "$ANDROID_SERIAL" install -r`, stages the model through `/data/local/tmp`, and copies it into the debug app's private files using `run-as`. The temporary device copy is removed. The app independently checks artifact bytes and checksum before timing.

The exact ungated Apache-2.0 fixture is `litert-community/gemma-4-E2B-it-litert-lm`, revision `41c5569623a7c189d6eec3e5225260c58cedbce7`, file `gemma-4-E2B-it.litertlm`, 2,588,147,712 bytes, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`. The download destination is Hugging Face and its artifact redirects; inference and synthetic prompts remain on the phone. The benchmark introduces no production permissions, SDKs, or credentials.

Each condition gets a separate engine load per round:

| Condition | Configuration |
|---|---|
| `gallery_default` | Raw SDK, GPU text backend, context 4000, `cacheDir=null`, default activation and seed, sampler top-K 64 / top-P 0.95 / temperature 1.0, engine speculative flag false, per-send `enable_thinking=false`. |
| `gallery_mtp` | Same raw configuration with the engine speculative flag true, matching Gallery's user toggle. |
| `mobie_default` | Production `LiteRtLmRuntimeAdapter`, `ComputeOptions()` Auto backend/context/speculation defaults, default `SamplerSettings()` of 40 / 0.95 / 0.7 with seed 0. Thinking is explicitly off and the output cap is shared. |
| `mobie_gallery_sampler` | The same production adapter defaults with sampler 64 / 0.95 / 1.0 and seed 0, to isolate runtime configuration from sampling. |

All conditions use the same `outputTokens` cap, default 256. The raw SDK receives it through `sendMessageAsync(maxOutputToken=...)`; Mobie receives it through compute and generation configuration while retaining its production context admission checks. Mobie's actual context, backend, CPU threads, fallback reason, and drafter state are recorded. The raw drafter field is the requested engine flag, not an independently measured native activation signal. Raw conditions do not silently fall back from GPU.

The raw configuration follows Gallery's `LlmChatModelHelper.kt` and `model_allowlists/1_0_20.json`. `ExperimentalFlags.enableBenchmark=true` differs from Gallery's default false. This mode records native timing/token statistics so `BenchmarkInfo` can be read; it does not select a different sampler or backend. Instrumentation overhead is still present and should be assessed separately. Raw settings leave optional conversation fields at SDK defaults. These text workloads disable audio on both paths. Gallery uses a CPU audio backend only when audio input is enabled. `WITH_VISION=true` adds Gallery's GPU vision backend and requests production vision loading in Mobie, with no attached images. The test records Mobie's actual `vision_backend` after production fallback. If production cannot initialize GPU vision, the optional matched-vision block fails instead of comparing different encoders. The default text-only run does not initialize vision or audio encoders.

Default rounds run the four conditions in the order shown, then in reverse, giving ABBA ordering when projected onto any pair. `ROUNDS` must be even, between 2 and 20. Each block performs one excluded warmup, then `REPETITIONS` of all four prompts, recreating/resetting the conversation before each subsequent generation. The warmup uses the summarization prompt. `COOL_START=true` waits up to 180 seconds for Android thermal status NONE before each block. It records wait duration and timeout, then proceeds even if cooling times out. Thermal reporting needs Android 10 or newer for this option; otherwise thermal fields are -1. Keep timeouts visible in conclusions.

The report contains literal UTF-8 prompt text, IDs, and hashes. The four fixed task categories mirror model-card use cases; they are not Google's published evaluation fixtures. For manual Gallery entry, use exactly these strings with no trailing newline:

```text
Summarize this text in three sentences: A neighborhood library started lending tools as well as books. Residents can borrow drills, ladders, and gardening equipment for one week. Volunteers inspect each returned item and teach a monthly repair workshop. The program reduces duplicate purchases and helps neighbors finish small projects. Its main challenges are storage space, maintenance costs, and making popular tools available fairly.
```

```text
Write a short Kotlin function that takes a list of integers and returns the three most frequent values. Break ties by ascending numeric value. Include a small example and explain the time complexity.
```

```text
Rewrite this message in a friendly, professional tone while preserving every request: Your report is late again. Send the corrected sales totals by Thursday at noon, explain why the numbers changed, and tell me who will review the next draft.
```

```text
Design a small balcony garden for someone with two hours a week, morning sunlight, and a limited budget. Suggest plants, a simple watering routine, and one way to reuse household materials. Explain your choices.
```

Every generation records load duration for its block, first nonempty SDK chunk latency, native decode/prefill rates and token counts, generation duration, generation ms divided by native decode tokens, visible output hash and length, sampled PSS, thermals, resolved compute, and errors. The Mobie first-chunk/generation values come from adapter statistics; raw values bracket SDK streaming. Neither measures displayed-frame latency. One 100 ms PSS/thermal monitor serves each block, with endpoint reads for short generations. The generation PSS peak and load PSS peak are distinct. Sampling misses peaks and excludes some GPU memory. File hashes, reset, and export occur outside generation timing.

JSONL is written incrementally under app-private files as `mobie-god-mode-benchmark.jsonl`. A JSON report and CSV are staged under external-files `benchmarks` and checksum-verified after export to `/sdcard/mobie-god-mode-benchmark.json` and `.csv` before teardown, including on handled failures. The script clears prior exports first, pulls both files, and saves instrumentation output and `summary.md` under `build/god-mode-benchmark/<UTC timestamp>` or `RESULT_DIR`. A native process crash may prevent export; missing output is a failed run. The summarizer rejects failures, incomplete sample matrices, mixed model/APK/device identities, and missing conditions, and excludes warmups. Re-run it with:

```bash
python3 scripts/perf/summarize_god_mode.py <results>/mobie-god-mode-benchmark.json
```

Results apply only to the recorded device/build. Hashing warms model pages; neither the OS cache nor persistent compiled caches are cleared. Mobie retains its production app-private cache and Gallery uses the SDK default. Different samplers can change output length, content, and quality. Report native decode ratios separately from first-chunk and overall latency, review individual outputs/lengths, and do not attribute all differences to MTP.

For a real Gallery-app cross-check, record Gallery's exact version and APK hashes on the same phone, verify it loads the identical artifact bytes above, and select Gemma 4 E2B chat with GPU, context 4000, sampler 64 / 0.95 / 1.0, and thinking off. Match text-only versus vision input mode. Leave MTP off for `gallery_default`, then repeat with it on for `gallery_mtp`. Start a fresh chat before each prompt, perform an excluded summarization warmup, and repeat the same four prompt strings and cap. Gallery exposes per-message statistics; record its decode/prefill rates, token counts and reported latency for every message, save screenshots, and record thermal state/cooling and output length. Alternate Gallery and Mobie blocks rather than always measuring one app first.

Check Gallery's actual output limit controls: its context limit is not an output-token cap. If its UI cannot enforce the same output cap, record that mismatch and compare native decode cautiously, rather than treating total duration as matched. Gallery's UI timing and release build can also differ from raw SDK instrumentation. This manual collection is independent evidence to cross-check the raw configuration, not a result produced by this test. No Gallery app or physical-phone performance was verified when authoring the harness.
