package com.cagri.bigboscagri.ui

enum class DesignStyle(val preferenceKey: String) {
    Glass("glass"),
    Ice("ice"),
    LiquidGlass("liquid_glass");

    companion object {
        fun fromPreference(value: String?): DesignStyle =
            entries.firstOrNull { it.preferenceKey == value } ?: Glass
    }
}
