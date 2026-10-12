package com.cagri.real.data

enum class ReminderCategory(val title: String, val emptyMessage: String) {
    Today("24 hour", "Önündeki 24 saatte bir REAL yok"),
    Planned("Planlı", "Henüz planlanan hatırlatıcı yok"),
    Important("Önemli", "Yıldız eklediğin hatırlatıcılar burada görünür"),
    NoAlert("Uyarı yok", "Henüz hatırlatıcı yok"),
    Completed("Tamamlananlar", "Tamamladığın hatırlatıcılar burada görünür"),
    JournalEntries("Günlüklerim", "Henüz günlük eklenmemiş")
}

