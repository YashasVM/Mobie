package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val auto = DecodeRunSettings(cpuThreads = null, contextTokens = null)

    /** The measurement recorded for exactly [settings], or null. */
    private fun measured(
        history: DecodeSpeedHistory,
        identity: String,
        backend: ComputeBackend,
        settings: DecodeRunSettings = auto,
    ): DecodeSpeed? = history.read(identity, backend).firstOrNull { it.settings == settings }?.speed

    @Test fun `remembers actual range across store recreation and separates model backend and device`() {
        history.record("repo/model@revision", ComputeBackend.CPU, auto, 9.0, 32)
        history.record("repo/model@revision", ComputeBackend.CPU, auto, 12.0, 40)
        history.record("repo/model@revision", ComputeBackend.GPU, auto, 20.0, 30)
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertEquals(DecodeSpeed(9.0, 12.0, DecodeSpeedSource.MEASURED, 2), measured(recreated, "repo/model@revision", ComputeBackend.CPU))
        assertEquals(20.0, measured(recreated, "repo/model@revision", ComputeBackend.GPU)?.lowTokensPerSecond)
        assertNull(measured(recreated, "repo/other@revision", ComputeBackend.CPU))
        assertNull(measured(recreated, "repo/model@new-revision", ComputeBackend.CPU))
        assertNull(measured(DecodeSpeedHistory(store, "other-device", "0.18.0"), "repo/model@revision", ComputeBackend.CPU))
        assertTrue(values.keys.all { it.startsWith("decode.") && !it.contains("repo") })
    }

    @Test fun `ignores invalid rates and short replies without losing a previous measurement`() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            history.record("model", ComputeBackend.CPU, auto, rate, 20)
        }
        history.record("model", ComputeBackend.CPU, auto, 5.0, 1)
        history.record("model", ComputeBackend.AUTO, auto, 5.0, 20)
        history.record("model", ComputeBackend.NPU, auto, 5.0, 20)
        history.record("", ComputeBackend.CPU, auto, 5.0, 20)
        assertTrue(values.isEmpty())
        history.record("model", ComputeBackend.CPU, auto, 10.0, 20)
        history.record("model", ComputeBackend.CPU, auto, Double.NaN, 20)
        assertEquals(10.0, measured(history, "model", ComputeBackend.CPU)?.lowTokensPerSecond)
    }

    @Test fun `uses recent runs instead of retaining old thermal settings forever`() {
        for (rate in 1..10) history.record("model", ComputeBackend.CPU, auto, rate.toDouble(), 20)
        assertEquals(DecodeSpeed(3.0, 10.0, DecodeSpeedSource.MEASURED, 8), measured(history, "model", ComputeBackend.CPU))
    }

    @Test fun `corrupted persisted values do not become measurements`() {
        history.record("model", ComputeBackend.CPU, auto, 10.0, 20)
        val key = values.keys.single { it != DecodeSpeedHistory.INDEX_KEY }
        values[key] = "NaN,Infinity,-2,not-a-number,0"
        assertNull(measured(history, "model", ComputeBackend.CPU))
        values[key] = "4;auto;NaN,-1,zero"
        assertNull(measured(history, "model", ComputeBackend.CPU))
        values[key] = "99;auto;9.0"
        assertNull(measured(history, "model", ComputeBackend.CPU))
    }

    @Test fun `runtime and schema upgrades ignore old measurements and prune obsolete keys`() {
        history.record("model", ComputeBackend.CPU, auto, 7.0, 32)
        assertEquals(7.0, measured(DecodeSpeedHistory(store, "device-build-a", "0.18.0"), "model", ComputeBackend.CPU)?.lowTokensPerSecond)
        val upgraded = DecodeSpeedHistory(store, "device-build-a", "0.19.0")
        assertNull(measured(upgraded, "model", ComputeBackend.CPU))
        upgraded.record("model", ComputeBackend.CPU, auto, 20.0, 32)
        val newSchema = DecodeSpeedHistory(store, "device-build-a", "0.19.0", schemaVersion = 4)
        assertNull(measured(newSchema, "model", ComputeBackend.CPU))
        assertTrue(values.keys.none { it.startsWith("decode.v3.") })
        values["decode.legacy-unversioned-hash"] = "30.0"
        assertNull(measured(DecodeSpeedHistory(store, "device-build-a", "0.19.0", schemaVersion = 4), "model", ComputeBackend.CPU))
        assertTrue("decode.legacy-unversioned-hash" !in values)
    }

    @Test fun `schema two entries without settings are removed, not reused`() {
        assertEquals(3, DecodeSpeedHistory.SCHEMA_VERSION)
        history.record("model", ComputeBackend.CPU, auto, 7.0, 32)
        values["decode.v2.legacy"] = "1.0;auto;30.0"
        DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertTrue(values.keys.none { it.startsWith("decode.v2.") })
        assertEquals(7.0, measured(DecodeSpeedHistory(store, "device-build-a", "0.18.0"), "model", ComputeBackend.CPU)?.lowTokensPerSecond)
    }

    @Test fun `thread count and context size keep separate measurements for CPU`() {
        history.record("model", ComputeBackend.CPU, DecodeRunSettings(4, null), 7.0, 32)
        history.record("model", ComputeBackend.CPU, DecodeRunSettings(8, null), 20.0, 32)
        history.record("model", ComputeBackend.CPU, DecodeRunSettings(4, 2_048), 12.0, 32)
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertEquals(7.0, measured(recreated, "model", ComputeBackend.CPU, DecodeRunSettings(4, null))?.lowTokensPerSecond)
        assertEquals(20.0, measured(recreated, "model", ComputeBackend.CPU, DecodeRunSettings(8, null))?.lowTokensPerSecond)
        assertEquals(12.0, measured(recreated, "model", ComputeBackend.CPU, DecodeRunSettings(4, 2_048))?.lowTokensPerSecond)
        assertNull(measured(recreated, "model", ComputeBackend.CPU, DecodeRunSettings(2, null)))
        assertEquals(3, recreated.read("model", ComputeBackend.CPU).size)
    }

    @Test fun `GPU measurements ignore the CPU thread count`() {
        history.record("model", ComputeBackend.GPU, DecodeRunSettings(4, null), 30.0, 32)
        val stored = history.read("model", ComputeBackend.GPU)
        assertEquals(listOf(auto), stored.map { it.settings })
        val match = matchDecodeMeasurement(stored, ComputeBackend.GPU, DecodeRunSettings(8, null))
        assertEquals(30.0, match?.speed?.lowTokensPerSecond)
        assertTrue(match?.exactSettings ?: false)
    }

    @Test fun `each artifact keeps its four most recent settings groups`() {
        for (threads in 1..5) history.record("model", ComputeBackend.CPU, DecodeRunSettings(threads, null), threads.toDouble(), 32)
        val groups = history.read("model", ComputeBackend.CPU).map { it.settings.cpuThreads }
        assertEquals(listOf(2, 3, 4, 5), groups)
    }

    @Test fun `matching prefers exact settings and falls back to the nearest same-backend run`() {
        val measurements = listOf(
            DecodeMeasurement(ComputeBackend.CPU, DecodeRunSettings(4, null), DecodeSpeed(7.0, 7.0, DecodeSpeedSource.MEASURED, 1)),
            DecodeMeasurement(ComputeBackend.CPU, DecodeRunSettings(8, null), DecodeSpeed(20.0, 20.0, DecodeSpeedSource.MEASURED, 1)),
            DecodeMeasurement(ComputeBackend.GPU, DecodeRunSettings(null, null), DecodeSpeed(30.0, 30.0, DecodeSpeedSource.MEASURED, 1)),
        )
        val exact = matchDecodeMeasurement(measurements, ComputeBackend.CPU, DecodeRunSettings(8, null))
        assertEquals(DecodeMatch(DecodeSpeed(20.0, 20.0, DecodeSpeedSource.MEASURED, 1), exactSettings = true), exact)

        val nearest = matchDecodeMeasurement(measurements, ComputeBackend.CPU, DecodeRunSettings(5, null))
        assertEquals(7.0, nearest?.speed?.lowTokensPerSecond)
        assertFalse(nearest?.exactSettings ?: true)

        // Auto is its own setting: an explicit thread count never claims an Auto run as exact.
        assertFalse(matchDecodeMeasurement(measurements.take(1), ComputeBackend.CPU, DecodeRunSettings(null, null))?.exactSettings ?: true)
        assertNull(matchDecodeMeasurement(measurements.take(1), ComputeBackend.GPU, DecodeRunSettings(null, null)))
        // GPU ignores thread count, so a GPU run at other threads is still exact.
        assertTrue(matchDecodeMeasurement(measurements, ComputeBackend.GPU, DecodeRunSettings(2, null))?.exactSettings ?: false)
    }

    @Test fun `global cap uses read and write recency across recreation`() {
        repeat(DecodeSpeedHistory.MAX_HISTORIES) { history.record("model-$it", ComputeBackend.CPU, auto, 7.0, 32) }
        history.read("model-0", ComputeBackend.CPU)
        history.record("model-1", ComputeBackend.CPU, auto, 8.0, 32)
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        recreated.record("extra", ComputeBackend.GPU, auto, 30.0, 32)
        assertNull(measured(recreated, "model-2", ComputeBackend.CPU))
        assertEquals(7.0, measured(recreated, "model-0", ComputeBackend.CPU)?.lowTokensPerSecond)
        assertEquals(2, measured(recreated, "model-1", ComputeBackend.CPU)?.sampleCount)
        assertEquals(30.0, measured(recreated, "extra", ComputeBackend.GPU)?.lowTokensPerSecond)
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES, values.keys.count { it != DecodeSpeedHistory.INDEX_KEY })
        repeat(100) { recreated.record("new-$it", ComputeBackend.GPU, auto, 30.0, 32) }
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES + 1, values.size)
    }

    @Test fun `recreation recovers unindexed writes and trims preexisting oversize and legacy stores`() {
        history.record("model", ComputeBackend.CPU, auto, 7.0, 32)
        val key = values.keys.single { it != DecodeSpeedHistory.INDEX_KEY }
        val namespace = key.substringBeforeLast('.') + "."
        repeat(100) { values[namespace + "orphan-$it"] = "1;auto;9.0" }
        repeat(100) { values["decode.legacy-$it"] = "99.0" }
        values["unrelated"] = "keep"
        val recreated = DecodeSpeedHistory(store, "device-build-a", "0.18.0")
        assertEquals(7.0, measured(recreated, "model", ComputeBackend.CPU)?.lowTokensPerSecond)
        assertEquals(DecodeSpeedHistory.MAX_HISTORIES, values.keys.count { it.startsWith(namespace) })
        assertTrue(values.keys.none { it.startsWith("decode.legacy-") })
        assertEquals("keep", values["unrelated"])
        values.remove(DecodeSpeedHistory.INDEX_KEY)
        assertEquals(7.0, measured(DecodeSpeedHistory(store, "device-build-a", "0.18.0"), "model", ComputeBackend.CPU)?.lowTokensPerSecond)
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
