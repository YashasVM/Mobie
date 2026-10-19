package dev.yashasvm.mobie.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock

data class RunTelemetry(
    val sdkChunks: Int = 0,
    val firstChunkMs: Long? = null,
    val liveSdkChunksPerSecond: Double? = null,
    val pssBytes: Long? = null,
    val thermalStatus: Int? = null,
    val batteryCurrentMilliAmps: Double? = null,
    val estimatedEnergyMilliWh: Double? = null,
    val estimatedEnergyPerTokenMilliWh: Double? = null,
)

/**
 * Android documents microamps, but Samsung and some other vendors report milliamps. A phone
 * running inference draws roughly 10 mA to 10 A, which is 10..10,000 in mA or 10,000..10,000,000
 * in µA, so magnitudes below 10,000 are read as milliamps. The only misread case is a µA device
 * drawing under 10 mA, which does not happen while a model is generating.
 */
internal object BatteryCurrentMath {
    private const val MILLIAMP_CEILING = 10_000L

    fun microamps(raw: Long): Long? {
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        return if (kotlin.math.abs(raw) < MILLIAMP_CEILING) raw * 1_000L else raw
    }

    fun milliWh(currentMicroamps: Long?, voltageMillivolts: Int, elapsedMs: Long): Double? {
        if (currentMicroamps == null || voltageMillivolts <= 0 || elapsedMs <= 0) return null
        return kotlin.math.abs(currentMicroamps.toDouble()) * voltageMillivolts * elapsedMs / 3_600_000_000_000.0
    }

    fun milliWhPerToken(energyMilliWh: Double?, decodeTokens: Int): Double? =
        energyMilliWh?.takeIf { decodeTokens > 0 }?.div(decodeTokens)
}

/** Samples whole-app memory and whole-device power. Call on a background dispatcher. */
class DeviceRunSampler(context: Context) {
    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val battery = appContext.getSystemService(BatteryManager::class.java)

    private var pssBytes: Long? = null
    private var pssAt: Long? = null
    private var voltageMillivolts = 0
    private var charging = false
    private var batteryAt: Long? = null

    /**
     * Battery current is read every call. PSS walks this process's page maps and the sticky
     * battery broadcast is a binder round trip, so both are refreshed at most once a second.
     * `Debug.getPss` is used instead of `ActivityManager.getProcessMemoryInfo`, which Android 10+
     * rate-limits and would return stale values.
     */
    fun sample(): Sample {
        val now = SystemClock.elapsedRealtime()
        if (RunTelemetrySamplingPolicy.shouldRefresh(pssAt, now)) {
            pssBytes = Debug.getPss().takeIf { it > 0 }?.times(1_024L)
            pssAt = now
        }
        if (RunTelemetrySamplingPolicy.shouldRefresh(batteryAt, now)) {
            val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            voltageMillivolts = status?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            charging = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            batteryAt = now
        }
        return Sample(
            pssBytes = pssBytes,
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null,
            // While plugged in, battery current is net of the charger and says nothing about
            // what inference costs, so it is not reported.
            currentMicroamps = if (charging) null else BatteryCurrentMath.microamps(
                battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            ),
            voltageMillivolts = voltageMillivolts,
        )
    }

    data class Sample(
        val pssBytes: Long?,
        val thermalStatus: Int?,
        val currentMicroamps: Long?,
        val voltageMillivolts: Int,
    )
}
