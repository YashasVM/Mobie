package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import android.net.Uri
import android.os.SystemClock
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.attachment.AttachmentPolicy
import dev.yashasvm.mobie.core.attachment.AttachmentRejection
import dev.yashasvm.mobie.core.attachment.AttachmentResult
import dev.yashasvm.mobie.core.attachment.DocumentAttachment
import dev.yashasvm.mobie.core.attachment.DocumentPromptAssembler
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.device.BatteryCurrentMath
import dev.yashasvm.mobie.core.device.RunTelemetry
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.core.runtime.RuntimeModelOwnership
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.core.runtime.utf8ByteCount
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.data.history.toDocument
import dev.yashasvm.mobie.data.history.toHistory
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
    /** Files the user attached to this message, with their bounded text. */
    val attachments: List<DocumentAttachment> = emptyList(),
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
    /** Id of the installed model the user last chatted with; drives the switcher and "back to chat". */
    val activeInstalledId: String? = null,
    val runtimeState: RuntimeState = RuntimeState.IDLE,
    val loadAttempt: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
    val history: List<ChatHistorySession> = emptyList(),
    val recentChats: List<RecentChat> = emptyList(),
    val sessionId: String? = null,
    val stats: InferenceStats? = null,
    val telemetry: RunTelemetry = RunTelemetry(),
    val sampler: SamplerSettings = SamplerSettings(),
    val imageInputReady: Boolean = false,
    val computeBackend: ComputeBackend = ComputeBackend.AUTO,
    val cpuThreads: Int? = null,
    val activeCompute: ActiveCompute? = null,
    /** Documents chosen for the next message. Cleared when the model changes or the caller clears them. */
    val pendingAttachments: List<DocumentAttachment> = emptyList(),
    /** Plain-language reason the last attachment failed; the UI shows it and can dismiss it. */
    val attachmentError: String? = null,
)

data class RecentChat(val modelId: String, val modelTitle: String, val session: ChatHistorySession)

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    /** Matches the runtime default used when a model reports no context window. */
    private val DEFAULT_ATTACHMENT_CONTEXT_TOKENS = 4_096
    private val mutableState = MutableStateFlow(
        MobieUiState(
            // Device facts are read off the main thread in init; the UI already tolerates a null device.
            device = null,
            tokenConfigured = container.tokenStore.read() != null,
            welcomeSeen = container.tokenStore.hasSeenWelcome(),
            onboardingComplete = container.tokenStore.hasCompletedOnboarding(),
            sampler = container.samplerSettings.read(),
            computeBackend = container.computeSettings.selectedBackend(),
            cpuThreads = container.computeSettings.selectedCpuThreads(),
            activeInstalledId = container.lastUsedModel.read(),
        ),
    )
    val state: StateFlow<MobieUiState> = mutableState.asStateFlow()
    /** Launch routing runs once per process, on the first installed-model load. */
    private var launchRouted = false
    private var catalogJob: Job? = null
    private var downloadObserverJob: Job? = null
    private var inferenceJob: Job? = null
    private var telemetryJob: Job? = null
    private val runtimeLifecycle = Mutex()
    private val runtimeOperation = AtomicLong(0L)
    private val selectionRevision = AtomicLong(0L)
    private val runtimeOwnership = RuntimeModelOwnership()

    init {
        viewModelScope.launch {
            val device = readDeviceProfile()
            mutableState.update { it.copy(device = it.device ?: device) }
        }
        container.runtimes.all().forEach { it.configureSampler(mutableState.value.sampler) }
        refreshInstalled()
        loadFeatured()
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

    /** Cancels generation or a load together with its telemetry sampler, so neither outlives the other. */
    private fun cancelInference() {
        inferenceJob?.cancel()
        telemetryJob?.cancel()
    }

    /** Auto falls back to CPU when GPU does not fit, so only an explicit choice is admitted as that backend. */
    private fun admissionBackend(selected: ComputeBackend = state.value.computeBackend): ComputeBackend =
        selected.takeIf { it != ComputeBackend.AUTO } ?: ComputeBackend.CPU

    fun setComputeBackend(backend: ComputeBackend) {
        container.computeSettings.setBackend(backend)
        mutableState.update { current ->
            val updated = current.copy(computeBackend = backend)
            val device = current.device
            val artifact = current.selectedArtifact
            if (device == null || artifact == null) updated else updated.copy(
                // GPU keeps its own copy of the weights, so the memory verdict changes with the backend.
                compatibility = container.compatibility.resolve(artifact, device,
                    current.selected?.supportsVision == true, admissionBackend(backend)),
            )
        }
    }

    fun setCpuThreads(threads: Int?) {
        container.computeSettings.setCpuThreads(threads)
        mutableState.update { it.copy(cpuThreads = threads) }
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
                                    when (container.compatibility.resolve(artifact, device).status) {
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
        mutableState.update {
            it.copy(
                selected = presentedModel,
                selectedArtifact = artifact,
                recentModels = if (presentedModel == null) it.recentModels else {
                    (listOf(presentedModel) + it.recentModels.filterNot { recent -> recent.id == presentedModel.id }).take(5)
                },
                compatibility = artifact?.let { candidate -> container.compatibility.resolve(candidate, device, model?.supportsVision == true, admissionBackend()) },
                download = null,
                downloadedPath = null,
                chatting = false,
                runtimeState = RuntimeState.IDLE,
                messages = presentedModel?.let(::readHistory).orEmpty(),
                history = sessions,
                sessionId = currentSessionId,
                stats = null,
                imageInputReady = false,
                activeCompute = null,
                error = null,
                device = device,
                // Pending files were budgeted for the previous model's context window.
                pendingAttachments = emptyList(),
                attachmentError = null,
            )
        }
        if (presentedModel != null && artifact != null) {
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
            val compatibility = container.compatibility.resolve(artifact, device, model.supportsVision, admissionBackend())
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
        mutableState.update {
            it.copy(loadAttempt = it.loadAttempt + 1, chatting = it.selected != null, runtimeState = RuntimeState.LOADING, error = null)
        }
        val revision = selectionRevision.get()
        val path = state.value.downloadedPath
        if (path != null && File(path).isFile) {
            viewModelScope.launch {
                val current = state.value
                val device = readDeviceProfile()
                if (selectionRevision.get() != revision) return@launch
                val compatibility = container.compatibility.resolve(current.selectedArtifact, device,
                    current.selected?.supportsVision == true, admissionBackend())
                mutableState.update { it.copy(device = device, compatibility = compatibility) }
                if (compatibility.status == Compatibility.INCOMPATIBLE) {
                    mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = compatibility.reason) }
                    return@launch
                }
                if (compatibility.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM && !allowReclaim) {
                    mutableState.update { it.copy(runtimeState = RuntimeState.ERROR, error = compatibility.reason) }
                    return@launch
                }
                openChat(path, allowReclaim)
            }
        } else {
            mutableState.update {
                it.copy(
                    downloadedPath = null,
                    download = null,
                    runtimeState = RuntimeState.ERROR,
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
                // Replay sends the same text the model saw, including attached file contents.
                text = if (it.fromUser) DocumentPromptAssembler.assemble(it.text, it.attachments) else it.text,
                interrupted = it.interrupted,
                imagePath = it.imagePath,
            )
        }
        val result = runtimeLifecycle.withLock {
            if (!isCurrentRuntimeOperation(operation)) return
            adapter.configureCompute(
                ComputeOptions(
                    backend = state.value.computeBackend,
                    cpuThreads = state.value.cpuThreads,
                    artifactIdentity = "${model.id}|${artifact.fileName}|${artifact.downloadUrl}|${artifact.sha256.orEmpty()}",
                ),
            )
            if (preferReset && runtimeOwnership.owns(model.id)) {
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
                mutableState.update {
                    it.copy(
                        runtimeState = RuntimeState.READY,
                        imageInputReady = adapter.supportsImageInput,
                        activeCompute = adapter.activeCompute,
                    )
                }
            },
            onFailure = { error ->
                mutableState.update {
                    it.copy(runtimeState = RuntimeState.ERROR, error = error.message ?: "Model failed to load")
                }
            },
        )
    }

    /**
     * Chooses a local document for the next message. Failures are reported in [MobieUiState.attachmentError].
     * The file is read off the main thread and bounded by the selected model's context window.
     */
    fun attachDocument(uri: Uri) {
        val current = state.value
        val model = current.selected
        val artifact = current.selectedArtifact
        if (model == null || artifact == null) {
            mutableState.update { it.copy(attachmentError = "Choose a model before attaching a file.") }
            return
        }
        val budget = attachmentBudgetBytes(model, artifact, current.compatibility)
        val remaining = budget - AttachmentPolicy.totalTextBytes(current.pendingAttachments)
        viewModelScope.launch {
            if (remaining < AttachmentPolicy.MIN_TEXT_BUDGET_BYTES) {
                mutableState.update { it.copy(attachmentError = AttachmentRejection.NoContextRoom.message) }
                return@launch
            }
            when (val result = container.attachments.read(uri, remaining)) {
                is AttachmentResult.Rejected -> mutableState.update {
                    it.copy(attachmentError = result.reason.message)
                }
                is AttachmentResult.Accepted -> mutableState.update { latest ->
                    val stillSameModel = latest.selected?.id == model.id
                    val room = budget - AttachmentPolicy.totalTextBytes(latest.pendingAttachments)
                    when {
                        !stillSameModel -> latest
                        result.attachment.text.utf8ByteCount() > room -> latest.copy(
                            attachmentError = AttachmentRejection.NoContextRoom.message,
                        )
                        else -> latest.copy(
                            pendingAttachments = latest.pendingAttachments + result.attachment,
                            attachmentError = null,
                        )
                    }
                }
            }
        }
    }

    fun removePendingAttachment(index: Int) {
        mutableState.update { current ->
            if (index !in current.pendingAttachments.indices) current
            else current.copy(pendingAttachments = current.pendingAttachments.filterIndexed { i, _ -> i != index })
        }
    }

    /** Call after the UI has passed the pending files to [sendMessage], or when the user discards them. */
    fun clearPendingAttachments() {
        mutableState.update { it.copy(pendingAttachments = emptyList(), attachmentError = null) }
    }

    fun dismissAttachmentError() {
        mutableState.update { it.copy(attachmentError = null) }
    }

    /**
     * Returns false when the prompt was rejected, so the composer keeps the user's draft and files.
     * [attachments] are sent with the question; the transcript keeps the question and file metadata.
     */
    fun sendMessage(
        prompt: String,
        imagePath: String? = null,
        attachments: List<DocumentAttachment> = emptyList(),
    ): Boolean {
        val current = state.value
        val model = current.selected ?: return false
        val artifact = current.selectedArtifact ?: return false
        if ((prompt.isBlank() && attachments.isEmpty()) || current.runtimeState != RuntimeState.READY) return false
        if (imagePath != null && (!model.supportsVision || !current.imageInputReady)) return false
        if (AttachmentPolicy.totalTextBytes(attachments) >
            attachmentBudgetBytes(model, artifact, current.compatibility)
        ) return false
        val adapter = container.runtimes.adapterFor(artifact.format) ?: return false
        val question = prompt.trim()
        val generationPrompt = DocumentPromptAssembler.assemble(question, attachments)
        mutableState.update {
            it.copy(
                runtimeState = RuntimeState.GENERATING,
                messages = it.messages +
                    ChatMessage(true, question, imagePath, attachments = attachments) +
                    ChatMessage(false, ""),
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
            adapter.generate(generationPrompt, imagePath, GenerationConfig(sampler = current.sampler)).collectBatchedTokens { event ->
                if (!isCurrentRuntimeOperation(operation) ||
                    state.value.selected?.id != model.id || state.value.sessionId != sessionId
                ) return@collectBatchedTokens
                when (event) {
                    is InferenceEvent.Token -> mutableState.update { currentState ->
                        currentState.copy(messages = currentState.messages.updateLastAssistant(event.text, event.thinking))
                    }
                    is InferenceEvent.Stats -> mutableState.update {
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
        container.samplerSettings.write(settings)
        container.runtimes.all().forEach { it.configureSampler(settings) }
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
                pendingAttachments = emptyList(),
                attachmentError = null,
                history = container.chatHistory.sessions(model.id),
                sessionId = sessionId,
                runtimeState = if (canReuseLoadedModel) RuntimeState.LOADING else RuntimeState.IDLE,
                stats = null,
                error = null,
            )
        }
        if (!canReuseLoadedModel) return
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, artifact, path, emptyList(), preferReset = canReuseLoadedModel, operation = operation)
        }
    }

    /** Selecting a model opens its conversation without allocating native memory. */
    fun startInstalledChat(entry: InstalledModelEntry) = prepareInstalledChat(entry, newSession = true)

    private fun prepareInstalledChat(entry: InstalledModelEntry, newSession: Boolean, sessionId: String? = null) {
        container.lastUsedModel.write(entry.model.id)
        mutableState.update { it.copy(activeInstalledId = entry.model.id) }
        beginSelection(entry.model) {
            if (newSession) container.chatHistory.startNewSession(entry.model.id)
            if (sessionId != null) container.chatHistory.activate(entry.model.id, sessionId)
            mutableState.update {
                it.copy(
                    chatting = true,
                    downloadedPath = entry.localPath,
                    messages = readHistory(entry.model),
                    history = container.chatHistory.sessions(entry.model.id),
                    sessionId = container.chatHistory.currentSessionId(entry.model.id),
                )
            }
        }
    }

    fun switchModel(entry: InstalledModelEntry) {
        if (state.value.chatting && state.value.selected?.id == entry.model.id) return
        startInstalledChat(entry)
    }

    fun openCatalog() = select(null)

    fun returnToChat() {
        val entry = state.value.installedModels.firstOrNull { it.model.id == state.value.activeInstalledId }
        if (entry == null) select(null) else prepareInstalledChat(entry, newSession = false)
    }

    fun refreshRecents() {
        val chats = state.value.installedModels.flatMap { entry ->
            container.chatHistory.sessions(entry.model.id).filter { it.messages.isNotEmpty() }
                .map { RecentChat(entry.model.id, entry.model.title, it) }
        }.sortedByDescending { it.session.updatedAt }
        mutableState.update { it.copy(recentChats = chats) }
    }

    fun openRecent(chat: RecentChat) {
        val entry = state.value.installedModels.firstOrNull { it.model.id == chat.modelId } ?: return
        prepareInstalledChat(entry, newSession = false, sessionId = chat.session.id)
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
            it.copy(chatting = false, runtimeState = RuntimeState.IDLE, stats = null, error = null)
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
            // Most recently installed first, so the routing fallback picks the newest model.
            val installed = withContext(Dispatchers.IO) {
                container.downloads.installedModels().sortedByDescending { File(it.localPath).lastModified() }
            }
            mutableState.update { it.copy(installedModels = installed) }
            routeOnLaunch(installed)
        }
    }

    /**
     * Opens chat on the last-used installed model the first time installed models are known. Runs
     * once, and not if the user already picked something while the list was loading.
     */
    private fun routeOnLaunch(installed: List<InstalledModelEntry>) {
        if (launchRouted) return
        val current = state.value

        launchRouted = true
        if (current.selected != null || current.chatting || selectionRevision.get() != 0L) return
        val destination = initialDestination(installed, current.activeInstalledId)
        if (destination is LaunchDestination.Chat) prepareInstalledChat(destination.entry, newSession = false)
    }

    private fun artifactForDevice(model: AiModel, device: DeviceProfile): ModelArtifact? =
        container.compatibility.selectBestArtifact(model, device) ?: model.bestArtifact

    private fun readHistory(model: AiModel): List<ChatMessage> = container.chatHistory.read(model.id).map {
        ChatMessage(
            it.fromUser,
            it.text,
            it.imagePath,
            it.thinking,
            interrupted = it.interrupted,
            attachments = it.attachments.map { attachment -> attachment.toDocument() },
        )
    }

    private fun persistHistory(modelId: String, messages: List<ChatMessage>) {
        container.chatHistory.write(
            modelId,
            messages.map {
                HistoryMessage(
                    it.fromUser,
                    it.text,
                    it.imagePath,
                    it.thinking,
                    it.interrupted,
                    attachments = it.attachments.map { attachment -> attachment.toHistory() },
                )
            },
        )
    }

    /** Text budget for files in one message, from the model's effective context window. */
    private fun attachmentBudgetBytes(model: AiModel, artifact: ModelArtifact, compatibility: CompatibilityResult?): Int {
        val contextWindow = compatibility?.contextWindowTokens?.takeIf { it > 0 }
            ?: artifact.contextWindowTokens?.takeIf { it > 0 }
            ?: DEFAULT_ATTACHMENT_CONTEXT_TOKENS
        return AttachmentPolicy.textBudgetBytes(contextWindow, hasImage = model.supportsVision)
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
