package com.cagri.real.storage

import android.content.Context
import java.io.File

/** Once per process launch on IO. Only stale previews; no account assets, books or APK installer files. */
internal object LocalCacheMaintenance {
    fun onLaunch(context: Context) {
        val cutoff = System.currentTimeMillis() - 7L * 86_400_000L
        context.cacheDir.listFiles()?.filter { it.isFile && (it.name.startsWith("profile_source_") || it.name.startsWith("profile_preview_")) &&
            it.lastModified() in 1..cutoff }?.forEach { runCatching { it.delete() } }
        // Coil owns/evicts its 32 MB image cache. Never manually walk its live journal/files.
    }
}
