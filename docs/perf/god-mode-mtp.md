# GOD MODE: MTP drafter and artifact selection

Investigated 2026-10-20 against Google AI Edge Gallery `main` at commit 8f5f2a0 and LiteRT-LM v0.18.0, the runtime both apps pin. This is a source and configuration analysis plus Google's published numbers. It is **not** a Mobie measurement on a phone. No claim that Mobie beats Gallery is made until the phone benchmark in `god-mode-benchmark-protocol.md` has run.

## Why configuration is the only lever

Gallery and Mobie call the same LiteRT-LM 0.18.0 engine, so on the same phone, file and backend their native decode speed is the same. Neither app's per-token Kotlin work is significant: Gallery's metrics tracker is a no-op (`MetricsTracker.create` returns `NoOpMetricsTracker`). A real difference must come from what each app asks the engine to do.

## What Gallery asks for (Gemma 4 E2B, AI Chat)

From `LlmChatModelHelper.kt` and `model_allowlists/1_0_20.json`:

- File `gemma-4-E2B-it.litertlm` (2,588,147,712 bytes, revision 6e5c4f1e in Gallery's allowlist).
- `Backend.GPU()` (allowlist accelerators `gpu,cpu`), GPU vision, CPU audio, `maxNumTokens = 4000`, `cacheDir = null` (the SDK caches beside the model).
- Sampler topK 64, topP 0.95, temperature 1.0. Thinking off unless toggled.
- `ExperimentalFlags.enableSpeculativeDecoding = false` unless the user turns on the speculative-decoding switch, whose default is `false`.

## What the artifact offers

The container header (read with a 4 KiB range request; fixtures in `app/src/test/resources/litertlm-headers/`) shows:

| File | Main graph | MTP drafter | Vision | Runs on CPU |
| --- | --- | --- | --- | --- |
| `gemma-4-E2B-it.litertlm` | `tf_lite_prefill_decode` | yes, 44.3 MB | yes | yes |
| `gemma-4-E2B-it-gpu.litertlm` | `tf_lite_artisan_text_decoder`, `backend_constraint=gpu_artisan` | no | no | no |

The same split holds for Gemma 4 E4B and 12B. I surveyed the 57 most-downloaded `litert-community` repos with LiteRT-LM files; 21 gated files returned 401 and were not inspected. Among chat models, only the Gemma 4 files carry a drafter: the generic builds and the Tensor-specific ones. The only other drafter is in a Qwen3 text-to-speech model, which is not a chat model.

The drafter verifies by exact match: a drafted token is kept only while it equals the main model's own choice at that position (`runtime/executor/llm_litert_mtp_drafter.cc`, `Draft`). In 0.18.0 that choice is **greedy**. `CreateGreedySampler` builds both the drafter and verifier samplers with top-k 1, so while the drafter runs, temperature, top-k and top-p shape only the first token after the prompt. Gallery has the same behavior when a user turns its switch on. The owner chose speed by default: Auto runs the drafter on GPU, the settings panel says replies follow the model's most likely words while it runs, and Off restores sampled replies. Greedy text can still differ from non-drafter greedy text through floating-point rounding in batched verification.

## Google's published effect (not a Mobie measurement)

From the `litert-community/gemma-4-E2B-it-litert-lm` model card, Samsung S26 Ultra, LiteRT-LM:

| Backend | Baseline decode | With drafter, by task | Ratio |
| --- | --- | --- | --- |
| GPU | 51.5 tok/s | 66.5 (free form) to 91.7 (summarize) | 1.29x to 1.78x |
| CPU | 40.7 tok/s | 36.3 (code) to 47.5 (summarize) | 0.89x to 1.17x |

Even Google's best published case is under 2x on decode alone, and the CPU drafter can be slower. A 2x end-to-end result therefore cannot be promised from the drafter alone. It has to be measured, including time to first token and whole-reply time.

## What Mobie had wrong before this change

1. **Gemma 4 was invisible.** Its repos publish no `pipeline_tag`, so the catalog mapped them to `UNKNOWN` and filtered them out. Gallery's headline model could not even be downloaded for a comparison.
2. **The wrong file would have been recommended.** The filename rule treated `-gpu` as a portable hint, so the smaller GPU-only artisan build ranked first. That build has no CPU fallback for Auto, no drafter and no vision.
3. **Auto speculation meant "off".** Null was passed to the engine, which leaves the drafter unbuilt, matching Gallery's default.

## What changed

- `LiteRtArtifactHeader` parses the container header with bounds checks (12,000 fuzzed mutations fail closed). Classification mirrors `schema/model_info/model_info.cc`.
- The catalog range-reads headers only where names cannot be trusted: repositories without task tags, and files named `gpu` or `opencl`. Vendor-named files are still excluded without a request. If a probe fails, the old filename behavior applies.
- Header facts set the model type (chat or vision only for an LLM container), mark accelerator-bound builds hardware-specific with a stated reason, and record whether a file ships a drafter. The fact is persisted with the install.
- `SpeculativeDecodingPolicy`: Auto builds the drafter during engine initialization on GPU, and not on CPU. Building it at init means the first reply does not pay the lazy load. Explicit On and Off are honored on any backend. Every conversation states its request explicitly, because the SDK keeps a lazily loaded drafter and its null setting would reuse it. If Auto's initialization fails, the same configuration is retried once without the drafter, the fallback is explained, and Auto stops asking for the drafter on that engine. A 256 MiB allowance (above the card's largest +220 MB) enters the pre-download, compute-panel and load RAM estimates alike, including a drafter that a live settings change makes the SDK load later.
- Every engine initialization sets the SDK's process-wide `ExperimentalFlags` inside one lock and restores the previous values, so no engine inherits another load's request.
- The running label reads `GPU · draft model`. Speed history (schema 5) records whether the drafter ran, so runs with and without it are never compared.

## Still unproven

- Exact-build phone results: decode tok/s, first token, whole-reply time, peak RAM and thermals for `gallery_default`, `gallery_mtp`, `mobie_default` and `mobie_gallery_sampler`.
- Drafter initialization success on Adreno and Mali GPUs. The retry exists because this is unverified.
- Whether the GPU-only artisan build is faster than the generic file on some GPUs. It is excluded until it is tested.
