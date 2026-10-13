package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import java.security.MessageDigest

/** Source URL/revision and checksum keep measurements apart when the same file is replaced. */
fun modelPerformanceIdentity(modelId: String, artifact: ModelArtifact): String =
    "$modelId|${artifact.fileName}|${artifact.downloadUrl}|${artifact.sha256.orEmpty()}|${artifact.sizeBytes}"

internal interface DecodeSpeedKeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun keys(): Set<String>
    fun remove(key: String)
}

/** Eight runs per artifact/backend, at most 64 histories globally, evicted by last read/write. */
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

    fun read(identity: String, backend: ComputeBackend): DecodeSpeed? {
        if (identity.isBlank() || !backend.isConcrete()) return null
        val key = key(identity, backend)
        val speed = samples(key).toSpeed() ?: return null
        touch(key)
        return speed
    }

    fun record(identity: String, backend: ComputeBackend, tokensPerSecond: Double, decodeTokens: Int): DecodeSpeed? {
        // Prefill rates, chunk rates, empty and one-token replies are not useful decode samples.
        if (identity.isBlank() || !backend.isConcrete() || !tokensPerSecond.isFinite() || tokensPerSecond <= 0 || decodeTokens < 2) {
            return read(identity, backend)
        }
        val key = key(identity, backend)
        val updated = (samples(key) + tokensPerSecond).takeLast(8)
        store.write(key, updated.joinToString(","))
        touch(key)
        return updated.toSpeed()
    }

    private fun samples(key: String): List<Double> =
        store.read(key).orEmpty().split(',').mapNotNull {
            it.toDoubleOrNull()?.takeIf { value -> value.isFinite() && value > 0 }
        }.takeLast(8)

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

    private fun List<Double>.toSpeed(): DecodeSpeed? = if (isEmpty()) null else DecodeSpeed(
        lowTokensPerSecond = min(), highTokensPerSecond = max(),
        source = DecodeSpeedSource.MEASURED, sampleCount = size,
    )

    private fun ComputeBackend.isConcrete(): Boolean = this == ComputeBackend.CPU || this == ComputeBackend.GPU

    internal companion object {
        const val SCHEMA_VERSION = 2
        const val MAX_HISTORIES = 64
        const val INDEX_KEY = "decode.lru"

        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
