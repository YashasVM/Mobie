package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ComputeOptionsTest {
    @Test fun defaultsPreserveArtifactSettingsAndAutomaticThreads() {
        val options = ComputeOptions()
        assertEquals(ComputeBackend.AUTO, options.backend)
        assertNull(options.cpuThreads)
        assertNull(options.contextTokens)
        assertEquals(1_024, options.maxOutputTokens)
        assertNull(options.thinkingEnabled)
        assertNull(options.thinkingTokenBudget)
        assertNull(options.speculativeDecoding)
        assertEquals(ActivationType.AUTO, options.activationType)
    }

    @Test fun rangesRejectInvalidControls() {
        for (value in listOf(0, 9)) assertThrows(IllegalArgumentException::class.java) { ComputeOptions(cpuThreads = value) }
        for (value in listOf(1_023, 4_097)) assertThrows(IllegalArgumentException::class.java) { ComputeOptions(contextTokens = value) }
        for (value in listOf(0, 1_025)) assertThrows(IllegalArgumentException::class.java) { ComputeOptions(maxOutputTokens = value) }
        for (value in listOf(-1, 1_025)) assertThrows(IllegalArgumentException::class.java) { ComputeOptions(thinkingTokenBudget = value) }
        assertEquals(0, ComputeOptions(thinkingTokenBudget = 0).thinkingTokenBudget)
        assertEquals(1_024, ComputeOptions(thinkingTokenBudget = 1_024).thinkingTokenBudget)
    }

    @Test fun explicitThreadsNeverExceedDetectedCores() {
        assertEquals(2, ComputeOptions(cpuThreads = 8).forCoreCount(2).cpuThreads)
        assertEquals(1, ComputeOptions(cpuThreads = 8).forCoreCount(0).cpuThreads)
        assertNull(ComputeOptions().forCoreCount(2).cpuThreads)
    }

    @Test fun dynamicContextIsBoundedByArtifactAndStaticContextCannotChange() {
        val dynamic = ComputeCapabilities(2_048, true, false, false)
        assertEquals(listOf(1_024, 2_048), dynamic.contextChoices())
        assertEquals(2_048, dynamic.resolveContext(null, 4_096))
        assertEquals(1_024, dynamic.resolveContext(1_024, 4_096))
        assertThrows(IllegalArgumentException::class.java) { dynamic.resolveContext(4_096, 4_096) }
        val fixed = dynamic.copy(dynamicContext = false)
        assertEquals(listOf(2_048), fixed.contextChoices())
        assertEquals(2_048, fixed.resolveContext(null, 1_024))
        assertThrows(IllegalArgumentException::class.java) { fixed.resolveContext(1_024, 4_096) }
    }

    @Test fun optionalFeaturesRequireArtifactSupport() {
        val basic = ComputeCapabilities(4_096, true, false, false)
        basic.validate(ComputeOptions(speculativeDecoding = false, thinkingEnabled = false))
        assertThrows(IllegalArgumentException::class.java) { basic.validate(ComputeOptions(speculativeDecoding = true)) }
        assertThrows(IllegalArgumentException::class.java) { basic.validate(ComputeOptions(thinkingTokenBudget = 64)) }
        basic.copy(supportsThinking = true, supportsSpeculativeDecoding = true)
            .validate(ComputeOptions(speculativeDecoding = true, thinkingTokenBudget = 64))
    }
}
