package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodTrackerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Yaşam ekranının saf (Compose'suz) mantığı: selamlama, karşılama cümlesi, mood puanı, sahne seçimi.
 *
 * NOT: Saat aralıkları (selamlama, sahne) varsayımdır; buradan değiştirilir.
 * Sahne: Sabah 05-11, Akşam 17-23, kalan saatlerde Okuma.
 */
internal object YasamSentence {
    private val TR: Locale get() = com.cagri.real.i18n.I18n.locale

    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> t("Günaydın")
        in 11..17 -> t("İyi günler")
        else -> t("İyi akşamlar")
    }

    /** 0 = Sabah, 1 = Akşam, 2 = Okuma. */
    fun activeScene(hour: Int): Int = when (hour) {
        in 5..11 -> 0
        in 17..23 -> 1
        else -> 2
    }

    /** 1800 -> "1,8", 2000 -> "2", 0 -> "0". */
    fun litres(ml: Int): String {
        val text = String.format(TR, "%.1f", ml / 1000.0)
        return if (text.endsWith(",0") || text.endsWith(".0")) text.dropLast(2) else text
    }

    /** Veri yoksa ilgili parça atlanır; ikisi de yoksa null döner (sayı uydurulmaz). */
    fun build(yesterdayMl: Int?, yesterdayMood: JournalMood?): String? {
        val water = yesterdayMl?.takeIf { it > 0 }?.let {
            if (it < 1000) t("Dün {0} ml su içtin", it) else t("Dün {0} L su içtin", litres(it))
        }
        val mood = yesterdayMood?.let { t("ruh halin {0}", moodPhrase(it)) }
        return when {
            water != null && mood != null -> t("{0},\n{1}.", water, mood)
            water != null -> t("{0}.", water)
            mood != null -> t("Dün {0}.", mood)
            else -> null
        }
    }

    fun moodPhrase(mood: JournalMood): String = when (mood) {
        JournalMood.VERY_GOOD -> t("çok iyiydi")
        JournalMood.GOOD -> t("iyiydi")
        JournalMood.NEUTRAL -> t("normaldi")
        JournalMood.HARD -> t("zordu")
        JournalMood.BAD -> t("kötüydü")
        JournalMood.HAPPY -> t("mutluydu")
        JournalMood.EXCITED -> t("heyecanlıydı")
        JournalMood.CALM -> t("sakindi")
        JournalMood.TIRED -> t("yorgundu")
        JournalMood.BORED -> t("sıkkındı")
        JournalMood.STRESSED -> t("stresliydi")
        JournalMood.ANGRY -> t("sinirliydi")
        JournalMood.SAD -> t("üzgündü")
        JournalMood.LOW -> t("keyifsizdi")
        JournalMood.FUCK_OFF -> t("zordu")
    }

    /** Mini çizgi için 1..5 arası kaba puan. */
    fun moodScore(mood: JournalMood): Float = when (mood) {
        JournalMood.VERY_GOOD -> 5f
        JournalMood.HAPPY, JournalMood.EXCITED -> 4.5f
        JournalMood.GOOD -> 4f
        JournalMood.CALM -> 3.5f
        JournalMood.NEUTRAL -> 3f
        JournalMood.BORED, JournalMood.TIRED -> 2.5f
        JournalMood.LOW, JournalMood.HARD -> 2f
        JournalMood.STRESSED, JournalMood.SAD, JournalMood.ANGRY -> 1.5f
        JournalMood.BAD, JournalMood.FUCK_OFF -> 1f
    }

    fun lastMood(entries: List<MoodTrackerEntry>): JournalMood? =
        entries.maxByOrNull { it.details.loggedAt }?.mood

    fun yesterdayMood(entries: List<MoodTrackerEntry>, today: LocalDate, zone: ZoneId): JournalMood? {
        val yesterday = today.minusDays(1)
        return entries
            .filter { Instant.ofEpochMilli(it.details.loggedAt).atZone(zone).toLocalDate() == yesterday }
            .maxByOrNull { it.details.loggedAt }?.mood
    }

    fun moodPoints(entries: List<MoodTrackerEntry>): List<Float> =
        entries.sortedBy { it.details.loggedAt }.takeLast(7).map { moodScore(it.mood) }
}
