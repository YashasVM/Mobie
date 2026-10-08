# Android inference optimization

Reviewed on 2026-10-08. Mobie pins LiteRT-LM Android 0.16.1. No physical ARM64 inference benchmark was available for this pass.

## Rust and the existing runtime

Rust can implement a native Android library, but changing the wrapper language does not change the model weights, KV cache, or matrix multiplication kernels. Mobie already calls native inference through LiteRT-LM's Kotlin/JNI API. A Rust wrapper around that same engine would still run the same kernels. This is an architectural inference, not a benchmark result. See the [official engine implementation](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Engine.kt).

[Candle](https://github.com/huggingface/candle) and [mistral.rs](https://github.com/EricLBuehler/mistral.rs) are Rust alternatives. Their documented capabilities do not establish that Mobie's exact artifacts work faster or use less RAM on Android. Adopting either would require a new adapter, compatible artifacts, reproducible native builds, license notices, and device validation. No relative speed claim is justified by the research gathered here.

## LiteRT-LM options worth measuring

Mobie currently uses two CPU worker threads at most for text inference. Its GPU path is for the vision encoder. The existing context policy already reduces allocation when RAM is constrained, and the app preserves the native conversation between ordinary completed turns to reuse its context.

The official [v0.18.0 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.18.0), published October 6, adds model introspection, on-demand KV cache growth on NPU, and GPU attention mask pruning. These changes do not establish a benefit for Mobie's CPU path. Keep 0.16.1 pinned until an SDK upgrade is tested with the exact supported artifacts.

Text GPU inference is a candidate experiment using the [official Android API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md). Its benefit depends on the artifact, GPU driver, memory use, and sustained thermals. Catching a Kotlin exception cannot recover from a native process crash. Do not enable it globally based on unrelated model-card benchmarks.

Benchmark CPU thread counts of 1, 2, and 4 on the target phone rather than choosing all available cores. More threads are not proof of faster decode on a heterogeneous, memory-constrained mobile SoC.

## llama.cpp as a future adapter

[llama.cpp's Android instructions](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md) document NDK builds and warn that excessive context sizes can exhaust memory. Its [public C API](https://github.com/ggml-org/llama.cpp/blob/master/include/llama.h) exposes memory mapping and key/value cache types. Its [backend documentation](https://github.com/ggml-org/llama.cpp) includes ARM CPU optimizations and Vulkan.

For a future GGUF adapter, evaluate quantized weights, a bounded context, supported quantized KV types, memory mapping, and CPU versus Vulkan. Quantized KV caches reduce the storage per cached element, but speed and output quality need measurement. Memory mapping lets file-backed pages be demand-paged and reclaimed; it does not eliminate the working set of weights used repeatedly during decoding. A model much larger than available RAM may cause repeated storage reads and become unusably slow. Do not relax compatibility checks because mmap exists, or force pages into RAM with mlock by default.

This is a substantial runtime addition, so GGUF remains disabled under the repository's v1 rules.

## Verification before changing native configuration

Use the same artifact SHA-256, prompt, output budget, context size, and device for baseline and candidate builds. Record load latency, first-token latency, prefill and decode throughput, sampled peak RAM, Android version, CPU/GPU backend, and thermal behavior. Repeat warm runs and include a sustained session. End-of-generation PSS, which the app currently reports, is not peak RAM.

The repository includes an opt-in real-model lifecycle test:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.yashasvm.mobie.LiteRtEndToEndTest \
  -Pandroid.testInstrumentationRunnerArguments.litertE2E=true
```

It downloads a checksum-verified Qwen3 0.6B INT4 artifact and exercises generation, cancellation, reset, and reload. Run it on a physical ARM64 phone for native-runtime claims. An x86_64 emulator can verify UI and application behavior but cannot establish phone inference performance.
