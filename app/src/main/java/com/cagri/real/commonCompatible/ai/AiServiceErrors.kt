package com.cagri.real.ai

/** User-safe errors: no provider response, credentials, model names or private input. */
fun aiServiceFailureMessage(status: Int, code: String): String = when {
    code == "PRO_REQUIRED" -> "Bu AI özelliği REAL Pro gerektiriyor. Ayarlar → REAL Pro bölümünden erişimini görebilirsin."
    status == 429 || code == "QUOTA_LIMIT" -> "AI kullanım sınırına ulaşıldı. Bir süre sonra tekrar dene."
    code == "PRIVATE_AI_NOT_CONFIGURED" -> "Bu AI özelliği henüz hazır değil. Normal kart eklemeyi kullanabilirsin."
    status == 401 || status == 403 -> "AI bağlantısı doğrulanamadı. Hesabını ve bağlantını kontrol et."
    status == 504 || code == "TOTAL_TIMEOUT" -> "AI zamanında yanıt veremedi. Tekrar deneyebilirsin."
    status >= 500 -> "AI şu an yanıt veremiyor. Tekrar deneyebilir veya normal kart ekleyebilirsin."
    else -> "AI isteği tamamlanamadı. Tekrar deneyebilirsin."
}
