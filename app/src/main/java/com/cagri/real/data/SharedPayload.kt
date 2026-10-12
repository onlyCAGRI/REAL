package com.cagri.real.data

import android.content.Intent

data class SharedPayload(
    val title: String,
    val url: String?
)

object ShareParser {
    
    fun parseUrlOnly(rawText: String?): SharedPayload? {
        val value = rawText?.trim()?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) } ?: return null
        val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (parsed.scheme?.lowercase() !in listOf("http", "https") || parsed.host.isNullOrBlank()) return null
        return SharedPayload(value, value)
    }

    
    private val URL_REGEX = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()

    fun parse(intent: Intent?): SharedPayload? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
        val extraTitle = intent.getStringExtra(Intent.EXTRA_TITLE)?.trim()

        return parseValues(extraText, extraSubject, extraTitle)
    }

    fun parseValues(
        rawText: String?,
        rawSubject: String? = null,
        rawTitle: String? = null
    ): SharedPayload? {
        val extraText = rawText?.trim()
        val extraSubject = rawSubject?.trim()
        val extraTitle = rawTitle?.trim()

        val subject = when {
            !extraSubject.isNullOrBlank() -> extraSubject
            !extraTitle.isNullOrBlank() -> extraTitle
            else -> null
        }

        if (extraText.isNullOrBlank()) {
            return if (!subject.isNullOrBlank()) SharedPayload(title = subject, url = null) else null
        }

        val urlMatch = URL_REGEX.find(extraText)
        val extractedUrl = urlMatch?.value

        return when {
            
            !subject.isNullOrBlank() && extractedUrl != null -> {
                SharedPayload(title = subject, url = extractedUrl)
            }
            
            extractedUrl != null -> {
                val textWithoutUrl = extraText.replace(extractedUrl, "").trim()
                if (textWithoutUrl.isNotBlank()) {
                    
                    SharedPayload(title = textWithoutUrl, url = extractedUrl)
                } else {
                    
                    SharedPayload(title = extractedUrl, url = extractedUrl)
                }
            }
            
            !subject.isNullOrBlank() -> {
                SharedPayload(title = "$subject\n$extraText".trim(), url = null)
            }
            
            else -> {
                SharedPayload(title = extraText, url = null)
            }
        }
    }
}
