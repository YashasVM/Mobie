package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.BackendAdmission
import dev.yashasvm.mobie.core.device.CpuCluster
import dev.yashasvm.mobie.core.device.DecodeSpeed
import dev.yashasvm.mobie.core.device.DecodeSpeedConfidence
import dev.yashasvm.mobie.core.device.DecodeSpeedSource
import dev.yashasvm.mobie.core.device.GpuHardware
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeSpeedPresentationTest {
    @Test fun `chip summary uses detected IDs renderer and core count`() {
        val hardware = HardwareProfile("Qualcomm", "SM8650", 8,
            listOf(CpuCluster((0..7).toList(), 3_300_000)), GpuHardware("Adreno (TM) 750", "Qualcomm"), true, 8L * 1024 * 1024 * 1024)
        assertEquals("Snapdragon 8 Gen 3 · Adreno 750 GPU · 8 CPU cores", hardwareSummary(hardware))
        assertEquals("8 × 3.3 GHz · 8.0 GB RAM", hardwareDetails(hardware))
        assertEquals("SM9999 · GPU not reported · 8 CPU cores", hardwareSummary(hardware.copy(socModel = "SM9999", gpu = null)))
        assertTrue(hardwareSummary(null).contains("Reading"))
    }

    @Test fun `estimate rounds range outwards without decimal false precision`() {
        assertEquals("8–20 tokens/s", decodeSpeedLabel(DecodeSpeed(8.3, 19.4, DecodeSpeedSource.ESTIMATED)))
        assertEquals("0.3–0.9 tokens/s", decodeSpeedLabel(DecodeSpeed(0.31, 0.85, DecodeSpeedSource.ESTIMATED)))
        assertEquals("<0.1 tokens/s", decodeSpeedLabel(DecodeSpeed(0.01, 0.09, DecodeSpeedSource.ESTIMATED)))
        assertEquals("estimate", decodeSpeedSourceLabel(DecodeSpeed(8.3, 19.4, DecodeSpeedSource.ESTIMATED)))
    }

    @Test fun `restricted hardware estimates show lower confidence`() {
        val fallback = DecodeSpeed(2.0, 15.0, DecodeSpeedSource.ESTIMATED, confidence = DecodeSpeedConfidence.LOW)
        assertEquals("estimate · lower confidence", decodeSpeedSourceLabel(fallback))
        assertEquals("measured on this phone · last run", decodeSpeedSourceLabel(
            fallback.copy(source = DecodeSpeedSource.MEASURED, sampleCount = 1)))
    }

    @Test fun `actual runs are marked measured and single run does not invent a range`() {
        val single = DecodeSpeed(10.3, 10.3, DecodeSpeedSource.MEASURED, 1)
        assertEquals("≈10 tokens/s", decodeSpeedLabel(single))
        assertEquals("measured on this phone · last run", decodeSpeedSourceLabel(single))
        val repeated = DecodeSpeed(9.1, 12.2, DecodeSpeedSource.MEASURED, 4)
        assertEquals("9–12 tokens/s", decodeSpeedLabel(repeated))
        assertEquals("measured on this phone · 4 recent runs", decodeSpeedSourceLabel(repeated))
    }

    @Test fun `backend RAM labels name the size and status, and stay silent for unknown size`() {
        val gib = 1024L * 1024L * 1024L
        assertEquals("needs ~3.0 GB RAM · Free memory first", backendRamLabel(result(Compatibility.WARNING, 3 * gib)))
        assertEquals(null, backendRamLabel(result(Compatibility.WARNING, 0)))
        assertEquals("Auto uses GPU · needs ~3.0 GB RAM",
            autoPickLabel(BackendAdmission(ComputeBackend.GPU, result(Compatibility.COMPATIBLE, 3 * gib))))
        assertEquals("Auto uses CPU", autoPickLabel(BackendAdmission(ComputeBackend.CPU, result(Compatibility.COMPATIBLE, 0))))
    }

    private fun result(status: Compatibility, ramBytes: Long) = CompatibilityResult(status, "", ramBytes)
}
