package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import java.security.MessageDigest
import kotlin.math.abs

/** Source URL/revision and checksum keep measurements apart when the same file is replaced. */
fun modelPerformanceIdentity(modelId: String, artifact: ModelArtifact): String =
    "$modelId|${artifact.fileName}|${artifact.downloadUrl}|${artifact.sha256.orEmpty()}|${artifact.sizeBytes}"

/**
 * The settings a decode run used. Null means Auto. Thread count only changes CPU speed, so GPU
 * measurements always store null threads.
 */
data class DecodeRunSettings(val cpuThreads: Int?, val contextTokens: Int?) {
    internal fun forBackend(backend: ComputeBackend): DecodeRunSettings =
        if (backend == ComputeBackend.CPU) this else copy(cpuThreads = null)
}

/** One backend's measured speed at the settings it ran with. */
data class DecodeMeasurement(val backend: ComputeBackend, val settings: DecodeRunSettings, val speed: DecodeSpeed)

/**
 * The measurement to show for the current settings. [exactSettings] is false when the closest
 * measurement came from different thread or context settings.
 */
data class DecodeMatch(val speed: DecodeSpeed, val exactSettings: Boolean)

/**
 * Exact settings win. Otherwise the nearest same-backend measurement is used: closest thread count
 * first, then closest context size. Auto matches only Auto.
 */
fun matchDecodeMeasurement(
    measurements: List<DecodeMeasurement>,
    backend: ComputeBackend,
    current: DecodeRunSettings,
): DecodeMatch? {
    val wanted = current.forBackend(backend)
    val nearest = measurements
        .filter { it.backend == backend }
        .minWithOrNull(
            compareBy<DecodeMeasurement>(
                { settingDistance(it.settings.cpuThreads, wanted.cpuThreads) },
                { settingDistance(it.settings.contextTokens, wanted.contextTokens) },
            ),
        ) ?: return null
    return DecodeMatch(nearest.speed, exactSettings = nearest.settings == wanted)
}

private fun settingDistance(measured: Int?, wanted: Int?): Int = when {
    measured == wanted -> 0
    measured == null || wanted == null -> UNMATCHED_DISTANCE
    else -> abs(measured - wanted)
}

private const val UNMATCHED_DISTANCE = Int.MAX_VALUE / 2

internal interface DecodeSpeedKeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun keys(): Set<String>
    fun remove(key: String)
}

/**
 * One bucket per artifact and backend. Each bucket keeps up to four settings groups (thread count
 * and context size), eight runs per group, and at most 64 buckets globally, evicted by last read or
 * write. Schema 2 entries had no settings, so they are removed rather than reused.
 */
internal class DecodeSpeedHistory(
    private val store: DecodeSpeedKeyValueStore,
    deviceFingerprint: String,
    runtimeVersion: String,
    schemaVersion: Int = SCHEMA_VERSION,
) {
    private val namespace = "decode.v$schemaVersion.${digest("$deviceFingerprint\u0000$runtimeVersion\u0000$schemaVersion")}."
    private val recency = mutableListOf<String>()

    init {
        val existing = store.keys().filter { it.startsWith("decode.") && it != INDEX_KEY }.toSet()
        // Old unversioned entries and other runtime/schema/device generations are never reused.
        existing.filterNot { it.startsWith(namespace) }.forEach(store::remove)
        val current = existing.filter { it.startsWith(namespace) }.toSet()
        val indexed = store.read(INDEX_KEY).orEmpty().split(',').filter { it in current }.distinct()
        // Recover an orphaned write after process death without trusting it as most recent.
        recency.addAll((current - indexed.toSet()).sorted())
        recency.addAll(indexed)
        trim()
        if (recency.isNotEmpty() || store.read(INDEX_KEY) != null) persistRecency()
    }

    fun read(identity: String, backend: ComputeBackend): List<DecodeMeasurement> {
        if (identity.isBlank() || !backend.isConcrete()) return emptyList()
        val key = key(identity, backend)
        val groups = groups(key)
        if (groups.isEmpty()) return emptyList()
        touch(key)
        return groups.map { DecodeMeasurement(backend, it.settings, it.samples.toSpeed()) }
    }

    fun record(identity: String, backend: ComputeBackend, settings: DecodeRunSettings, tokensPerSecond: Double, decodeTokens: Int) {
        // Prefill rates, chunk rates, empty and one-token replies are not useful decode samples.
        if (identity.isBlank() || !backend.isConcrete() || !tokensPerSecond.isFinite() || tokensPerSecond <= 0 || decodeTokens < 2) {
            return
        }
        val key = key(identity, backend)
        val wanted = settings.forBackend(backend)
        val groups = groups(key)
        val samples = ((groups.firstOrNull { it.settings == wanted }?.samples).orEmpty() + tokensPerSecond).takeLast(MAX_SAMPLES)
        // The most recently written group is last, so the oldest settings group is dropped first.
        val updated = (groups.filterNot { it.settings == wanted } + Group(wanted, samples)).takeLast(MAX_GROUPS_PER_BUCKET)
        store.write(key, updated.joinToString("\n") { it.encode() })
        touch(key)
    }

    private fun groups(key: String): List<Group> =
        store.read(key).orEmpty().lines().mapNotNull(Group::decode).takeLast(MAX_GROUPS_PER_BUCKET)

    private fun key(identity: String, backend: ComputeBackend): String =
        namespace + digest("${backend.name}\u0000$identity")

    private fun touch(key: String) {
        recency.remove(key)
        recency.add(key)
        trim()
        persistRecency()
    }

    private fun trim() {
        while (recency.size > MAX_HISTORIES) store.remove(recency.removeAt(0))
    }

    private fun persistRecency() = store.write(INDEX_KEY, recency.joinToString(","))

    private fun List<Double>.toSpeed(): DecodeSpeed = DecodeSpeed(
        lowTokensPerSecond = min(), highTokensPerSecond = max(),
        source = DecodeSpeedSource.MEASURED, sampleCount = size,
    )

    private fun ComputeBackend.isConcrete(): Boolean = this == ComputeBackend.CPU || this == ComputeBackend.GPU

    /** One line per settings group: `threads;context;rate,rate`, where `auto` stands for Auto. */
    private data class Group(val settings: DecodeRunSettings, val samples: List<Double>) {
        fun encode(): String = listOf(
            settings.cpuThreads?.toString() ?: AUTO,
            settings.contextTokens?.toString() ?: AUTO,
            samples.joinToString(","),
        ).joinToString(";")

        companion object {
            fun decode(line: String): Group? {
                val parts = line.split(';', limit = 3)
                if (parts.size != 3) return null
                val threads = setting(parts[0], 1..8) ?: return null
                val context = setting(parts[1], 1_024..4_096) ?: return null
                val samples = parts[2].split(',').mapNotNull {
                    it.toDoubleOrNull()?.takeIf { value -> value.isFinite() && value > 0 }
                }.takeLast(MAX_SAMPLES)
                if (samples.isEmpty()) return null
                return Group(DecodeRunSettings(threads.value, context.value), samples)
            }

            /** Null when the text is not Auto or a number in range; Auto is a value, not a failure. */
            private fun setting(text: String, range: IntRange): Setting? = when (text) {
                AUTO -> Setting(null)
                else -> text.toIntOrNull()?.takeIf { it in range }?.let { Setting(it) }
            }

            private class Setting(val value: Int?)
        }
    }

    internal companion object {
        const val SCHEMA_VERSION = 3
        const val MAX_HISTORIES = 64
        const val MAX_GROUPS_PER_BUCKET = 4
        const val MAX_SAMPLES = 8
        const val INDEX_KEY = "decode.lru"
        private const val AUTO = "auto"

        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
