# AGENTS.md

These instructions apply to the entire Mobie repository.

## Mission

Build a fast, consumer-friendly Android app that discovers compatible Hugging Face models, downloads them safely, and performs inference locally. Prefer a small, truthful MVP over broad but unreliable support.

## Non-negotiable product rules

1. Inference is local after a model has downloaded. Do not send prompts, outputs, embeddings, or model contents to a remote inference API.
2. Never claim a model or runtime works unless the exact artifact was loaded and tested on Android.
3. Before download, show artifact size, estimated RAM, free storage, runtime, quantization, license/gated state, and compatibility.
4. Block clearly impossible downloads. A warning may be user-overridable only when the model can plausibly run after memory is freed.
5. Downloads must resume, use app-private storage, survive process death, and validate a trusted checksum when available.
6. Never execute arbitrary code from a Hugging Face repository. Download only recognized model/config/tokenizer artifacts.
7. Store Hugging Face credentials only in Android Keystore-backed encrypted storage. Never log, commit, export, or place tokens in URLs.
8. Gated/private models must respect Hugging Face authentication, licenses, and access errors. Do not bypass access controls.
9. Conversion is a controlled backend pipeline. The mobile app submits a repo/model ID and displays server state; it does not execute untrusted converters.

## Architecture

The dependency flow is:

```text
ui → core interfaces/models ← data implementations
                         ↖ runtime adapters
```

- `core/model`: platform-independent model metadata and status types.
- `core/device`: device facts and a conservative compatibility policy.
- `core/runtime`: the only interface UI/domain code may use for inference.
- `data/catalog`: Hugging Face API access and strict mapping into domain models.
- `data/download`: WorkManager-based transfer, resume, progress, validation, and atomic finalization.
- `data/conversion`: conversion-service contract only.
- `ui`: Compose UI and presentation state. It must not parse Hugging Face responses or call native runtime APIs directly.

Keep the app as one Gradle module until build time, ownership, or test isolation gives a concrete reason to split it. Package boundaries are intentional and should remain clean.

## Runtime contract

Every engine implements `RuntimeAdapter`:

- declare one `ModelFormat`;
- load only an artifact it understands;
- stream tokens through `InferenceEvent.Token`;
- publish measured performance through `InferenceEvent.Stats`;
- release native memory deterministically in `unload`;
- surface load/generation errors without crashing the process.

Initial mappings:

| Artifact | Runtime | Rule |
|---|---|---|
| `.gguf` | llama.cpp | Accept only supported architectures and quantizations verified by the bundled build. |
| `.litertlm` / supported `.task` | LiteRT-LM | Validate required metadata and accelerator/device support. |

ONNX and ExecuTorch must be added as new adapters; do not add format-specific branches to the UI. Native libraries must be pinned to a reviewed revision, built reproducibly for `arm64-v8a`, and accompanied by license notices.

## Compatibility and model metadata

- Prefer artifact metadata from Hugging Face siblings/LFS; treat missing sizes or hashes as unknown, never zero-sized proof.
- Memory estimates must include model weights, KV cache, runtime overhead, and prompt context. Update the estimator per runtime as real measurements become available.
- Use total RAM to determine whether a model can ever fit and current available RAM to decide whether to warn.
- Keep an explicit incompatibility reason that the UI can explain.
- Recommend mobile-sensible quantization; do not automatically choose the smallest file if its quality/runtime compatibility is worse.
- Do not label a base Transformers repository as directly runnable merely because it could be converted.

## Coding rules

- Kotlin and Jetpack Compose are the default. Use coroutines/Flow for async streams.
- Target JDK 17, min SDK 26, and the repository-pinned SDK/dependency versions.
- Prefer standard AndroidX and small, well-maintained dependencies. Explain any large SDK addition in the PR.
- No `GlobalScope`, blocking network/disk work on the main thread, hard-coded secrets, silent catches, or unbounded retries.
- Make downloads atomic: write `.part`, validate, then rename.
- Keep user-facing copy plain and specific. Avoid developer jargon on primary screens.
- Do not introduce dependency injection frameworks, databases, or multiple modules without a demonstrated MVP need.
- Format Kotlin consistently and keep public behavior documented.

## Testing requirements

Every pull request must run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

UI or navigation changes must also pass `connectedDebugAndroidTest` on an emulator. Native-runtime claims still require the physical ARM64 test described below; an x86_64 emulator is not evidence that a model engine works on a phone.

Add or update tests for:

- compatibility boundaries (RAM, storage, ABI, format);
- Hugging Face response and artifact mapping;
- resume behavior, HTTP range edge cases, checksum failure, and atomic finalization;
- credential redaction and gated-model error handling;
- runtime lifecycle and cancellation;
- conversion status transitions.

Native runtime changes also require at least one physical `arm64-v8a` device test with the exact model artifact recorded. Report model, quantization, device, Android version, peak RAM, first-token latency, tokens/second, thermals/throttling notes, and result.

## Pull request checklist

- State the user-visible outcome in a few lines.
- Note any new network destination, permission, native library, or credential handling.
- Include tests and the exact commands/results.
- Attach UI evidence for visual changes.
- Confirm no prompts or inference data leave the device.
- Confirm unsupported models remain clearly labeled and cannot reach the wrong runtime.
- Keep generated model files, tokens, keystores, APKs, and local configuration out of Git.
