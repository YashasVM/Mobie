package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import android.os.SystemClock
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.device.BatteryCurrentMath
import dev.yashasvm.mobie.core.device.DecodeMeasurement
import dev.yashasvm.mobie.core.device.DecodeRunSettings
import dev.yashasvm.mobie.core.device.DecodeSpeedStore
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.device.HardwareProfileProvider
import dev.yashasvm.mobie.core.device.modelPerformanceIdentity
import dev.yashasvm.mobie.core.device.RunTelemetry
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.core.runtime.RuntimeModelOwnership
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.HistoryMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class RuntimeState { IDLE, LOADING, READY, GENERATING, STOPPING, ERROR }

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val imagePath: String? = null,
    val thinking: String = "",
    val rawText: String? = null,
    val interrupted: Boolean = false,
    /** Measured performance for this assistant reply; null until the runtime reports it. */
    val stats: InferenceStats? = null,
)

data class MobieUiState(
    val models: List<AiModel> = emptyList(),
    val recentModels: List<AiModel> = emptyList(),
    val installedModels: List<InstalledModelEntry> = emptyList(),
    val selected: AiModel? = null,
    val selectedArtifact: ModelArtifact? = null,
    val compatibility: CompatibilityResult? = null,
    val device: DeviceProfile? = null,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Catalog fetch failure, kept apart from [error] so an offline refresh never hides a model or chat error. */
    val catalogError: String? = null,
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
    val telemetry: RunTelemetry = RunTelemetry(),
    val sampler: SamplerSettings = SamplerSettings(),
    val imageInputReady: Boolean = false,
    /** The user's saved choices. Artifact limits are applied in [effectiveComputeOptions], never written back. */
    val computeOptions: ComputeOptions = ComputeOptions(),
    /** The options the loaded engine was started with; null when no engine was loaded with them. */
    val appliedComputeOptions: ComputeOptions? = null,
    val appliedSampler: SamplerSettings? = null,
    val computeCapabilities: ComputeCapabilities? = null,
    val activeCompute: ActiveCompute? = null,
    val hardware: HardwareProfile? = null,
    val decodeMeasurements: List<DecodeMeasurement> = emptyList(),
    val memoryReliefInProgress: Boolean = false,
) {
    val computeBackend: ComputeBackend get() = computeOptions.backend
    val cpuThreads: Int? get() = computeOptions.cpuThreads

    /** What the next load of the selected artifact would run: saved choices after its metadata limits. */
    val effectiveComputeOptions: ComputeOptions get() = AdvancedComputePolicy.forArtifact(
        computeOptions, hardware?.cpuCoreCount?.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors(), computeCapabilities,
    )
    val computeNeedsApply: Boolean get() = AdvancedComputePolicy.needsApply(effectiveComputeOptions, sampler, appliedComputeOptions, appliedSampler)
}

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MobieUiState(
            // Device facts are read off the main thread in init; the UI already tolerates a null device.
            device = null,
            tokenConfigured = container.tokenStore.read() != null,
            welcomeSeen = container.tokenStore.hasSeenWelcome(),
            onboardingComplete = container.tokenStore.hasCompletedOnboarding(),
            sampler = AdvancedComputePolicy.sampler(container.samplerSettings.read()).also(container.samplerSettings::write),
            // Saved compute options are read on IO in init; recommended defaults show until then.
            computeOptions = ComputeOptions(),
        ),
    )
    /** Set on the main thread by an explicit edit, so a slower preference read cannot overwrite it. */
    private var computeOptionsEdited = false
    val state: StateFlow<MobieUiState> = mutableState.asStateFlow()
    private var catalogJob: Job? = null
    private var downloadObserverJob: Job? = null
    private var inferenceJob: Job? = null
    private var telemetryJob: Job? = null
    private var memoryRefreshJob: Job? = null
    private val runtimeLifecycle = Mutex()
    private val runtimeOperation = AtomicLong(0L)
    private val selectionRevision = AtomicLong(0L)
    private val runtimeOwnership = RuntimeModelOwnership()
    private val decodeSpeeds = DecodeSpeedStore(container.appContext)

    init {
        viewModelScope.launch {
            val device = readDeviceProfile()
            mutableState.update { it.copy(device = it.device ?: device) }
        }
        viewModelScope.launch {
            // Hardware detection and the preference read both touch disk, so they run on IO.
            val (hardware, options) = withContext(Dispatchers.IO) {
                val hardware = HardwareProfileProvider(container.appContext).detect()
                hardware to container.computeSettings.readAdvancedOptions(hardware.cpuCoreCount)
            }
            mutableState.update {
                it.copy(hardware = hardware, computeOptions = if (computeOptionsEdited) it.computeOptions else options)
            }
        }
        container.runtimes.all().forEach { it.configureSampler(mutableState.value.sampler) }
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

    /** Device facts read /proc and binder, so they run on IO rather than the main thread. */
    private suspend fun readDeviceProfile(): DeviceProfile = withContext(Dispatchers.IO) { container.deviceProfile.current() }

    /** Refreshes pre-flight admission from live RAM without changing the selected artifact. */
    fun refreshMemory() {
        if (state.value.memoryReliefInProgress) return
        memoryRefreshJob?.cancel()
        memoryRefreshJob = viewModelScope.launch { refreshMemorySnapshot() }
    }

    private suspend fun refreshMemorySnapshot() {
        val device = readDeviceProfile()
        mutableState.update { current ->
            current.copy(
                device = device,
                compatibility = current.selectedArtifact?.let { artifact ->
                    container.compatibility.resolve(admissionArtifact(artifact), device,
                        current.selected?.supportsVision == true, current.computeBackend)
                },
            )
        }
    }

    /** Only idle native resources are released; generation and model loading are never interrupted. */
    fun freeUpMemory(onReady: () -> Unit) {
        if (state.value.memoryReliefInProgress) return
        val revision = selectionRevision.get()
        memoryRefreshJob?.cancel()
        mutableState.update { it.copy(memoryReliefInProgress = true) }
        viewModelScope.launch {
            try {
                runtimeLifecycle.withLock {
                    val current = state.value
                    if (current.chatting || current.runtimeState in setOf(RuntimeState.LOADING, RuntimeState.GENERATING, RuntimeState.STOPPING)) {
                        return@launch
                    }
                    val operation = runtimeOperation.get()
                    container.runtimes.all().forEach { it.unload() }
                    runtimeOwnership.clear()
                    if (isCurrentRuntimeOperation(operation)) {
                        mutableState.update {
                            it.copy(runtimeState = RuntimeState.IDLE, activeCompute = null, imageInputReady = false, stats = null)
                        }
                    }
                }
                withContext(Dispatchers.IO) { container.catalog.trimMemory() }
                refreshMemorySnapshot()
                if (selectionRevision.get() == revision && !state.value.chatting) onReady()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(error = error.message ?: "Mobie could not release its memory. Restart Mobie before loading a model.")
                }
            } finally {
                mutableState.update { it.copy(memoryReliefInProgress = false) }
            }
        }
    }

    /** Cancels generation or a load together with its telemetry sampler, so neither outlives the other. */
    private fun cancelInference() {
        inferenceJob?.cancel()
        telemetryJob?.cancel()
    }

    private fun admissionArtifact(artifact: ModelArtifact?, options: ComputeOptions = state.value.computeOptions): ModelArtifact? =
        AdvancedComputePolicy.compatibilityArtifact(artifact, options,
            state.value.computeCapabilities.takeIf { artifact == state.value.selectedArtifact })

    fun setComputeBackend(backend: ComputeBackend) = updateComputeOptions(state.value.computeOptions.copy(backend = backend))

    fun setCpuThreads(threads: Int?) = updateComputeOptions(state.value.computeOptions.copy(cpuThreads = threads))

    /**
     * Saves the user's explicit choice without mutating the loaded engine or an in-flight reply.
     * Artifact limits are not written back, so a choice survives loading an artifact that lacks it.
     */
    fun updateComputeOptions(options: ComputeOptions) {
        val cores = state.value.hardware?.cpuCoreCount ?: Runtime.getRuntime().availableProcessors()
        val saved = options.forCoreCount(cores)
        computeOptionsEdited = true
        container.computeSettings.writeAdvancedOptions(saved, cores)
        mutableState.update { latest ->
            val updated = latest.copy(computeOptions = saved)
            val device = latest.device
            val artifact = latest.selectedArtifact
            if (device == null || artifact == null) updated else updated.copy(
                compatibility = container.compatibility.resolve(admissionArtifact(artifact, saved), device,
                    latest.selected?.supportsVision == true, saved.backend),
            )
        }
    }

    fun resetComputeSettings() {
        updateComputeOptions(ComputeOptions())
        updateSampler(SamplerSettings())
    }

    /** The explicit action claims the lifecycle before any asynchronous memory check. */
    fun applyComputeSettings() {
        val current = state.value
        val path = current.downloadedPath ?: return
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        if (!AdvancedComputePolicy.canApply(current.runtimeState, downloaded = true)) return
        cancelInference()
        val operation = nextRuntimeOperation()
        val revision = selectionRevision.get()
        mutableState.update { it.copy(chatting = true, runtimeState = RuntimeState.LOADING, activeCompute = null,
            stats = null, telemetry = RunTelemetry(), error = null) }
        inferenceJob = viewModelScope.launch {
            val device = readDeviceProfile()
            if (!isCurrentRuntimeOperation(operation) || selectionRevision.get() != revision || state.value.selectedArtifact != artifact) return@launch
            val compatibility = container.compatibility.resolve(admissionArtifact(artifact), device,
                model.supportsVision, state.value.computeBackend)
            mutableState.update { it.copy(device = device, compatibility = compatibility) }
            if (compatibility.status == Compatibility.INCOMPATIBLE || compatibility.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM) {
                // Nothing was unloaded, so the previous engine (if any) is still the one answering.
                val keptEngine = container.runtimes.adapterFor(artifact.format)?.activeCompute
                mutableState.update { it.afterRefusedReload(current, keptEngine, compatibility.reason) }
                return@launch
            }
            loadRuntimeConversation(model, artifact, path, current.messages, preferReset = false, operation = operation)
        }
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
            mutableState.update { it.copy(loading = true, catalogError = null, query = expectedQuery ?: "") }
            request().fold(
                onSuccess = { models ->
                    if (expectedQuery == null || state.value.query == expectedQuery) {
                        val device = readDeviceProfile()
                        val compatible = models
                            .map { model -> model.preferArtifact(artifactForDevice(model, device)) }
                            .sortedWith(
                                compareBy<AiModel> { model ->
                                    val artifact = model.bestArtifact
                                    when (container.compatibility.resolve(artifact, device, backend = state.value.computeBackend).status) {
                                        Compatibility.COMPATIBLE -> 0
                                        Compatibility.WARNING -> 1
                                        else -> 2
                                    }
                                }
                                    .thenBy { model -> model.bestArtifact?.sizeBytes ?: Long.MAX_VALUE }
                                    .thenByDescending { it.downloads },
                            )
                        mutableState.update { it.copy(models = compatible, device = device, loading = false) }
                    }
                },
                onFailure = { error ->
                    if (expectedQuery == null || state.value.query == expectedQuery) {
                        mutableState.update { it.copy(loading = false, catalogError = catalogFailureMessage(error)) }
                    }
                },
            )
        }
    }

    fun select(model: AiModel?) = beginSelection(model)

    /**
     * Stops the previous model's work now, then applies the new selection once the device profile
     * is read off the main thread. A newer selection made in the meantime supersedes this one.
     */
    private fun beginSelection(model: AiModel?, afterSelect: (() -> Unit)? = null) {
        downloadObserverJob?.cancel()
        cancelInference()
        val operation = nextRuntimeOperation()
        viewModelScope.launch { unloadRuntime(operation) }
        val revision = selectionRevision.incrementAndGet()
        viewModelScope.launch {
            val device = readDeviceProfile()
            if (selectionRevision.get() != revision) return@launch
            applySelection(model, device)
            afterSelect?.invoke()
        }
    }

    private fun applySelection(model: AiModel?, device: DeviceProfile) {
        val artifact = model?.let { artifactForDevice(it, device) }
        val presentedModel = model?.preferArtifact(artifact)
        val sessions = presentedModel?.let { container.chatHistory.sessions(it.id) }.orEmpty()
        val currentSessionId = presentedModel?.let { container.chatHistory.currentSessionId(it.id) }
        val capabilities = if (presentedModel != null && artifact != null) {
            container.computeSettings.readCachedCapabilities(modelPerformanceIdentity(presentedModel.id, artifact))
        } else null
        mutableState.update {
            it.copy(
                selected = presentedModel,
                selectedArtifact = artifact,
                recentModels = if (presentedModel == null) it.recentModels else {
                    (listOf(presentedModel) + it.recentModels.filterNot { recent -> recent.id == presentedModel.id }).take(5)
                },
                compatibility = artifact?.let { candidate -> container.compatibility.resolve(admissionArtifact(candidate), device, model?.supportsVision == true, state.value.computeBackend) },
                download = null,
                downloadedPath = null,
                chatting = false,
                runtimeState = RuntimeState.IDLE,
                messages = presentedModel?.let(::readHistory).orEmpty(),
                history = sessions,
                sessionId = currentSessionId,
                stats = null,
                decodeMeasurements = emptyList(),
                imageInputReady = false,
                activeCompute = null,
                computeCapabilities = capabilities,
                appliedComputeOptions = null,
                appliedSampler = null,
                error = null,
                device = device,
            )
        }
        if (presentedModel != null && artifact != null) {
            viewModelScope.launch {
                val measurements = decodeSpeeds.read(modelPerformanceIdentity(presentedModel.id, artifact))
                mutableState.update {
                    if (it.selected?.id == presentedModel.id && it.selectedArtifact == artifact) {
                        it.copy(decodeMeasurements = measurements)
                    } else it
                }
            }
            observeDownload(presentedModel, artifact)
            viewModelScope.launch(Dispatchers.IO) {
                val downloaded = container.downloads.completedFile(presentedModel.id, artifact)?.absolutePath
                if (
                    downloaded != null &&
                    state.value.selected?.id == presentedModel.id &&
                    state.value.selectedArtifact == artifact
                ) {
                    mutableState.update { it.copy(downloadedPath = downloaded) }
                }
            }
        }
    }

    fun download(allowWarning: Boolean = false) {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        viewModelScope.launch {
            val device = readDeviceProfile()
            val compatibility = container.compatibility.resolve(admissionArtifact(artifact), device, model.supportsVision, state.value.computeBackend)
            mutableState.update { it.copy(device = device, compatibility = compatibility, error = null) }
            if (model.gated && !state.value.tokenConfigured) {
                mutableState.update { it.copy(error = "This gated model requires a Hugging Face token.") }
                return@launch
            }
            if (
                compatibility.status !in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) ||
                (compatibility.status == Compatibility.WARNING && !allowWarning)
            ) return@launch

            downloadObserverJob?.cancel()
            container.downloads.enqueue(model, artifact)
            observeDownload(model, artifact)
        }
    }

    fun cancelDownload() {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        container.downloads.cancel(model, artifact)
    }

    fun runInstalled(allowReclaim: Boolean = false) {
        val path = state.value.downloadedPath
        if (path != null && File(path).isFile) {
            viewModelScope.launch {
                val current = state.value
                val device = readDeviceProfile()
                val compatibility = container.compatibility.resolve(admissionArtifact(current.selectedArtifact), device,
                    current.selected?.supportsVision == true, current.computeBackend, allowReclaim = allowReclaim)
                mutableState.update { it.copy(device = device, compatibility = compatibility) }
                if (compatibility.status == Compatibility.INCOMPATIBLE) {
                    mutableState.update { it.copy(error = compatibility.reason) }
                    return@launch
                }
                if (compatibility.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM && !allowReclaim) {
                    mutableState.update { it.copy(error = compatibility.reason) }
                    return@launch
                }
                openChat(path, allowReclaim)
            }
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

    private fun openChat(path: String, allowReclaim: Boolean = false) {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        val history = readHistory(model)
        val sessions = container.chatHistory.sessions(model.id)
        val operation = nextRuntimeOperation()
        mutableState.update {
            it.copy(
                chatting = true,
                downloadedPath = path,
                runtimeState = RuntimeState.LOADING,
                activeCompute = null,
                messages = history,
                history = sessions,
                sessionId = container.chatHistory.currentSessionId(model.id),
                error = null,
            )
        }
        cancelInference()
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, artifact, path, history, preferReset = false,
                operation = operation, allowReclaim = allowReclaim)
        }
    }

    private suspend fun loadRuntimeConversation(
        model: AiModel,
        artifact: ModelArtifact,
        path: String,
        history: List<ChatMessage>,
        preferReset: Boolean,
        operation: Long,
        allowReclaim: Boolean = false,
    ) {
        val adapter = container.runtimes.adapterFor(artifact.format)
        if (adapter == null) {
            if (isCurrentRuntimeOperation(operation)) {
                mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = "No runtime for this model") }
            }
            return
        }
        val restored = history.map {
            RuntimeMessage(
                fromUser = it.fromUser,
                text = it.text,
                interrupted = it.interrupted,
                imagePath = it.imagePath,
            )
        }
        val loadState = state.value
        val preserveLoadedSettings = preferReset && runtimeOwnership.owns(model.id)
        val options = AdvancedComputePolicy.forArtifact(
            if (preserveLoadedSettings) loadState.appliedComputeOptions ?: loadState.computeOptions else loadState.computeOptions,
            loadState.hardware?.cpuCoreCount ?: Runtime.getRuntime().availableProcessors(), loadState.computeCapabilities)
        val sampler = AdvancedComputePolicy.sampler(
            if (preserveLoadedSettings) loadState.appliedSampler ?: loadState.sampler else loadState.sampler)
        val settingsUnchanged = loadState.appliedComputeOptions == options && loadState.appliedSampler == sampler
        val result = runtimeLifecycle.withLock {
            if (!isCurrentRuntimeOperation(operation)) return
            adapter.configureCompute(options.copy(artifactIdentity = modelPerformanceIdentity(model.id, artifact)))
            adapter.configureSampler(sampler)
            if (preferReset && settingsUnchanged && runtimeOwnership.owns(model.id)) {
                val reset = adapter.resetConversation(restored)
                if (reset.isSuccess) {
                    reset
                } else {
                    runtimeOwnership.clear()
                    (if (allowReclaim) adapter.loadAllowingMemoryReclaim(path, model.supportsVision, restored)
                     else adapter.load(path, model.supportsVision, restored)).also { loaded ->
                        if (loaded.isSuccess) runtimeOwnership.markLoaded(model.id)
                    }
                }
            } else {
                runtimeOwnership.clear()
                (if (allowReclaim) adapter.loadAllowingMemoryReclaim(path, model.supportsVision, restored)
                 else adapter.load(path, model.supportsVision, restored)).also { loaded ->
                    if (loaded.isSuccess) runtimeOwnership.markLoaded(model.id)
                }
            }
        }
        if (!isCurrentRuntimeOperation(operation)) return
        result.fold(
            onSuccess = {
                // Only the applied snapshot changes. Saved choices are not rewritten with this artifact's limits.
                mutableState.update {
                    it.copy(
                        runtimeState = RuntimeState.READY,
                        imageInputReady = adapter.supportsImageInput,
                        activeCompute = adapter.activeCompute,
                        computeCapabilities = adapter.computeCapabilities,
                        appliedComputeOptions = options,
                        appliedSampler = sampler,
                    )
                }
                adapter.computeCapabilities?.let { capabilities ->
                    container.computeSettings.writeCachedCapabilities(modelPerformanceIdentity(model.id, artifact), capabilities)
                }
            },
            onFailure = { error ->
                // A refused reload can leave the previous engine running; chat must keep showing it.
                val keptEngine = adapter.activeCompute
                val message = error.message ?: "Model failed to load"
                mutableState.update {
                    if (keptEngine != null) {
                        it.copy(runtimeState = RuntimeState.READY, activeCompute = keptEngine, error = keptRunningMessage(keptEngine, message))
                    } else {
                        it.copy(runtimeState = RuntimeState.ERROR, error = message)
                    }
                }
            },
        )
    }

    /** Returns false when the prompt was rejected, so the composer keeps the user's draft. */
    fun sendMessage(prompt: String, imagePath: String? = null): Boolean {
        val current = state.value
        val model = current.selected ?: return false
        val artifact = current.selectedArtifact ?: return false
        if (prompt.isBlank() || current.runtimeState != RuntimeState.READY) return false
        if (imagePath != null && (!model.supportsVision || !current.imageInputReady)) return false
        val adapter = container.runtimes.adapterFor(artifact.format) ?: return false
        mutableState.update {
            it.copy(
                runtimeState = RuntimeState.GENERATING,
                messages = it.messages + ChatMessage(true, prompt.trim(), imagePath) + ChatMessage(false, ""),
                stats = null,
                telemetry = RunTelemetry(),
                error = null,
            )
        }
        cancelInference()
        telemetryJob = startTelemetrySampling()
        val operation = runtimeOperation.get()
        val sessionId = current.sessionId
        inferenceJob = viewModelScope.launch {
            adapter.generate(prompt.trim(), imagePath, GenerationConfig(sampler = current.appliedSampler ?: current.sampler)).collectBatchedTokens { event ->
                if (!isCurrentRuntimeOperation(operation) ||
                    state.value.selected?.id != model.id || state.value.sessionId != sessionId
                ) return@collectBatchedTokens
                when (event) {
                    is InferenceEvent.Token -> mutableState.update { currentState ->
                        currentState.copy(messages = currentState.messages.updateLastAssistant(event.text, event.thinking))
                    }
                    is InferenceEvent.Stats -> {
                        mutableState.update {
                            it.copy(
                                stats = event.value,
                                telemetry = it.telemetry.copy(
                                    estimatedEnergyPerTokenMilliWh = BatteryCurrentMath.milliWhPerToken(
                                        it.telemetry.estimatedEnergyMilliWh,
                                        event.value.decodeTokenCount,
                                    ),
                                ),
                                messages = it.messages.attachStatsToLastAssistant(event.value),
                            )
                        }
                        viewModelScope.launch {
                            // Settings are the requested ones the engine was loaded with, so the key matches what the user chose.
                            val applied = current.appliedComputeOptions ?: current.effectiveComputeOptions
                            val settings = DecodeRunSettings(cpuThreads = applied.cpuThreads, contextTokens = applied.contextTokens)
                            val measurements = decodeSpeeds.record(modelPerformanceIdentity(model.id, artifact), event.value, settings)
                            mutableState.update {
                                if (it.selected?.id == model.id && it.selectedArtifact == artifact) {
                                    it.copy(decodeMeasurements = measurements)
                                } else it
                            }
                        }
                    }
                    is InferenceEvent.Progress -> mutableState.update {
                        it.copy(telemetry = it.telemetry.copy(
                            sdkChunks = event.sdkChunks,
                            firstChunkMs = event.firstChunkMs,
                            liveSdkChunksPerSecond = if (event.sdkChunks > 1 && event.elapsedMs > event.firstChunkMs) {
                                (event.sdkChunks - 1) * 1_000.0 / (event.elapsedMs - event.firstChunkMs)
                            } else null,
                        ))
                    }
                    is InferenceEvent.Error -> {
                        telemetryJob?.cancel()
                        val messages = state.value.messages.finishInterruptedReply()
                        persistHistory(model.id, messages)
                        mutableState.update {
                            it.copy(
                                runtimeState = if (event.requiresReload) RuntimeState.ERROR else RuntimeState.READY,
                                // Cancellation can disable image support in the stall guard; the UI must follow it.
                                imageInputReady = adapter.supportsImageInput,
                                messages = messages,
                                history = container.chatHistory.sessions(model.id),
                                error = event.message,
                            )
                        }
                    }
                    InferenceEvent.Complete -> {
                        telemetryJob?.cancel()
                        val messages = state.value.messages.finishReply()
                        persistHistory(model.id, messages)
                        mutableState.update {
                            it.copy(
                                runtimeState = RuntimeState.READY,
                                imageInputReady = adapter.supportsImageInput,
                                messages = messages,
                                history = container.chatHistory.sessions(model.id),
                            )
                        }
                    }
                }
            }
        }
        return true
    }

    fun stopGeneration() {
        val current = state.value
        if (current.runtimeState != RuntimeState.GENERATING) return
        val activeInference = inferenceJob
        val modelId = current.selected?.id
        val sessionId = current.sessionId
        val operation = runtimeOperation.get()
        telemetryJob?.cancel()
        mutableState.update {
            // Keep the placeholder until collection has flushed pending text on cancellation.
            it.copy(runtimeState = RuntimeState.STOPPING, error = null)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (modelId != null) {
                saveBeforeCancellation(current.messages, activeInference) { persistHistory(modelId, it) }
            } else {
                activeInference?.cancelAndJoin()
            }
            if (isCurrentRuntimeOperation(operation) && state.value.selected?.id == modelId && state.value.sessionId == sessionId &&
                state.value.runtimeState == RuntimeState.STOPPING && modelId != null
            ) {
                val messages = state.value.messages.finishInterruptedReply()
                mutableState.update { it.copy(messages = messages) }
                persistHistory(modelId, messages)
            }
            mutableState.update { latest ->
                if (
                    latest.runtimeState == RuntimeState.STOPPING &&
                    isCurrentRuntimeOperation(operation) &&
                    latest.selected?.id == modelId &&
                    latest.sessionId == sessionId
                ) {
                    latest.copy(
                        runtimeState = RuntimeState.READY,
                        imageInputReady = latest.selectedArtifact
                            ?.let { container.runtimes.adapterFor(it.format)?.supportsImageInput } == true,
                        history = modelId?.let { container.chatHistory.sessions(it) }.orEmpty(),
                    )
                } else {
                    latest
                }
            }
        }
    }


    fun updateSampler(settings: SamplerSettings): String? {
        val error = runCatching { settings.validated() }.exceptionOrNull()?.message
        if (error != null) return error
        if (settings.topK > 64) return "Top-k must be between 1 and 64."
        container.samplerSettings.write(settings)
        mutableState.update { it.copy(sampler = settings) }
        return null
    }

    private fun startTelemetrySampling(): Job = viewModelScope.launch(Dispatchers.IO) {
        var previousAt = SystemClock.elapsedRealtime()
        var energyMilliWh: Double? = null
        while (isActive && state.value.runtimeState == RuntimeState.GENERATING) {
            val sample = container.runSampler.sample()
            val now = SystemClock.elapsedRealtime()
            val increment = BatteryCurrentMath.milliWh(
                sample.currentMicroamps,
                sample.voltageMillivolts,
                now - previousAt,
            )
            previousAt = now
            if (increment != null) energyMilliWh = (energyMilliWh ?: 0.0) + increment
            mutableState.update { current ->
                current.copy(telemetry = current.telemetry.copy(
                    pssBytes = sample.pssBytes,
                    thermalStatus = sample.thermalStatus,
                    batteryCurrentMilliAmps = sample.currentMicroamps?.div(1_000.0),
                    estimatedEnergyMilliWh = energyMilliWh,
                ))
            }
            delay(250)
        }
    }

    fun newChat() {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        val path = current.downloadedPath ?: return
        val canReuseLoadedModel = current.runtimeState == RuntimeState.READY
        cancelInference()
        val operation = nextRuntimeOperation()
        val sessionId = container.chatHistory.startNewSession(model.id)
        mutableState.update {
            it.copy(
                messages = emptyList(),
                history = container.chatHistory.sessions(model.id),
                sessionId = sessionId,
                runtimeState = RuntimeState.LOADING,
                stats = null,
                error = null,
            )
        }
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, artifact, path, emptyList(), preferReset = canReuseLoadedModel, operation = operation)
        }
    }

    fun startInstalledChat(entry: InstalledModelEntry) {
        beginSelection(entry.model) {
            container.chatHistory.startNewSession(entry.model.id)
            openChat(entry.localPath)
        }
    }

    fun deleteInstalled(entry: InstalledModelEntry) {
        val deletingSelectedModel = state.value.selected?.id == entry.model.id
        if (deletingSelectedModel) cancelInference()
        val operation = if (deletingSelectedModel) nextRuntimeOperation() else null
        viewModelScope.launch {
            val deleted = try {
                runtimeLifecycle.withLock {
                    if (operation != null && !isCurrentRuntimeOperation(operation)) return@withLock false
                    if (deletingSelectedModel || runtimeOwnership.owns(entry.model.id)) {
                        container.runtimes.all().forEach { it.unload() }
                        runtimeOwnership.clear()
                    }
                    if (operation != null && !isCurrentRuntimeOperation(operation)) return@withLock false
                    container.downloads.deleteInstalled(entry.model)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                telemetryJob?.cancel()
                if (operation == null || isCurrentRuntimeOperation(operation)) {
                    mutableState.update {
                        it.copy(
                            runtimeState = RuntimeState.ERROR,
                            error = error.message ?: "${entry.model.title} could not be deleted.",
                        )
                    }
                }
                return@launch
            }
            if (deleted) {
                mutableState.update { current ->
                    val deletedSelectedModel = current.selected?.id == entry.model.id
                    current.copy(
                        installedModels = current.installedModels.filterNot { installed -> installed.model.id == entry.model.id },
                        download = if (deletedSelectedModel) null else current.download,
                        downloadedPath = if (deletedSelectedModel) null else current.downloadedPath,
                        chatting = if (deletedSelectedModel) false else current.chatting,
                        runtimeState = if (deletedSelectedModel) RuntimeState.IDLE else current.runtimeState,
                        stats = if (deletedSelectedModel) null else current.stats,
                        error = null,
                    )
                }
            } else if (operation == null || isCurrentRuntimeOperation(operation)) {
                if (deletingSelectedModel) {
                    // The stream was already cancelled, so GENERATING would never clear on its own.
                    telemetryJob?.cancel()
                    val runtimeLoaded = container.runtimes.all().any { it.activeCompute != null }
                    mutableState.update {
                        it.copy(runtimeState = if (runtimeLoaded) RuntimeState.READY else RuntimeState.IDLE)
                    }
                }
                mutableState.update { it.copy(error = "${entry.model.title} could not be deleted.") }
            }
        }
    }

    fun leaveChat() {
        cancelInference()
        val operation = nextRuntimeOperation()
        viewModelScope.launch { unloadRuntime(operation) }
        mutableState.update {
            it.copy(chatting = false, runtimeState = RuntimeState.IDLE, stats = null, error = null, appliedComputeOptions = null, appliedSampler = null)
        }
    }

    private suspend fun unloadRuntime(operation: Long) {
        try {
            runtimeLifecycle.withLock {
                if (!isCurrentRuntimeOperation(operation)) return
                container.runtimes.all().forEach { it.unload() }
                runtimeOwnership.clear()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The stall guard fails closed after an unload timeout, so surface the state instead of crashing.
            mutableState.update {
                it.copy(
                    runtimeState = RuntimeState.ERROR,
                    error = error.message ?: "The model could not be unloaded. Restart Mobie before loading another model.",
                )
            }
        }
    }

    fun selectHistory(sessionId: String) {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        val path = current.downloadedPath ?: return
        val canReuseLoadedModel = current.runtimeState == RuntimeState.READY
        container.chatHistory.activate(model.id, sessionId)
        val history = readHistory(model)
        cancelInference()
        val operation = nextRuntimeOperation()
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
            loadRuntimeConversation(model, artifact, path, history, preferReset = canReuseLoadedModel, operation = operation)
        }
    }

    fun saveToken(token: String) {
        container.tokenStore.save(token.ifBlank { null })
        mutableState.update { it.copy(tokenConfigured = token.isNotBlank()) }
    }

    private fun observeDownload(model: AiModel, artifact: ModelArtifact) {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            container.downloads.observe(model.id, artifact).collect { progress ->
                if (
                    state.value.selected?.id != model.id ||
                    state.value.selectedArtifact != artifact ||
                    progress == null
                ) return@collect
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

    private fun artifactForDevice(model: AiModel, device: DeviceProfile): ModelArtifact? =
        container.compatibility.selectBestArtifact(model, device) ?: model.bestArtifact

    private fun readHistory(model: AiModel): List<ChatMessage> = container.chatHistory.read(model.id).map {
        ChatMessage(it.fromUser, it.text, it.imagePath, it.thinking, interrupted = it.interrupted)
    }

    private fun persistHistory(modelId: String, messages: List<ChatMessage>) {
        container.chatHistory.write(
            modelId,
            messages.map {
                HistoryMessage(it.fromUser, it.text, it.imagePath, it.thinking, it.interrupted)
            },
        )
    }

    private fun nextRuntimeOperation(): Long = runtimeOperation.incrementAndGet()

    private fun isCurrentRuntimeOperation(operation: Long): Boolean = runtimeOperation.get() == operation

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
        // Fast path: no tag is being tracked (rawText == null) and chunk cannot start one.
        // Covers both plain-text responses and post-close answer tokens after rawText is cleared.
        if (message.rawText == null && !chunk.contains('<')) {
            this[index] = message.copy(text = message.text + chunk)
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
        val trailingOpen = raw.lastIndexOf('<')
        val partialTag = open < 0 && trailingOpen >= 0 &&
            REASONING_TAGS.any { "<$it>".startsWith(raw.substring(trailingOpen), ignoreCase = true) }
        val answer = when {
            partialTag -> raw.substring(0, trailingOpen)
            open < 0 -> raw
            close < 0 -> raw.substring(0, open)
            else -> (raw.substring(0, open) + raw.substring(close + tag!!.first.length + 3)).trimStart()
        }
        // Clear rawText once the close tag is found (or when no tag was involved).
        // Next token then hits the fast path above, skipping O(n) re-scans of accumulated raw text.
        val newRawText = when {
            close >= 0 -> null   // close found: subsequent tokens are pure answer, no re-scan needed
            tag != null -> raw   // inside thinking block before close
            partialTag -> raw    // partial open tag: carry forward for next chunk
            else -> null         // no tag involvement: don't grow rawText
        }
        this[index] = message.copy(text = answer, thinking = thinking, rawText = newRawText)
    }
}

internal fun List<ChatMessage>.markLastAssistantInterrupted(): List<ChatMessage> {
    if (lastOrNull()?.fromUser != false) return this
    val index = lastIndex
    return toMutableList().apply {
        this[index] = this[index].copy(interrupted = true)
    }
}

internal fun List<ChatMessage>.finishInterruptedReply(): List<ChatMessage> =
    finishReply().removeBlankAssistant().markLastAssistantInterrupted()

/** The first save runs before cancelAndJoin can wait on a slow native cancellation. */
internal suspend fun saveBeforeCancellation(
    messages: List<ChatMessage>,
    inference: Job?,
    persist: (List<ChatMessage>) -> Unit,
) {
    persist(messages.finishInterruptedReply())
    inference?.cancelAndJoin()
}

internal fun List<ChatMessage>.finishReply(): List<ChatMessage> {
    if (lastOrNull()?.fromUser != false) return this
    val last = last()
    val raw = last.rawText ?: return this
    // Only an unfinished opening tag is held back. An actual reasoning block remains hidden.
    val pendingStart = raw.lastIndexOf('<').takeIf { index ->
        index >= 0 && REASONING_TAGS.any { "<$it>".startsWith(raw.substring(index), ignoreCase = true) }
    }
    if (pendingStart == null || REASONING_TAGS.any { raw.contains("<$it>", ignoreCase = true) }) {
        return dropLast(1) + last.copy(rawText = null)
    }
    return dropLast(1) + last.copy(text = raw, rawText = null)
}

private val REASONING_TAGS = listOf("think", "thinking", "analysis", "reasoning")

private fun List<ChatMessage>.removeBlankAssistant(): List<ChatMessage> =
    if (lastOrNull()?.let { !it.fromUser && it.text.isBlank() && it.thinking.isBlank() } == true) dropLast(1) else this

internal fun List<ChatMessage>.attachStatsToLastAssistant(stats: InferenceStats): List<ChatMessage> {
    val index = indexOfLast { !it.fromUser }
    if (index < 0) return this
    return toMutableList().apply { this[index] = this[index].copy(stats = stats) }
}

/** Plain copy for a failed catalog fetch. Network failures say what still works offline. */
internal fun catalogFailureMessage(error: Throwable): String = when (error) {
    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
        "No connection to Hugging Face. Models you've downloaded still work offline from Library."
    else -> error.message ?: "Catalog request failed"
}
