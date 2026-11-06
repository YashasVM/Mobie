package dev.yashasvm.mobie.core.attachment

/**
 * Builds the text sent to the runtime from a user's question and its attachments.
 *
 * The visible chat message keeps only the question; this assembly is used for the generation
 * request and for replaying history into a restored conversation. Without attachments the result
 * is exactly the question, so existing prompts are unchanged.
 */
object DocumentPromptAssembler {
    fun assemble(question: String, attachments: List<DocumentAttachment>): String {
        if (attachments.isEmpty()) return question
        val blocks = attachments.joinToString(separator = "\n") { attachment ->
            "<file name=\"${escapeAttribute(attachment.name)}\">\n" +
                escapeBody(attachment.text) +
                "\n</file>"
        }
        return if (question.isEmpty()) blocks else "$blocks\n\n$question"
    }

    private fun escapeAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** A file cannot close its own block early, which would let its text read as the question. */
    private fun escapeBody(value: String): String = value.replace("</file>", "&lt;/file&gt;", ignoreCase = true)
}
