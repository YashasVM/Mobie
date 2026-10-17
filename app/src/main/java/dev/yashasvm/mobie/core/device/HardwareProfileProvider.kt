package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads sysfs/proc and creates a temporary GL context only on an IO worker. */
class HardwareProfileProvider(context: Context) {
    private val appContext = context.applicationContext

    suspend fun detect(): HardwareProfile = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        val root = File("/sys/devices/system/cpu")
        val possible = readOptional(File(root, "possible"), notes)?.let(HardwareProfileParser::cpuIds).orEmpty()
        val cores = possible.ifEmpty {
            notes += "CPU topology was unavailable; using Android's processor count."
            (0 until Runtime.getRuntime().availableProcessors().coerceIn(1, 256)).toList()
        }
        val frequencies = cores.mapNotNull { core ->
            val directory = File(root, "cpu$core/cpufreq")
            val value = readOptional(File(directory, "cpuinfo_max_freq"), notes)
                ?.takeIf { HardwareProfileParser.maxFrequencyKhz(it) != null }
                ?: readOptional(File(directory, "scaling_max_freq"), notes)
            value?.let { core to it }
        }.toMap()
        val related = cores.mapNotNull { core ->
            readOptional(File(root, "cpu$core/cpufreq/related_cpus"), notes)?.let { core to it }
        }.toMap()
        val platformSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
        val cpuInfo = if (HardwareProfileParser.reportedName(platformSoc) == null) {
            readOptional(File("/proc/cpuinfo"), notes).orEmpty()
        } else ""
        val memory = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        HardwareProfile(
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HardwareProfileParser.reportedName(Build.SOC_MANUFACTURER)
            } else null,
            socModel = HardwareProfileParser.socModel(platformSoc, cpuInfo),
            cpuCoreCount = cores.size,
            cpuClusters = HardwareProfileParser.cpuClusters(cores, frequencies, related),
            gpu = queryGpu(notes),
            openClLibraryPresent = openClPresent(notes),
            totalRamBytes = memory.totalMem,
            detectionNotes = notes.distinct(),
        )
    }

    private fun readOptional(file: File, notes: MutableList<String>): String? = try {
        file.readText()
    } catch (_: IOException) {
        notes += "Cannot read ${file.absolutePath}."
        null
    } catch (_: SecurityException) {
        notes += "Android denied access to ${file.absolutePath}."
        null
    }

    private fun openClPresent(notes: MutableList<String>): Boolean = try {
        // Do not dlopen a vendor library. Namespace/access restrictions and model qualification
        // remain the runtime's responsibility. Presence alone must never enable a backend.
        listOf("/vendor/lib64", "/system/lib64", "/system/vendor/lib64", "/odm/lib64",
            "/vendor/lib", "/system/lib", "/system/vendor/lib", "/odm/lib")
            .any { File(it, "libOpenCL.so").isFile }
    } catch (_: SecurityException) {
        notes += "Android denied access to OpenCL library paths."
        false
    }

    private fun queryGpu(notes: MutableList<String>): GpuHardware? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            notes += "GPU information is unavailable: EGL display could not be opened."
            return null
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            notes += "GPU information is unavailable: EGL initialization failed."
            return null
        }
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            ), 0, configs, 0, 1, count, 0) && count[0] > 0) { "No offscreen EGL config" }
            val config = checkNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }
            surface = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "EGL surface creation failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL context activation failed" }
            val renderer = checkNotNull(HardwareProfileParser.reportedName(GLES20.glGetString(GLES20.GL_RENDERER)))
            return GpuHardware(renderer, HardwareProfileParser.reportedName(GLES20.glGetString(GLES20.GL_VENDOR)))
        } catch (failure: IllegalStateException) {
            notes += "GPU information is unavailable: ${failure.message}."
            return null
        } catch (failure: IllegalArgumentException) {
            notes += "GPU information is unavailable: ${failure.message}."
            return null
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
    }
}
