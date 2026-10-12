package com.cagri.real.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * REAL Semantic Typography Tokens
 *
 * Grounded in current visual values to guarantee zero visual regressions.
 * All tokens define typography semantics (font size, weight, line height, letter spacing, font family)
 * while leaving component-level properties (colors, maxLines, alignments, gestures) to the calling UI.
 */
object RealType {
    // -------------------------------------------------------------
    // 1. Structural & Screen Hierarchy
    // -------------------------------------------------------------
    val displayHero = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = Color.Unspecified
    )

    val screenTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
        color = Color.Unspecified
    )

    val sectionHeader = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color = Color.Unspecified
    )

    val subSectionHeader = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 2. Card Typography
    // -------------------------------------------------------------
    val cardTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    )

    val cardTitleEmphasized = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    )

    val cardBody = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.Unspecified
    )

    val cardBodyProminent = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val cardMetaPrimary = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color.Unspecified
    )

    val cardMetaSecondary = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = Color.Unspecified
    )

    val cardSwipeAction = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 3. Input & Composer
    // -------------------------------------------------------------
    val composerInput = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val composerInputEmphasized = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val composerPrompt = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = Color.Unspecified
    )

    val composerVoicePrompt = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val composerChecklistItem = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.Unspecified
    )

    val composerChecklistPlaceholder = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val composerAction = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val composerActionSmall = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    val composerScheduleChip = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    val composerSuggestion = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    val composerStatus = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    val composerAuxiliaryPill = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    val composerAuxiliaryText = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    val composerSectionLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 4. Controls, Pills & Badges
    // -------------------------------------------------------------
    val actionLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = Color.Unspecified
    )

    val actionLabelSmall = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color.Unspecified
    )

    val pillLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = Color.Unspecified
    )

    val microBadge = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 5. Metrics & Numeric Display
    // -------------------------------------------------------------
    val metricGiant = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        color = Color.Unspecified
    )

    val metricLarge = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 6. Category Deck & Header
    // -------------------------------------------------------------
    val categoryTitleHero = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        color = Color.Unspecified
    )

    val categoryTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        color = Color.Unspecified
    )

    val categoryTitleMoods = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = Color.Unspecified
    )

    val categoryOpenIndicator = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        color = Color.Unspecified
    )

    val categoryPill = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val categoryShowAllAction = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val categorySelectionLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val categorySortMenuLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = Color.Unspecified
    )

    val categorySortMenuLabelSelected = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = Color.Unspecified
    )

    val categoryStatLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    val categoryStatValue = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = Color.Unspecified
    )

    val categoryProgressHeader = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        color = Color.Unspecified
    )

    val categoryProgressSubtitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val categoryProgressPercent = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        color = Color.Unspecified
    )

    val categoryProgressPercentSymbol = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 7. Detail & Rich Content Blocks
    // -------------------------------------------------------------
    val detailDialogTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = Color.Unspecified
    )

    val detailMusicTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    )

    val detailMediaTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val detailChecklistItemView = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = Color.Unspecified
    )

    val detailDialogSubtitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = Color.Unspecified
    )

    val detailChecklistItemEdit = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Color.Unspecified
    )

    val detailSnippetReadingBody = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    )

    val detailBlockHeader = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val detailBlockLabel = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val detailSnippetTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = Color.Unspecified
    )

    val detailSnippetBody = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Color.Unspecified
    )

    val detailBlockMeta = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val detailSnippetSource = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = Color.Unspecified
    )

    val detailBlockMetaSecondary = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = Color.Unspecified
    )

    val detailAttribution = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = Color.Unspecified
    )

    // -------------------------------------------------------------
    // 8. Shared Editorial Typography System
    // (Visual source of truth: PlannerNewDailyView / Editorial Spec)
    // -------------------------------------------------------------
    val editorialTitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 54.sp,
        lineHeight = 57.sp,
        letterSpacing = (-2.2).sp,
        color = Color(0xFFF3F1F4)
    )

    val editorialSection = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-1.0).sp,
        color = Color.Unspecified
    )

    val editorialItem = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-1.0).sp,
        color = Color.Unspecified
    )

    val editorialItemCompleted = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-1.0).sp,
        textDecoration = TextDecoration.LineThrough,
        color = Color(0xFF6F6F78)
    )

    val editorialSubtitle = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = Color(0xFF8C8C95)
    )

    val editorialMeta = TextStyle(
        fontFamily = RealFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp,
        color = Color(0xFF85858E)
    )

    // Direct semantic aliases
    val Title = editorialTitle
    val Section = editorialSection
    val Item = editorialItem
    val ItemCompleted = editorialItemCompleted
    val Subtitle = editorialSubtitle
    val Meta = editorialMeta

    val editorialSectionHeader = editorialSection
    val editorialItemTitle = editorialItem
    val editorialItemTitleCompleted = editorialItemCompleted
    val editorialMetaLabel = editorialMeta

    /**
     * Resolves an editorial title style scaled responsively for long titles.
     * Prevents clipping, overflow, or excessive line wraps while maintaining
     * the thin Normal weight and negative tracking identity.
     */
    fun responsiveEditorialTitle(text: String): TextStyle = when {
        text.length > 24 -> editorialTitle.copy(
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1.2).sp
        )
        text.length > 14 -> editorialTitle.copy(
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1.8).sp
        )
        else -> editorialTitle
    }
}
