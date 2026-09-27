package com.cagri.bigboscagri.ui

enum class DesignStyle(val preferenceKey: String) {
    Glass("glass"),
    Ice("ice"),
    LiquidGlass("liquid_glass");

    companion object {
        fun fromPreference(value: String?): DesignStyle = when (value) {
            LiquidGlass.preferenceKey -> LiquidGlass
            // Ice is no longer user-selectable; keep old installs safe by migrating it to Focus/Glass.
            Ice.preferenceKey, Glass.preferenceKey -> Glass
            else -> Glass
        }
    }
}
