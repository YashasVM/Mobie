package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtVisionBackendPolicyTest {
    @Test
    fun realArm64DeviceCanAttemptGpuVision() {
        assertTrue(
            LiteRtVisionBackendPolicy.shouldAttemptGpu(
                supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a"),
                fingerprint = "samsung/e3qxxx/e3q:16/BP2A/user/release-keys",
                model = "SM-S928B",
                hardware = "qcom",
                product = "e3qxxx",
            ),
        )
    }

    @Test
    fun x86EmulatorSkipsGpuVision() {
        assertFalse(
            LiteRtVisionBackendPolicy.shouldAttemptGpu(
                supportedAbis = arrayOf("x86_64", "x86"),
                fingerprint = "google/sdk_gphone64_x86_64/emu64xa:36/TEST/dev-keys",
                model = "sdk_gphone64_x86_64",
                hardware = "ranchu",
                product = "sdk_gphone64_x86_64",
            ),
        )
    }

    @Test
    fun arm64EmulatorAlsoSkipsGpuVision() {
        assertFalse(
            LiteRtVisionBackendPolicy.shouldAttemptGpu(
                supportedAbis = arrayOf("arm64-v8a"),
                fingerprint = "generic/sdk_gphone64_arm64/emu64a:36/TEST/dev-keys",
                model = "Android SDK built for arm64",
                hardware = "ranchu",
                product = "sdk_gphone64_arm64",
            ),
        )
    }

    @Test
    fun realX86DeviceUsesCpuVisionInsteadOfAssumingOpenCl() {
        assertFalse(
            LiteRtVisionBackendPolicy.shouldAttemptGpu(
                supportedAbis = arrayOf("x86_64"),
                fingerprint = "vendor/device/product:16/release-keys",
                model = "x86 tablet",
                hardware = "intel",
                product = "tablet",
            ),
        )
    }
}
