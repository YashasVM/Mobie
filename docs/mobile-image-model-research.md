# Mobile image models in Mobie

Reviewed on 2026-10-11 against Mobie's pinned LiteRT-LM Android 0.16.1 and the primary sources linked below. This is an implementation assessment, not a device benchmark. No image-generation runtime was added, and this document does not establish a speedup or Android support for an untested artifact.

## What Mobie can represent today

Image understanding accepts a photo and produces text, for example a description or an answer about its contents. Image generation accepts text and produces a new image. The two require different model pipelines and output contracts.

Mobie's existing image path is image understanding. `NativeRuntimeAdapters.kt` sends `Content.ImageFile` alongside text, initializes a separate vision backend, and streams text. It attempts GPU vision on a physical ARM64 device, then CPU vision, then text-only initialization. Its engine configuration limits image capacity to one. The catalog's `AiModel.supportsVision` describes model metadata; it does not prove that vision initialized successfully on the current phone.

The pinned [0.16.1 Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/docs/api/kotlin/getting_started.md) documents image input for models that support it and separate text and vision backend choices. The [0.16.1 configuration source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt) exposes `visionBackend`, `maxNumImages`, and CPU, GPU, and NPU backends. These APIs establish a route to integration. They do not establish that a given model, GPU driver, or NPU library combination works on an Android device.

The current `RuntimeAdapter` publishes text tokens, statistics, completion, and errors. It has no generated-image event or diffusion progress event. A diffusion checkpoint must never be sent to the existing LiteRT-LM text adapter merely because it is an image model.

## Image handling changes in this PR

`ChatScreen.kt` now limits imports to 20 MiB of encoded bytes, 25 million decoded pixels, and 16,384 pixels per side. It accepts JPEG, PNG, and WebP according to decoded file metadata and requires a successful sampled decode before attachment. It preserves the original image bytes for inference. Failed imports and cancelled copies delete their temporary files. These limits are conservative product bounds, not runtime memory measurements.

Thumbnails sample according to the longest dimension, including panoramas, and reject paths outside the actual cache directory. Instrumentation coverage checks byte preservation, invalid data, encoded-size and dimension limits, panorama thumbnails, sibling-directory rejection, and cleanup after rejection. Chat copy distinguishes image input from image generation. Active capability reporting now disables photo input after text-only fallback. Durable history images, orientation normalization, and native vision correctness remain follow-up work.

## Candidate routes and evidence limits

| Task | Candidate route | Evidence available | Mobie status |
|---|---|---|---|
| Describe or question a photo | Vision-capable `.litertlm` with LiteRT-LM 0.16.1 | Pinned Android API accepts image input | Implemented path; exact artifact/device capability still needs validation |
| Classification, detection, segmentation | LiteRT `CompiledModel` or a matching MediaPipe task | Official LiteRT NPU segmentation samples | Requires its own artifact type, adapter, and output contract |
| Generate an image from text on GPU | MediaPipe Image Generator and a compatible converted foundation model | Official Android guide and codelab | Experimental candidate; unavailable in Mobie |
| Generate an image on Snapdragon NPU | Qualcomm QAIRT/QNN diffusion pipeline | Qualcomm's Stable Diffusion model entry | Candidate native integration; unavailable in Mobie |
| Generate an image on Dimensity NPU | MediaTek NeuroPilot model pipeline | First-party platform and device demonstrations | SDK/device access and exact pipeline validation required |

### Image understanding on CPU and GPU

The existing adapter is the smallest route to useful photo chat. Keep text inference and image encoding measurements separate. A faster vision encoder can improve image-prompt latency without improving text decode throughput. GPU initialization also needs Android native-library visibility, as the [pinned Kotlin guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/docs/api/kotlin/getting_started.md) describes.

Prioritize a small vision bundle such as the repository's `litert-community/SmolVLM2-500M` test candidate. This PR resolves and pins revision `78a25681a0811230bfa3079504a717e9a4a139d1`, 360,822,960 bytes, and SHA-256 `b808b328d845a600a33c5295f93d9217487317bd334dbc91b2a8d50e26e60ad0` from the [publisher's blob metadata](https://huggingface.co/api/models/litert-community/SmolVLM2-500M?blobs=true). The lifecycle test checks that generation completes with visible output, not that the answer correctly identifies the generated test image. It therefore cannot prove image comprehension or a comparative speedup.

The pinned vision lifecycle ran on Android 15 x86_64 during this audit and completed image replacement and text follow-up. Its blue/yellow synthetic image received the description "blue, white, and green". That fails the manual color-quality check. See [raw observations](benchmarks/2026-10-11-vision-emulator.txt). Nonblank output is insufficient evidence of image understanding, and this emulator result says nothing about a phone's GPU or NPU.

### NPU access requires model and chip agreement

The official [LiteRT-LM NPU instructions](https://developers.google.cn/edge/litert/next/litert_lm_npu) list chip-specific Gemma3-1B bundles for Qualcomm SM8550, SM8650, and SM8750 and MediaTek MT6989 and MT6991. They require matching runtime/dispatch libraries. Qualcomm's instructions also require QAIRT HTP libraries. These are language-model examples, not evidence that a vision encoder or diffusion pipeline works through the same bundle.

Google's [Snapdragon sample](https://github.com/google-ai-edge/litert-samples/blob/main/samples/litert/qualcomm/gemma3/npu/README.md) explicitly matches its artifact to SM8750 and describes a text-only app. Changing `Backend.CPU` to `Backend.NPU` while keeping an arbitrary generic model is not an implementation plan. Mobie's current rejection of hardware-specific artifacts should remain until the app can choose a validated artifact and the reviewed libraries for the exact chip.

For conventional vision models, current [LiteRT NPU documentation](https://developers.google.cn/edge/litert/next/npu) supports ahead-of-time and on-device compilation for Qualcomm and MediaTek. Its Android distribution example requires API 31 or newer. Google's Tensor route uses ahead-of-time compilation. This is a separate integration from Mobie's 0.16.1 wrapper; it does not justify changing Mobie's min SDK or declaring NPU support for all phones.

The official [segmentation sample device table](https://github.com/google-ai-edge/LiteRT/blob/main/litert/samples/image_segmentation/kotlin_npu/android_jit/README.md) contains Qualcomm, MediaTek, and Google Tensor groups with different Android requirements. That sample includes internal build commands and does not make a ready-to-build Mobie module. Treat the table as sample-specific evidence, not a universal image-model allowlist. A commercial phone name can cover different regional chips. Detect the actual SoC and verify its driver/library compatibility.

### GPU image generation

Google's [MediaPipe Android Image Generator guide](https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator/android?authuser=1) describes an experimental diffusion task, the `tasks-vision-image-generator` dependency, OpenCL library declarations, and conversion of a Stable Diffusion v1.5-compatible foundation model. The converted assets are a directory of model files, not a `.litertlm` bundle. The guide recommends downloading production models rather than bundling them into the APK. Pin a reviewed version rather than copying the guide's `latest.release` dependency.

The [official Android codelab](https://codelabs.developers.google.com/mp-image-generation-basic-android) recommends a physical device with at least 8 GB RAM. It provides synchronous final-image generation and step-by-step execution with optional intermediate images. All execution belongs off the main thread. Eight GB is a test recommendation, not a measured fit guarantee. Model assets, temporary tensors, the denoiser, and decoded output all contribute to the actual memory requirement.

This is the most direct documented GPU prototype to investigate after photo chat validation. It requires a new runtime and a review of added native libraries and licenses. Any conversion must occur in Mobie's controlled backend pipeline with pinned tools and inputs. The mobile app downloads recognized converted assets and never executes repository converters.

### Vendor image-generation pipelines

Qualcomm's [Stable Diffusion v1.5 entry](https://aihub.qualcomm.com/models/stable_diffusion_v1_5) describes a CLIP text encoder, U-Net denoiser, and VAE decoder and lists mobile devices and model license terms. Its page also returns an ambiguous default chipset-selection state. Use a specific downloadable artifact, SDK version, chip, and export recipe as the basis for a prototype; a general supported-device list is insufficient evidence for Mobie.

MediaTek's [NeuroPilot site](https://neuropilot.mediatek.com/) provides a route to tools and APIs but asks developers to apply for SDK and platform-document access. Its [Dimensity 9300 announcement](https://www.mediatek.com/press-room/mediateks-new-all-big-core-design-for-flagship-dimensity-9300-chipset-maximizes-smartphone-performance-and-efficiency) reports on-device Stable Diffusion image generation. This shows a vendor implementation exists. It does not provide Mobie's model, resolution, step count, runtime package, or independent result. Do not turn vendor demonstration numbers into an app performance claim.

## Implementation sequence

1. Make model capability copy explicit. This PR labels image generation unavailable and publishes active vision capability after load, so a text-only fallback disables image attachment and explains why. Model-details metadata can further distinguish "Text chat" from "Text + image input".
2. Bound local image ingestion before native decoding. Check decoded dimensions, cap encoded bytes and pixel count, normalize orientation, and resize only within a documented model input policy. Use app-private durable storage for photos the user expects a saved chat to retain. Clean abandoned attachments. Keep photographs and derived tensors local.
3. Keep thumbnail sampling bounded for wide or tall images. This PR fixes the previous policy, which downsampled only while both dimensions remained above 320. Add provider-driven cancellation tests and device evidence for import behavior beyond the file-URI instrumentation coverage.
4. The small vision test candidate now has a pinned revision, size and digest. Add literal expected-color and simple shape checks, plus follow-up, replacement-image, reset, cancellation, and reload tests. Record failures as failures rather than accepting any nonblank response.
5. Benchmark CPU text with CPU and GPU vision independently on the same physical phone. Keep the artifact, prompt, image bytes, context, and output limit identical. Keep the faster configuration only if correctness, RAM, and sustained behavior pass.
6. Prototype image generation behind an unavailable-by-default runtime registration. Add a generated-image result and bounded progress through core interfaces, a recognized multi-file artifact manifest, atomic verified installation, a diffusion-specific RAM estimator, and deterministic cleanup. Expose it only after exact ARM64 artifact/device tests pass. Keep the v1 catalog rule until a reviewed product change explicitly permits the new format.
7. Evaluate vendor NPU deployment only with the required chip, libraries, licensing, and artifacts in hand. Start with one chip and one known recipe. Provide a separately validated CPU/GPU artifact fallback rather than assuming an NPU-compiled bundle can fall back unchanged.

The bounded import, thumbnail and active photo-capability changes above are implemented in this PR. The remaining runtime proposals require separate work; this research document does not enable image generation or NPU execution.

## Physical-device validation protocol

Record the commit, build type, LiteRT-LM or image-runtime version, native library revisions, artifact revision and SHA-256, quantization, model context/input dimensions, exact SoC, RAM, Android version, GPU driver, and backend. Keep raw logs outside Git if they contain user content. Use synthetic test images and fixed benchmark prompts.

For image understanding, run at least five measured repetitions for each candidate after separate cold-load measurements. Alternate baseline and candidate order with a consistent cooldown. Record image preparation time, vision encoding time when the runtime exposes it, prefill throughput, first native output, first visible answer, second output callback, decode throughput, end-to-end duration, sampled peak memory, and thermal state. Callback count is not token count; an SDK chunk can contain multiple tokens. Do not label second-callback latency as second-token latency unless token boundaries are available.

Include a sustained ten-minute session, not just a cool initial run. Include a text-only control, a first photo, text follow-up, a replacement photo, restored history, cancellation, background/foreground transitions, memory pressure, malformed images, and unsupported hardware. The image tests should distinguish actual image recognition from plausible text-only answers.

For image generation, fix model hash, seed, resolution, sampler, step count, and prompt. Record load time, first preview time, final-image time, sampled peak RAM, temporary storage, thermals, output checks, cancellation latency, and resource release. Compare image quality at the same generation settings. Fewer denoising steps can reduce latency while changing quality; report that as a settings tradeoff.

Run the repository-required `./gradlew testDebugUnitTest lintDebug assembleDebug` and emulator UI tests for product changes. The current opt-in vision lifecycle command is:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.LiteRtVisionEndToEndTest \
  -Pandroid.testInstrumentationRunnerArguments.litertVisionE2E=true
```

Run native validation on a physical `arm64-v8a` phone. An emulator can verify UI behavior and unsupported-device handling. Report 1.5x or 2.2x gains only from comparable repeated measurements that retain acceptable output quality and fit within memory and thermal limits.
