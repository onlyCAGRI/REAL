package com.cagri.real.data

/**
 * Share → REAL: was the text copied from WhatsApp or from an e-mail? First the app that shared it (Android tells REAL
 * the sending app), then the shape of the text itself (WhatsApp's "[date time] Name: message" / exported chat lines,
 * e-mail "From: / Subject:" headers).
 */
object SnippetSourceDetector {
    private val MAIL_APPS = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail", "ch.protonmail.android", "com.android.email", "com.yandex.mail",
        "com.readdle.spark", "com.easilydo.mail", "me.bluemail.mail", "com.fsck.k9", "net.thunderbird.android"
    )
    // Copied WhatsApp messages: "[15.10.2026 14:32] Ahmet: ..." (also "[10/15/26, 2:32 PM]").
    private val WHATSAPP_COPY = Regex("""^\[\d{1,2}[./]\d{1,2}[./]\d{2,4},? \d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s?[AaPp][Mm])?] [^:\n]{1,60}: """, RegexOption.MULTILINE)
    // Exported chat: "15.10.2026 14:32 - Ahmet: ...".
    private val WHATSAPP_EXPORT = Regex("""^\d{1,2}[./]\d{1,2}[./]\d{2,4},? \d{1,2}[:.]\d{2}(?:\s?[AaPp][Mm])? - [^:\n]{1,60}: """, RegexOption.MULTILINE)
    private val MAIL_HEADER = Regex("""^(From|Kimden|Gönderen|De|Von):\s.+$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val MAIL_SUBJECT = Regex("""^(Subject|Konu|Objet|Betreff|Sent|Gönderildi|Tarih|Date):\s.+$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun detect(senderPackage: String?, text: String?, hasEmailExtras: Boolean = false): SnippetSource {
        val app = senderPackage?.removePrefix("android-app://")?.substringBefore('/')
        if (app != null) {
            if (app.startsWith("com.whatsapp")) return SnippetSource.WHATSAPP_TEXT
            if (app in MAIL_APPS) return SnippetSource.EMAIL_TEXT
        }
        val value = text.orEmpty()
        if (WHATSAPP_COPY.containsMatchIn(value) || WHATSAPP_EXPORT.containsMatchIn(value)) return SnippetSource.WHATSAPP_TEXT
        if (hasEmailExtras || (MAIL_HEADER.containsMatchIn(value) && MAIL_SUBJECT.containsMatchIn(value))) return SnippetSource.EMAIL_TEXT
        return SnippetSource.PLAIN_TEXT
    }
}
