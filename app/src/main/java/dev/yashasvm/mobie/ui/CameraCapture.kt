package dev.yashasvm.mobie.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Delegates capture to the camera app, then imports only a validated image into chat storage. */
@Composable
internal fun rememberCameraCapture(
    enabled: Boolean,
    identity: String,
    onImage: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentIdentity by rememberUpdatedState(identity)
    val acceptImage by rememberUpdatedState(onImage)
    val busy by rememberUpdatedState(onBusy)
    val error by rememberUpdatedState(onError)
    var preparing by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val path = pendingPath
        val sourceIdentity = pendingIdentity
        pendingPath = null
        pendingIdentity = null
        if (path != null) {
            scope.launch {
                var imported: String? = null
                busy(true)
                try {
                    if (captured && currentEnabled && currentIdentity == sourceIdentity) {
                        val source = cameraCaptureFile(context, path)
                        if (source != null) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", source)
                            imported = copyImageToCache(context, uri)
                        }
                        if (imported == null) {
                            error("Couldn't attach that photo. Use a JPEG, PNG, or WebP under 20 MB and 25 megapixels.")
                        } else if (currentEnabled && currentIdentity == sourceIdentity) {
                            acceptImage(checkNotNull(imported))
                            imported = null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error("Couldn't attach that photo. Try again or choose a photo.")
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        imported?.let { File(it).delete() }
                        cameraCaptureFile(context, path)?.delete()
                    }
                    busy(false)
                }
            }
        }
    }
    return {
        if (currentEnabled && pendingPath == null && !preparing) {
            preparing = true
            scope.launch {
                var source: File? = null
                try {
                    val sourceIdentity = currentIdentity
                    withContext(Dispatchers.IO) {
                        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                        source = File.createTempFile("capture-", ".jpg", directory)
                    }
                    currentCoroutineContext().ensureActive()
                    if (currentEnabled && currentIdentity == sourceIdentity) {
                        val file = checkNotNull(source)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
                        pendingPath = file.absolutePath
                        pendingIdentity = sourceIdentity
                        launcher.launch(uri)
                        source = null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    pendingPath = null
                    pendingIdentity = null
                    error("Couldn't open the camera. Install or enable a camera app, or choose a photo.")
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { source?.delete() }
                    preparing = false
                }
            }
        }
    }
}

/** Restored activity state may only reference the directory exposed to camera apps. */
internal fun cameraCaptureFile(context: Context, path: String): File? {
    val file = File(path).canonicalFile
    val directory = File(context.cacheDir, "camera").canonicalFile
    return file.takeIf { it.parentFile == directory && it.name.startsWith("capture-") && it.extension == "jpg" }
}
