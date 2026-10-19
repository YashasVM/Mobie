package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamePresentationTest {
    private fun device(manufacturer: String, model: String, soc: String) = DeviceProfile(
        totalRamBytes = 1, availableRamBytes = 1, availableStorageBytes = 1,
        supportedAbis = listOf("arm64-v8a"), sdkInt = 35,
        manufacturer = manufacturer, model = model, socModel = soc,
    )

    @Test
    fun `emulator codenames are replaced with plain words`() {
        val emulator = device("Google", "sdk_gphone64_x86_64", "ranchu")
        assertEquals("Android emulator", deviceName(emulator))
        assertNull(chipName(emulator))
    }

    @Test
    fun `real phones keep their name and chip`() {
        val phone = device("google", "Pixel 8", "Tensor G3")
        assertEquals("Google Pixel 8", deviceName(phone))
        assertEquals("Tensor G3", chipName(phone))
    }
}
