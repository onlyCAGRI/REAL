package com.cagri.real.data

enum class MoodScope(val persistKey: String, val label: String) {
    MOMENT("moment", "Anlık his"),
    DAILY("daily", "Günün havası");

    companion object {
        fun fromKey(key: String?): MoodScope? = entries.firstOrNull { it.persistKey == key }
    }
}

enum class MoodEnergy(val persistKey: String, val label: String) {
    LOW("low", "Düşük"),
    BALANCED("balanced", "Dengeli"),
    HIGH("high", "Yüksek");

    companion object {
        fun fromKey(key: String?): MoodEnergy? = entries.firstOrNull { it.persistKey == key }
    }
}

data class MoodDetails(
    val scope: MoodScope = MoodScope.DAILY,
    val descriptors: List<String> = emptyList(),
    val energy: MoodEnergy? = null,
    val factors: List<String> = emptyList(),
    val loggedAt: Long
)
enum class JournalMood(val persistKey: String, val label: String) {
    VERY_GOOD("very_good", "Çok iyi"),
    GOOD("good", "İyi"),
    NEUTRAL("neutral", "Normal"),
    HARD("hard", "Zor"),
    BAD("bad", "Kötü"),
    HAPPY("happy", "Mutlu"),
    EXCITED("excited", "Heyecanlı"),
    CALM("calm", "Sakin"),
    TIRED("tired", "Yorgun"),
    BORED("bored", "Sıkılmış"),
    STRESSED("stressed", "Stresli"),
    ANGRY("angry", "Sinirli"),
    SAD("sad", "Üzgün"),
    LOW("low", "Keyifsiz"),
    FUCK_OFF("fuck_off", "F*ck off");

    companion object {
        fun fromKey(key: String?): JournalMood? = entries.firstOrNull { it.persistKey == key }
    }
}

