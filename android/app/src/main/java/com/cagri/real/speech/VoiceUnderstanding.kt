package com.cagri.real.speech

import java.util.Locale

/** One underlined piece of the "Doğru mu anladım?" sentence. [options] = what it may be (first = shown now). */
data class VoiceUnsureSpan(val start: Int, val end: Int, val heard: String, val options: List<String>)

data class VoiceUnderstandingText(val text: String, val spans: List<VoiceUnsureSpan>)

/**
 * Builds the sentence shown before saving when the AI is not sure what it heard: every unsure word is replaced by
 * the AI's guess (if it has one) and underlined; words the speech service itself marked as low confidence are
 * underlined too. Pure, so it can be unit tested.
 */
object VoiceUnderstanding {
    private val TR = Locale.forLanguageTag("tr")

    fun build(transcript: String, heard: VoiceHeard, lowConfidence: List<String> = emptyList()): VoiceUnderstandingText {
        val lower = transcript.lowercase(TR)
        data class Hit(val start: Int, val end: Int, val heard: String, val options: List<String>)
        val hits = mutableListOf<Hit>()
        fun free(start: Int, end: Int) = hits.none { start < it.end && it.start < end }
        heard.unsure.forEach { word ->
            val at = lower.indexOf(word.heard.lowercase(TR))
            if (at < 0 || !free(at, at + word.heard.length)) return@forEach
            val original = transcript.substring(at, at + word.heard.length)
            hits += Hit(at, at + word.heard.length, original, listOfNotNull(word.meant, original).distinctBy { it.lowercase(TR) })
        }
        lowConfidence.forEach { word ->
            val match = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word.lowercase(TR)) + "(?![\\p{L}\\p{N}])").find(lower) ?: return@forEach
            if (!free(match.range.first, match.range.last + 1)) return@forEach
            val original = transcript.substring(match.range)
            hits += Hit(match.range.first, match.range.last + 1, original, listOf(original))
        }
        hits.sortBy { it.start }
        val out = StringBuilder()
        val spans = mutableListOf<VoiceUnsureSpan>()
        var cursor = 0
        hits.forEach { hit ->
            out.append(transcript, cursor, hit.start)
            val shown = hit.options.first()
            spans += VoiceUnsureSpan(out.length, out.length + shown.length, hit.heard, hit.options)
            out.append(shown)
            cursor = hit.end
        }
        out.append(transcript.substring(cursor))
        return VoiceUnderstandingText(out.toString(), spans)
    }

    /** The person picked [option] for span [index]; later spans shift with the new length. */
    fun pick(state: VoiceUnderstandingText, index: Int, option: String): VoiceUnderstandingText {
        val span = state.spans.getOrNull(index) ?: return state
        val text = state.text.substring(0, span.start) + option + state.text.substring(span.end)
        val delta = option.length - (span.end - span.start)
        val spans = state.spans.mapIndexed { i, s ->
            when {
                i == index -> s.copy(end = s.start + option.length, options = listOf(option) + (s.options - option))
                s.start > span.start -> s.copy(start = s.start + delta, end = s.end + delta)
                else -> s
            }
        }
        return VoiceUnderstandingText(text, spans)
    }
}

/**
 * Names the AI wrote into a card that the person did not say: a place, person, show or a capitalised word of a
 * title that matches no word of the transcript (or its alternatives). That is where a misheard foreign word
 * ("vende kiçında" -> "When Kitchen") turns into a wrong card, so REAL asks even when the AI felt sure.
 * A word counts as said when a spoken word starts with its first five letters ("Belediyesi" ~ "belediyesinde").
 * Recommendations ("bana film öner") are skipped: their titles are meant to be new.
 */
object VoiceSubstitutions {
    private val recommendation = Regex("\\b(oner\\w*|tavsiye\\w*|recommend\\w*|suggest\\w*)\\b")
    private val spaces = Regex("\\s+")
    private val spacesOrCommas = Regex("[\\s,]+")

    private fun key(word: String) = voiceNameKey(word.substringBefore('\'').substringBefore('’'))
        .replace(Regex("[^a-z0-9]"), "")

    fun names(transcript: String, alternatives: List<String>, intents: List<VoiceIntent<*, *>>): List<String> {
        val heard = (listOf(transcript) + alternatives)
            .flatMap { voiceNameKey(it).split(Regex("[^a-z0-9]+")) }
            .filter { it.isNotBlank() }
            .toSet()
        val recommends = recommendation.containsMatchIn(voiceNameKey(transcript))
        fun said(word: String): Boolean {
            val k = key(word)
            if (k.length < 3 || k.all { it.isDigit() }) return true
            val stem = k.take(5)
            return heard.any { it.startsWith(stem) || (k.length <= 5 && it == k) }
        }
        val candidates = intents.flatMap { intent ->
            val titleNames = intent.title.split(spaces).drop(1).filter { it.firstOrNull()?.isUpperCase() == true }
            val content = if (recommends) emptyList() else listOfNotNull(intent.media?.title, intent.music?.artist, intent.music?.track)
                .flatMap { it.split(spaces) }
            titleNames + listOfNotNull(intent.locationName).flatMap { it.split(spacesOrCommas) } +
                intent.people.flatMap { it.split(spaces) } + content
        }
        return candidates.map { it.trim(',', '.', ';', ':', '!', '?', '"') }
            .filter { it.isNotBlank() && !said(it) }
            .distinctBy { key(it) }
            .take(6)
    }

    /** The sentence to confirm, with every substituted name underlined. */
    fun text(display: String, names: List<String>): VoiceUnderstandingText {
        val lower = display.lowercase()
        val spans = names.mapNotNull { name ->
            val at = lower.indexOf(name.lowercase())
            if (at < 0) null else VoiceUnsureSpan(at, at + name.length, name, listOf(name))
        }.sortedBy { it.start }
            .fold(mutableListOf<VoiceUnsureSpan>()) { acc, span -> if (acc.none { span.start < it.end }) acc += span; acc }
        return VoiceUnderstandingText(display, spans)
    }
}
