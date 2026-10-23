package com.cagri.real.data

import com.cagri.real.i18n.t

enum class ReminderCategory(val title: String, private val trEmptyMessage: String) {
    Today("24 hour", "Önündeki 24 saatte bir REAL yok"),
    Planned("Planned", "Henüz planlanan hatırlatıcı yok"),
    Important("Important", "Yıldız eklediğin hatırlatıcılar burada görünür"),
    NoAlert("No alert", "Henüz hatırlatıcı yok"),
    Completed("Completed", "Tamamladığın hatırlatıcılar burada görünür"),
    JournalEntries("Journal", "Henüz günlük eklenmemiş");

    val emptyMessage: String get() = t(trEmptyMessage)
}

