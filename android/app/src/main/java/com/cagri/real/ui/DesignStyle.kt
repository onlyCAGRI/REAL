package com.cagri.real.ui

/**
 * REAL now exposes a single visual language: Liquid Glass.
 *
 * Legacy enum values stay only for source compatibility with older internal
 * branches; persisted preferences are always migrated to LiquidGlass.
 */
enum class DesignStyle(val preferenceKey: String) {
    Glass("glass"),
    Ice("ice"),
    LiquidGlass("liquid_glass");

    companion object {
        fun fromPreference(value: String?): DesignStyle = LiquidGlass
    }
}
