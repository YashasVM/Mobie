package dev.yashasvm.mobie.core.runtime

import kotlin.math.min

internal data class ThermalInferenceDecision(
    val allowed: Boolean,
    val maxNewTokens: Int,
    val errorMessage: String? = null,
)

internal object ThermalInferencePolicy {
    private const val THERMAL_STATUS_SEVERE = 3
    private const val THERMAL_STATUS_CRITICAL = 4
    private const val SEVERE_MAX_NEW_TOKENS = 256

    fun decide(thermalStatus: Int, requestedMaxNewTokens: Int): ThermalInferenceDecision {
        val requested = requestedMaxNewTokens.coerceAtLeast(1)
        return when {
            thermalStatus >= THERMAL_STATUS_CRITICAL -> ThermalInferenceDecision(
                allowed = false,
                maxNewTokens = 0,
                errorMessage = "Device is too hot for local inference. Let it cool down before generating again.",
            )
            thermalStatus == THERMAL_STATUS_SEVERE -> ThermalInferenceDecision(
                allowed = true,
                maxNewTokens = min(requested, SEVERE_MAX_NEW_TOKENS),
            )
            else -> ThermalInferenceDecision(
                allowed = true,
                maxNewTokens = requested,
            )
        }
    }
}
