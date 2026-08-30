package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dev.yashasvm.mobie.core.model.DeviceProfile

class DeviceProfileProvider(private val context: Context) {
    fun current(): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        return DeviceProfile(
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            availableStorageBytes = storage.availableBytes,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            releaseVersion = Build.VERSION.RELEASE.orEmpty(),
        )
    }
}
