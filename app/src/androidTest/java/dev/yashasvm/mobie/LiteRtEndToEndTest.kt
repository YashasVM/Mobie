package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtEndToEndTest {
    @Test
    fun downloadsLoadsAndGeneratesWithRealLiteRtModel() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val artifact = ModelArtifact(
            fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            sizeBytes = 344_437_808L,
            sha256 = "e3e290109da4388d65a17510a0c66af91c8039f52d2c465868dbc43c09a776cf",
            format = ModelFormat.LITERT_LM,
        )
        val downloads = ModelDownloadManager(context)
        val requestId = downloads.enqueue("litert-community/Qwen3-0.6B", artifact)
        val completed = withTimeout(20 * 60 * 1000L) {
            downloads.observe(requestId).first { it.state.isFinished }
        }
        assertTrue("Model download failed: ${completed.error}", completed.state == WorkInfo.State.SUCCEEDED)
        val path = checkNotNull(completed.localPath)

        val runtime = LiteRtLmRuntimeAdapter(context)
        runtime.load(path).getOrThrow()
        val events = mutableListOf<InferenceEvent>()
        withTimeout(15 * 60 * 1000L) {
            runtime.generate("Reply with exactly: Mobie works", config = GenerationConfig(maxNewTokens = 32))
                .collect { events += it }
        }
        runtime.unload()

        val output = events.filterIsInstance<InferenceEvent.Token>().joinToString("") { it.text }
        val errors = events.filterIsInstance<InferenceEvent.Error>().joinToString { it.message }
        assertTrue("LiteRT-LM produced no output. Runtime errors: $errors", output.isNotBlank())
        assertTrue("Generation did not complete", events.any { it is InferenceEvent.Complete })
        assertTrue("No measured runtime stats", events.any { it is InferenceEvent.Stats })
    }
}
