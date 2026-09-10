package dev.yashasvm.mobie.core

import android.content.Context
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.device.DeviceProfileProvider
import dev.yashasvm.mobie.core.runtime.InferenceStallGuardRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
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
    val deviceProfile = DeviceProfileProvider(appContext)
    val compatibility = CompatibilityResolver()
    private val liteRtRuntime = LiteRtLmRuntimeAdapter(appContext)
    private val stallGuardedLiteRtRuntime = InferenceStallGuardRuntimeAdapter(liteRtRuntime)
    val runtimes = RuntimeRegistry(
        setOf(
            ThermalGuardRuntimeAdapter(
                delegate = stallGuardedLiteRtRuntime,
                thermalStatusProvider = {
                    deviceProfile.current().thermalStatus
                },
            ),
        ),
    )
}
