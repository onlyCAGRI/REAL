package com.cagri.real.auth

import com.cagri.real.i18n.t

/** Firebase and the Worker use one canonical display identity (maximum 80 characters). */
fun profileDisplayName(firstName: String, lastName: String = ""): String {
    fun normalize(value: String) = value.trim().replace(Regex("\\s+"), " ")
    val first = normalize(firstName)
    val last = normalize(lastName)
    require(first.isNotBlank()) { t("Adınızı yazın.") }
    val name = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
    require(name.length <= 80) { t("Ad ve soyad toplamı en fazla 80 karakter olmalı.") }
    require(name.none { it.isISOControl() }) { t("Adınız geçersiz karakter içeriyor.") }
    return name
}
