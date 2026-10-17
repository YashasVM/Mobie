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
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatThread
import dev.yashasvm.mobie.data.history.HistoryMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.io.IOException
import java.util.UUID
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
    val preferencesLoaded: Boolean = false,
    val download: DownloadProgress? = null,
    val downloadedPath: String? = null,
    val chatting: Boolean = false,
    val runtimeState: RuntimeState = RuntimeState.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    /** Every saved chat across all models, newest first, for the chats drawer. */
    val chats: List<ChatThread> = emptyList(),
    /** The open chat's id; null for a new chat that has not been saved yet. */
    val sessionId: String? = null,
    /** Navigation stack. The chat home is always the root. */
    val screens: List<AppScreen> = listOf(AppScreen.CHAT),
    /** Tab the Models screen opens on. */
    val catalogTab: CatalogTab = CatalogTab.DISCOVER,
    /** True while the chat home is deciding which model to open or switching models. */
    val chatPending: Boolean = false,
    /** Set when a saved chat is open whose model is no longer installed; the chat is read-only. */
    val readOnlyChat: ReadOnlyChat? = null,
    val stats: InferenceStats? = null,
    val telemetry: RunTelemetry = RunTelemetry(),
    val sampler: SamplerSettings = SamplerSettings(),
    val imageInputReady: Boolean = false,
    val computeBackend: ComputeBackend = ComputeBackend.AUTO,
    val cpuThreads: Int? = null,
    val activeCompute: ActiveCompute? = null,
)

/** A saved chat shown without a runtime because its model is no longer on this phone. */
data class ReadOnlyChat(val modelId: String, val modelTitle: String)

val MobieUiState.screen: AppScreen get() = screens.lastOrNull() ?: AppScreen.CHAT

/** Identifies a chat to come back to: a saved session, or a new unsaved chat when [sessionId] is null. */
private data class ChatRef(val modelId: String, val sessionId: String?)

class MobieViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MobieUiState(
            // Device facts are read off the main thread in init; the UI already tolerates a null device.
            device = null,
            sampler = container.samplerSettings.read(),
            computeBackend = container.computeSettings.selectedBackend(),
            cpuThreads = container.computeSettings.selectedCpuThreads(),
        ),
    )
    val state: StateFlow<MobieUiState> = mutableState.asStateFlow()
    private var catalogJob: Job? = null
    private var downloadObserverJob: Job? = null
    private var inferenceJob: Job? = null
    private var telemetryJob: Job? = null
    private val runtimeLifecycle = Mutex()
    private val runtimeOperation = AtomicLong(0L)
    private val selectionRevision = AtomicLong(0L)
    private val runtimeOwnership = RuntimeModelOwnership()
    private val credentialAccess = Mutex()
    private var homeJob: Job? = null
    private var selectionJob: Job? = null
    /** The chat the home should return to after a detour through the Models screens. */
    private var lastChat: ChatRef? = null
    // One writer at a time keeps history saves in the order they were made.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val historyWrites = Dispatchers.IO.limitedParallelism(1)

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            credentialAccess.withLock {
                val saved = withContext(Dispatchers.IO) {
                    val store = container.tokenStore
                    Triple(store.read() != null, store.hasSeenWelcome(), store.hasCompletedOnboarding())
                }
                mutableState.update {
                    it.copy(tokenConfigured = saved.first, welcomeSeen = saved.second,
                        onboardingComplete = saved.third, preferencesLoaded = true)
                }
            }
            if (state.value.welcomeSeen || state.value.onboardingComplete) {
                openHome()
                loadFeatured()
            } else {
                refreshInstalled()
            }
        }
        viewModelScope.launch {
            val device = readDeviceProfile()
            mutableState.update { it.copy(device = it.device ?: device) }
        }
        container.runtimes.all().forEach { it.configureSampler(mutableState.value.sampler) }
    }

    fun finishOnboarding(token: String?) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            credentialAccess.withLock {
                withContext(Dispatchers.IO) { container.tokenStore.completeOnboarding(token) }
                mutableState.update {
                    it.copy(onboardingComplete = true, tokenConfigured = !token.isNullOrBlank(), error = null)
                }
            }
            openHome()
            loadFeatured()
        }
    }

    fun finishWelcome() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            credentialAccess.withLock {
                withContext(Dispatchers.IO) { container.tokenStore.markWelcomeSeen() }
                mutableState.update { it.copy(welcomeSeen = true) }
            }
            openHome()
            loadFeatured()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Chat home and navigation

    /**
     * Opens the chat home: [preferred] when its model is still installed (or as a read-only chat
     * when it is not), otherwise a new chat with the launch model from [chooseLaunchModel]. The
     * runtime starts loading in the background; nothing is saved until the first message.
     */
    private fun openHome(preferred: ChatRef? = null) {
        cancelPendingNavigation()
        mutableState.update { it.copy(chatPending = true, screens = listOf(AppScreen.CHAT)) }
        homeJob = viewModelScope.launch {
            val (installed, chats, lastUsed) = withContext(historyWrites) {
                Triple(
                    container.downloads.installedModels(),
                    container.chatHistory.allSessions(),
                    container.chatHistory.lastModelId(),
                )
            }
            mutableState.update { it.copy(installedModels = installed, chats = chats) }
            val preferredThread = preferred?.sessionId?.let { id ->
                chats.firstOrNull { it.modelId == preferred.modelId && it.session.id == id }
            }
            val preferredEntry = preferred?.let { ref -> installed.firstOrNull { it.model.id == ref.modelId } }
            when {
                preferredEntry != null -> openInstalledChat(preferredEntry, preferredThread)
                preferredThread != null -> openReadOnly(preferredThread)
                else -> {
                    val modelId = chooseLaunchModel(installed.map { it.model.id }, lastUsed, chats.map { it.modelId })
                    val entry = installed.firstOrNull { it.model.id == modelId }
                    if (entry != null) {
                        openInstalledChat(entry, thread = null)
                    } else {
                        beginSelection(null) { mutableState.update { it.copy(chatPending = false) } }
                    }
                }
            }
        }
    }

    /** Selects an installed model and opens [thread] (or a new chat) with it, unloading any other model first. */
    private fun openInstalledChat(entry: InstalledModelEntry, thread: ChatThread?) {
        mutableState.update { it.copy(chatPending = true, readOnlyChat = null) }
        beginSelection(entry.model) {
            openChat(entry.localPath, thread?.session?.id, thread?.session?.messages?.map(::toChatMessage))
        }
    }

    private fun openReadOnly(thread: ChatThread) {
        val title = shortModelName(thread.modelId, thread.session.modelTitle)
        beginSelection(null) {
            lastChat = ChatRef(thread.modelId, thread.session.id)
            mutableState.update {
                it.copy(
                    chatPending = false,
                    readOnlyChat = ReadOnlyChat(thread.modelId, title),
                    messages = thread.session.messages.map(::toChatMessage),
                    sessionId = thread.session.id,
                    screens = listOf(AppScreen.CHAT),
                )
            }
        }
    }

    /** Opens a chat from the drawer, switching models when the chat belongs to another one. */
    fun openThread(thread: ChatThread) {
        cancelPendingNavigation()
        val current = state.value
        val sameLoadedModel = current.chatting && current.readOnlyChat == null &&
            current.selected?.id == thread.modelId && current.downloadedPath != null
        if (sameLoadedModel) {
            if (current.sessionId == thread.session.id) {
                mutableState.update { it.copy(screens = listOf(AppScreen.CHAT), chatPending = false) }
                return
            }
            resumeLoadedModel(thread.session.id, thread.session.messages.map(::toChatMessage))
            return
        }
        val entry = current.installedModels.firstOrNull { it.model.id == thread.modelId }
        if (entry != null) openInstalledChat(entry, thread) else openReadOnly(thread)
    }

    /** Starts a chat with an installed model, from the model switcher or the Library. */
    fun startInstalledChat(entry: InstalledModelEntry) {
        cancelPendingNavigation()
        val current = state.value
        if (current.chatting && current.readOnlyChat == null && current.selected?.id == entry.model.id) {
            mutableState.update { it.copy(screens = listOf(AppScreen.CHAT)) }
            newChat()
        } else {
            openInstalledChat(entry, thread = null)
        }
    }

    fun openModels(tab: CatalogTab = CatalogTab.DISCOVER) {
        cancelPendingNavigation()
        mutableState.update { it.copy(screens = it.screens.pushScreen(AppScreen.CATALOG), catalogTab = tab, chatPending = false) }
        if (state.value.models.isEmpty() && !state.value.loading) loadFeatured()
        refreshInstalled()
    }

    /**
     * Opens the model page for a chat whose model was removed. Uses a model already in the catalog
     * when possible; otherwise searches the catalog for it and falls back to the search results.
     */
    fun findModel(modelId: String) {
        cancelPendingNavigation()
        val known = (state.value.models + state.value.recentModels).firstOrNull { it.id == modelId }
        if (known != null) {
            openModelPage(known)
            return
        }
        mutableState.update { it.copy(screens = it.screens.pushScreen(AppScreen.CATALOG), catalogTab = CatalogTab.DISCOVER) }
        val query = modelId.substringAfterLast('/')
        setQuery(query)
        loadCatalog(expectedQuery = query, then = { models ->
            models.firstOrNull { it.id == modelId }?.let { match ->
                if (state.value.screen == AppScreen.CATALOG) openModelPage(match)
            }
        }) { container.catalog.search(query) }
    }

    /** Opens a model's page from the catalog. The chat home reloads its chat when the user comes back. */
    fun openModelPage(model: AiModel) {
        cancelPendingNavigation()
        mutableState.update { it.copy(screens = it.screens.pushScreen(AppScreen.MODEL), chatPending = false) }
        select(model)
    }

    /** System or toolbar back from a secondary screen. Returns false when already at the chat home. */
    fun back(): Boolean {
        val current = state.value
        if (current.screens.size <= 1) return false
        cancelPendingNavigation()
        val stack = current.screens.popScreen()
        mutableState.update { it.copy(screens = stack) }
        if (stack.last() == AppScreen.CHAT && !current.chatting && current.readOnlyChat == null) {
            // Opening a model page released the chat's model; bring the last chat back.
            openHome(lastChat)
        }
        return true
    }

    fun renameChat(thread: ChatThread, title: String) {
        launchHistoryWrite {
            container.chatHistory.rename(thread.modelId, thread.session.id, title)
            val chats = container.chatHistory.allSessions()
            mutableState.update { it.copy(chats = chats) }
        }
    }

    fun deleteChat(thread: ChatThread) {
        val current = state.value
        val isOpen = current.sessionId == thread.session.id &&
            (current.selected?.id == thread.modelId || current.readOnlyChat?.modelId == thread.modelId)
        // Stop this stream before queueing deletion. Its cancellation must not save the chat again.
        if (isOpen) cancelInference(saveHistory = false)
        launchHistoryWrite {
            container.chatHistory.delete(thread.modelId, thread.session.id)
            val chats = container.chatHistory.allSessions()
            mutableState.update { it.copy(chats = chats) }
        }
        if (isOpen) {
            if (lastChat == ChatRef(thread.modelId, thread.session.id)) lastChat = null
            if (current.readOnlyChat != null) openHome() else newChat()
        }
    }

    /** Device facts read /proc and binder, so they run on IO rather than the main thread. */
    private suspend fun readDeviceProfile(): DeviceProfile = withContext(Dispatchers.IO) { container.deviceProfile.current() }

    private fun cancelPendingNavigation() {
        homeJob?.cancel()
        selectionJob?.cancel()
        selectionRevision.incrementAndGet()
    }

    /** Cancels generation or a load together with its telemetry sampler, so neither outlives the other. */
    private fun cancelInference(saveHistory: Boolean = true) {
        val current = state.value
        val model = current.selected
        val sessionId = current.sessionId
        if (saveHistory && inferenceJob?.isActive == true && model != null && sessionId != null &&
            current.runtimeState in setOf(RuntimeState.GENERATING, RuntimeState.STOPPING)
        ) {
            persistHistory(model, sessionId, current.messages.finishInterruptedReply())
        }
        inferenceJob?.cancel()
        inferenceJob = null
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
        then: ((List<AiModel>) -> Unit)? = null,
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
                        then?.invoke(compatible)
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
        selectionJob?.cancel()
        downloadObserverJob?.cancel()
        cancelInference()
        val operation = nextRuntimeOperation()
        viewModelScope.launch { unloadRuntime(operation) }
        val revision = selectionRevision.incrementAndGet()
        mutableState.update {
            it.copy(selected = null, selectedArtifact = null, downloadedPath = null,
                chatting = false, runtimeState = RuntimeState.IDLE, imageInputReady = false)
        }
        selectionJob = viewModelScope.launch {
            val device = readDeviceProfile()
            if (selectionRevision.get() != revision) return@launch
            applySelection(model, device)
            afterSelect?.invoke()
        }
    }

    private fun applySelection(model: AiModel?, device: DeviceProfile) {
        val artifact = model?.let { artifactForDevice(it, device) }
        val presentedModel = model?.preferArtifact(artifact)
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
                // The chat that opens next fills these in; a model page shows no conversation.
                messages = emptyList(),
                sessionId = null,
                readOnlyChat = null,
                stats = null,
                imageInputReady = false,
                activeCompute = null,
                error = null,
                device = device,
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
        val revision = selectionRevision.get()
        viewModelScope.launch {
            val device = readDeviceProfile()
            if (selectionRevision.get() != revision) return@launch
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
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        val path = current.downloadedPath
        val revision = selectionRevision.get()
        val operation = runtimeOperation.get()
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) { path != null && File(path).isFile }
            if (selectionRevision.get() != revision || !isCurrentRuntimeOperation(operation)) return@launch
            if (!exists || path == null) {
                mutableState.update {
                    it.copy(downloadedPath = null, download = null,
                        error = "The local model file is missing. Download it again.")
                }
                return@launch
            }
            val device = readDeviceProfile()
            if (selectionRevision.get() != revision || !isCurrentRuntimeOperation(operation)) return@launch
            val compatibility = container.compatibility.resolve(artifact, device, model.supportsVision, admissionBackend())
            mutableState.update { it.copy(device = device, compatibility = compatibility) }
            if (compatibility.status == Compatibility.INCOMPATIBLE ||
                (compatibility.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM && !allowReclaim)
            ) {
                mutableState.update { it.copy(error = compatibility.reason) }
                return@launch
            }
            val resume = lastChat?.takeIf { it.modelId == model.id }?.sessionId
            openChat(path, resume, allowReclaim = allowReclaim)
        }
    }

    /**
     * Shows the chat for the selected model and loads its runtime in the background. [sessionId]
     * null opens a new chat that stays unsaved until its first message. [preloaded] skips the disk
     * read when the caller already has the messages.
     */
    private fun openChat(
        path: String,
        sessionId: String?,
        preloaded: List<ChatMessage>? = null,
        allowReclaim: Boolean = false,
    ) {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        cancelInference()
        val operation = nextRuntimeOperation()
        lastChat = ChatRef(model.id, sessionId)
        mutableState.update {
            it.copy(
                chatting = true,
                chatPending = false,
                readOnlyChat = null,
                screens = listOf(AppScreen.CHAT),
                downloadedPath = path,
                runtimeState = RuntimeState.LOADING,
                activeCompute = null,
                messages = preloaded.orEmpty(),
                sessionId = sessionId,
                stats = null,
                error = null,
            )
        }
        inferenceJob = viewModelScope.launch {
            val history = preloaded ?: if (sessionId == null) emptyList() else withContext(Dispatchers.IO) {
                container.chatHistory.session(model.id, sessionId)?.messages.orEmpty().map(::toChatMessage)
            }
            if (!isCurrentRuntimeOperation(operation)) return@launch
            if (preloaded == null) mutableState.update { it.copy(messages = history) }
            launchHistoryWrite { container.chatHistory.setLastModelId(model.id) }
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
        val result = try {
            runtimeLifecycle.withLock {
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
                        container.runtimes.all().forEach { it.unload() }
                        runtimeOwnership.clear()
                        (if (allowReclaim) adapter.loadAllowingMemoryReclaim(path, model.supportsVision, restored)
                         else adapter.load(path, model.supportsVision, restored)).also { loaded ->
                            if (loaded.isSuccess) runtimeOwnership.markLoaded(model.id)
                        }
                    }
                } else {
                    // A superseded selection may have skipped its queued unload. Release every
                    // engine here before loading another model, under the same lifecycle lock.
                    container.runtimes.all().forEach { it.unload() }
                    runtimeOwnership.clear()
                    (if (allowReclaim) adapter.loadAllowingMemoryReclaim(path, model.supportsVision, restored)
                     else adapter.load(path, model.supportsVision, restored)).also { loaded ->
                        if (loaded.isSuccess) runtimeOwnership.markLoaded(model.id)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
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

    /** Returns false when the prompt was rejected, so the composer keeps the user's draft. */
    fun sendMessage(prompt: String, imagePath: String? = null): Boolean {
        val current = state.value
        val model = current.selected ?: return false
        val artifact = current.selectedArtifact ?: return false
        if (prompt.isBlank() || !current.canSendToRuntime) return false
        if (imagePath != null && (!model.supportsVision || !current.imageInputReady)) return false
        val adapter = container.runtimes.adapterFor(artifact.format) ?: return false
        cancelInference()
        // Save the first prompt immediately so it also survives leaving during generation.
        val sessionId = current.sessionId ?: UUID.randomUUID().toString()
        lastChat = ChatRef(model.id, sessionId)
        mutableState.update {
            it.copy(
                sessionId = sessionId,
                runtimeState = RuntimeState.GENERATING,
                messages = it.messages + ChatMessage(true, prompt.trim(), imagePath) + ChatMessage(false, ""),
                stats = null,
                telemetry = RunTelemetry(),
                error = null,
            )
        }
        persistHistory(model, sessionId, state.value.messages.removeBlankAssistant())
        telemetryJob = startTelemetrySampling()
        val operation = runtimeOperation.get()
        inferenceJob = viewModelScope.launch {
            runtimeLifecycle.withLock {
                if (!isCurrentRuntimeOperation(operation)) return@withLock
                adapter.generate(prompt.trim(), imagePath, GenerationConfig(sampler = current.sampler)).collectBatchedTokens { event ->
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
                            persistHistory(model, sessionId, messages)
                            mutableState.update {
                                it.copy(
                                    runtimeState = if (event.requiresReload) RuntimeState.ERROR else RuntimeState.READY,
                                    // Cancellation can disable image support in the stall guard; the UI must follow it.
                                    imageInputReady = adapter.supportsImageInput,
                                    messages = messages,
                                    error = event.message,
                                )
                            }
                        }
                        InferenceEvent.Complete -> {
                            telemetryJob?.cancel()
                            val messages = state.value.messages.finishReply()
                            persistHistory(model, sessionId, messages)
                            mutableState.update {
                                it.copy(
                                    runtimeState = RuntimeState.READY,
                                    imageInputReady = adapter.supportsImageInput,
                                    messages = messages,
                                )
                            }
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
        val model = current.selected
        val modelId = model?.id
        val sessionId = current.sessionId
        val operation = runtimeOperation.get()
        telemetryJob?.cancel()
        mutableState.update {
            // Keep the placeholder until collection has flushed pending text on cancellation.
            it.copy(runtimeState = RuntimeState.STOPPING, error = null)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (model != null && sessionId != null) {
                saveBeforeCancellation(current.messages, activeInference) { persistHistory(model, sessionId, it) }
            } else {
                activeInference?.cancelAndJoin()
            }
            if (isCurrentRuntimeOperation(operation) && state.value.selected?.id == modelId && state.value.sessionId == sessionId &&
                state.value.runtimeState == RuntimeState.STOPPING && model != null && sessionId != null
            ) {
                val messages = state.value.messages.finishInterruptedReply()
                mutableState.update { it.copy(messages = messages) }
                persistHistory(model, sessionId, messages)
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

    /**
     * Starts a new, unsaved chat. With a model loaded the runtime conversation is reset in place;
     * otherwise (no model, or a read-only chat) the home opens with the launch model.
     */
    fun newChat() {
        cancelPendingNavigation()
        val current = state.value
        val model = current.selected
        val path = current.downloadedPath
        if (!current.chatting || current.readOnlyChat != null || model == null || path == null || current.selectedArtifact == null) {
            lastChat = null
            openHome()
            return
        }
        mutableState.update { it.copy(screens = listOf(AppScreen.CHAT)) }
        // Already on a fresh chat: nothing to reset.
        if (current.sessionId == null && current.messages.isEmpty() && current.runtimeState != RuntimeState.ERROR) return
        lastChat = ChatRef(model.id, null)
        resumeLoadedModel(sessionId = null, messages = emptyList())
    }

    /** Swaps the conversation on the loaded model, resetting the runtime instead of reloading when it can. */
    private fun resumeLoadedModel(sessionId: String?, messages: List<ChatMessage>) {
        val current = state.value
        val model = current.selected ?: return
        val artifact = current.selectedArtifact ?: return
        val path = current.downloadedPath ?: return
        val canReuseLoadedModel = current.runtimeState == RuntimeState.READY
        cancelInference()
        val operation = nextRuntimeOperation()
        lastChat = ChatRef(model.id, sessionId)
        mutableState.update {
            it.copy(
                messages = messages,
                sessionId = sessionId,
                screens = listOf(AppScreen.CHAT),
                chatPending = false,
                runtimeState = RuntimeState.LOADING,
                imageInputReady = false,
                stats = null,
                error = null,
            )
        }
        inferenceJob = viewModelScope.launch {
            loadRuntimeConversation(model, artifact, path, messages, preferReset = canReuseLoadedModel, operation = operation)
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
            if (isCurrentRuntimeOperation(operation)) {
                mutableState.update {
                    it.copy(
                        runtimeState = RuntimeState.ERROR,
                        error = error.message ?: "The model could not be unloaded. Restart Mobie before loading another model.",
                    )
                }
            }
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            credentialAccess.withLock {
                withContext(Dispatchers.IO) { container.tokenStore.save(token.ifBlank { null }) }
                mutableState.update { it.copy(tokenConfigured = token.isNotBlank()) }
            }
        }
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
                if (state.value.selected?.id != model.id || state.value.selectedArtifact != artifact) return@collect
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

    private fun toChatMessage(message: HistoryMessage): ChatMessage =
        ChatMessage(message.fromUser, message.text, message.imagePath, message.thinking, interrupted = message.interrupted)

    /**
     * Saves the conversation off the main thread, in order, then refreshes the chats list. The
     * write finishes even if the screen closes mid-save so a finished reply is not lost.
     */
    private fun persistHistory(model: AiModel, sessionId: String, messages: List<ChatMessage>) {
        val snapshot = messages.map { HistoryMessage(it.fromUser, it.text, it.imagePath, it.thinking, it.interrupted) }
        launchHistoryWrite {
            container.chatHistory.writeSession(model.id, sessionId, snapshot, model.title)
            val chats = container.chatHistory.allSessions()
            mutableState.update { it.copy(chats = chats) }
        }
    }

    private fun launchHistoryWrite(write: suspend () -> Unit) {
        viewModelScope.launch(historyWrites + NonCancellable) {
            try {
                write()
            } catch (_: IOException) {
                mutableState.update { it.copy(error = "Couldn't save chat history. Free some storage and try again.") }
            }
        }
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
