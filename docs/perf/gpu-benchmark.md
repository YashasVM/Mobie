# GPU performance on Galaxy S24 Ultra

Measured 2026-10-13 on Samsung SM-S928B, serial RZCY81Q06XR, Android 16 / SDK 36, Snapdragon 8 Gen 3 / Adreno 750. USB charging; ambient temperature was not measured. These results cover the exact artifact below. They do not certify other models or devices.

## Artifact and runtime

Repository `litert-community/Qwen3-0.6B`, source revision `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76`, filename `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`. Installed local name `Qwen3-0.6B_dynamic_wi4b32_afp32.ctx4096-d45a823d8579.litertlm`, 344671744 bytes. SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`, recomputed on the phone. INT4 block-32 weights and FP32 artifact activations. Context capacity is 4096 in the original production measurements.

LiteRT-LM 0.16.1 fails this artifact's GPU initialization at `STABLEHLO_COMPOSITE odml.swiglu`, after only 46/916 prefill nodes delegate. Auto falls back to CPU. The pinned official 0.18.0 Android AAR delegates 916/916 prefill and 830/830 decode nodes to OpenCL. CPU sampling remains CPU work. Exact 0.18.0 LICENSE and THIRD_PARTY_NOTICE are bundled in app assets.

## Matched visible-output measurements before resuming

The prompt is `Explain in a few concise sentences why local AI inference can be useful on a phone. /no_think`. Each case uses empty history, temperature 0, topK 1, topP 1, seed 0, thinking explicitly off, 64 output tokens, persistent app-private compiled cache, and native benchmark counters. A warm-up precedes three measured repetitions. TTFT is the observed first nonempty SDK chunk, also the first visible output in these thinking-off cases. Chunks are not counted as tokenizer tokens. PSS is sampled every 100 ms and includes graphics mtrack.

| Configuration | Decode tok/s | TTFT ms | Load ms | Peak PSS GiB | Thermal start/max/end | Mobie vs Gallery |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| 0.16.1 Auto, CPU8 fallback, median | 7.65272 | 3194 | 3521 | 1.710 | 0/0/0 in all reps | Not measured |
| 0.18.0 GPU, conservative minimum of three reps | 30.08654 | 977 in that rep | 5490 in that rep | 3.055 median | 0/0/0 in all reps | Not measured |
| 0.18.0 GPU, actual three-rep median | 30.18675 | 964 | 5490 | 3.055 | 0/0/0 in all reps | Not measured |
| 0.18.0 CPU2, median | 6.21257 | 5062 | 2376 | 1.704 | 0/0/0 in all reps | Not measured |
| 0.18.0 CPU4, median | 7.93260 | 3416 | 2429 | 1.705 | 0/0/0 in all reps | Not measured |
| 0.18.0 CPU6, median | 7.61806 | 3211 | 2344 | 1.707 | 0/0/0 in all reps | Not measured |
| 0.18.0 CPU8, median | 7.45073 | 3304 | 2399 | 1.697 | Two reps 0/0/0; one 0/1/1 | Not measured |

The handoff's conservative 7.65 to 30.09 comparison is **3.93x**. The raw three-repetition GPU median is 30.19, or 3.94458x. The handoff had labeled 30.09 a median; the raw records show it is the slowest of the three measured GPU repetitions. CPU4 is 1.03657x the matched old baseline. This comparison includes the runtime upgrade; it does not isolate CPU-thread tuning from SDK changes.

The earlier 8.05 tok/s run used artifact-default reasoning and is excluded from the matched claim. A default-thinking 64-token run exhausted reasoning before producing a visible answer and is excluded. Longer-budget reasoning runs are separate workloads. No sustained performance claim follows from these short runs.

Raw evidence is under `app/build/gpu-benchmark/`: `visible-baseline-0.16.1/cumulative-results.jsonl`, `visible-gpu-0.18.0/cumulative-results.jsonl`, and `visible-cpu-{2,4,6,8}-0.18.0/cumulative-results.jsonl`. Each directory includes native logs and thermal snapshots. `candidate.jsonl` and `final-cumulative-results.jsonl` preserve the cumulative records. Generated artifacts and APKs stay out of Git.

## Memory and context

A loaded 4096-context snapshot reports total PSS 3201427 KiB, GL mtrack 2255744 KiB, native heap PSS 133571 KiB, and Other mmap 623758 KiB. Model-file mapping PSS is 2836 KiB. Allocator totals overlap resident accounting and must not be added to PSS. Raw captures are in `gpu-memory-resident/meminfo.txt` and `smaps.txt`.

One earlier direct-SDK 1024-context probe produced 30.14882 decode tok/s and 245.36612 prefill tok/s, with immediate resident PSS 1646869504 bytes. A later snapshot shows total PSS 1607840 KiB and GL mtrack 1000920 KiB. This is one exploratory sample, without per-case thermal logging, observed TTFT, or peak PSS. It suggests lower context can cut graphics memory substantially, but does not establish a default. `gpu-context-probe.jsonl` and `gpu-context-1024/` preserve the evidence.

## Resume qualification

The direct-SDK context sweep, new-default Auto, and five-minute GPU loop are complete below. The user stopped all phone work before CPU4 sustained, lifecycle, precision, Gallery comparison, and chat demo completed. Every completed new result records artifact SHA, device, Android, backend, context, quantization, peak PSS, observed TTFT, native tok/s, and thermal status.

Gallery 1.0.20 at commit `4928788ef07cdf2194fc435a0f446cf3b997bd5d` also pins LiteRT-LM 0.18.0. The SDK upgrade establishes runtime parity, not a Gallery advantage. A configuration reconstructed in Mobie's harness must be labeled as such; it is not an installed Gallery app measurement. Gallery's normal chat turns benchmarking off, while this harness enables native counters. Sampler, context, output budget, and thinking differences change the workload and cannot prove identical-configuration superiority.

Inference remains local. This work adds no network destination, permission, remote inference, or credential handling. Unsupported formats cannot reach this LiteRT-LM adapter.

### Context sweep after resuming

Four direct-SDK repetitions per capacity, first excluded as warm-up, thinking off and identical short prompt/greedy sampler/64-token cap. Exact SHA, model, device, Android, and quantization match the artifact above. Thermal status was 0/0/0 in every measured repetition. These are exploratory SDK runs, not a production metadata override.

| Context | Decode median tok/s | Observed TTFT median ms | Sampled peak PSS median GiB | Output check |
| --- | ---: | ---: | ---: | --- |
| 1024 | 30.51667 | 981 | 1.535 | All three visible hashes match the 4096 output |
| 2048 | 28.10602 | 1002 | 2.042 | Consistent across reps, but differs from 4096; 48 rather than 57 native decode tokens |
| 4096 | 28.89176 | 1069 | 3.051 | All three visible hashes match the 1024 output |

The per-case end-of-generation resident reading occasionally exceeds the 100 ms sampled peak by less than 0.5%. The raw records preserve both; later probe code folds that end reading into peak accounting. Peak measurements are sampled bounds, not a guarantee that every transient allocation was observed.

**Recommended production default remains 4096 for this artifact.** Local `ModelInfo` reports max context 4096 and `isDynamicContext=false`. The filename's word "dynamic" cannot override native metadata. Engine initialization succeeds at smaller exploratory capacities, but that discrepancy needs upstream clarification and longer-context correctness tests before a consumer override. Lowering capacity also reduces retained chat length. The 2048 output difference means this sweep is not a quality-equivalence proof.

Evidence is in `resume-sdk-context-{1024,2048,4096}/`, including native logs, instrumentation results, cumulative JSONL, and thermal snapshots. The initial production 1024 attempt in `resume-context-1024/` correctly rejected a metadata-incompatible setting before loading native weights. It has no throughput result.

Reproduce a summary with `python3 docs/perf/summarize-gpu-results.py app/build/gpu-benchmark/resume-sdk-context-1024/cumulative-results.jsonl --backend GPU --context 1024`. The script selects the latest invocation and excludes iteration zero.

### New-default Auto

The production adapter selected GPU in all four runs, with no CPU-thread override and no fallback reason. Three measured repetitions after warm-up produced median decode **28.18529 tok/s**, observed first visible chunk **1101 ms**, load **5851 ms**, median sampled peak PSS **3281891328 bytes / 3.057 GiB**, and thermal 0/0/0 throughout. Every output hash matches the greedy 4096-context reference. This is **3.68304x** the matched 0.16.1 Auto+CPU8 baseline. It clears the 2x target and the preferred 3x target on this exact artifact and phone.

Runtime metadata reports context 4096, fixed context, and no speculative-decoding support. A speculative-on trial is therefore not offered for this artifact. These short default runs do not establish sustained performance or support for other artifacts. Evidence is in `resume-auto/`.

### Five-minute GPU loop

One persistent production engine, first generation excluded as warm-up, fresh conversation per measured prompt, same 4096 context/greedy sampler/thinking-off/64 output cap. Twenty measured samples span 307461 ms. Median decode is **33.18999 tok/s**; first-minute median is39.77307 and last-minute median30.84292, a22.45% decline. Minimum sample24.90297. Thermal status rises from0 to1 around150 seconds and ends at1. Peak sampled PSS is **6014105600bytes / 5.601 GiB**. Median observed TTFT is **13171ms**, with first-minute median12768 and last-minute median12421.

This workload includes conversation reset and allocation costs. Reset runs reach roughly double the initial loaded PSS and take12–17 seconds to deliver a first chunk, even though native decode remains fast. The reset implementation creates the replacement conversation before closing the previous one. That overlaps native resources; native allocation/first-use work is a candidate cause, not a proven full explanation. No change to ownership/cleanup order is justified solely by this run. The result does not promise a13-second delay on ordinary retained-chat turns, which do not reset every prompt.

Peak PSS exceeds the current generic admission heuristic substantially. Before broad GPU release, memory admission must account for measured graphics/session memory and replacement overlap. A safe reset/rebuild optimization needs its own lifecycle and failure-recovery qualification. A headless decode improvement is not a complete user-experience qualification.

Evidence is in `resume-sustained-gpu/`, including per-sample thermal/TTFT/decode/PSS under `samples`, native logs, before/after thermalservice output, and `mid-run-meminfo.txt`. Artifact, SHA, quantization, device, Android, and runtime are on the parent result record and apply to every nested sample.


## Code changes after the sustained run

A2 changed reset/rebuild to cancel and await active generation, close the old native conversation, then create the replacement. Failed allocation surfaces an error and leaves the selected history pending for the next send to retry. LiteRT-LM 0.18.0 supports this order through synchronous JNI conversation deletion and a still-live engine.

GPU admission adds `contextTokens * max(576 KiB, artifactBytes / 1024)` for graphics buffers to existing weights, overhead, and KV budgets. This rounds up the measured context-sweep slope of about 518 KiB/token and covers the 2.26 GB GL allocation at 4096. The reference artifact's one-session estimates are 1.83 GiB at 1024 and 3.64 GiB at 4096. Auto preserves the admission reason when GPU is skipped for CPU.

These changes are **unverified on the phone until re-run**. All measurements above predate them. Reset latency and driver memory release still need physical validation. Exact re-run commands and B's compatibility-estimator integration hook are in `/home/yvm/code/Mobie-research/handoff-A-gpu-runtime.md`. Backend defaults and benchmark harness are unchanged.


A2's latest required gates after concurrent B edits passed in 13s with 425 unit tests and no failures/errors/skips, lint 0 errors and 48 warnings, and debug assembly. Log `/tmp/mobie-A2-shared-gates.log`; isolated reports and APKs under `/tmp/mobie-A2-build/app/`. Test APK assembly passed in the preceding 424-test build, logged in `/tmp/mobie-A2-final-gates.log`; rebuild it after B's UI edits before installing. A2 ran no device commands.


## Phone unavailable at session end

The user requested an immediate stop before taking the phone. Agent A ran `adb -s RZCY81Q06XR shell am force-stop dev.yashasvm.mobie` successfully and stopped the local phone runner. No new phone command or instrumentation starts afterward. CPU4 instrumentation reported "Process crashed" after the force-stop; this is an interrupted test, not evidence of a spontaneous runtime failure.

Completed cases are frozen as `completed-results.jsonl` under `resume-sdk-context-{1024,2048,4096}/`, `resume-auto/`, and `resume-sustained-gpu/`. `resume-completed-index.json` records full model identity, quantization, SHA, phone serial, device, Android, runtime, and source file for each case. Original cumulative and native evidence remains intact. No interrupted sample is used for an aggregate or performance claim.

| Remaining phone work | Status |
| --- | --- |
| Five-minute CPU4 loop, after thermal status returns to 0 | not run (phone unavailable); previous invocation interrupted before a completed result |
| Cancel mid-generation and recover, unload then reload, CPU/GPU switch in one process, cache growth | not run (phone unavailable); `LiteRtGpuLifecycleProbeTest` is written and compiled, not executed |
| FP16 versus automatic activation, output-quality checks, repeatability | not run (phone unavailable); keep AUTO as the default and overrides experimental |
| Speculative-decoding trial on a supported artifact | not run (phone unavailable); current exact Qwen artifact advertises no support, so true remains blocked |
| Gallery-equivalent configuration versus tuned Mobie, matched workload and actual app caveats | not run (phone unavailable); no Mobie-vs-Gallery speed claim |
| Visible on-screen chat demo and screenshot in `/home/yvm/code/Mobie-research/` | not run (phone unavailable) |
| Longer-context correctness/history test for smaller exploratory allocations | not run (phone unavailable); production retains metadata-fixed 4096 |
| Release APK runtime verification after shrinker | not run (phone unavailable) |

## Code-only validation

`assembleRelease` passed in 1m33s using the isolated A build directory. The unsigned release APK contains the exact 0.18.0 AAR LICENSE, 16086 bytes, SHA-256 `ec40a2da15ab0e8b44a9f787ab210203d3cad32572f5f99744a88eb90ff9ba4e`, and THIRD_PARTY_NOTICE, 2198164 bytes, SHA-256 `52a70c82685debf85d4ea9d677615b8636ffd3b75435ce71f247bf5982fb2ba2`. Debug packaging also matches exactly. R8 warns that some dependency Kotlin metadata is newer than its parser; the build succeeds, but release runtime is unqualified.

Final `ANDROID_HOME=/home/yvm/Android/Sdk ./gradlew --init-script /tmp/mobie-A.init.gradle testDebugUnitTest lintDebug assembleDebug` passed in 48 seconds: 414 unit tests, zero failures/errors/skips; lint zero errors and 48 warnings; debug assembly passed. The final log is `app/build/gpu-benchmark/resume-final-gates.log`. B owns Advanced UI and emulator checks. Agent A's physical evidence cannot substitute for their Compose/navigation test requirements.

Subsequent B-reported validation passed with 415 unit tests, lint zero errors and 48 warnings, debug assembly, and 10 ComputeSettingsPanelTest cases on emulator-5554 only. This is separate from A's recorded 414-test run. Advanced controls preserve applied engine settings until explicit reload, and activation/speculative overrides remain unqualified on the phone. No additional physical evidence or performance result follows from the emulator checks.
