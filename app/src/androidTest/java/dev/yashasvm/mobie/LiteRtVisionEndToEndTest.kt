package dev.yashasvm.mobie

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtVisionEndToEndTest {
    private companion object {
        const val TAG = "MobieVisionE2E"
        const val MODEL_ID = "litert-community/SmolVLM2-500M"
        const val FILE_NAME = "SmolVLM2-500M.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/SmolVLM2-500M/resolve/main/SmolVLM2-500M.litertlm"
    }

    @Test
    fun restoresHistoricalImageThenHandlesTextFollowUpAndReplacementImage(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertVisionE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val artifact = ModelArtifact(
            fileName = FILE_NAME,
            downloadUrl = MODEL_URL,
            // The upstream bundle may receive metadata/tokenizer-only refreshes. Let the download
            // worker trust the server-reported length instead of pinning a stale byte count here.
            sizeBytes = 0,
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
            contextWindowTokens = 2_048,
        )
        val downloads = ModelDownloadManager(context)
        val requestId = downloads.enqueue(MODEL_ID, artifact)
        val completed = withTimeout(20 * 60 * 1000L) {
            downloads.observe(requestId).first { it.state.isFinished }
        }
        assertTrue("Vision model download failed: ${completed.error}", completed.state == WorkInfo.State.SUCCEEDED)
        val path = checkNotNull(completed.localPath)

        val imageDirectory = checkNotNull(context.getExternalFilesDir("vision-e2e"))
        val restoredImage = File(imageDirectory, "restored-image.png")
        val replacementImage = File(imageDirectory, "replacement-image.png")
        createTestImage(restoredImage, Color.RED, Color.WHITE)
        createTestImage(replacementImage, Color.BLUE, Color.YELLOW)

        val runtime = LiteRtLmRuntimeAdapter(context)
        runtime.load(
            modelPath = path,
            vision = true,
            history = listOf(
                RuntimeMessage(
                    fromUser = true,
                    text = "This earlier image contains a large colored square. Remember it for the conversation.",
                    imagePath = restoredImage.absolutePath,
                ),
                RuntimeMessage(
                    fromUser = false,
                    text = "I can use that image as context for later questions.",
                ),
            ),
        ).getOrThrow()

        try {
            val restoredFollowUp = generate(
                runtime,
                prompt = "Reply briefly: are you still able to continue this conversation after the restored image?",
            )
            assertSuccessful("text follow-up after restored image", restoredFollowUp)
            Log.i(TAG, "Restored-image follow-up: ${restoredFollowUp.visibleOutput()}")

            val replacement = generate(
                runtime,
                prompt = "Describe the dominant colors in this new image in one short sentence.",
                imagePath = replacementImage.absolutePath,
            )
            assertSuccessful("replacement image", replacement)
            Log.i(TAG, "Replacement-image response: ${replacement.visibleOutput()}")

            val postReplacement = generate(
                runtime,
                prompt = "Reply briefly: can you continue after receiving the replacement image?",
            )
            assertSuccessful("text follow-up after replacement image", postReplacement)
            Log.i(TAG, "Post-replacement follow-up: ${postReplacement.visibleOutput()}")
        } finally {
            runtime.unload()
        }
    }

    private suspend fun generate(
        runtime: LiteRtLmRuntimeAdapter,
        prompt: String,
        imagePath: String? = null,
    ): List<InferenceEvent> = withTimeout(15 * 60 * 1000L) {
        runtime.generate(
            prompt = prompt,
            imagePath = imagePath,
            config = GenerationConfig(maxNewTokens = 64),
        ).toList()
    }

    private fun assertSuccessful(label: String, events: List<InferenceEvent>) {
        val errors = events.filterIsInstance<InferenceEvent.Error>().joinToString { it.message }
        assertTrue("No visible output for $label. Runtime errors: $errors", events.visibleOutput().isNotBlank())
        assertTrue("Generation did not complete for $label. Runtime errors: $errors", events.any { it is InferenceEvent.Complete })
        assertTrue("Runtime returned an error for $label: $errors", errors.isBlank())
    }

    private fun List<InferenceEvent>.visibleOutput(): String =
        filterIsInstance<InferenceEvent.Token>()
            .filterNot { it.thinking }
            .joinToString("") { it.text }
            .trim()

    private fun createTestImage(destination: File, background: Int, foreground: Int) {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            val paint = Paint().apply { color = foreground }
            canvas.drawRect(144f, 144f, 368f, 368f, paint)
            FileOutputStream(destination).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
