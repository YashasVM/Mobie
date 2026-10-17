package dev.yashasvm.mobie.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareProfileParserTest {
    private val legacyCpuInfo = """
        processor : 0
        model name : ARMv7 Processor rev 3 (v7l)
        CPU implementer : 0x41
        Hardware : Qualcomm Technologies, Inc MSM8998
        Revision : 0000
    """.trimIndent()

    @Test fun `uses platform SoC and falls back only when unreported`() {
        assertEquals("SM8650", HardwareProfileParser.socModel(" SM8650 ", legacyCpuInfo))
        for (name in listOf(null, "", "unknown", " UNKNOWN ", "0")) {
            assertEquals("Qualcomm Technologies, Inc MSM8998", HardwareProfileParser.socModel(name, legacyCpuInfo))
        }
        assertNull(HardwareProfileParser.socModel(null, "processor : 0\nCPU architecture : 8\n"))
        assertNull(HardwareProfileParser.socModel("unknown", "Hardware : unknown"))
    }

    @Test fun `parses sysfs ranges and whitespace without duplicates`() {
        assertEquals((0..7).toList(), HardwareProfileParser.cpuIds("0-7\n"))
        assertEquals(listOf(0, 1, 2, 4, 5), HardwareProfileParser.cpuIds("0-2,4 5 4\n"))
        assertEquals(listOf(2, 3, 4), HardwareProfileParser.cpuIds("2 3\t4\n"))
        for (bad in listOf("", "-1", "2-1", "0-999999999", "1-2-3", "0 no", "2147483648")) {
            assertTrue(HardwareProfileParser.cpuIds(bad).isEmpty())
        }
    }

    @Test fun `parses maximum kHz and rejects missing invalid or implausible values`() {
        assertEquals(3_300_000, HardwareProfileParser.maxFrequencyKhz("3300000\n"))
        for (bad in listOf(null, "", "unknown", "0", "-1", "3300 MHz", "999999999999", "99999999")) {
            assertNull(HardwareProfileParser.maxFrequencyKhz(bad))
        }
    }

    @Test fun `groups a one plus three plus four policy topology`() {
        val related = (0..3).associateWith { "0 1 2 3\n" } +
            (4..6).associateWith { "4 5 6\n" } + mapOf(7 to "7\n")
        val frequencies = (0..3).associateWith { "1800000\n" } +
            (4..6).associateWith { "2800000\n" } + mapOf(7 to "3300000\n")
        assertEquals(listOf(
            CpuCluster(listOf(0, 1, 2, 3), 1_800_000),
            CpuCluster(listOf(4, 5, 6), 2_800_000),
            CpuCluster(listOf(7), 3_300_000),
        ), HardwareProfileParser.cpuClusters((0..7).toList(), frequencies, related))
    }

    @Test fun `restricted policy files group by frequency and retain unknown cores`() {
        assertEquals(listOf(
            CpuCluster(listOf(0, 1), 1_800_000), CpuCluster(listOf(2), 3_000_000), CpuCluster(listOf(3), null),
        ), HardwareProfileParser.cpuClusters(listOf(3, 1, 2, 0),
            mapOf(0 to "1800000", 1 to "1800000", 2 to "3000000"), emptyMap()))
        assertEquals(listOf(CpuCluster(listOf(0, 1), null)),
            HardwareProfileParser.cpuClusters(listOf(0, 1), emptyMap(), mapOf(0 to "8-9", 1 to "bad")))
    }

    @Test fun `software renderer is distinct from physical GPU information`() {
        assertTrue(GpuHardware("Google SwiftShader", "Google").isSoftwareRenderer)
        assertFalse(GpuHardware("ANGLE (Qualcomm, Adreno 750, Vulkan)", "Google").isSoftwareRenderer)
        assertFalse(GpuHardware("ANGLE (Vulkan emulator)", "Google").isSoftwareRenderer)
        assertTrue(GpuHardware("ANGLE (Google, SwiftShader Device, Vulkan)", "Google").isSoftwareRenderer)
        for (renderer in listOf("llvmpipe", "softpipe", "Software Rasterizer", "swrast", "lavapipe")) {
            assertTrue(GpuHardware(renderer, null).isSoftwareRenderer)
        }
        assertFalse(GpuHardware("Adreno (TM) 750", "Qualcomm").isSoftwareRenderer)
        assertFalse(GpuHardware("Mali-G715", "ARM").isSoftwareRenderer)
    }
}
