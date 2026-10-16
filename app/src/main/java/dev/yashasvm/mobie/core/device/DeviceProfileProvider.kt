package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.runtime.LiteRtVisionBackendPolicy

class DeviceProfileProvider(private val context: Context) {
    fun current(): DeviceProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = MemorySnapshotReader(activityManager).read()
        val storage = StatFs(context.filesDir.absolutePath)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java).currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        return DeviceProfile(
            totalRamBytes = memory.totalBytes,
            availableRamBytes = memory.availableNowBytes,
            availableAfterReclaimBytes = memory.availableAfterReclaimBytes,
            swapTotalBytes = memory.swapTotalBytes,
            swapFreeBytes = memory.swapFreeBytes,
            appPssBytes = memory.appPssBytes,
            availableStorageBytes = storage.availableBytes,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            releaseVersion = Build.VERSION.RELEASE.orEmpty(),
            lowMemoryThresholdBytes = memory.thresholdBytes,
            isLowMemory = memory.isLowMemory,
            isLowRamDevice = activityManager.isLowRamDevice,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
            mediaPerformanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
            thermalStatus = thermalStatus,
            gpuAttemptable = LiteRtVisionBackendPolicy.shouldAttemptGpu(
                Build.SUPPORTED_ABIS, Build.FINGERPRINT, Build.MODEL, Build.HARDWARE, Build.PRODUCT,
            ),
        )
    }
}
