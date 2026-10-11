package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SamplerSettingsTest {
    @Test fun `defaults match the original runtime configuration`() {
        assertEquals(SamplerSettings(0.7, 40, 0.95, 0), SamplerSettings())
    }

    @Test fun `validates boundaries and round trips persisted values`() {
        val settings = SamplerSettings(0.0, 200, 1.0, -123)
        assertEquals(settings, SamplerSettingsCodec.decode(SamplerSettingsCodec.encode(settings)))
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(-0.1, 40, 0.95, 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(2.1, 40, 0.95, 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(0.7, 0, 0.95, 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(0.7, 201, 0.95, 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(0.7, 40, 0.0, 0).validated() }
        assertThrows(IllegalArgumentException::class.java) { SamplerSettings(Double.NaN, 40, 0.95, 0).validated() }
    }

    @Test fun `invalid persisted values fall back to safe defaults`() {
        assertEquals(SamplerSettings(), SamplerSettingsCodec.decode(mapOf("topK" to "bad")))
        assertEquals(SamplerSettings(), SamplerSettingsCodec.decode(mapOf("topP" to "NaN")))
    }
}
