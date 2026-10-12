package com.cagri.real.ui.components.planner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.theme.RealFont

/**
 * Planner Feature-Local Semantic Typography Tokens (Level 2).
 *
 * Scoped to stable, non-geometry-bound Planner calendar roles.
 * Layout-sensitive, Canvas-measured, dynamic, and optical typography
 * remains component-owned (Level 3).
 */
internal object PlannerType {
    val monthTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = Color.Unspecified
    )

    val weekdayLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    val selectedDayHeader = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = Color.Unspecified
    )

    val eventListTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val eventListTime = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        color = Color.Unspecified
    )

    val legendLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // Editor Typography
    // -------------------------------------------------------------
    val pickerValue = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        color = Color.Unspecified
    )

    val editorSupportingText = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    val peopleInput = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val peoplePlaceholder = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Unspecified
    )
}
