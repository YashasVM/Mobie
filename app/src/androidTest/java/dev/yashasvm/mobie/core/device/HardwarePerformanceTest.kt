package dev.yashasvm.mobie.core.device

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.RuntimeComputeSettings
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwarePerformanceTest {
    @Test fun userBackendChoiceSurvivesSettingsRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RuntimeComputeSettings(context)
        val original = settings.selectedBackend()
        try {
            for (backend in listOf(ComputeBackend.CPU, ComputeBackend.GPU, ComputeBackend.AUTO)) {
                settings.setBackend(backend)
                assertEquals(backend, RuntimeComputeSettings(context).selectedBackend())
            }
        } finally {
            settings.setBackend(original)
        }
    }

    @Test fun detectionReadsRealAndroidHardwareAndReleasesItsOffscreenContext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = HardwareProfileProvider(context)
        val first = provider.detect()
        val second = provider.detect()
        assertTrue(first.cpuCoreCount > 0)
        assertTrue(first.totalRamBytes > 0)
        assertEquals(first.cpuCoreCount, first.cpuClusters.sumOf { it.coreIds.size })
        assertEquals(first.gpu, second.gpu)
        assertNotNull("Emulator should expose its GL renderer through EGL", first.gpu)
        assertFalse(checkNotNull(first.gpu).renderer.isBlank())
        // A software-rendered emulator verifies detection only, not native inference support.
    }

    @Test fun actualBackendStatsSurvivePreferenceRecreationWithoutMixingAutoOrCpuAndGpu() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("decode_speed_history", Context.MODE_PRIVATE)
        val existing = preferences.all.keys.toSet()
        val identity = "instrumentation/${UUID.randomUUID()}@source"
        try {
            DecodeSpeedStore(context).record(identity, InferenceStats(
                tokensPerSecond = 18.0, ramBytes = 0, decodeTokenCount = 32,
                compute = ActiveCompute(ComputeBackend.GPU),
            ))
            DecodeSpeedStore(context).record(identity, InferenceStats(
                tokensPerSecond = 8.0, ramBytes = 0, decodeTokenCount = 32,
                compute = ActiveCompute(ComputeBackend.CPU, cpuThreads = 4, fallbackReason = "GPU load failed"),
            ))
            DecodeSpeedStore(context).record(identity, InferenceStats(
                tokensPerSecond = 99.0, ramBytes = 0, decodeTokenCount = 32,
                compute = ActiveCompute(ComputeBackend.AUTO),
            ))
            DecodeSpeedStore(context).record(identity, InferenceStats(
                tokensPerSecond = 99.0, ramBytes = 0, decodeTokenCount = 32,
            ))
            val saved = DecodeSpeedStore(context).read(identity)
            assertEquals(2, saved.size)
            assertEquals(18.0, saved[ComputeBackend.GPU]?.lowTokensPerSecond)
            assertEquals(8.0, saved[ComputeBackend.CPU]?.lowTokensPerSecond)
            assertEquals(DecodeSpeedSource.MEASURED, saved[ComputeBackend.CPU]?.source)
            assertTrue(DecodeSpeedStore(context).read("$identity-new-revision").isEmpty())
        } finally {
            preferences.edit().apply {
                (preferences.all.keys - existing).forEach { remove(it) }
            }.commit()
        }
    }
}
