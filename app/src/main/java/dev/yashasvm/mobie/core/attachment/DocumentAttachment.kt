package dev.yashasvm.mobie.core.attachment

/**
 * A local text document the user attached to a chat message.
 *
 * [text] is the already-bounded content that may be sent to the model. It includes the
 * [AttachmentPolicy.TRUNCATION_MARKER] when [truncated] is true. [sizeBytes] is the size of the
 * original file, which can be larger than the stored text.
 */
data class DocumentAttachment(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val text: String,
    val truncated: Boolean,
) {
    /** Display-only projection. The UI shows chips from this and never needs the body text. */
    val meta: AttachmentMeta get() = AttachmentMeta(name = name, sizeBytes = sizeBytes, truncated = truncated)
}

/** Name, size and truncation state of an attachment, for chips and transcripts. */
data class AttachmentMeta(
    val name: String,
    val sizeBytes: Long,
    val truncated: Boolean,
)

/** Why a file cannot be attached. [message] is plain copy the UI can show as-is. */
sealed interface AttachmentRejection {
    val message: String

    data class UnsupportedType(val name: String) : AttachmentRejection {
        override val message: String
            get() = "$name is not a text file Mobie can read. Attach .txt, .md, .csv, .json, .xml, .yaml, .log, " +
                "or source code files."
    }

    /** PDF text extraction needs a large parser, so PDFs are refused rather than shipped half-working. */
    data object PdfNotSupported : AttachmentRejection {
        override val message: String =
            "PDF files can't be read on this device yet. Copy the text into a .txt or .md file and attach that."
    }

    data class TooLarge(val limitBytes: Long) : AttachmentRejection {
        override val message: String
            get() = "This file is larger than ${limitBytes / (1024 * 1024)} MB. Attach a smaller part of it."
    }

    data object Empty : AttachmentRejection {
        override val message: String = "This file is empty."
    }

    data object Binary : AttachmentRejection {
        override val message: String = "This file looks like binary data, not text."
    }

    /** The model's context window has no room left for another file. */
    data object NoContextRoom : AttachmentRejection {
        override val message: String =
            "Attached files already fill this model's context window. Remove one before adding another."
    }

    data object ReadFailed : AttachmentRejection {
        override val message: String = "Mobie couldn't open that file. Try attaching it again."
    }
}

sealed interface AttachmentResult {
    data class Accepted(val attachment: DocumentAttachment) : AttachmentResult
    data class Rejected(val reason: AttachmentRejection) : AttachmentResult
}
