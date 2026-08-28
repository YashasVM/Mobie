# Mobie

Mobie is an Android browser and local runner for mobile-friendly AI models hosted on Hugging Face. It aims to offer a wider catalog than curated gallery apps while being honest about device limits, model formats, licenses, and gated access.

## MVP status

The repository currently contains a buildable native Android foundation with:

- Hugging Face featured-model browsing and search
- filtering to directly supported artifacts or text models that may be convertible
- GGUF and LiteRT-LM artifact detection
- RAM, storage, ABI, and format compatibility checks
- model details with runtime, quantization, size, license, and device guidance
- resumable background downloads using HTTP range requests
- foreground download execution for multi-gigabyte models
- optional SHA-256 verification when Hugging Face exposes a digest
- encrypted local storage for a Hugging Face access token
- conversion-request API boundary and visible request state
- runtime-adapter interfaces for llama.cpp and LiteRT-LM

Native llama.cpp and LiteRT-LM binaries are **not yet bundled**. Their adapters fail clearly instead of simulating inference. The next runtime milestone is to add audited native dependencies, wire streaming generation, and expose measured tokens/second and RAM usage.

## Product flow

```text
Hugging Face model
  → compatibility resolver
  → supported or converted artifact
  → resumable download + checksum
  → runtime adapter
  → local inference UI
```

Unsupported models can be submitted to a conversion service and move through:

```text
Requested → Reviewing → Converting → Testing → Ready / Unsupported
```

## Project structure

```text
app/src/main/java/dev/yashasvm/mobie/
├── core/
│   ├── device/       Device profiling and compatibility policy
│   ├── model/        Shared domain models
│   ├── runtime/      Runtime-neutral API and engine adapters
│   └── security/     Secure Hugging Face credential storage
├── data/
│   ├── catalog/      Hugging Face API mapping
│   ├── conversion/   Conversion request API
│   └── download/     Resumable, verified model downloads
└── ui/               Compose screens and state
```

## Build

Requirements: Android Studio with JDK 17 and Android SDK 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI runs the same checks on every pull request, installs the APK on an API 35 emulator, runs Compose UI smoke tests, and uploads a debug APK after a successful build.

## Conversion service

Set `CONVERSION_API_URL` through a build configuration when a backend exists. With no endpoint configured, requests remain local placeholders marked `Requested`; the app never pretends conversion has started remotely.

Expected request:

```json
{ "modelId": "organization/model-name" }
```

The service should own license review, conversion, quantization, reproducibility, device testing, artifact publication, and status updates. Do not run arbitrary model code in the Android client.

## Privacy and safety

- Model inference must be completely local after download.
- The Hugging Face token is encrypted with Android Keystore-backed storage and is sent only to Hugging Face.
- Gated models require the user to accept the upstream terms and authenticate.
- Compatibility warnings must not be bypassed silently.
- Model files stay in app-private storage.

See [AGENTS.md](AGENTS.md) before making changes.
