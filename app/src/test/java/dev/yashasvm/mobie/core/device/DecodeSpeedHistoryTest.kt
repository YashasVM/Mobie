package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeSpeedHistoryTest {
    private val values = mutableMapOf<String, String>()
    private val store = object : DecodeSpeedKeyValueStore {
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun keys(): Set<String> = values.keys.toSet()
        override fun remove(key: String) { values.remove(key) }
    }
    private val history = DecodeSpeedHistory(store, "device-build-a", "0.18.0")

    @Test fun `remembers actual range across store recreation and separates model backend and device`() {
        history.record("repo/model@revision", ComputeBackend.CPU, 9.0, 32)
        history.record("repo/model@revision", ComputeBackend.CPU, 12.0, 40)
        history.record("repo/model@revision", ComputeBackend.GPU, 20.0, 30)
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertEquals(DecodeSpeed(9.0, 12.0, DecodeSpeedSource.MEASURED, 2), recreated.read("repo/model@revision", ComputeBackend.CPU))
        assertEquals(20.0, recreated.read("repo/model@revision", ComputeBackend.GPU)?.lowTokensPerSecond)
        assertNull(recreated.read("repo/other@revision", ComputeBackend.CPU))
        assertNull(recreated.read("repo/model@new-revision", ComputeBackend.CPU))
        assertNull(DecodeSpeedHistory(store, "other-device", "0.18.0").read("repo/model@revision", ComputeBackend.CPU))
        assertTrue(values.keys.all { it.startsWith("decode.") && !it.contains("repo") })
    }

    @Test fun `ignores invalid rates and short replies without losing a previous measurement`() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            history.record("model", ComputeBackend.CPU, rate, 20)
        }
        history.record("model", ComputeBackend.CPU, 5.0, 1)
        history.record("model", ComputeBackend.AUTO, 5.0, 20)
        history.record("model", ComputeBackend.NPU, 5.0, 20)
        history.record("", ComputeBackend.CPU, 5.0, 20)
        assertTrue(values.isEmpty())
        history.record("model", ComputeBackend.CPU, 10.0, 20)
        assertEquals(10.0, history.record("model", ComputeBackend.CPU, Double.NaN, 20)?.lowTokensPerSecond)
    }

    @Test fun `uses recent runs instead of retaining old thermal or thread settings forever`() {
        for (rate in 1..10) history.record("model", ComputeBackend.CPU, rate.toDouble(), 20)
        assertEquals(DecodeSpeed(3.0, 10.0, DecodeSpeedSource.MEASURED, 8), history.read("model", ComputeBackend.CPU))
    }

    @Test fun `corrupted persisted values do not become measurements`() {
        history.record("model", ComputeBackend.CPU, 10.0, 20)
        values[values.keys.single { it != DecodeSpeedHistory.INDEX_KEY }] = "NaN,Infinity,-2,not-a-number,0"
        assertNull(history.read("model", ComputeBackend.CPU))
    }

    @Test fun `runtime and schema upgrades ignore old measurements and prune obsolete keys`() {
        history.record("model", ComputeBackend.CPU, 7.0, 32)
        assertEquals(7.0, DecodeSpeedHistory(store, "device-build-a", "0.18.0").read("model", ComputeBackend.CPU)?.lowTokensPerSecond)
        val upgraded = DecodeSpeedHistory(store, "device-build-a", "0.19.0")
        assertNull(upgraded.read("model", ComputeBackend.CPU))
        upgraded.record("model", ComputeBackend.CPU, 20.0, 32)
        val newSchema = DecodeSpeedHistory(store, "device-build-a", "0.19.0", schemaVersion = 3)
        assertNull(newSchema.read("model", ComputeBackend.CPU))
        assertTrue(values.keys.none { it.startsWith("decode.v2.") })
        values["decode.legacy-unversioned-hash"] = "30.0"
        assertNull(DecodeSpeedHistory(store, "device-build-a", "0.19.0", schemaVersion = 3).read("model", ComputeBackend.CPU))
        assertTrue("decode.legacy-unversioned-hash" !in values)
    }

    @Test fun `global cap uses read and write recency across recreation`() {
        repeat(DecodeSpeedHistory.MAX_HISTORIES) { history.record("model-$it", ComputeBackend.CPU, 7.0, 32) }
        history.read("model-0", ComputeBackend.CPU)
        history.record("model-1", ComputeBackend.CPU, 8.0, 32)
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        recreated.record("extra", ComputeBackend.GPU, 30.0, 32)
        assertNull(recreated.read("model-2", ComputeBackend.CPU))
        assertEquals(7.0, recreated.read("model-0", ComputeBackend.CPU)?.lowTokensPerSecond)
        assertEquals(2, recreated.read("model-1", ComputeBackend.CPU)?.sampleCount)
        assertEquals(30.0, recreated.read("extra", ComputeBackend.GPU)?.lowTokensPerSecond)
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES, values.keys.count { it != DecodeSpeedHistory.INDEX_KEY })
        repeat(100) { recreated.record("new-$it", ComputeBackend.GPU, 30.0, 32) }
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES + 1, values.size)
    }

    @Test fun `recreation recovers unindexed writes and trims preexisting oversize and legacy stores`() {
        history.record("model", ComputeBackend.CPU, 7.0, 32)
        val key = values.keys.single { it != DecodeSpeedHistory.INDEX_KEY }
        val namespace = key.substringBeforeLast('.') + "."
        repeat(100) { values[namespace + "orphan-$it"] = "9.0" }
        repeat(100) { values["decode.legacy-$it"] = "99.0" }
        values["unrelated"] = "keep"
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertEquals(7.0, recreated.read("model", ComputeBackend.CPU)?.lowTokensPerSecond)
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES, values.keys.count { it.startsWith(namespace) })
        assertTrue(values.keys.none { it.startsWith("decode.legacy-") })
        assertEquals("keep", values["unrelated"])
        values.remove(DecodeSpeedHistory.INDEX_KEY)
        assertEquals(7.0, DecodeSpeedHistory(store, "device-build-a", "0.18.0").read("model", ComputeBackend.CPU)?.lowTokensPerSecond)
    }

    @Test fun `artifact revision checksum filename size and repository distinguish histories`() {
        val artifact = ModelArtifact("weights.litertlm", "https://example.invalid/resolve/rev/weights.litertlm",
            1_000L, "abc", ModelFormat.LITERT_LM)
        val original = modelPerformanceIdentity("repo/model", artifact)
        for (changed in listOf(artifact.copy(fileName = "other.litertlm"),
            artifact.copy(downloadUrl = "https://example.invalid/resolve/new-rev/weights.litertlm"),
            artifact.copy(sha256 = "def"), artifact.copy(sizeBytes = 2_000L))) {
            assertNotEquals(original, modelPerformanceIdentity("repo/model", changed))
        }
        assertNotEquals(original, modelPerformanceIdentity("repo/other", artifact))
    }
}
