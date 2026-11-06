package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.composables.icons.lucide.R as LucideR
import java.util.Locale

/** Speech output is opt-in and only uses a voice installed on the device. */
internal class OfflineReader(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var initialized = false
    private var utterance = 0L
    private var engine: TextToSpeech? = null
    var speakingText by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed) {
                    initialized = status == TextToSpeech.SUCCESS
                    if (!initialized) error = "Speech output is unavailable on this phone."
                }
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                main.post { if (!closed && utteranceId == "reply-$utterance-end") speakingText = null }
            }
            @Deprecated("Android callback")
            override fun onError(utteranceId: String?) {
                main.post {
                    if (!closed && utteranceId?.startsWith("reply-$utterance-") == true) {
                        stop()
                        error = "Couldn't read this reply aloud. Check your installed speech voices."
                    }
                }
            }
        })
    }

    fun toggle(text: String) {
        if (closed) return
        if (speakingText == text) {
            stop()
            return
        }
        stop()
        error = null
        val tts = engine ?: return
        if (!initialized) {
            error = "Speech output is still starting. Try again in a moment."
            return
        }
        val locale = Locale.getDefault()
        val voice = tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language }
            .sortedByDescending { it.locale == locale }.firstOrNull()
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            error = "Install an offline ${locale.displayLanguage} voice in Android speech settings to read replies aloud."
            return
        }
        speakingText = text
        val currentUtterance = utterance
        val chunks = speechChunks(text)
        chunks.forEachIndexed { index, chunk ->
            val id = if (index == chunks.lastIndex) "reply-$currentUtterance-end" else "reply-$currentUtterance-$index"
            if (tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) {
                stop()
                error = "Couldn't read this reply aloud."
                return
            }
        }
    }

    fun stop() {
        utterance++
        engine?.stop()
        speakingText = null
    }

    fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
        main.removeCallbacksAndMessages(null)
    }
}

internal fun speechChunks(text: String): List<String> = buildList {
    var start = 0
    while (start < text.length) {
        var end = minOf(start + 3500, text.length)
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        add(text.substring(start, end))
        start = end
    }
}

internal fun appendDictation(draft: String, spoken: String): String =
    if (spoken.isBlank()) draft else if (draft.isBlank()) spoken.trim() else "${draft.trimEnd()} ${spoken.trim()}"

@Composable
internal fun rememberOfflineReader(): OfflineReader {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reader = remember(context, lifecycle) { OfflineReader(context) }
    DisposableEffect(reader, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) reader.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            reader.close()
        }
    }
    return reader
}

/** API 31's dedicated on-device service is the only recognition path; there is no online fallback. */
@Composable
internal fun OfflineDictationButton(enabled: Boolean, onText: (String) -> Unit, onError: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnText by rememberUpdatedState(onText)
    val latestOnError by rememberUpdatedState(onError)
    val latestEnabled by rememberUpdatedState(enabled)
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listening by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(0L) }
    val available = remember(context) {
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
    val start = {
        if (latestEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && Build.VERSION.SDK_INT >= 31 && available) {
            try {
                val service = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it }
                val currentRequest = ++request
                service.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (currentRequest == request) listening = true }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) {
                        if (currentRequest != request) return
                        listening = false
                        latestOnError(when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access to dictate on this phone."
                            else -> "On-device dictation is unavailable. Check your installed speech language."
                        })
                    }
                    override fun onResults(results: Bundle?) {
                        if (currentRequest != request) return
                        listening = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (latestEnabled && !text.isNullOrBlank()) latestOnText(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                latestOnError(null)
                listening = true
                service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            } catch (_: RuntimeException) {
                listening = false
                latestOnError("Couldn't start on-device dictation on this phone.")
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else latestOnError("Microphone access is needed for on-device dictation. You can still type your message.")
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            request++
            recognizer?.cancel()
            listening = false
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                request++
                recognizer?.cancel()
                listening = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            request++
            recognizer?.destroy()
            recognizer = null
        }
    }
    IconButton(
        enabled = enabled,
        modifier = Modifier.testTag("dictate_on_device"),
        onClick = {
            when {
                listening -> { request++; recognizer?.cancel(); listening = false }
                !available -> latestOnError("On-device dictation needs Android 12 or newer and an installed on-device speech service.")
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    ) {
        LucideIcon(
            if (listening) LucideR.drawable.lucide_ic_square else LucideR.drawable.lucide_ic_mic,
            if (listening) "Stop dictation" else "Dictate on device",
            Modifier.size(20.dp),
        )
    }
}
