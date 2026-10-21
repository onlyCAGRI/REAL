package com.cagri.real.platform.android.ai

import com.cagri.real.ai.aiFeatureForPath
import com.cagri.real.network.*
import com.cagri.real.platform.android.auth.AndroidBackend
import java.net.URI

internal class AiConsentTransport(private val delegate: HttpTransport,
    private val forAccount: (String) -> HttpTransport) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        val feature=if (request.url.startsWith("${AndroidBackend.ORIGIN}/")) aiFeatureForPath(URI(request.url).path) else null
        if(feature==null) return delegate.execute(request)
        val uid=checkNotNull(AndroidBackend.auth.currentUser()?.id) { "REAL hesabına giriş yap." }
        com.cagri.real.platform.android.access.ProAccess.requirePath(URI(request.url).path)
        AndroidAiConsent.require(feature)
        check(AndroidBackend.auth.currentUser()?.id==uid) { "Hesap değişti." }
        val response=forAccount(uid).execute(request)
        check(AndroidAiConsent.coordinator.allowed(uid,feature)) { "AI onayı kapatıldı; yanıt uygulanmadı." }
        return response
    }
}
