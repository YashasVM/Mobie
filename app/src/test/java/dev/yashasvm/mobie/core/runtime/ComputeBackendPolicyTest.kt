package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ComputeBackendPolicyTest {
    @Test fun autoTriesGpuThenCpuAndKeepsGpuFirst() {
        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), ComputeBackendPolicy.attempts(ComputeBackend.AUTO, true, null))
        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), ComputeBackendPolicy.attempts(ComputeBackend.AUTO, true, ComputeBackend.GPU))
        assertEquals(listOf(ComputeBackend.CPU), ComputeBackendPolicy.attempts(ComputeBackend.AUTO, false, ComputeBackend.GPU))
    }

    @Test fun onlyAutoGpuFirstWinsAreRemembered() {
        assertTrue(ComputeBackendPolicy.shouldRememberSuccess(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU), ComputeBackend.GPU))
        // A CPU fallback after GPU failed or was skipped for memory is not a preference.
        assertFalse(ComputeBackendPolicy.shouldRememberSuccess(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU), ComputeBackend.CPU))
        assertFalse(ComputeBackendPolicy.shouldRememberSuccess(ComputeBackend.AUTO, listOf(ComputeBackend.CPU), ComputeBackend.CPU))
        // Explicit choices never change what Auto remembers.
        assertFalse(ComputeBackendPolicy.shouldRememberSuccess(ComputeBackend.GPU, listOf(ComputeBackend.GPU), ComputeBackend.GPU))
        assertFalse(ComputeBackendPolicy.shouldRememberSuccess(ComputeBackend.CPU, listOf(ComputeBackend.CPU), ComputeBackend.CPU))
    }

    @Test fun legacyCpuMemoryIsIgnoredAndGpuIsKept() {
        val entries = mutableMapOf<String, String>()
        val memory = SuccessfulBackendMemory(object : BackendKeyValueStore {
            override fun get(key: String) = entries[key]
            override fun put(key: String, value: String) { entries[key] = value }
            override fun remove(key: String) { entries.remove(key) }
        })
        memory.remember("repo/artifact@revision-1", "device-a", ComputeBackend.GPU)
        assertEquals(ComputeBackend.GPU, memory.get("repo/artifact@revision-1", "device-a"))
        // An older build could persist a CPU fallback as preferred; it must not pin Auto to CPU.
        for (key in entries.keys.toList()) entries[key] = ComputeBackend.CPU.name
        assertNull(memory.get("repo/artifact@revision-1", "device-a"))
    }

    @Test fun explicitBackendsNeverSilentlyFallback() {
        assertEquals(listOf(ComputeBackend.GPU), ComputeBackendPolicy.attempts(ComputeBackend.GPU, false, null))
        assertEquals(listOf(ComputeBackend.CPU), ComputeBackendPolicy.attempts(ComputeBackend.CPU, true, null))
        assertEquals(emptyList<ComputeBackend>(), ComputeBackendPolicy.attempts(ComputeBackend.NPU, true, null))
    }

    @Test fun memoryIsScopedToArtifactAndDeviceAndInvalidates() {
        val entries = mutableMapOf<String, String>()
        val memory = SuccessfulBackendMemory(object : BackendKeyValueStore {
            override fun get(key: String) = entries[key]
            override fun put(key: String, value: String) { entries[key] = value }
            override fun remove(key: String) { entries.remove(key) }
        })
        memory.remember("repo/artifact@revision-1", "device-a", ComputeBackend.GPU)
        assertEquals(ComputeBackend.GPU, memory.get("repo/artifact@revision-1", "device-a"))
        assertNull(memory.get("repo/artifact@revision-2", "device-a"))
        assertNull(memory.get("repo/artifact@revision-1", "device-b"))
        memory.invalidate("repo/artifact@revision-1", "device-a")
        assertNull(memory.get("repo/artifact@revision-1", "device-a"))
    }

    @Test fun gpuFailureFallsBackToCpuWithVisibleReasonAndInvalidatesMemory() {
        val tried = mutableListOf<ComputeBackend>()
        val invalidated = mutableListOf<ComputeBackend>()
        val result = loadWithBackendFallback(
            attempts = listOf(ComputeBackend.GPU, ComputeBackend.CPU),
            load = { backend ->
                tried += backend
                if (backend == ComputeBackend.GPU) throw IllegalStateException("OpenCL unavailable")
                "engine"
            },
            onFailure = { backend, _ -> invalidated += backend },
        )
        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), tried)
        assertEquals(ComputeBackend.CPU, result.backend)
        assertEquals("engine", result.value)
        assertEquals("GPU load failed: OpenCL unavailable. Using CPU.", result.fallbackReason)
        assertEquals(listOf(ComputeBackend.GPU), invalidated)
    }

    @Test fun firstAttemptSuccessHasNoFallbackReason() {
        val result = loadWithBackendFallback(listOf(ComputeBackend.GPU, ComputeBackend.CPU), load = { "engine" })
        assertEquals(ComputeBackend.GPU, result.backend)
        assertNull(result.fallbackReason)
    }

    @Test fun explicitBackendFailureIsSurfacedWithoutCpuRetry() {
        val tried = mutableListOf<ComputeBackend>()
        try {
            loadWithBackendFallback<String>(listOf(ComputeBackend.GPU), load = {
                tried += it
                throw IllegalStateException("no GPU")
            })
            fail("expected GPU failure to propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("no GPU", expected.message)
        }
        assertEquals(listOf(ComputeBackend.GPU), tried)
    }

    @Test fun lastFailureCarriesEarlierFailureAsSuppressed() {
        val gpu = IllegalStateException("gpu")
        try {
            loadWithBackendFallback<String>(listOf(ComputeBackend.GPU, ComputeBackend.CPU), load = {
                if (it == ComputeBackend.GPU) throw gpu
                throw IllegalStateException("cpu")
            })
            fail("expected CPU failure")
        } catch (expected: IllegalStateException) {
            assertEquals("cpu", expected.message)
            assertSame(gpu, expected.suppressed.single())
        }
    }

    @Test fun cancellationIsNotTreatedAsBackendFailure() {
        val tried = mutableListOf<ComputeBackend>()
        try {
            loadWithBackendFallback<String>(listOf(ComputeBackend.GPU, ComputeBackend.CPU), load = {
                tried += it
                throw CancellationException("stopped")
            })
            fail("expected cancellation")
        } catch (expected: CancellationException) {
            assertTrue(expected.message == "stopped")
        }
        assertEquals(listOf(ComputeBackend.GPU), tried)
    }

    @Test fun noAttemptsMeansNpuIsUnavailable() {
        try {
            loadWithBackendFallback<String>(emptyList(), load = { "unreachable" })
            fail("expected unavailable backend")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("NPU"))
        }
    }

    @Test fun autoSkipsGpuWhenItsWeightCopyDoesNotFit() {
        val admitted = admitAttemptsForMemory(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU)) {
            if (it == ComputeBackend.GPU) "no room" else null
        }
        assertEquals(listOf(ComputeBackend.CPU), admitted.attempts)
        assertTrue(admitted.skippedReason!!.startsWith("GPU skipped"))
    }

    @Test fun autoKeepsGpuWhenItFits() {
        val admitted = admitAttemptsForMemory(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU)) { null }
        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), admitted.attempts)
        assertNull(admitted.skippedReason)
    }

    @Test fun lastAutoAttemptAndExplicitChoicesAreNeverFiltered() {
        val onlyCpu = admitAttemptsForMemory(ComputeBackend.AUTO, listOf(ComputeBackend.CPU)) { "no room" }
        assertEquals(listOf(ComputeBackend.CPU), onlyCpu.attempts)
        val explicitGpu = admitAttemptsForMemory(ComputeBackend.GPU, listOf(ComputeBackend.GPU)) { "no room" }
        assertEquals(listOf(ComputeBackend.GPU), explicitGpu.attempts)
        assertNull(explicitGpu.skippedReason)
    }
}
