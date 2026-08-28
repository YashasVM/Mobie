package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.ConversionStatus
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.data.download.DownloadProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MobieUiState(
    val models: List<AiModel> = emptyList(),
    val recentModels: List<AiModel> = emptyList(),
    val selected: AiModel? = null,
    val compatibility: CompatibilityResult? = null,
    val device: DeviceProfile? = null,
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val conversionStatus: ConversionStatus? = null,
    val tokenConfigured: Boolean = false,
    val download: DownloadProgress? = null,
)

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(MobieUiState())
    val state: StateFlow<MobieUiState> = mutableState.asStateFlow()
    private var catalogJob: Job? = null
    private var downloadObserverJob: Job? = null

    init {
        mutableState.update {
            it.copy(device = container.deviceProfile.current(), tokenConfigured = container.tokenStore.read() != null)
        }
        loadFeatured()
    }

    fun loadFeatured() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, query = "") }
            container.catalog.featured().fold(
                onSuccess = { models -> mutableState.update { it.copy(models = models, loading = false) } },
                onFailure = { error -> mutableState.update { it.copy(loading = false, error = error.message) } },
            )
        }
    }

    fun setQuery(value: String) {
        if (state.value.query != value) catalogJob?.cancel()
        mutableState.update { it.copy(query = value, loading = false, error = null) }
    }

    fun search() {
        val query = state.value.query
        if (query.isBlank()) return
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            container.catalog.search(query).fold(
                onSuccess = { models ->
                    if (state.value.query == query) mutableState.update { it.copy(models = models, loading = false) }
                },
                onFailure = { error ->
                    if (state.value.query == query) {
                        mutableState.update { it.copy(loading = false, error = error.message) }
                    }
                },
            )
        }
    }

    fun select(model: AiModel?) {
        downloadObserverJob?.cancel()
        val device = model?.let { container.deviceProfile.current() } ?: state.value.device
        mutableState.update {
            it.copy(
                selected = model,
                recentModels = if (model == null) it.recentModels else {
                    (listOf(model) + it.recentModels.filterNot { recent -> recent.id == model.id }).take(5)
                },
                compatibility = if (model != null && device != null) {
                    container.compatibility.resolve(model.bestArtifact, device)
                } else null,
                conversionStatus = null,
                download = null,
                error = null,
                device = device,
            )
        }
    }

    fun download(allowWarning: Boolean = false) {
        val model = state.value.selected ?: return
        val artifact = model.bestArtifact ?: return
        val device = container.deviceProfile.current()
        val compatibility = container.compatibility.resolve(artifact, device)
        mutableState.update { it.copy(device = device, compatibility = compatibility, error = null) }
        if (compatibility.status in setOf(Compatibility.INCOMPATIBLE, Compatibility.CONVERSION_REQUIRED)) return
        if (compatibility.status == Compatibility.WARNING && !allowWarning) return

        downloadObserverJob?.cancel()
        val id = container.downloads.enqueue(model.id, artifact)
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(id).collect { progress ->
                if (state.value.selected?.id == model.id) {
                    mutableState.update { it.copy(download = progress) }
                }
            }
        }
    }

    fun requestConversion() = viewModelScope.launch {
        val modelId = state.value.selected?.id ?: return@launch
        container.conversion.request(modelId).fold(
            onSuccess = { status -> mutableState.update { it.copy(conversionStatus = status) } },
            onFailure = { error -> mutableState.update { it.copy(error = error.message) } },
        )
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
