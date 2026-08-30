package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterruptedDownloadResumeTest {
    @Test
    fun interruptedTransferResumesFromPersistedPartialFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val payload = ByteArray(512 * 1024) { index -> (index * 31).toByte() }
        val splitAt = 137_219
        val observedRange = AtomicReference<String?>(null)

        ServerSocket(0).use { server ->
            val serving = Thread {
                repeat(2) { attempt ->
                    server.accept().use { socket ->
                        val headers = readRequestHeaders(socket)
                        val range = headers.firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()
                        if (attempt == 0) {
                            assertEquals(null, range)
                            writeResponseHeaders(socket, 200, payload.size, null)
                            socket.getOutputStream().apply {
                                write(payload, 0, splitAt)
                                flush()
                            }
                            // Closing before Content-Length bytes are sent simulates a dropped connection.
                        } else {
                            observedRange.set(range)
                            val expected = "bytes=$splitAt-"
                            assertEquals(expected, range)
                            val remaining = payload.size - splitAt
                            writeResponseHeaders(
                                socket,
                                206,
                                remaining,
                                "bytes $splitAt-${payload.lastIndex}/${payload.size}",
                            )
                            socket.getOutputStream().apply {
                                write(payload, splitAt, remaining)
                                flush()
                            }
                        }
                    }
                }
            }.apply { start() }

            val artifact = ModelArtifact(
                fileName = "resume-test.litertlm",
                downloadUrl = "http://127.0.0.1:${server.localPort}/resume-test.litertlm",
                sizeBytes = payload.size.toLong(),
                sha256 = sha256(payload),
                format = ModelFormat.LITERT_LM,
            )
            val downloads = ModelDownloadManager(context)
            val requestId = downloads.enqueue("mobie-test/interrupted-resume", artifact)
            val result = withTimeout(60_000L) {
                downloads.observe(requestId).first { it.state.isFinished }
            }

            serving.join(5_000)
            assertTrue("Download failed: ${result.error}", result.state == WorkInfo.State.SUCCEEDED)
            assertEquals("bytes=$splitAt-", observedRange.get())
            val downloaded = checkNotNull(result.localPath).let(::java.io.File)
            assertArrayEquals(payload, downloaded.readBytes())

            downloaded.parentFile?.deleteRecursively()
        }
    }

    private fun readRequestHeaders(socket: Socket): List<String> {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
        val headers = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headers += line
        }
        return headers
    }

    private fun writeResponseHeaders(socket: Socket, code: Int, contentLength: Int, contentRange: String?) {
        val reason = if (code == 206) "Partial Content" else "OK"
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Connection: close\r\n")
            if (contentRange != null) append("Content-Range: $contentRange\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
