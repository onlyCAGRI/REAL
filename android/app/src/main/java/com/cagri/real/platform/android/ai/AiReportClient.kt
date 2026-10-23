package com.cagri.real.platform.android.ai

import com.cagri.real.i18n.t
import com.cagri.real.network.HttpRequest
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class AiReportFeature { MOOD, MEDIA, GAME }
enum class AiReportReason { HARMFUL, HARASSMENT, SEXUAL, INACCURATE, OTHER }
/** Only the explicitly reviewed suggestion is sent; never automatically attaches input/context. */
class AiReportClient(private val uid: String) {
    suspend fun submit(id: String,feature: AiReportFeature,reason: AiReportReason,title: String,content: String) = withContext(Dispatchers.IO) {
        check(AndroidBackend.auth.currentUser()?.id==uid) { t("Hesap değişti.") }
        val payload=JSONObject().put("id",id).put("feature",feature.name).put("reason",reason.name)
            .put("title",title.take(300)).put("content",content.take(4000))
        val response=AndroidBackend.syncTransport().execute(HttpRequest("${AndroidBackend.ORIGIN}/api/ai-reports/v1/reports","POST",
            mapOf("Content-Type" to "application/json"),payload.toString(),10000,15000))
        check(response.statusCode in 200..299 && runCatching { JSONObject(response.body).optBoolean("ok") }.getOrDefault(false)) { when(response.statusCode) {
            429 -> t("Bildirim sınırına ulaşıldı. Daha sonra tekrar dene.")
            423 -> t("Bu hesabın bulut verileri kapalı; bildirim gönderilmedi.")
            else -> t("Bildirim gönderilemedi. Tekrar deneyebilirsin.")
        } }
        check(AndroidBackend.auth.currentUser()?.id==uid) { t("Hesap değişti.") }
    }
}
