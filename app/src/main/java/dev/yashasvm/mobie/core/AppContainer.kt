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

class AppContainer(context: Context, private val runtimeOverride: RuntimeRegistry? = null) {
    val appContext: Context = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    // Heavy members are created on first use so cold start only pays for what the first screen needs.
    // EncryptedSharedPreferences and Keystore master-key setup are the most expensive of these.
    val tokenStore by lazy { HuggingFaceTokenStore(appContext) }
    val catalog by lazy { HuggingFaceCatalogRepository(http, tokenStore) }
    val conversion by lazy { ConversionRepository(http) }
    val downloads by lazy { ModelDownloadManager(appContext) }
    val attachments by lazy { dev.yashasvm.mobie.data.attachment.AttachmentReader(appContext) }
    val chatHistory by lazy { ChatHistoryStore(appContext) }
    val samplerSettings by lazy { SamplerSettingsStore(appContext) }
    val deviceProfile by lazy { DeviceProfileProvider(appContext) }
    val runSampler by lazy { DeviceRunSampler(appContext) }
    val compatibility = CompatibilityResolver()
    val computeSettings by lazy { RuntimeComputeSettings(appContext) }
    private val liteRtRuntime by lazy { LiteRtLmRuntimeAdapter(appContext, computeSettings) }
    private val stallGuardedLiteRtRuntime by lazy { InferenceStallGuardRuntimeAdapter(liteRtRuntime) }
    val runtimes by lazy {
        runtimeOverride ?: RuntimeRegistry(
            setOf(
                ThermalGuardRuntimeAdapter(
                    delegate = stallGuardedLiteRtRuntime,
                    // Polled on the collector's (main) context, so keep it to one cheap PowerManager call.
                    thermalStatusProvider = ::currentThermalStatus,
                ),
            ),
        )
    }

    private fun currentThermalStatus(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appContext.getSystemService(PowerManager::class.java).currentThermalStatus
    } else {
        PowerManager.THERMAL_STATUS_NONE
    }
}
