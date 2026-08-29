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

enum class RuntimeState { IDLE, LOADING, READY, GENERATING, ERROR }

data class ChatMessage(val fromUser: Boolean, val text: String, val imagePath: String? = null)

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
    val onboardingComplete: Boolean = false,
    val download: DownloadProgress? = null,
    val downloadedPath: String? = null,
    val chatting: Boolean = false,
    val runtimeState: RuntimeState = RuntimeState.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val stats: InferenceStats? = null,
)

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MobieUiState(
            device = container.deviceProfile.current(),
            tokenConfigured = container.tokenStore.read() != null,
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
        if (mutableState.value.onboardingComplete) loadFeatured()
    }

    fun finishOnboarding(token: String?) {
        container.tokenStore.completeOnboarding(token)
        mutableState.update {
            it.copy(onboardingComplete = true, tokenConfigured = !token.isNullOrBlank(), error = null)
        }
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
        val downloaded = if (model != null && artifact != null) {
            container.downloads.completedFile(model.id, artifact)?.absolutePath
        } else null
        mutableState.update {
            it.copy(
                selected = model,
                recentModels = if (model == null) it.recentModels else {
                    (listOf(model) + it.recentModels.filterNot { recent -> recent.id == model.id }).take(5)
                },
                compatibility = artifact?.let { candidate -> container.compatibility.resolve(candidate, device) },
                download = null,
                downloadedPath = downloaded,
                chatting = false,
                runtimeState = RuntimeState.IDLE,
                messages = model?.let(::readHistory).orEmpty(),
                stats = null,
                error = null,
                device = device,
            )
        }
        if (model != null && artifact != null && downloaded == null) observeDownload(model, artifact)
    }

    fun download(allowWarning: Boolean = false) {
        val model = state.value.selected ?: return
        val artifact = model.bestArtifact ?: return
        val device = container.deviceProfile.current()
        val compatibility = container.compatibility.resolve(artifact, device)
        mutableState.update { it.copy(device = device, compatibility = compatibility, error = null) }
        if (
            compatibility.status !in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) ||
            (compatibility.status == Compatibility.WARNING && !allowWarning)
        ) return

        downloadObserverJob?.cancel()
        container.downloads.enqueue(model, artifact)
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(model.id, artifact).collect { progress ->
                if (progress == null) return@collect
                if (state.value.selected?.id != model.id) return@collect
                mutableState.update { it.copy(download = progress) }
                if (progress.state == WorkInfo.State.SUCCEEDED && progress.localPath != null) {
                    mutableState.update { it.copy(downloadedPath = progress.localPath) }
                    refreshInstalled()
                }
            }
        }
    }

    fun runInstalled() {
        state.value.downloadedPath?.let(::openChat)
    }

    private fun openChat(path: String) {
        val model = state.value.selected ?: return
        val history = readHistory(model)
        mutableState.update {
            it.copy(
                chatting = true,
                downloadedPath = path,
                runtimeState = RuntimeState.LOADING,
                messages = history,
                error = null,
            )
        }
        inferenceJob?.cancel()
        inferenceJob = viewModelScope.launch {
            val adapter = container.runtimes.adapterFor(model.bestArtifact?.format ?: return@launch)
            if (adapter == null) {
                mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = "No runtime for this model") }
                return@launch
            }
            runtimeLifecycle.withLock {
                adapter.load(
                    path,
                    model.supportsVision,
                    history.map { RuntimeMessage(it.fromUser, it.text) },
                )
            }.fold(
                onSuccess = { mutableState.update { it.copy(runtimeState = RuntimeState.READY) } },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(runtimeState = RuntimeState.ERROR, error = error.message ?: "Model failed to load")
                    }
                },
            )
        }
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
                        current.copy(messages = current.messages.updateLastAssistant(event.text))
                    }
                    is InferenceEvent.Stats -> mutableState.update { it.copy(stats = event.value) }
                    is InferenceEvent.Error -> mutableState.update {
                        val messages = it.messages.removeBlankAssistant()
                        persistHistory(model.id, messages)
                        it.copy(runtimeState = RuntimeState.READY, messages = messages, error = event.message)
                    }
                    InferenceEvent.Complete -> mutableState.update {
                        persistHistory(model.id, it.messages)
                        it.copy(runtimeState = RuntimeState.READY)
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
        inferenceJob?.cancel()
        container.chatHistory.clear(model.id)
        mutableState.update { it.copy(messages = emptyList(), stats = null, error = null) }
        viewModelScope.launch {
            unloadRuntime()
            openChat(path)
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

    fun saveToken(token: String) {
        container.tokenStore.save(token.ifBlank { null })
        mutableState.update { it.copy(tokenConfigured = token.isNotBlank()) }
    }

    private fun observeDownload(model: AiModel, artifact: dev.yashasvm.mobie.core.model.ModelArtifact) {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(model.id, artifact).collect { progress ->
                if (state.value.selected?.id != model.id || progress == null) return@collect
                mutableState.update {
                    it.copy(
                        download = progress,
                        downloadedPath = progress.localPath ?: it.downloadedPath,
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
        ChatMessage(it.fromUser, it.text, it.imagePath)
    }

    private fun persistHistory(modelId: String, messages: List<ChatMessage>) {
        container.chatHistory.write(modelId, messages.map { HistoryMessage(it.fromUser, it.text, it.imagePath) })
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MobieViewModel(container) as T
            }
    }
}

private fun List<ChatMessage>.updateLastAssistant(chunk: String): List<ChatMessage> {
    val index = indexOfLast { !it.fromUser }
    if (index < 0) return this
    return toMutableList().apply { this[index] = this[index].copy(text = this[index].text + chunk) }
}

private fun List<ChatMessage>.removeBlankAssistant(): List<ChatMessage> =
    if (lastOrNull()?.let { !it.fromUser && it.text.isBlank() } == true) dropLast(1) else this
