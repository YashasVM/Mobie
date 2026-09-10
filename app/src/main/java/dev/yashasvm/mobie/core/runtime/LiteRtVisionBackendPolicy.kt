package dev.yashasvm.mobie.core.runtime

internal object LiteRtVisionBackendPolicy {
    fun shouldAttemptGpu(
        supportedAbis: Array<String>,
        fingerprint: String,
        model: String,
        hardware: String,
        product: String,
    ): Boolean {
        val normalizedFingerprint = fingerprint.lowercase()
        val normalizedModel = model.lowercase()
        val normalizedHardware = hardware.lowercase()
        val normalizedProduct = product.lowercase()
        val emulator =
            normalizedFingerprint.startsWith("generic") ||
                "emulator" in normalizedFingerprint ||
                "emulator" in normalizedModel ||
                "android sdk built for" in normalizedModel ||
                normalizedHardware == "goldfish" ||
                normalizedHardware == "ranchu" ||
                "sdk_gphone" in normalizedProduct ||
                "emulator" in normalizedProduct

        if (emulator) return false

        // LiteRT-LM's Android GPU backend requires a real OpenCL-capable device. Android emulators
        // and x86/x86_64 test devices normally expose SwiftShader rather than a usable OpenCL stack,
        // so initializing GPU vision there adds latency and can fail before CPU fallback is useful.
        return supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
    }
}
