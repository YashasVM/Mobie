package dev.yashasvm.mobie

import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exploratory SDK context allocation. Does not override production metadata restrictions. */
@OptIn(ExperimentalApi::class)
class LiteRtGpuContextProbeTest {
    @Test fun probesContextAllocation(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("gpuContextProbe") == "true")
        assertTrue("Physical ARM64 required", "arm64-v8a" in Build.SUPPORTED_ABIS && Build.HARDWARE !in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, checkNotNull(args.getString("modelPath"))).canonicalFile
        require(model.path.startsWith(context.filesDir.canonicalPath + "/") && model.isFile)
        val tokens = checkNotNull(args.getString("contextTokens")).toInt().also { require(it in listOf(1024, 2048, 4096)) }
        val repetitions = (args.getString("repetitions")?.toInt() ?: 4).also { require(it in 1..10) }
        val holdSeconds = (args.getString("holdSeconds")?.toInt() ?: 0).also { require(it in 0..60) }
        val power = context.getSystemService(PowerManager::class.java)
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        ExperimentalFlags.enableBenchmark = true
        repeat(repetitions) { iteration ->
            coroutineScope {
                val startThermal = power.currentThermalStatus
                val thermalMax = AtomicInteger(startThermal)
                val peak = AtomicLong(Debug.getPss() * 1024L)
                val monitor = launch(Dispatchers.Default) {
                    while (isActive) {
                        peak.accumulateAndGet(Debug.getPss() * 1024L, ::maxOf)
                        thermalMax.accumulateAndGet(power.currentThermalStatus, ::maxOf)
                        delay(100)
                    }
                }
                val result = JSONObject().put("context_tokens", tokens).put("iteration", iteration)
                    .put("model", model.name).put("artifact_sha256", sha).put("artifact_bytes", model.length())
                    .put("quantization", "INT4 block32 / FP32 artifact activations").put("device", Build.MODEL)
                    .put("android", Build.VERSION.RELEASE).put("runtime_version", BuildConfig.LITERT_LM_VERSION)
                    .put("active_backend", "GPU").put("thermal_start", startThermal)
                ModelInfo.from(model.path).use { info ->
                    result.put("artifact_max_context", info.maxContextTokens()).put("dynamic_context", info.isDynamicContext())
                }
                val engine = Engine(EngineConfig(modelPath = model.path, backend = Backend.GPU(), maxNumTokens = tokens,
                    cacheDir = File(model.parentFile, ".litert-cache").path))
                try {
                    val started = SystemClock.elapsedRealtime()
                    withTimeout(120_000) { engine.initialize() }
                    result.put("load_ms", SystemClock.elapsedRealtime() - started)
                    val conversation = engine.createConversation(ConversationConfig(thinkingConfig = ThinkingConfig(false),
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)))
                    try {
                        var firstChunkMs: Long? = null
                        val visible = StringBuilder()
                        val generationStart = SystemClock.elapsedRealtime()
                        withTimeout(120_000) {
                            conversation.sendMessageAsync(Contents.of(PROMPT), maxOutputToken = 64).collect {
                                if (it.toString().isNotEmpty()) {
                                    if (firstChunkMs == null) firstChunkMs = SystemClock.elapsedRealtime() - generationStart
                                    visible.append(it.toString())
                                }
                            }
                        }
                        assertTrue("No visible answer", visible.isNotEmpty())
                        val info = conversation.getBenchmarkInfo()
                        val residentPss = Debug.getPss() * 1024L
                        peak.accumulateAndGet(residentPss, ::maxOf)
                        result.put("decode_tps", info.lastDecodeTokensPerSecond).put("prefill_tps", info.lastPrefillTokensPerSecond)
                            .put("decode_tokens", info.lastDecodeTokenCount).put("resident_pss_bytes", residentPss)
                            .put("first_chunk_ms", firstChunkMs).put("pid", android.os.Process.myPid())
                            .put("visible_sha256", MessageDigest.getInstance("SHA-256").digest(visible.toString().toByteArray())
                                .joinToString("") { "%02x".format(it) })
                        if (holdSeconds > 0) delay(holdSeconds * 1000L)
                    } finally { conversation.close() }
                } catch (failure: Exception) {
                    result.put("error", failure.message ?: failure.javaClass.simpleName)
                    throw failure
                } finally {
                    engine.close()
                    monitor.cancel()
                    result.put("peak_pss_bytes", peak.get()).put("thermal_max", thermalMax.get())
                        .put("thermal_end", power.currentThermalStatus)
                    File(context.filesDir, "gpu-context-probe.jsonl").appendText(result.toString() + "\n")
                    android.util.Log.i("MobieGpuContextProbe", result.toString())
                }
            }
        }
    }

    private companion object {
        const val PROMPT = "Explain in a few concise sentences why local AI inference can be useful on a phone. /no_think"
    }
}
