package dev.yashasvm.mobie.core.device

import android.content.Context
import android.os.Build
import dev.yashasvm.mobie.BuildConfig
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.InferenceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** App-private preferences contain numeric performance samples only, never prompts or outputs. */
class DecodeSpeedStore(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    // Construct and read preferences on IO, including the first disk load.
    private val history by lazy {
        val preferences = appContext.getSharedPreferences("decode_speed_history", Context.MODE_PRIVATE)
        DecodeSpeedHistory(object : DecodeSpeedKeyValueStore {
            override fun read(key: String): String? = preferences.getString(key, null)
            override fun write(key: String, value: String) {
                preferences.edit().putString(key, value).apply()
            }
            override fun keys(): Set<String> = preferences.all.keys
            override fun remove(key: String) { preferences.edit().remove(key).apply() }
        }, Build.FINGERPRINT, BuildConfig.LITERT_LM_VERSION)
    }

    suspend fun read(identity: String): List<DecodeMeasurement> = withContext(Dispatchers.IO) {
        mutex.withLock { readHistory(identity) }
    }

    /** [settings] carry applied choices; native statistics supply the resolved backend and counts. */
    suspend fun record(identity: String, stats: InferenceStats, settings: DecodeRunSettings): List<DecodeMeasurement> =
        withContext(Dispatchers.IO) {
            // The requested backend may be Auto or may have changed after load. Only the actual
            // backend reported with these SDK stats identifies what produced this measurement.
            mutex.withLock {
                stats.compute?.let { actual ->
                    history.record(identity, actual.backend, settings.copy(
                        resolvedCpuThreads = actual.cpuThreads,
                        resolvedContextTokens = actual.contextTokens,
                    ), stats.tokensPerSecond, stats.decodeTokenCount)
                }
                readHistory(identity)
            }
        }

    private fun readHistory(identity: String): List<DecodeMeasurement> =
        listOf(ComputeBackend.CPU, ComputeBackend.GPU).flatMap { backend -> history.read(identity, backend) }
}
