package dev.yashasvm.mobie.core.runtime

/**
 * LiteRT-LM is currently configured with a single image slot. When recreating a conversation,
 * restore only the newest usable user image so stale/missing media cannot make conversation setup
 * fail and older multimodal turns remain available as text context.
 */
internal object VisionHistoryPolicy {
    fun latestUsableImageIndex(
        history: List<RuntimeMessage>,
        visionReady: Boolean,
        isUsable: (String) -> Boolean,
    ): Int {
        if (!visionReady) return -1
        return history.indexOfLast { message ->
            message.fromUser && message.imagePath?.let(isUsable) == true
        }
    }
}
