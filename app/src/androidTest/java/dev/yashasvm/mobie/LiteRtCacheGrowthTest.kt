package dev.yashasvm.mobie

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtCacheGrowthTest {
    @Test
    fun measuresFirstLoadAndReloadCacheGrowthForRealQwenModel() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertE2E") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val artifact = ModelArtifact(
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            sizeBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
        )
        val modelFile = checkNotNull(
            ModelDownloadManager(context).completedFile("litert-community/Qwen3-0.6B-int4", artifact),
        ) { "The real Qwen E2E test must download and verify the model before cache measurement." }
        val modelDirectory = checkNotNull(modelFile.parentFile)
        val cacheDirectory = File(modelDirectory, ".litert-cache")

        // Start from a known cold-cache state. The preceding E2E test has already unloaded the model.
        assertTrue("Could not clear the prior LiteRT cache", !cacheDirectory.exists() || cacheDirectory.deleteRecursively())
        val freeBeforeFirstLoad = modelDirectory.usableSpace
        val runtime = LiteRtLmRuntimeAdapter(context)

        val firstLoadStartedNs = SystemClock.elapsedRealtimeNanos()
        runtime.load(modelFile.absolutePath).getOrThrow()
        val firstLoadMs = elapsedMs(firstLoadStartedNs)
        val cacheBytesAfterFirstLoad = directoryBytes(cacheDirectory)
        val freeAfterFirstLoad = modelDirectory.usableSpace
        runtime.unload()

        val reloadStartedNs = SystemClock.elapsedRealtimeNanos()
        runtime.load(modelFile.absolutePath).getOrThrow()
        val reloadMs = elapsedMs(reloadStartedNs)
        val cacheBytesAfterReload = directoryBytes(cacheDirectory)
        val freeAfterReload = modelDirectory.usableSpace
        runtime.unload()

        assertTrue("LiteRT did not create its configured cache directory", cacheDirectory.isDirectory)
        assertTrue("First-load timing was not measured", firstLoadMs > 0.0)
        assertTrue("Reload timing was not measured", reloadMs > 0.0)

        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-litert-cache-metrics.txt")
        metricsFile.writeText(
            buildString {
                appendLine("model=litert-community/Qwen3-0.6B-int4")
                appendLine("artifact=${artifact.fileName}")
                appendLine("artifact_bytes=${artifact.sizeBytes}")
                appendLine("first_load_ms=$firstLoadMs")
                appendLine("reload_ms=$reloadMs")
                appendLine("cache_bytes_after_first_load=$cacheBytesAfterFirstLoad")
                appendLine("cache_bytes_after_reload=$cacheBytesAfterReload")
                appendLine("cache_growth_on_reload_bytes=${cacheBytesAfterReload - cacheBytesAfterFirstLoad}")
                appendLine("free_bytes_before_first_load=$freeBeforeFirstLoad")
                appendLine("free_bytes_after_first_load=$freeAfterFirstLoad")
                appendLine("free_bytes_after_reload=$freeAfterReload")
                appendLine("filesystem_delta_first_load_bytes=${freeBeforeFirstLoad - freeAfterFirstLoad}")
                appendLine("filesystem_delta_reload_bytes=${freeAfterFirstLoad - freeAfterReload}")
            },
        )
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "cp ${metricsFile.absolutePath} /sdcard/mobie-litert-cache-metrics.txt",
            ),
        ).use { it.readBytes() }
    }

    private fun directoryBytes(directory: File): Long = if (!directory.isDirectory) {
        0L
    } else {
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    private fun elapsedMs(startedNs: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0
}
