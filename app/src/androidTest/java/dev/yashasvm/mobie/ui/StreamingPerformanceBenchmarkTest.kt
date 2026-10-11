package dev.yashasvm.mobie.ui

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import java.io.File
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Application work for a burst of SDK chunks, not a model decode or phone speed benchmark. */
class StreamingPerformanceBenchmarkTest {
    @Test
    fun comparesPerChunkPresentationAgainstBatchedPresentation(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("streamBenchmark") == "true")
        val chunks = List(2_048) { index ->
            if (index % 8 == 0) "\n- **Item $index**: " else "local output "
        }
        val expected = chunks.joinToString("")
        suspend fun runCase(batched: Boolean): Measurement {
            var messages = listOf(ChatMessage(false, ""))
            var updates = 0
            var firstAt = 0L
            var secondAt = 0L
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val events = flow {
                chunks.forEach { emit(InferenceEvent.Token(it)) }
                emit(InferenceEvent.Complete)
            }
            val consume: (InferenceEvent) -> Unit = { event ->
                if (event is InferenceEvent.Token) {
                    updates++
                    if (updates == 1) firstAt = SystemClock.elapsedRealtimeNanos() - startedAt
                    if (updates == 2) secondAt = SystemClock.elapsedRealtimeNanos() - startedAt
                    messages = messages.updateLastAssistant(event.text, event.thinking)
                    // Exercise the real Markdown parser that runs when the reply text changes.
                    assertTrue(parseChatMarkdown(messages.last().text).isNotEmpty())
                }
            }
            if (batched) events.collectBatchedTokens(consume) else events.collect(consume)
            val elapsed = SystemClock.elapsedRealtimeNanos() - startedAt
            assertEquals(expected, messages.single().text)
            return Measurement(elapsed, updates, firstAt, secondAt)
        }

        repeat(2) { runCase(false); runCase(true) }
        val baseline = mutableListOf<Measurement>()
        val candidate = mutableListOf<Measurement>()
        repeat(7) { round ->
            if (round % 2 == 0) {
                baseline += runCase(false)
                candidate += runCase(true)
            } else {
                candidate += runCase(true)
                baseline += runCase(false)
            }
        }
        fun median(values: List<Long>): Long = values.sorted()[values.size / 2]
        val baselineNs = median(baseline.map { it.elapsedNs })
        val candidateNs = median(candidate.map { it.elapsedNs })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-stream-benchmark.txt")
        metricsFile.writeText(
            buildString {
                appendLine("scope=SDK chunk burst presentation and Markdown parsing only; no native inference")
                appendLine("device=${Build.MODEL};sdk=${Build.VERSION.SDK_INT};abis=${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("chunks=${chunks.size};characters=${expected.length};warmups=2;rounds=7;order=alternating")
                appendLine("baseline_median_ms=${baselineNs / 1_000_000.0}")
                appendLine("candidate_median_ms=${candidateNs / 1_000_000.0}")
                appendLine("presentation_speedup=${baselineNs.toDouble() / candidateNs}")
                appendLine("baseline_updates=${baseline.map { it.updates }}")
                appendLine("candidate_updates=${candidate.map { it.updates }}")
                appendLine("baseline_first_chunk_ns=${baseline.map { it.firstNs }}")
                appendLine("candidate_first_chunk_ns=${candidate.map { it.firstNs }}")
                appendLine("baseline_second_chunk_ns=${baseline.map { it.secondNs }}")
                appendLine("candidate_second_chunk_ns=${candidate.map { it.secondNs }}")
                appendLine("baseline_elapsed_ns=${baseline.map { it.elapsedNs }}")
                appendLine("candidate_elapsed_ns=${candidate.map { it.elapsedNs }}")
            },
        )
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cp ${metricsFile.absolutePath} /sdcard/mobie-stream-benchmark.txt",
            ),
        ).use { it.readBytes() }
        Unit
    }

    private data class Measurement(val elapsedNs: Long, val updates: Int, val firstNs: Long, val secondNs: Long)
}
