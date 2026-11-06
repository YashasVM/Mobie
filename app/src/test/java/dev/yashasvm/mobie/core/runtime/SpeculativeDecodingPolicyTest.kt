package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun `conversation requests are explicit so a lazily loaded drafter cannot leak into Auto on CPU`() {
        assertEquals(true, SpeculativeDecodingPolicy.conversationRequest(null, artifactHasDrafter = true, ComputeBackend.GPU))
        assertEquals(false, SpeculativeDecodingPolicy.conversationRequest(null, artifactHasDrafter = true, ComputeBackend.CPU))
        assertEquals(true, SpeculativeDecodingPolicy.conversationRequest(true, artifactHasDrafter = true, ComputeBackend.CPU))
        assertEquals(false, SpeculativeDecodingPolicy.conversationRequest(false, artifactHasDrafter = true, ComputeBackend.GPU))
        assertNull(SpeculativeDecodingPolicy.conversationRequest(null, artifactHasDrafter = false, ComputeBackend.GPU))
        assertTrue(SpeculativeDecodingPolicy.conversationUses(null, artifactHasDrafter = true, ComputeBackend.GPU))
        assertFalse(SpeculativeDecodingPolicy.conversationUses(null, artifactHasDrafter = true, ComputeBackend.CPU))
        assertFalse(SpeculativeDecodingPolicy.conversationUses(true, artifactHasDrafter = false, ComputeBackend.GPU))
    }

    @Test
    fun `engine initialization sets flags only inside the guard and restores previous values`() {
        var speculation: Boolean? = false
        var benchmark = false
        val seen = mutableListOf<Pair<Boolean?, Boolean>>()
        fun initialize(request: Boolean?, fail: Boolean = false) = LiteRtEngineInitialization.withFlags(
            speculation = request,
            benchmark = true,
            readSpeculation = { speculation },
            writeSpeculation = { speculation = it },
            readBenchmark = { benchmark },
            writeBenchmark = { benchmark = it },
        ) {
            seen += speculation to benchmark
            if (fail) throw IllegalStateException("native failure")
        }
        initialize(true)
        initialize(null)
        assertThrows(IllegalStateException::class.java) { initialize(true, fail = true) }
        assertEquals(listOf<Pair<Boolean?, Boolean>>(true to true, null to true, true to true), seen)
        assertEquals(false, speculation)
        assertFalse(benchmark)
    }

    @Test
    fun `concurrent engine initializations never observe each other's request`() {
        var speculation: Boolean? = null
        val violations = java.util.concurrent.atomic.AtomicInteger()
        val threads = (0 until 8).map { index ->
            Thread {
                repeat(200) {
                    val request = index % 2 == 0
                    LiteRtEngineInitialization.withFlags(
                        speculation = request, benchmark = true,
                        readSpeculation = { speculation }, writeSpeculation = { speculation = it },
                        readBenchmark = { true }, writeBenchmark = {},
                    ) {
                        Thread.yield()
                        if (speculation != request) violations.incrementAndGet()
                    }
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(0, violations.get())
        assertNull(speculation)
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
