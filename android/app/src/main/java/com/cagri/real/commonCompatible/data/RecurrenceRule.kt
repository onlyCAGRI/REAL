package com.cagri.real.data

import com.cagri.real.i18n.t

enum class RecurrenceRule(val storageKey: String, private val trDisplayName: String) {
    NONE("none", "Tekrarlama"),
    DAILY("daily", "Her gün"),
    WEEKLY("weekly", "Her hafta"),
    MONTHLY("monthly", "Her ay"),
    YEARLY("yearly", "Her yıl");

    val displayName: String get() = t(trDisplayName)

    companion object {
        fun fromStorage(key: String?): RecurrenceRule =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: NONE
    }
}

