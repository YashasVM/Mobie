package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

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

/** Android reports microamps, but a few devices incorrectly return milliamps. */
internal object BatteryCurrentMath {
    fun microamps(raw: Long): Long? {
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        val magnitude = kotlin.math.abs(raw)
        return when {
            magnitude < 1_000L -> raw * 1_000L
            magnitude < 10_000L -> null // Ambiguous: either a low current in µA or a large value in mA.
            else -> raw
        }
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
    private val activity = appContext.getSystemService(ActivityManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val battery = appContext.getSystemService(BatteryManager::class.java)

    fun sample(): Sample {
        val pss = activity.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
            ?.totalPss?.toLong()?.times(1_024L)?.takeIf { it > 0 }
        val voltage = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        return Sample(
            pssBytes = pss,
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null,
            currentMicroamps = BatteryCurrentMath.microamps(
                battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            ),
            voltageMillivolts = voltage,
        )
    }

    data class Sample(
        val pssBytes: Long?,
        val thermalStatus: Int?,
        val currentMicroamps: Long?,
        val voltageMillivolts: Int,
    )
}
