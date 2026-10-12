package com.cagri.real.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cagri.real.ui.DesignStyle


val LocalDesignStyle = staticCompositionLocalOf { DesignStyle.LiquidGlass }
val LocalLiquidReduceMotion = staticCompositionLocalOf { false }
internal val LocalInsideLiquidGlass = staticCompositionLocalOf { false }

enum class LiquidGlassStrength { Regular, Clear }


enum class LiquidGlassRole { Chrome, Sheet, Dock }

object LiquidGlassTokens {
    val capsuleRadius = 32.dp
    val contentRadius = 24.dp
    val contentColor = Color(0xFF111113)
    val fallbackTop = Color(0xFF202022)
    val fallbackBottom = Color(0xFF101012)
    val regularTint = Color(0xA00C0C0E)
    val clearTint = Color(0x660C0C0E)
    val sheetTint = Color(0xD10C0C0E)
    val staticTop = Color(0xB5202022)
    val staticBottom = Color(0xCC0D0D0F)
    val selectedTop = Color(0xB82B2B2D)
    val selectedBottom = Color(0xCC141416)
    val selectionAccent = Color(0xFFD2DEE1)
    val contentEdgeTop = Color(0x26FFFFFF)
    val contentEdgeBottom = Color(0x0AFFFFFF)
    val atmospherePeak = Color(0xFF101213)
    val sheetBlur = 9.dp
    val topBlur = 18.dp
    val topExtension = 40.dp
    val edgeTop = Color(0x62FFFFFF)
    val edgeMiddle = Color(0x0AFFFFFF)
    val edgeBottom = Color(0x2AFFFFFF)
    val edgeWidth = 0.65.dp
    val shadow = 9.dp
    val regularBlur = 5.dp
    val clearBlur = 1.dp
    val lensWidth = 18.dp
    val refraction = 7.dp
    val samplePadding = 24.dp
    const val sampleScale = 0.5f
    const val dispersion = 0.025f
    const val pressScale = 0.986f
    const val pressMillis = 140
    const val settleMillis = 200

    // Dock: clearer, more refractive glass for the home navigation pieces (calendar, composer, mic, tab bar).
    // dockRefraction must stay below samplePadding so the lens never samples outside the recorded area.
    val dockTintTop = Color(0x330C0C0E)
    val dockTintBottom = Color(0x480C0C0E)
    val dockBlur = 2.dp
    val dockLensWidth = 22.dp
    val dockRefraction = 12.dp
    const val dockDispersion = 0.05f
    val dockEdgeWidth = 0.9.dp
    val dockEdgeTop = Color(0xA6FFFFFF)
    val dockEdgeBottom = Color(0x66FFFFFF)
    val dockSelectedPill = Color(0x73000000)
}


fun shouldCaptureBackdrop(style: DesignStyle, reduceDynamicBlur: Boolean, sdk: Int): Boolean =
    !reduceDynamicBlur && sdk >= if (style == DesignStyle.LiquidGlass) 33 else 31
