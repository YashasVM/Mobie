package dev.yashasvm.mobie.core.runtime

/**
 * Splits one LiteRT-LM streamed chunk into reasoning and visible answer text in a single pass.
 *
 * Called once per SDK callback on the generation hot path, so it avoids the intermediate lists and
 * repeated lowercase() calls that separate filter/joinToString passes would allocate.
 */
internal object LiteRtChannelText {
    data class Split(val reasoning: String, val visible: String)

    fun split(channels: Map<String, String>, reasoningChannels: Set<String>): Split {
        if (channels.isEmpty()) return EMPTY
        // SDK chunks commonly contain one channel. Reuse its text rather than copying it through
        // two builders; multi-channel chunks still concatenate in the SDK's iteration order.
        if (channels.size == 1) {
            val (name, value) = channels.entries.first()
            if (value.isEmpty()) return EMPTY

            return if (name.lowercase() in reasoningChannels) Split(value, "") else Split("", value)
        }
        val reasoning = StringBuilder()
        val visible = StringBuilder()
        for ((name, value) in channels) {
            if (name.lowercase() in reasoningChannels) {
                reasoning.append(value)
            } else {
                visible.append(value)
            }
        }
        return Split(
            reasoning = if (reasoning.isEmpty()) "" else reasoning.toString(),
            visible = if (visible.isEmpty()) "" else visible.toString(),
        )
    }

    private val EMPTY = Split(reasoning = "", visible = "")
}
