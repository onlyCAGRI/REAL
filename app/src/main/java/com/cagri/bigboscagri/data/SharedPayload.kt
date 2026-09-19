package com.cagri.bigboscagri.data

import android.content.Intent

data class SharedPayload(
    val title: String,
    val url: String?
)

object ShareParser {
    // Matches http:// or https:// URLs safely
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
            // Case 1: Distinct subject/title AND an extracted URL
            !subject.isNullOrBlank() && extractedUrl != null -> {
                SharedPayload(title = subject, url = extractedUrl)
            }
            // Case 2: No distinct subject, but text contains a URL
            extractedUrl != null -> {
                val textWithoutUrl = extraText.replace(extractedUrl, "").trim()
                if (textWithoutUrl.isNotBlank()) {
                    // Extra text contains both title and URL (e.g. "Some article title https://...")
                    SharedPayload(title = textWithoutUrl, url = extractedUrl)
                } else {
                    // Pure URL shared (e.g. "https://openai.com")
                    SharedPayload(title = extractedUrl, url = extractedUrl)
                }
            }
            // Case 3: Subject exists, but no URL found in text
            !subject.isNullOrBlank() -> {
                SharedPayload(title = "$subject\n$extraText".trim(), url = null)
            }
            // Case 4: Plain text only, no URL
            else -> {
                SharedPayload(title = extraText, url = null)
            }
        }
    }
}
