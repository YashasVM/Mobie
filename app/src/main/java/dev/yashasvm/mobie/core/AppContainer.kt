package dev.yashasvm.mobie.core

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.device.DeviceProfileProvider
import dev.yashasvm.mobie.core.device.DeviceRunSampler
import dev.yashasvm.mobie.core.runtime.InferenceStallGuardRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.SamplerSettingsStore
import dev.yashasvm.mobie.core.runtime.RuntimeComputeSettings
import dev.yashasvm.mobie.core.runtime.RuntimeRegistry
import dev.yashasvm.mobie.core.runtime.ThermalGuardRuntimeAdapter
import dev.yashasvm.mobie.core.security.HuggingFaceTokenStore
import dev.yashasvm.mobie.data.catalog.HuggingFaceCatalogRepository
import dev.yashasvm.mobie.data.conversion.ConversionRepository
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import dev.yashasvm.mobie.data.history.ChatHistoryStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val tokenStore = HuggingFaceTokenStore(appContext)
    val catalog = HuggingFaceCatalogRepository(http, tokenStore)
    val conversion = ConversionRepository(http)
    val downloads = ModelDownloadManager(appContext)
    val chatHistory = ChatHistoryStore(appContext)
    val samplerSettings = SamplerSettingsStore(appContext)
    val deviceProfile = DeviceProfileProvider(appContext)
    val runSampler = DeviceRunSampler(appContext)
    val compatibility = CompatibilityResolver()
    val computeSettings = RuntimeComputeSettings(appContext)
    private val liteRtRuntime = LiteRtLmRuntimeAdapter(appContext, computeSettings)
    private val stallGuardedLiteRtRuntime = InferenceStallGuardRuntimeAdapter(liteRtRuntime)
    val runtimes = RuntimeRegistry(
        setOf(
            ThermalGuardRuntimeAdapter(
                delegate = stallGuardedLiteRtRuntime,
                // Polled on the collector's (main) context, so keep it to one cheap PowerManager call.
                thermalStatusProvider = ::currentThermalStatus,
            ),
        ),
    )

    private fun currentThermalStatus(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appContext.getSystemService(PowerManager::class.java).currentThermalStatus
    } else {
        PowerManager.THERMAL_STATUS_NONE
    }
}
