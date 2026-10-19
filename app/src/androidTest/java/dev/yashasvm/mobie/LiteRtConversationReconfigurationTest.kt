package dev.yashasvm.mobie

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.InferenceStallGuardRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.RuntimeAdapter
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in native correctness probe using the same checksum-pinned artifact as LiteRtEndToEndTest.
 * Uses cached installation first; modelPath may name an already-staged copy inside app filesDir.
 * No UI, cache deletion, timing thresholds, throughput comparison, or physical-device speed claim.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtConversationReconfigurationTest {
    @Test
    fun retainsEngineAcrossSettingsChangeThenFailsClosedAndRecovers(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("litertE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = verifiedFixture(context, arguments.getString("modelPath"))
        val native = LiteRtLmRuntimeAdapter(context)
        val runtime: RuntimeAdapter = InferenceStallGuardRuntimeAdapter(native)
        val originalOptions = ComputeOptions(
            backend = ComputeBackend.CPU,
            cpuThreads = 1,
            artifactIdentity = "$MODEL_ID/$FILE_NAME@$REVISION#$SHA256",
            maxOutputTokens = 32,
        )
        // Greedy sampling keeps all three probes deterministic without asserting particular text.
        val originalSampler = SamplerSettings(temperature = 0.0, topK = 1, topP = 1.0, seed = 0)
        val updatedSampler = originalSampler.copy(seed = 7)
        val updatedOptions = originalOptions.copy(maxOutputTokens = 24, thinkingEnabled = false)
        runtime.configureCompute(originalOptions)
        runtime.configureSampler(originalSampler)
        try {
            runtime.load(model.path).getOrThrow()
            assertEquals(ComputeBackend.CPU, runtime.activeCompute?.backend)
            val loadedEngine = checkNotNull(nativeField(native, "engine"))
            val first = generate(runtime, "Reply with one short greeting.", originalOptions, originalSampler)
            val restored = listOf(
                RuntimeMessage(true, "Reply with one short greeting."),
                RuntimeMessage(false, first.visibleOutput()),
            )
            val previousConversation = checkNotNull(nativeField(native, "conversation"))

            runtime.reconfigureConversation(updatedOptions, updatedSampler, restored).getOrThrow()

            assertSame("Conversation-only settings reloaded the model engine", loadedEngine, nativeField(native, "engine"))
            assertNotSame("Settings change kept the old native conversation", previousConversation, nativeField(native, "conversation"))
            assertEquals(updatedOptions, nativeField(native, "activeOptions"))
            assertEquals(updatedSampler, nativeField(native, "activeSampler"))
            assertEquals(restored, nativeField(native, "committedHistory"))
            assertEquals(ComputeBackend.CPU, runtime.activeCompute?.backend)
            val second = generate(runtime, "Reply briefly: are you ready?", updatedOptions, updatedSampler)
            assertSame("Post-reconfiguration generation replaced the engine", loadedEngine, nativeField(native, "engine"))

            // Wrong artifact identity is a deterministic refusal before any risky native allocation.
            val rejected = runtime.reconfigureConversation(
                updatedOptions.copy(artifactIdentity = "${originalOptions.artifactIdentity}:different-artifact"),
                updatedSampler,
                restored,
            )
            assertTrue("Changing the loaded artifact identity must require reload", rejected.isFailure)
            assertSame(loadedEngine, nativeField(native, "engine"))
            assertEquals("Rejected settings must not be published", updatedOptions, nativeField(native, "activeOptions"))
            assertBlocked(runtime, updatedOptions, updatedSampler)
            // Check the native adapter's fail-closed state too, not only the outer watchdog guard.
            assertBlocked(native, updatedOptions, updatedSampler)
            assertTrue("Reset must not resurrect a failed settings transition", native.resetConversation(restored).isFailure)

            runtime.unload()
            assertNull(nativeField(native, "engine"))
            runtime.configureCompute(updatedOptions)
            runtime.configureSampler(updatedSampler)
            runtime.load(model.path, history = restored).getOrThrow()
            assertNotSame("Recovery must initialize a new engine", loadedEngine, nativeField(native, "engine"))
            val recovered = generate(runtime, "Reply with one word: ready.", updatedOptions, updatedSampler)
            Log.i(
                TAG,
                "PASS engine_retained=true settings_applied=true failure_closed=true reload_recovered=true " +
                    "artifact=$FILE_NAME sha256=$SHA256 runtime=${BuildConfig.LITERT_LM_VERSION} " +
                    "device=${Build.MODEL} android=${Build.VERSION.RELEASE} abis=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "backend=CPU threads=1 visible_chars=${first.visibleOutput().length}," +
                    "${second.visibleOutput().length},${recovered.visibleOutput().length}; no timing comparison",
            )
        } finally {
            runtime.unload()
        }
    }

    private suspend fun generate(
        runtime: RuntimeAdapter,
        prompt: String,
        options: ComputeOptions,
        sampler: SamplerSettings,
    ): List<InferenceEvent> = withTimeout(15 * 60 * 1_000L) {
        runtime.generate(prompt, config = GenerationConfig(options.maxOutputTokens, sampler)).toList().also { events ->
            val errors = events.filterIsInstance<InferenceEvent.Error>()
            assertTrue("Native generation failed: ${errors.joinToString { it.message }}", errors.isEmpty())
            assertTrue("Native generation produced no visible answer", events.visibleOutput().isNotBlank())
            assertTrue("Native generation did not complete", events.any { it is InferenceEvent.Complete })
            assertTrue("Native generation reported no statistics", events.any { it is InferenceEvent.Stats })
        }
    }

    private suspend fun assertBlocked(runtime: RuntimeAdapter, options: ComputeOptions, sampler: SamplerSettings) {
        val events = withTimeout(15 * 60 * 1_000L) {
            runtime.generate("Do not reuse the rejected conversation.", config = GenerationConfig(options.maxOutputTokens, sampler)).toList()
        }
        assertTrue("A failed reconfiguration must require reload", events.filterIsInstance<InferenceEvent.Error>().any { it.requiresReload })
        assertFalse("A failed reconfiguration reused native generation", events.any { it is InferenceEvent.Token || it is InferenceEvent.Complete })
    }

    private suspend fun verifiedFixture(context: Context, stagedPath: String?): File {
        val artifact = ModelArtifact(
            fileName = FILE_NAME,
            downloadUrl = "https://huggingface.co/$MODEL_ID/resolve/$REVISION/$FILE_NAME",
            sizeBytes = SIZE_BYTES,
            sha256 = SHA256,
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
        )
        val model = if (stagedPath != null) {
            File(context.filesDir, stagedPath).canonicalFile.also { staged ->
                require(staged.path.startsWith(context.filesDir.canonicalPath + "/") && staged.isFile) {
                    "modelPath must identify an existing file inside the app's private files directory."
                }
            }
        } else {
            val downloads = ModelDownloadManager(context)
            downloads.completedFile(MODEL_ID, artifact) ?: run {
                val requestId = downloads.enqueue(MODEL_ID, artifact)
                val completed = withTimeout(20 * 60 * 1_000L) {
                    downloads.observe(requestId).first { it.state.isFinished }
                }
                assertEquals("Pinned model download failed: ${completed.error}", WorkInfo.State.SUCCEEDED, completed.state)
                File(checkNotNull(completed.localPath))
            }
        }
        withContext(Dispatchers.IO) {
            assertEquals("The probe requires the exact E2E artifact size", SIZE_BYTES, model.length())
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().use { input ->
                val buffer = ByteArray(1_024 * 1_024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals("The probe requires the exact E2E artifact SHA-256", SHA256, actual)
        }
        return model
    }

    /** Debug-only identity inspection proves weights were retained without using timing as a proxy. */
    private fun nativeField(runtime: LiteRtLmRuntimeAdapter, name: String): Any? =
        LiteRtLmRuntimeAdapter::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(runtime)

    private fun List<InferenceEvent>.visibleOutput(): String = filterIsInstance<InferenceEvent.Token>()
        .filterNot { it.thinking }.joinToString("") { it.text }.trim()

    private companion object {
        const val TAG = "MobieReconfigureE2E"
        const val MODEL_ID = "litert-community/Qwen3-0.6B-int4"
        const val REVISION = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7"
        const val FILE_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        const val SIZE_BYTES = 347_251_840L
        const val SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
    }
}
