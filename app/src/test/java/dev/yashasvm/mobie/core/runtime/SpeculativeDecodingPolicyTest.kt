package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeculativeDecodingPolicyTest {
    @Test
    fun `Auto builds the drafter on GPU only`() {
        assertEquals(true, SpeculativeDecodingPolicy.engineRequest(null, artifactHasDrafter = true, ComputeBackend.GPU))
        assertNull(SpeculativeDecodingPolicy.engineRequest(null, artifactHasDrafter = true, ComputeBackend.CPU))
    }

    @Test
    fun `explicit choices are honored on every backend`() {
        for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU)) {
            assertEquals(true, SpeculativeDecodingPolicy.engineRequest(true, artifactHasDrafter = true, backend))
            assertEquals(false, SpeculativeDecodingPolicy.engineRequest(false, artifactHasDrafter = true, backend))
        }
    }

    @Test
    fun `an artifact without a drafter never sends a request the SDK would reject`() {
        for (requested in listOf<Boolean?>(null, true, false)) {
            for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU)) {
                assertNull(SpeculativeDecodingPolicy.engineRequest(requested, artifactHasDrafter = false, backend))
                assertFalse(SpeculativeDecodingPolicy.buildsDrafter(requested, artifactHasDrafter = false, backend))
            }
        }
    }

    @Test
    fun `conversation request overrides the engine and null inherits it`() {
        assertTrue(SpeculativeDecodingPolicy.conversationUses(null, engineBuiltDrafter = true))
        assertFalse(SpeculativeDecodingPolicy.conversationUses(null, engineBuiltDrafter = false))
        assertFalse(SpeculativeDecodingPolicy.conversationUses(false, engineBuiltDrafter = true))
        // The SDK loads the drafter lazily for an explicit conversation-level On.
        assertTrue(SpeculativeDecodingPolicy.conversationUses(true, engineBuiltDrafter = false))
    }

    @Test
    fun `only an Auto drafter may be dropped after a failed initialization`() {
        assertTrue(SpeculativeDecodingPolicy.mayRetryWithoutDrafter(null, engineRequest = true))
        assertFalse(SpeculativeDecodingPolicy.mayRetryWithoutDrafter(true, engineRequest = true))
        assertFalse(SpeculativeDecodingPolicy.mayRetryWithoutDrafter(null, engineRequest = null))
        assertFalse(SpeculativeDecodingPolicy.mayRetryWithoutDrafter(false, engineRequest = false))
    }

    @Test
    fun `a built drafter is included in the RAM estimate`() {
        val artifact = ModelArtifact("gemma-4-E2B-it.litertlm", "", 2_588_147_712L, format = ModelFormat.LITERT_LM)
        for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU)) {
            val without = checkNotNull(RuntimeLoadMemoryPolicy.estimate(artifact, backend))
            val with = checkNotNull(RuntimeLoadMemoryPolicy.estimate(artifact, backend, speculativeDrafter = true))
            assertEquals(SpeculativeDecodingPolicy.DRAFTER_RAM_ALLOWANCE_BYTES, with.estimatedRamBytes - without.estimatedRamBytes)
            assertEquals(SpeculativeDecodingPolicy.DRAFTER_RAM_ALLOWANCE_BYTES, with.runtimeOverheadBytes - without.runtimeOverheadBytes)
        }
    }

    @Test
    fun `the compute label names the draft model only when it runs`() {
        assertEquals("GPU · draft model", ActiveCompute(ComputeBackend.GPU, speculativeDecoding = true).label)
        assertEquals("GPU", ActiveCompute(ComputeBackend.GPU).label)
        assertEquals("CPU · 4 threads", ActiveCompute(ComputeBackend.CPU, cpuThreads = 4).label)
    }
}
