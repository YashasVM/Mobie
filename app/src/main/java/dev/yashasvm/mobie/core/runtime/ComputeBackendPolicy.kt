package dev.yashasvm.mobie.core.runtime

import android.content.Context
import android.content.SharedPreferences
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

data class ActiveCompute(
    val backend: ComputeBackend,
    val cpuThreads: Int? = null,
    val fallbackReason: String? = null,
    val contextTokens: Int? = null,
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
            // An explicit GPU on an emulator or non-arm64 device is never attempted; see unavailableReason.
            ComputeBackend.GPU -> if (gpuPlausible) listOf(ComputeBackend.GPU) else emptyList()
            ComputeBackend.NPU -> emptyList() // No qualified NPU artifacts in the v1 catalog.
            ComputeBackend.AUTO -> if (gpuPlausible) listOf(ComputeBackend.GPU, ComputeBackend.CPU) else listOf(ComputeBackend.CPU)
        }

    /** Plain-language reason an explicit choice cannot run on this device, or null when it may run. */
    fun unavailableReason(requested: ComputeBackend, gpuPlausible: Boolean): String? = when {
        requested == ComputeBackend.NPU -> "NPU requires a qualified model build for this phone; none is available."
        requested == ComputeBackend.GPU && !gpuPlausible ->
            "GPU is not available on this device. Choose CPU or Auto to load this model."
        else -> null
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

/** JNI initialization errors contain source locations, not a useful action for the user. */
internal fun describeBackendLoadFailure(backend: ComputeBackend, failure: Exception): String =
    if (backend == ComputeBackend.GPU && failure.message.orEmpty().startsWith("Failed to create engine:")) {
        "This model could not be loaded on GPU by LiteRT-LM. Use CPU or try another model artifact."
    } else {
        failure.message ?: failure.javaClass.simpleName
    }

/**
 * Under Auto, drops backends that do not fit in memory right now (GPU needs graphics buffers and
 * private weights) so Auto falls through to CPU instead of being blocked or pushing Android to kill
 * apps. The last attempt is always kept; the general load gate decides whether it may run.
 * Explicit choices are not filtered here.
 */
internal fun admitAttemptsForMemory(
    requested: ComputeBackend,
    attempts: List<ComputeBackend>,
    blockReasonFor: (ComputeBackend) -> String?,
): AdmittedAttempts {
    if (requested != ComputeBackend.AUTO || attempts.size < 2) return AdmittedAttempts(attempts, null)
    val skipped = attempts.dropLast(1).mapNotNull { backend ->
        blockReasonFor(backend)?.let { reason -> backend to reason.trim().trimEnd('.') }
    }
    if (skipped.isEmpty()) return AdmittedAttempts(attempts, null)
    val kept = attempts - skipped.map { it.first }.toSet()
    return AdmittedAttempts(
        kept,
        skipped.joinToString(" ") { (backend, reason) ->
            "${backend.name} skipped: $reason."
        } + " Using ${kept.first().name}.",
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
                val description = (failure.message ?: failure.javaClass.simpleName).trim().trimEnd('.')
                "${attempts.first().name} load failed: $description. Using ${backend.name}."
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
class RuntimeComputeSettings internal constructor(
    internal val preferences: SharedPreferences,
    private val coreCountProvider: () -> Int,
) {
    constructor(context: Context, coreCountProvider: () -> Int = { Runtime.getRuntime().availableProcessors() }) :
        this(context.applicationContext.getSharedPreferences("runtime_compute", Context.MODE_PRIVATE), coreCountProvider)
    internal val memory = SuccessfulBackendMemory(object : BackendKeyValueStore {
        override fun get(key: String): String? = preferences.getString(key, null)
        override fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }
        override fun remove(key: String) { preferences.edit().remove(key).apply() }
    })

    fun selectedBackend(): ComputeBackend = preferences.getString("selected_backend", null)
        ?.let { runCatching { ComputeBackend.valueOf(it) }.getOrNull() }
        ?.takeIf { it != ComputeBackend.NPU } ?: ComputeBackend.AUTO

    fun selectedCpuThreads(coreCount: Int = coreCountProvider()): Int? {
        val stored = preferences.getInt("cpu_threads", 0)
        val sanitized = CpuThreadPolicy.clamp(stored.takeIf { it != 0 }, coreCount)
        if (stored != (sanitized ?: 0)) preferences.edit().putInt("cpu_threads", sanitized ?: 0).apply()
        return sanitized
    }

    fun setBackend(backend: ComputeBackend) {
        require(backend != ComputeBackend.NPU) { "NPU artifacts are not qualified" }
        preferences.edit().putString("selected_backend", backend.name).apply()
    }

    fun setCpuThreads(threads: Int?, coreCount: Int = coreCountProvider()) {
        val sanitized = CpuThreadPolicy.clamp(threads?.takeIf { it != 0 }, coreCount)
        preferences.edit().putInt("cpu_threads", sanitized ?: 0).apply()
    }
}
