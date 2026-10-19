package dev.yashasvm.mobie.core.runtime

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message

/** Resolves a streamed answer without rejoining a single immutable text content item. */
internal object LiteRtMessageText {
    fun visibleAnswer(chunk: Message, split: LiteRtChannelText.Split): String {
        // Preserve channel precedence and never leak a reasoning-only chunk into the answer.
        if (split.visible.isNotEmpty()) return split.visible
        if (split.reasoning.isNotEmpty()) return ""

        // LiteRT-LM 0.18.0's Message.toString delegates to Contents.joinToString even for one
        // Text item. Its Text.toString returns this same string, so that common fallback can
        // avoid the join's builder and result copy. Do not reinterpret mixed/non-text content.
        val contents = chunk.contents.contents
        if (contents.size == 1) {
            val only = contents[0]
            if (only is Content.Text) return only.text
        }
        return chunk.toString()
    }
}
