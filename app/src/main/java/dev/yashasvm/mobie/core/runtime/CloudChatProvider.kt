package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.CloudModel
import kotlinx.coroutines.flow.Flow

/** Cloud requests happen only after explicit model selection. No automatic local/cloud fallback. */
interface CloudChatProvider {
    suspend fun models(): List<CloudModel>
    fun generate(model: CloudModel, messages: List<RuntimeMessage>): Flow<InferenceEvent>
}
