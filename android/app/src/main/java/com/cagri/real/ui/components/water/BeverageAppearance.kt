package com.cagri.real.ui.components.water

import androidx.compose.ui.graphics.Color
import com.cagri.real.data.BeverageType

internal data class BeverageAppearance(
    val background: List<Color>, val liquid: List<Color>, val edge: Color,
    val bubble: Color, val accent: Color, val secondaryText: Color
)

internal fun BeverageType.appearance(): BeverageAppearance = when (this) {
    BeverageType.WATER -> BeverageAppearance(
        listOf(Color(0xFF00384E), Color(0xFF00647D), Color(0xFF315766)),
        listOf(Color(0xFF00384E), Color(0xFF00647D), Color(0xFF315766)),
        Color(0xFFACF8F5), Color(0xFFD1FBFF), Color(0xFFE4ED54), Color(0xFFB8CDCF))
    BeverageType.COFFEE -> BeverageAppearance(
        listOf(Color(0xFF1C100B), Color(0xFF2D1B12), Color(0xFF180F0B)),
        listOf(Color(0xFFA36A3B), Color(0xFF724020), Color(0xFF482616), Color(0xFF29170F)),
        Color(0xFFE5BB84), Color(0xFFEBC69D), Color(0xFFD9B17F), Color(0xFFD4B99E))
    BeverageType.BEER -> BeverageAppearance(
        listOf(Color(0xFF211B0D), Color(0xFF302814), Color(0xFF1A170E)),
        listOf(Color(0xFFF6D16A), Color(0xFFE6B443), Color(0xFFC8982C), Color(0xFF9C7122)),
        Color(0xFFFFF1BF), Color(0xFFFFF4CE), Color(0xFFF1D991), Color(0xFFDED2AE))
}
