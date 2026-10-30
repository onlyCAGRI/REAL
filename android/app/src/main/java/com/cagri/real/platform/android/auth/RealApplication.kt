package com.cagri.real.platform.android.auth

import android.app.Application
import kotlinx.coroutines.launch
import com.cagri.real.BuildConfig

class RealApplication : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }
        .diskCache { coil.disk.DiskCache.Builder().directory(java.io.File(cacheDir, "image_cache")).maxSizeBytes(32L * 1024 * 1024).build() }
        .build()

    override fun onCreate() {
        super.onCreate()
        com.cagri.real.i18n.I18n.init(this)
        com.cagri.real.ui.RealBrandType.init(this)
        com.cagri.real.platform.android.ai.AndroidAiConsent.initialize(this)
        com.cagri.real.updates.AppUpdateGate.init(this)
        com.cagri.real.platform.android.account.CloudBackupEpochStore.initialize(this)
        if (BuildConfig.REAL_APP_CHECK_ENABLED) installRealAppCheck()
        com.cagri.real.platform.android.sync.SyncLocal.initialize(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { com.cagri.real.storage.LocalCacheMaintenance.onLaunch(this@RealApplication) }
        }
    }
}
