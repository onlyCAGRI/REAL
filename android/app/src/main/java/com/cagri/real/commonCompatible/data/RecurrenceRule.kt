package com.cagri.real.data

enum class RecurrenceRule(val storageKey: String, val displayName: String) {
    NONE("none", "Tekrarlama"),
    DAILY("daily", "Her gün"),
    WEEKLY("weekly", "Her hafta"),
    MONTHLY("monthly", "Her ay"),
    YEARLY("yearly", "Her yıl");

    companion object {
        fun fromStorage(key: String?): RecurrenceRule =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: NONE
    }
}

