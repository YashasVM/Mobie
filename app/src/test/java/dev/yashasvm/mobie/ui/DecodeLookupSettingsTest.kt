package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.DecodeMeasurement
import dev.yashasvm.mobie.core.device.DecodeRunSettings
import dev.yashasvm.mobie.core.device.DecodeSpeed
import dev.yashasvm.mobie.core.device.DecodeSpeedSource
import dev.yashasvm.mobie.core.device.matchDecodeMeasurement
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeLookupSettingsTest {
    private val applied = ComputeOptions(ComputeBackend.CPU, cpuThreads = null, contextTokens = null,
        activationType = ActivationType.FP16, speculativeDecoding = true)
    private val running = ActiveCompute(ComputeBackend.CPU, cpuThreads = 4, contextTokens = 2_048)
    private val speed = DecodeSpeed(10.0, 12.0, DecodeSpeedSource.MEASURED, sampleCount = 2)
    /** What the ViewModel records for a run of [applied] on [running]. */
    private val recorded = DecodeMeasurement(ComputeBackend.CPU, DecodeRunSettings.fromApplied(applied, running), speed)

    @Test fun `recording keeps activation speculation and resolved counts`() {
        assertEquals(DecodeRunSettings(null, null, ActivationType.FP16, true, 4, 2_048), recorded.settings)
    }

    @Test fun `the loaded settings match their own measurement exactly`() {
        val query = decodeLookupSettings(applied, applied, running, ComputeBackend.CPU)
        val match = matchDecodeMeasurement(listOf(recorded), ComputeBackend.CPU, query)
        assertTrue(checkNotNull(match).exactSettings)
    }

    @Test fun `conversation-only pending changes still use the running engine's resolution`() {
        val pending = applied.copy(maxOutputTokens = 64, prefillPrefaceOnInit = true)
        assertEquals(recorded.settings, decodeLookupSettings(pending, applied, running, ComputeBackend.CPU))
    }

    @Test fun `pending engine settings never borrow the loaded engine's resolution`() {
        listOf(applied.copy(cpuThreads = 2), applied.copy(contextTokens = 4_096), applied.copy(backend = ComputeBackend.AUTO))
            .forEach { pending ->
                val query = decodeLookupSettings(pending, applied, running, ComputeBackend.CPU)
                assertNull(query.resolvedCpuThreads)
                assertNull(query.resolvedContextTokens)
                assertFalse(matchDecodeMeasurement(listOf(recorded), ComputeBackend.CPU, query)?.exactSettings ?: false)
            }
    }

    @Test fun `unloaded settings are never an exact match for a resolved run`() {
        val query = decodeLookupSettings(applied, null, null, ComputeBackend.CPU)
        assertEquals(DecodeRunSettings(null, null, ActivationType.FP16, true), query)
        assertFalse(checkNotNull(matchDecodeMeasurement(listOf(recorded), ComputeBackend.CPU, query)).exactSettings)
    }

    @Test fun `different activation or speculation never matches`() {
        listOf(applied.copy(activationType = ActivationType.AUTO), applied.copy(speculativeDecoding = null),
            applied.copy(speculativeDecoding = false)).forEach { pending ->
            assertNull(matchDecodeMeasurement(listOf(recorded), ComputeBackend.CPU,
                decodeLookupSettings(pending, applied, running, ComputeBackend.CPU)))
        }
    }

    @Test fun `a CPU engine's resolution is not applied to the GPU row`() {
        val query = decodeLookupSettings(applied, applied, running, ComputeBackend.GPU)
        assertNull(query.resolvedCpuThreads)
        assertNull(query.resolvedContextTokens)
    }
}
