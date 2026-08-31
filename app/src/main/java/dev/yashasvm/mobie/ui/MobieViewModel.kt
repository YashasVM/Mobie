package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.HistoryMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class RuntimeState { IDLE, LOADING, READY, GENERATING, ERROR }

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val imagePath: String? = null,
    val thinking: String = "",
    val rawText: String? = null,
)

data class MobieUiState(
    val models: List<AiModel> = emptyList(),
    val recentModels: List<AiModel> = emptyList(),
    val installedModels: List<InstalledModelEntry> = emptyList(),
    val selected: AiModel? = null,
    val compatibility: CompatibilityResult? = null,
    val device: DeviceProfile? = null,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val tokenConfigured: Boolean = false,
    val welcomeSeen: Boolean = false,
    val onboardingComplete: Boolean = false,
    val download: DownloadProgress? = null,
    val downloadedPath: String? = null,
    val chatting: Boolean = false,
    val runtimeState: RuntimeState = RuntimeState.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val history: List<ChatHistorySession> = emptyList(),
    val sessionId: String? = null,
    val stats: InferenceStats? = null,
)

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MobieUiState(
            device = container.deviceProfile.current(),
            tokenConfigured = container.tokenStore.read() != null,
            welcomeSeen = container.tokenStore.hasSeenWelcome(),
            onboardingComplete = container.tokenStore.hasCompletedOnboarding(),
        ),
    )
    val state: StateFlow<MobieUiState> = mutableState.asStateFlow()
    private var catalogJob: Job? = null
    private var downloadObserverJob: Job? = null
    private var inferenceJob: Job? = null
    private val runtimeLifecycle = Mutex()

    init {
        refreshInstalled()
        if (mutableState.value.welcomeSeen || mutableState.value.onboardingComplete) loadFeatured()
    }

    fun finishOnboarding(token: String?) {
        container.tokenStore.completeOnboarding(token)
        mutableState.update {
            it.copy(onboardingComplete = true, tokenConfigured = !token.isNullOrBlank(), error = null)
        }
        loadFeatured()
    }

    fun finishWelcome() {
        container.tokenStore.markWelcomeSeen()
        mutableState.update { it.copy(welcomeSeen = true) }
        loadFeatured()
    }

    fun loadFeatured() = loadCatalog { container.catalog.featured() }

    fun setQuery(value: String) {
        if (state.value.query != value) catalogJob?.cancel()
        mutableState.update { it.copy(query = value, loading = false, error = null) }
    }

    fun search() {
        val query = state.value.query.trim()
        if (query.isBlank()) return
        loadCatalog(expectedQuery = query) { container.catalog.search(query) }
    }

    private fun loadCatalog(
        expectedQuery: String? = null,
        request: suspend () -> Result<List<AiModel>>,
    ) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, query = expectedQuery ?: "") }
            request().fold(
                onSuccess = { models ->
                    if (expectedQuery == null || state.value.query == expectedQuery) {
                        val device = container.deviceProfile.current()
                        val compatible = models
                            .sortedWith(
                                compareBy<AiModel> {
                                    when (container.compatibility.resolve(it.bestArtifact, device).status) {
                                        Compatibility.COMPATIBLE -> 0
                                        Compatibility.WARNING -> 1
                                        else -> 2
                                    }
                                }
                                    .thenBy { it.bestArtifact?.sizeBytes ?: Long.MAX_VALUE }
                                    .thenByDescending { it.downloads },
                            )
                        mutableState.update { it.copy(models = compatible, device = device, loading = false) }
                    }
                },
                onFailure = { error ->
                    if (expectedQuery == null || state.value.query == expectedQuery) {
                        mutableState.update { it.copy(loading = false, error = error.message ?: "Catalog request failed") }
                    }
                },
            )
        }
    }

    fun select(model: AiModel?) {
        downloadObserverJob?.cancel()
        inferenceJob?.cancel()
        if (model == null) viewModelScope.launch { unloadRuntime() }
        val device = container.deviceProfile.current()
        val artifact = model?.bestArtifact
        val sessions = model?.let { container.chatHistory.sessions(it.id) }.orEmpty()
        val currentSessionId = sessions.firstOrNull()?.id
        mutableState.update {
            it.copy(
                selected = model,
                recentModels = if (model == null) it.recentModels else {
                    (listOf(model) + it.recentModels.filterNot { recent -> recent.id == model.id }).take(5)
                },
                compatibility = artifact?.let { candidate -> container.compatibility.resolve(candidate, device) },
                download = null,
                downloadedPath = null,
                chatting = false,
                runtimeState = RuntimeState.IDLE,
                messages = model?.let(::readHistory).orEmpty(),
                history = sessions,
                sessionId = currentSessionId,
                stats = null,
                error = null,
                device = device,
            )
        }
        if (model != null && artifact != null) {
            observeDownload(model, artifact)
            viewModelScope.launch(Dispatchers.IO) {
                val downloaded = container.downloads.completedFile(model.id, artifact)?.absolutePath
                if (downloaded != null && state.value.selected?.id == model.id) {
                    mutableState.update { it.copy(downloadedPath = downloaded) }
                }
            }
        }
    }

    fun download(allowWarning: Boolean = false) {
        val model = state.value.selected ?: return
        val artifact = model.bestArtifact ?: return
        val device = container.deviceProfile.current()
        val compatibility = container.compatibility.resolve(artifact, device)
        mutableState.update { it.copy(device = device, compatibility = compatibility, error = null) }
        if (model.gated && !state.value.tokenConfigured) {
            mutableState.update { it.copy(error = "This gated model requires a Hugging Face token.") }
            return
        }
        if (
            compatibility.status !in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) ||
            (compatibility.status == Compatibility.WARNING && !allowWarning)
        ) return

        downloadObserverJob?.cancel()
        container.downloads.enqueue(model, artifact)
        observeDownload(model, artifact)
    }

    fun cancelDownload() {
        val model = state.value.selected ?: return
        val artifact = model.bestArtifact ?: return
        container.downloads.cancel(model, artifact)
    }

    fun runInstalled() {
        val path = state.value.downloadedPath
        if (path != null && File(path).isFile) {
            openChat(path)
        } else {
            mutableState.update {
                it.copy(
                    downloadedPath = null,
                    download = null,
                    error = "The local model file is missing. Download it again.",
                )
            }
        }
    }

    private fun openChat(path: String) {
        val model = state.value.selected ?: return
        val history = readHistory(model)
        val sessions = container.chatHistory.sessions(model.id)
        mutableState.update {
            it.copy(
                chatting = true,
                downloadedPath = path,
                runtimeState = RuntimeState.LOADING,
                messages = history,
                history = sessions,
                sessionId = sessions.firstOrNull()?.id,
                error = null,
            )
        }
        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, path, history, preferReset = false)
        }
    }

    private suspend fun loadRuntimeConversation(
        model: AiModel,
        path: String,
        history: List<ChatMessage>,
        preferReset: Boolean,
    ) {
        val adapter = container.runtimes.adapterFor(model.bestArtifact?.format ?: return)
        if (adapter == null) {
            mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = "No runtime for this model") }
            return
        }
        val restored = history.map { RuntimeMessage(it.fromUser, it.text) }
        val result = runtimeLifecycle.withLock {
            if (preferReset) {
                val reset = adapter.resetConversation(restored)
                if (reset.isSuccess) reset else adapter.load(path, model.supportsVision, restored)
            } else {
                adapter.load(path, model.supportsVision, restored)
            }
        }
        result.fold(
            onSuccess = { mutableState.update { it.copy(runtimeState = RuntimeState.READY) } },
            onFailure = { error ->
                mutableState.update {
                    it.copy(runtimeState = RuntimeState.ERROR, error = error.message ?: "Model failed to load")
                }
            },
        )
    }

    fun sendMessage(prompt: String, imagePath: String? = null) {
        val model = state.value.selected ?: return
        if (prompt.isBlank() || state.value.runtimeState != RuntimeState.READY) return
        if (imagePath != null && !model.supportsVision) return
        val adapter = container.runtimes.adapterFor(model.bestArtifact?.format ?: return) ?: return
        mutableState.update {
            it.copy(
                runtimeState = RuntimeState.GENERATING,
                messages = it.messages + ChatMessage(true, prompt.trim(), imagePath) + ChatMessage(false, ""),
                stats = null,
                error = null,
            )
        }
        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            adapter.generate(prompt.trim(), imagePath).collect { event ->
                when (event) {
                    is InferenceEvent.Token -> mutableState.update { current ->
                        current.copy(messages = current.messages.updateLastAssistant(event.text, event.thinking))
                    }
                    is InferenceEvent.Stats -> mutableState.update { it.copy(stats = event.value) }
                    is InferenceEvent.Error -> {
                        val messages = state.value.messages.removeBlankAssistant()
                        persistHistory(model.id, messages)
                        mutableState.update {
                            it.copy(
                                runtimeState = RuntimeState.READY,
                                messages = messages,
                                history = container.chatHistory.sessions(model.id),
                                error = event.message,
                            )
                        }
                    }
                    InferenceEvent.Complete -> {
                        persistHistory(model.id, state.value.messages)
                        mutableState.update {
                            it.copy(runtimeState = RuntimeState.READY, history = container.chatHistory.sessions(model.id))
                        }
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        if (state.value.runtimeState != RuntimeState.GENERATING) return
        inferenceJob?.cancel()
        val model = state.value.selected
        val messages = state.value.messages.removeBlankAssistant()
        if (model != null) persistHistory(model.id, messages)
        mutableState.update { it.copy(runtimeState = RuntimeState.READY, messages = messages, error = null) }
        viewModelScope.launch { container.runtimes.all().forEach { it.cancel() } }
    }

    fun newChat() {
        val model = state.value.selected ?: return
        val path = state.value.downloadedPath ?: return
        val canReuseLoadedModel = state.value.runtimeState == RuntimeState.READY
        inferenceJob?.cancel()
        container.chatHistory.startNewSession(model.id)
        mutableState.update {
            it.copy(
                messages = emptyList(),
                history = container.chatHistory.sessions(model.id),
                sessionId = container.chatHistory.sessions(model.id).firstOrNull()?.id,
                runtimeState = RuntimeState.LOADING,
                stats = null,
                error = null,
            )
        }
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, path, emptyList(), preferReset = canReuseLoadedModel)
        }
    }

    fun startInstalledChat(entry: InstalledModelEntry) {
        select(entry.model)
        container.chatHistory.startNewSession(entry.model.id)
        openChat(entry.localPath)
    }

    fun deleteInstalled(entry: InstalledModelEntry) {
        viewModelScope.launch {
            val deleted = container.downloads.deleteInstalled(entry.model)
            if (deleted) {
                mutableState.update {
                    it.copy(installedModels = it.installedModels.filterNot { installed -> installed.model.id == entry.model.id })
                }
            } else {
                mutableState.update { it.copy(error = "${entry.model.title} could not be deleted.") }
            }
        }
    }

    fun leaveChat() {
        inferenceJob?.cancel()
        viewModelScope.launch { unloadRuntime() }
        mutableState.update {
            it.copy(chatting = false, runtimeState = RuntimeState.IDLE, stats = null, error = null)
        }
    }

    private suspend fun unloadRuntime() {
        runtimeLifecycle.withLock { container.runtimes.all().forEach { it.unload() } }
    }

    fun selectHistory(sessionId: String) {
        val model = state.value.selected ?: return
        val path = state.value.downloadedPath ?: return
        val canReuseLoadedModel = state.value.runtimeState == RuntimeState.READY
        container.chatHistory.activate(model.id, sessionId)
        val history = readHistory(model)
        inferenceJob?.cancel()
        mutableState.update {
            it.copy(
                messages = history,
                history = container.chatHistory.sessions(model.id),
                sessionId = sessionId,
                runtimeState = RuntimeState.LOADING,
                stats = null,
                error = null,
            )
        }
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, path, history, preferReset = canReuseLoadedModel)
        }
    }

    fun saveToken(token: String) {
        container.tokenStore.save(token.ifBlank { null })
        mutableState.update { it.copy(tokenConfigured = token.isNotBlank()) }
    }

    private fun observeDownload(model: AiModel, artifact: dev.yashasvm.mobie.core.model.ModelArtifact) {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(model.id, artifact).collect { progress ->
                if (state.value.selected?.id != model.id || progress == null) return@collect
                val completedPath = if (progress.state == WorkInfo.State.SUCCEEDED) {
                    withContext(Dispatchers.IO) {
                        container.downloads.completedFile(model.id, artifact)?.absolutePath
                    }
                } else {
                    null
                }
                mutableState.update {
                    it.copy(
                        download = progress,
                        downloadedPath = when {
                            completedPath != null -> completedPath
                            progress.state == WorkInfo.State.SUCCEEDED -> null
                            else -> it.downloadedPath
                        },
                    )
                }
                if (progress.state == WorkInfo.State.SUCCEEDED) refreshInstalled()
            }
        }
    }

    private fun refreshInstalled() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { container.downloads.installedModels() }
            mutableState.update { it.copy(installedModels = installed) }
        }
    }

    private fun readHistory(model: AiModel): List<ChatMessage> = container.chatHistory.read(model.id).map {
        ChatMessage(it.fromUser, it.text, it.imagePath, it.thinking)
    }

    private fun persistHistory(modelId: String, messages: List<ChatMessage>) {
        container.chatHistory.write(
            modelId,
            messages.map { HistoryMessage(it.fromUser, it.text, it.imagePath, it.thinking) },
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MobieViewModel(container) as T
            }
    }
}

internal fun List<ChatMessage>.updateLastAssistant(chunk: String, thinkingChunk: Boolean = false): List<ChatMessage> {
    val index = indexOfLast { !it.fromUser }
    if (index < 0) return this
    return toMutableList().apply {
        val message = this[index]
        if (thinkingChunk) {
            this[index] = message.copy(thinking = message.thinking + chunk)
            return@apply
        }
        val raw = (message.rawText ?: message.text) + chunk
        val tag = REASONING_TAGS
            .map { it to raw.indexOf("<$it>", ignoreCase = true) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
        val open = tag?.second ?: -1
        val close = tag?.let { raw.indexOf("</${it.first}>", startIndex = open, ignoreCase = true) } ?: -1
        val thinking = if (tag != null) {
            raw.substring(open + tag.first.length + 2, if (close > open) close else raw.length).trim()
        } else message.thinking
        val answer = when {
            open < 0 && REASONING_TAGS.any { "<$it>".startsWith(raw.trimStart(), ignoreCase = true) } -> ""
            open < 0 -> raw
            close < 0 -> raw.substring(0, open)
            else -> (raw.substring(0, open) + raw.substring(close + tag!!.first.length + 3)).trim()
        }
        this[index] = message.copy(text = answer, thinking = thinking, rawText = raw)
    }
}

private val REASONING_TAGS = listOf("think", "thinking", "analysis", "reasoning")

private fun List<ChatMessage>.removeBlankAssistant(): List<ChatMessage> =
    if (lastOrNull()?.let { !it.fromUser && it.text.isBlank() } == true) dropLast(1) else this
