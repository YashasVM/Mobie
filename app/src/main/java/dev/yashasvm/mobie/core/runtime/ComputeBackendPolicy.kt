package dev.yashasvm.mobie.core.runtime

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/** NPU remains reserved for a future SoC-specific artifact qualification path. */

data class ComputeOptions(
    val backend: ComputeBackend = ComputeBackend.AUTO,
    /** Null means topology-aware automatic selection. */
    val cpuThreads: Int? = null,
    /** Repository ID, artifact path and immutable source revision. */
    val artifactIdentity: String = "",
)

data class ActiveCompute(
    val backend: ComputeBackend,
    val cpuThreads: Int? = null,
    val fallbackReason: String? = null,
) {
    val label: String get() = if (backend == ComputeBackend.CPU) "CPU · $cpuThreads threads" else backend.name
}

internal object ComputeBackendPolicy {
    /**
     * A success is remembered only when Auto tried GPU first and GPU won. A CPU win, including a
     * CPU fallback after GPU failed or was skipped for memory, is not a preference to remember.
     */
    fun shouldRememberSuccess(requested: ComputeBackend, attempts: List<ComputeBackend>, winner: ComputeBackend): Boolean =
        requested == ComputeBackend.AUTO && winner == ComputeBackend.GPU && attempts.firstOrNull() == ComputeBackend.GPU

    fun attempts(requested: ComputeBackend, gpuPlausible: Boolean, remembered: ComputeBackend?): List<ComputeBackend> =
        when (requested) {
            ComputeBackend.CPU -> listOf(ComputeBackend.CPU)
            ComputeBackend.GPU -> listOf(ComputeBackend.GPU)
            ComputeBackend.NPU -> emptyList() // No qualified NPU artifacts in the v1 catalog.
            ComputeBackend.AUTO -> {
                val candidates = if (gpuPlausible) listOf(ComputeBackend.GPU, ComputeBackend.CPU) else listOf(ComputeBackend.CPU)
                if (remembered in candidates) listOf(remembered!!) + candidates.filterNot { it == remembered } else candidates
            }
        }
}

/** Result of a load that may have fallen back from an earlier backend. */
internal data class BackendLoadResult<T>(
    val backend: ComputeBackend,
    val value: T,
    /** Plain-language reason shown after an automatic fallback; null when the first attempt succeeded. */
    val fallbackReason: String?,
)

/** Attempts that passed memory admission, plus a note when Auto skipped a backend for memory. */
internal data class AdmittedAttempts(val attempts: List<ComputeBackend>, val skippedReason: String?)

/**
 * Under Auto, drops backends that do not fit in memory right now (GPU keeps a private copy of
 * the weights) so Auto falls through to CPU instead of being blocked or pushing Android to kill
 * apps. The last attempt is always kept; the general load gate decides whether it may run.
 * Explicit choices are not filtered here.
 */
internal fun admitAttemptsForMemory(
    requested: ComputeBackend,
    attempts: List<ComputeBackend>,
    blockReasonFor: (ComputeBackend) -> String?,
): AdmittedAttempts {
    if (requested != ComputeBackend.AUTO || attempts.size < 2) return AdmittedAttempts(attempts, null)
    val skipped = attempts.dropLast(1).filter { blockReasonFor(it) != null }
    if (skipped.isEmpty()) return AdmittedAttempts(attempts, null)
    val kept = attempts - skipped.toSet()
    return AdmittedAttempts(
        kept,
        "${skipped.joinToString { it.name }} skipped: not enough free memory for its copy of the weights. Using ${kept.first().name}.",
    )
}

internal fun <T> loadWithBackendFallback(
    attempts: List<ComputeBackend>,
    load: (ComputeBackend) -> T,
    onFailure: (ComputeBackend, Exception) -> Unit = { _, _ -> },
): BackendLoadResult<T> {
    if (attempts.isEmpty()) throw IllegalStateException("NPU requires a qualified model build for this phone; none is available.")
    var firstFailure: Exception? = null
    for (backend in attempts) {
        try {
            val value = load(backend)
            val reason = firstFailure?.let { failure ->
                "${attempts.first().name} load failed: ${failure.message ?: failure.javaClass.simpleName}. Using ${backend.name}."
            }
            return BackendLoadResult(backend, value, reason)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            onFailure(backend, failure)
            val previous = firstFailure
            if (backend == attempts.last()) {
                previous?.let(failure::addSuppressed)
                throw failure
            }
            firstFailure = previous ?: failure
        }
    }
    error("No backend was attempted")
}

internal interface BackendKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

internal class SuccessfulBackendMemory(private val store: BackendKeyValueStore) {
    /** Only GPU is ever remembered; older CPU entries (written when CPU fallbacks were recorded) are ignored. */
    fun get(identity: String, fingerprint: String): ComputeBackend? =
        store.get(key(identity, fingerprint))?.let { saved ->
            runCatching { ComputeBackend.valueOf(saved) }.getOrNull()?.takeIf { it == ComputeBackend.GPU }
        }

    fun remember(identity: String, fingerprint: String, backend: ComputeBackend) {
        require(backend == ComputeBackend.GPU)
        store.put(key(identity, fingerprint), backend.name)
    }

    fun invalidate(identity: String, fingerprint: String) = store.remove(key(identity, fingerprint))

    private fun key(identity: String, fingerprint: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$identity\u0000$fingerprint".toByteArray(Charsets.UTF_8))
        return "backend." + digest.joinToString("") { "%02x".format(it) }
    }
}

/** Settings and successful backend history use app-private preferences, never model metadata. */
class RuntimeComputeSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("runtime_compute", Context.MODE_PRIVATE)
    internal val memory = SuccessfulBackendMemory(object : BackendKeyValueStore {
        override fun get(key: String): String? = preferences.getString(key, null)
        override fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }
        override fun remove(key: String) { preferences.edit().remove(key).apply() }
    })

    fun selectedBackend(): ComputeBackend = preferences.getString("selected_backend", null)
        ?.let { runCatching { ComputeBackend.valueOf(it) }.getOrNull() }
        ?.takeIf { it != ComputeBackend.NPU } ?: ComputeBackend.AUTO

    fun selectedCpuThreads(): Int? = preferences.getInt("cpu_threads", 0).takeIf { it in 1..8 }

    fun setBackend(backend: ComputeBackend) {
        require(backend != ComputeBackend.NPU) { "NPU artifacts are not qualified" }
        preferences.edit().putString("selected_backend", backend.name).apply()
    }

    fun setCpuThreads(threads: Int?) {
        require(threads == null || threads in 1..8)
        preferences.edit().putInt("cpu_threads", threads ?: 0).apply()
    }
}
