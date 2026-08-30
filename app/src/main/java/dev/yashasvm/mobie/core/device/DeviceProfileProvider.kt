package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dev.yashasvm.mobie.core.model.DeviceProfile

class DeviceProfileProvider(private val context: Context) {
    fun current(): DeviceProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        return DeviceProfile(
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            availableStorageBytes = storage.availableBytes,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            releaseVersion = Build.VERSION.RELEASE.orEmpty(),
            lowMemoryThresholdBytes = memoryInfo.threshold,
            isLowMemory = memoryInfo.lowMemory,
            isLowRamDevice = activityManager.isLowRamDevice,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
            mediaPerformanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
        )
    }
}
