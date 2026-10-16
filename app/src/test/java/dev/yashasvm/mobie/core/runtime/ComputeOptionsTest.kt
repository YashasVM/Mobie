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
        assertEquals(2_048, dynamic.resolveContext(null) { 4_096 })
        assertEquals(1_024, dynamic.resolveContext(1_024) { 4_096 })
        assertThrows(IllegalArgumentException::class.java) { dynamic.resolveContext(4_096) { 4_096 } }
        val fixed = dynamic.copy(dynamicContext = false)
        assertEquals(listOf(2_048), fixed.contextChoices())
        assertEquals(2_048, fixed.resolveContext(null) { 1_024 })
        assertThrows(IllegalArgumentException::class.java) { fixed.resolveContext(1_024) { 4_096 } }
    }

    @Test fun fixedAndExplicitContextsDoNotReadMemoryForAnUnusedEstimate() {
        val unavailableMemory = { error("Memory must not be read to choose this context") }
        val fixed = ComputeCapabilities(4_096, false, false, false)
        assertEquals(4_096, fixed.resolveContext(null, unavailableMemory))
        assertEquals(4_096, fixed.resolveContext(4_096, unavailableMemory))
        assertThrows(IllegalArgumentException::class.java) { fixed.resolveContext(1_024, unavailableMemory) }
        val dynamic = fixed.copy(dynamicContext = true, maxContextTokens = 2_048)
        assertEquals(1_024, dynamic.resolveContext(1_024, unavailableMemory))
        assertThrows(IllegalArgumentException::class.java) { dynamic.resolveContext(4_096, unavailableMemory) }
        assertThrows(IllegalArgumentException::class.java) { fixed.copy(maxContextTokens = 0).resolveContext(null, unavailableMemory) }
    }

    @Test fun dynamicAutoContextUsesFreshMemoryEstimateAndArtifactLimit() {
        val dynamic = ComputeCapabilities(2_048, true, false, false)
        var estimate = 4_096
        var reads = 0
        val automatic = { reads += 1; estimate }
        assertEquals(2_048, dynamic.resolveContext(null, automatic))
        estimate = 1_024
        assertEquals(1_024, dynamic.resolveContext(null, automatic))
        assertEquals(2, reads)
    }

    @Test fun optionalFeaturesRequireArtifactSupport() {
        val basic = ComputeCapabilities(4_096, true, false, false)
        basic.validate(ComputeOptions(speculativeDecoding = false, thinkingEnabled = false))
        assertThrows(IllegalArgumentException::class.java) { basic.validate(ComputeOptions(speculativeDecoding = true)) }
        assertThrows(IllegalArgumentException::class.java) { basic.validate(ComputeOptions(thinkingTokenBudget = 64)) }
        basic.copy(supportsThinking = true, supportsSpeculativeDecoding = true)
            .validate(ComputeOptions(speculativeDecoding = true, thinkingTokenBudget = 64))
    }

    @Test fun thinkingBudgetIsSentOnlyWithExplicitOn() {
        // Model default sends no config, so the artifact's own reasoning default stands.
        assertNull(ComputeOptions().thinkingRequest())
        assertNull(ComputeOptions(thinkingTokenBudget = 64).thinkingRequest())
        assertEquals(ThinkingRequest(true, 64), ComputeOptions(thinkingEnabled = true, thinkingTokenBudget = 64).thinkingRequest())
        assertEquals(ThinkingRequest(true, UNLIMITED_THINKING_BUDGET), ComputeOptions(thinkingEnabled = true).thinkingRequest())
        assertEquals(ThinkingRequest(false, UNLIMITED_THINKING_BUDGET),
            ComputeOptions(thinkingEnabled = false, thinkingTokenBudget = 64).thinkingRequest())
    }
}
