package dev.yashasvm.mobie.core.runtime

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeComputeSettingsTest {
    @Test fun readClampsPersistedOversizeAndWritesSanitizedValue() {
        val storage = TestPreferences(8)
        val settings = RuntimeComputeSettings(storage.preferences) { 2 }
        assertEquals(2, settings.selectedCpuThreads())
        assertEquals(2, storage.cpuThreads)
    }

    @Test fun zeroAndNullRemainAuto() {
        val storage = TestPreferences(0)
        val settings = RuntimeComputeSettings(storage.preferences) { 4 }
        assertNull(settings.selectedCpuThreads())
        settings.setCpuThreads(8)
        assertEquals(4, storage.cpuThreads)
        settings.setCpuThreads(null)
        assertEquals(0, storage.cpuThreads)
        settings.setCpuThreads(0)
        assertNull(settings.selectedCpuThreads())
    }

    @Test fun recreationRetainsSanitizedValueAndAuto() {
        val storage = TestPreferences(8)
        RuntimeComputeSettings(storage.preferences) { 2 }.selectedCpuThreads()
        assertEquals(2, RuntimeComputeSettings(storage.preferences) { 8 }.selectedCpuThreads())
        RuntimeComputeSettings(storage.preferences) { 8 }.setCpuThreads(null)
        assertNull(RuntimeComputeSettings(storage.preferences) { 2 }.selectedCpuThreads())
        assertEquals(0, storage.cpuThreads)
    }

    @Test fun detectedCoreCountRefreshSanitizesPersistedOverride() {
        val storage = TestPreferences(8)
        val settings = RuntimeComputeSettings(storage.preferences) { 8 }
        assertEquals(8, settings.selectedCpuThreads())
        assertEquals(3, settings.selectedCpuThreads(coreCount = 3))
        assertEquals(3, storage.cpuThreads)
        settings.setCpuThreads(6, coreCount = 2)
        assertEquals(2, settings.selectedCpuThreads())
    }

    @Test fun corruptNegativeAndLargeCoreCountsHaveFiniteBounds() {
        val storage = TestPreferences(-9)
        val settings = RuntimeComputeSettings(storage.preferences) { 0 }
        assertEquals(1, settings.selectedCpuThreads())
        settings.setCpuThreads(99, coreCount = 32)
        assertEquals(8, storage.cpuThreads)
    }

    /** Implements the Android preferences boundary without adding a mocking dependency. */
    private class TestPreferences(var cpuThreads: Int) {
        private val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt" -> { cpuThreads = args!![1] as Int; proxy }
                "apply" -> null
                else -> error("Unexpected editor operation: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            when (method.name) {
                "getInt" -> cpuThreads
                "edit" -> editor
                else -> error("Unexpected preferences operation: ${method.name}")
            }
        } as SharedPreferences
    }
}
