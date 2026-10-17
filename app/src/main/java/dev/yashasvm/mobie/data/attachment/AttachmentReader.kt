package dev.yashasvm.mobie.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.yashasvm.mobie.core.attachment.AttachmentPolicy
import dev.yashasvm.mobie.core.attachment.AttachmentRejection
import dev.yashasvm.mobie.core.attachment.AttachmentResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a user-picked document from a content [Uri] into a bounded [AttachmentResult].
 *
 * The size is checked before any bytes are read when the provider reports it, and the read itself
 * stops one byte past [AttachmentPolicy.MAX_FILE_BYTES] for providers that do not. File contents
 * are never logged. The app only holds the bytes it needs for the text policy.
 */
class AttachmentReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /**
     * [textBudgetBytes] is the room left for this file in the current model's context window.
     * Runs its I/O on [Dispatchers.IO].
     */
    suspend fun read(uri: Uri, textBudgetBytes: Int): AttachmentResult = withContext(Dispatchers.IO) {
        try {
            val (name, declaredSize) = queryNameAndSize(uri)
            if (declaredSize != null && declaredSize > AttachmentPolicy.MAX_FILE_BYTES) {
                return@withContext AttachmentResult.Rejected(AttachmentRejection.TooLarge(AttachmentPolicy.MAX_FILE_BYTES))
            }
            // Provider failures become a visible rejection, never a silent empty attachment.
            val stream = resolver.openInputStream(uri)
                ?: return@withContext AttachmentResult.Rejected(AttachmentRejection.ReadFailed)
            val bytes = stream.use { it.readBounded(AttachmentPolicy.MAX_FILE_BYTES) }
                ?: return@withContext AttachmentResult.Rejected(AttachmentRejection.TooLarge(AttachmentPolicy.MAX_FILE_BYTES))
            AttachmentPolicy.evaluate(
                name = name,
                mimeType = resolver.getType(uri),
                bytes = bytes,
                textBudgetBytes = textBudgetBytes,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AttachmentResult.Rejected(AttachmentRejection.ReadFailed)
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        val fallbackName = uri.lastPathSegment.orEmpty()
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use fallbackName to null
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString) ?: fallbackName
                    // A reported size of zero or below is unknown, not proof of an empty file.
                    val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getLong)
                        ?.takeIf { it > 0 }
                    name to size
                }
                ?: (fallbackName to null)
        } catch (error: SecurityException) {
            fallbackName to null
        } catch (error: IllegalArgumentException) {
            // Some providers reject the projection; the bounded read still enforces the limit.
            fallbackName to null
        }
    }

    /** Reads at most [limit] bytes; returns null as soon as the stream proves it is longer. */
    private fun InputStream.readBounded(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
    }
}
