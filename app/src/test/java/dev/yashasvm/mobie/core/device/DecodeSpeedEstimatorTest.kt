package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.runtime.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeSpeedEstimatorTest {
    private val hardware = HardwareProfile("Qualcomm", "SM8650", 8,
        listOf(CpuCluster((0..3).toList(), 2_200_000), CpuCluster((4..7).toList(), 3_300_000)),
        GpuHardware("Adreno (TM) 750", "Qualcomm"), true, 8L * GIB)

    @Test fun `CPU range follows bandwidth divided by weight traffic`() {
        val result = checkNotNull(DecodeSpeedEstimator.predict(hardware, 2_000_000_000L, ComputeBackend.CPU).speed)
        assertEquals(55e9 * 0.05 / (2e9 * 1.5), result.lowTokensPerSecond, 0.0001)
        assertEquals(80e9 * 0.06 / (2e9 * 1.2), result.highTokensPerSecond, 0.0001)
        assertEquals(DecodeSpeedSource.ESTIMATED, result.source)
        assertTrue(result.lowTokensPerSecond < result.highTokensPerSecond)
    }

    @Test fun `doubling model bytes halves predicted decode speed`() {
        for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU)) {
            val small = checkNotNull(DecodeSpeedEstimator.predict(hardware, GIB, backend).speed)
            val big = checkNotNull(DecodeSpeedEstimator.predict(hardware, 2 * GIB, backend).speed)
            assertEquals(small.lowTokensPerSecond / 2, big.lowTokensPerSecond, 0.0001)
            assertEquals(small.highTokensPerSecond / 2, big.highTokensPerSecond, 0.0001)
        }
    }

    @Test fun `CPU and GPU use distinct effective bandwidth factors`() {
        val cpu = checkNotNull(DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.CPU).speed)
        val gpu = checkNotNull(DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.GPU).speed)
        assertTrue(cpu.highTokensPerSecond < gpu.highTokensPerSecond)
        assertTrue(gpu.highTokensPerSecond > cpu.lowTokensPerSecond)
    }

    @Test fun `detected clock core count and RAM change the hardware class`() {
        val entry = hardware.copy(cpuCoreCount = 4, totalRamBytes = 2 * GIB,
            cpuClusters = listOf(CpuCluster((0..3).toList(), 1_800_000)))
        val mid = hardware.copy(cpuClusters = listOf(CpuCluster((0..7).toList(), 2_600_000)))
        val speeds = listOf(entry, mid, hardware).map {
            checkNotNull(DecodeSpeedEstimator.predict(it, GIB, ComputeBackend.CPU).speed).highTokensPerSecond
        }
        assertTrue(speeds[0] < speeds[1] && speeds[1] < speeds[2])
    }

    @Test fun `measured runs supersede heuristic even when hardware or size is unknown`() {
        val measured = DecodeSpeed(9.0, 11.0, DecodeSpeedSource.MEASURED, 3)
        assertEquals(measured, DecodeSpeedEstimator.predict(null, 0, ComputeBackend.GPU, measured).speed)
        assertEquals(measured, DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.CPU, measured).speed)
    }

    @Test fun `unknown model hardware and unqualified backend produce no invented numbers`() {
        assertNull(DecodeSpeedEstimator.predict(hardware, 0, ComputeBackend.CPU).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware, -1, ComputeBackend.CPU).speed)
        assertNull(DecodeSpeedEstimator.predict(null, GIB, ComputeBackend.CPU).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.AUTO).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.NPU).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware.copy(gpu = null), GIB, ComputeBackend.GPU).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware.copy(openClLibraryPresent = false), GIB, ComputeBackend.GPU).speed)
        assertNull(DecodeSpeedEstimator.predict(hardware.copy(gpu = GpuHardware("SwiftShader", "Google")), GIB, ComputeBackend.GPU).speed)
    }

    @Test fun `malformed measurements are ignored and very large artifacts do not overflow`() {
        val corrupt = DecodeSpeed(Double.NaN, Double.POSITIVE_INFINITY, DecodeSpeedSource.MEASURED, 1)
        assertEquals(DecodeSpeedSource.ESTIMATED,
            DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.CPU, corrupt).speed?.source)
        val huge = checkNotNull(DecodeSpeedEstimator.predict(hardware, Long.MAX_VALUE, ComputeBackend.CPU).speed)
        assertTrue(huge.lowTokensPerSecond.isFinite() && huge.lowTokensPerSecond > 0)
        assertTrue(huge.highTokensPerSecond > huge.lowTokensPerSecond)
    }

    @Test fun `denied clocks fall back to other signals with lower confidence`() {
        val denied = hardware.copy(cpuClusters = listOf(CpuCluster((0..7).toList(), null)))
        for (profile in listOf(denied, denied.copy(cpuClusters = emptyList()),
            denied.copy(socModel = null, cpuCoreCount = 4, totalRamBytes = 2 * GIB),
            denied.copy(socModel = null, totalRamBytes = 0, cpuCoreCount = 1))) {
            for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU)) {
                val estimate = checkNotNull(DecodeSpeedEstimator.predict(profile, GIB, backend).speed)
                assertEquals(DecodeSpeedSource.ESTIMATED, estimate.source)
                assertEquals(DecodeSpeedConfidence.LOW, estimate.confidence)
                assertTrue(estimate.lowTokensPerSecond > 0 && estimate.highTokensPerSecond > estimate.lowTokensPerSecond)
            }
        }
        val normal = checkNotNull(DecodeSpeedEstimator.predict(hardware, GIB, ComputeBackend.CPU).speed)
        val fallback = checkNotNull(DecodeSpeedEstimator.predict(denied, GIB, ComputeBackend.CPU).speed)
        assertTrue(fallback.lowTokensPerSecond < normal.lowTokensPerSecond)
        assertTrue(fallback.highTokensPerSecond > normal.highTokensPerSecond)
        val measured = DecodeSpeed(7.0, 8.0, DecodeSpeedSource.MEASURED, 2)
        assertEquals(measured, DecodeSpeedEstimator.predict(denied, GIB, ComputeBackend.CPU, measured).speed)
    }

    @Test fun `restricted-clock fallback changes with reported SoC cores and RAM`() {
        val reported = hardware.copy(cpuClusters = emptyList())
        val anonymous = reported.copy(socModel = null)
        val small = anonymous.copy(cpuCoreCount = 4, totalRamBytes = 2 * GIB)
        val estimates = listOf(small, anonymous, reported).map {
            checkNotNull(DecodeSpeedEstimator.predict(it, GIB, ComputeBackend.CPU).speed)
        }
        assertTrue(estimates[0].highTokensPerSecond < estimates[1].highTokensPerSecond)
        assertTrue(estimates[1].highTokensPerSecond < estimates[2].highTokensPerSecond)
    }

    @Test fun `S24 Ultra dynamic INT4 ranges contain supplied measured CPU and GPU rates`() {
        val s24 = hardware.copy(totalRamBytes = 12 * GIB,
            cpuClusters = listOf(CpuCluster(listOf(0, 1), 2_200_000),
                CpuCluster((2..6).toList(), 3_200_000), CpuCluster(listOf(7), 3_390_000)))
        // Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm, not the distinct mixed INT4 artifact.
        val cpu = checkNotNull(DecodeSpeedEstimator.predict(s24, 344_671_744, ComputeBackend.CPU).speed)
        val gpu = checkNotNull(DecodeSpeedEstimator.predict(s24, 344_671_744, ComputeBackend.GPU).speed)
        assertTrue(cpu.lowTokensPerSecond <= 6.0 && cpu.highTokensPerSecond >= 8.0)
        assertTrue(gpu.lowTokensPerSecond <= 30.0 && gpu.highTokensPerSecond >= 30.0)
        assertTrue(cpu.lowTokensPerSecond >= 5.0 && cpu.highTokensPerSecond <= 12.0)
        assertTrue(gpu.lowTokensPerSecond >= 20.0 && gpu.highTokensPerSecond <= 45.0)
        // Marketing names must never select a phone-specific throughput value.
        val renamed = s24.copy(socModel = "different chip", gpu = GpuHardware("different GPU", null))
        assertEquals(cpu, DecodeSpeedEstimator.predict(renamed, 344_671_744, ComputeBackend.CPU).speed)
        assertEquals(gpu, DecodeSpeedEstimator.predict(renamed, 344_671_744, ComputeBackend.GPU).speed)
    }

    private companion object { const val GIB = 1024L * 1024 * 1024 }
}
