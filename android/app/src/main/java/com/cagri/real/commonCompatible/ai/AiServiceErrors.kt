package com.cagri.real.ai

import com.cagri.real.i18n.t

/** User-safe errors: no provider response, credentials, model names or private input. */
fun aiServiceFailureMessage(status: Int, code: String): String = when {
    code == "PRO_REQUIRED" -> t("Bu AI özelliği REAL Pro gerektiriyor. Ayarlar → REAL Pro bölümünden erişimini görebilirsin.")
    status == 429 || code == "QUOTA_LIMIT" -> t("AI kullanım sınırına ulaşıldı. Bir süre sonra tekrar dene.")
    code == "PRIVATE_AI_NOT_CONFIGURED" -> t("Bu AI özelliği henüz hazır değil. Normal kart eklemeyi kullanabilirsin.")
    status == 401 || status == 403 -> t("AI bağlantısı doğrulanamadı. Hesabını ve bağlantını kontrol et.")
    status == 504 || code == "TOTAL_TIMEOUT" -> t("AI zamanında yanıt veremedi. Tekrar deneyebilirsin.")
    status >= 500 -> t("AI şu an yanıt veremiyor. Tekrar deneyebilir veya normal kart ekleyebilirsin.")
    else -> t("AI isteği tamamlanamadı. Tekrar deneyebilirsin.")
}
