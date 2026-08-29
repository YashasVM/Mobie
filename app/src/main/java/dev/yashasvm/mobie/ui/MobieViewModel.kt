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
import dev.yashasvm.mobie.data.download.DownloadProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RuntimeState { IDLE, LOADING, READY, GENERATING, ERROR }

data class ChatMessage(val fromUser: Boolean, val text: String, val imagePath: String? = null)

data class MobieUiState(
    val models: List<AiModel> = emptyList(),
    val recentModels: List<AiModel> = emptyList(),
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

    init {
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
                            .filter { container.compatibility.resolve(it.bestArtifact, device).status == Compatibility.COMPATIBLE }
                            .sortedWith(
                                compareByDescending<AiModel> { it.bestArtifact?.sizeBytes ?: 0 }
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
                messages = emptyList(),
                stats = null,
                error = null,
                device = device,
            )
        }
    }

    fun download() {
        val model = state.value.selected ?: return
        val artifact = model.bestArtifact ?: return
        val device = container.deviceProfile.current()
        val compatibility = container.compatibility.resolve(artifact, device)
        mutableState.update { it.copy(device = device, compatibility = compatibility, error = null) }
        if (compatibility.status != Compatibility.COMPATIBLE) return

        downloadObserverJob?.cancel()
        val id = container.downloads.enqueue(model.id, artifact)
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(id).collect { progress ->
                if (state.value.selected?.id != model.id) return@collect
                mutableState.update { it.copy(download = progress) }
                if (progress.state == WorkInfo.State.SUCCEEDED && progress.localPath != null && !state.value.chatting) {
                    openChat(progress.localPath)
                }
            }
        }
    }

    fun runInstalled() {
        state.value.downloadedPath?.let(::openChat)
    }

    private fun openChat(path: String) {
        val model = state.value.selected ?: return
        mutableState.update {
            it.copy(chatting = true, downloadedPath = path, runtimeState = RuntimeState.LOADING, error = null)
        }
        viewModelScope.launch {
            val adapter = container.runtimes.adapterFor(model.bestArtifact?.format ?: return@launch)
            if (adapter == null) {
                mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = "No runtime for this model") }
                return@launch
            }
            adapter.load(path, model.supportsVision).fold(
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
                        it.copy(runtimeState = RuntimeState.ERROR, error = event.message)
                    }
                    InferenceEvent.Complete -> mutableState.update { it.copy(runtimeState = RuntimeState.READY) }
                }
            }
        }
    }

    fun leaveChat() {
        inferenceJob?.cancel()
        viewModelScope.launch { unloadRuntime() }
        mutableState.update {
            it.copy(chatting = false, runtimeState = RuntimeState.IDLE, messages = emptyList(), stats = null, error = null)
        }
    }

    private suspend fun unloadRuntime() {
        container.runtimes.all().forEach { it.unload() }
    }

    fun saveToken(token: String) {
        container.tokenStore.save(token.ifBlank { null })
        mutableState.update { it.copy(tokenConfigured = token.isNotBlank()) }
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
