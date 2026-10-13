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

Production-adapter 1024/2048/4096 probes, new-default Auto, five-minute GPU and CPU4 workloads, cancellation/reload/backend switching, precision trials, and Gallery-equivalent configuration are pending in this draft. Every new result records artifact SHA, device, Android, backend, context, quantization, peak PSS, observed TTFT, native tok/s, and thermal status.

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
