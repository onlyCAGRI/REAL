package com.cagri.real.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.theme.RealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RealEditorialTypographyTest {

    @Test
    fun editorialTitleConformsToSpec() {
        assertEquals(54.sp, RealType.editorialTitle.fontSize)
        assertEquals(57.sp, RealType.editorialTitle.lineHeight)
        assertEquals((-2.2).sp, RealType.editorialTitle.letterSpacing)
        assertEquals(FontWeight.Normal, RealType.editorialTitle.fontWeight)
        assertEquals(Color(0xFFF3F1F4), RealType.editorialTitle.color)
        assertNotEquals(FontWeight.Bold, RealType.editorialTitle.fontWeight)
        assertNotEquals(FontWeight.SemiBold, RealType.editorialTitle.fontWeight)
    }

    @Test
    fun editorialSectionConformsToSpec() {
        assertEquals(30.sp, RealType.editorialSection.fontSize)
        assertEquals(32.sp, RealType.editorialSection.lineHeight)
        assertEquals((-1.0).sp, RealType.editorialSection.letterSpacing)
        assertEquals(FontWeight.Normal, RealType.editorialSection.fontWeight)
    }

    @Test
    fun editorialItemAndCompletedConformToSpec() {
        assertEquals(30.sp, RealType.editorialItem.fontSize)
        assertEquals(32.sp, RealType.editorialItem.lineHeight)
        assertEquals((-1.0).sp, RealType.editorialItem.letterSpacing)
        assertEquals(FontWeight.Normal, RealType.editorialItem.fontWeight)

        // Completed item preserves 30sp size, has LineThrough and muted gray color
        assertEquals(30.sp, RealType.editorialItemCompleted.fontSize)
        assertEquals(32.sp, RealType.editorialItemCompleted.lineHeight)
        assertEquals((-1.0).sp, RealType.editorialItemCompleted.letterSpacing)
        assertEquals(FontWeight.Normal, RealType.editorialItemCompleted.fontWeight)
        assertEquals(TextDecoration.LineThrough, RealType.editorialItemCompleted.textDecoration)
        assertEquals(Color(0xFF6F6F78), RealType.editorialItemCompleted.color)
    }

    @Test
    fun editorialSubtitleConformsToSpec() {
        assertEquals(14.sp, RealType.editorialSubtitle.fontSize)
        assertEquals(18.sp, RealType.editorialSubtitle.lineHeight)
        assertEquals(Color(0xFF8C8C95), RealType.editorialSubtitle.color)
        assertEquals(FontWeight.Normal, RealType.editorialSubtitle.fontWeight)
    }

    @Test
    fun editorialMetaConformsToSpec() {
        assertEquals(10.sp, RealType.editorialMeta.fontSize)
        assertEquals(14.sp, RealType.editorialMeta.lineHeight)
        assertEquals(1.0.sp, RealType.editorialMeta.letterSpacing)
        assertEquals(Color(0xFF85858E), RealType.editorialMeta.color)
        assertEquals(FontWeight.Normal, RealType.editorialMeta.fontWeight)
    }

    @Test
    fun aliasesMatchUnderlyingTokens() {
        assertEquals(RealType.editorialTitle, RealType.Title)
        assertEquals(RealType.editorialSection, RealType.Section)
        assertEquals(RealType.editorialSection, RealType.editorialSectionHeader)
        assertEquals(RealType.editorialItem, RealType.Item)
        assertEquals(RealType.editorialItem, RealType.editorialItemTitle)
        assertEquals(RealType.editorialItemCompleted, RealType.ItemCompleted)
        assertEquals(RealType.editorialItemCompleted, RealType.editorialItemTitleCompleted)
        assertEquals(RealType.editorialSubtitle, RealType.Subtitle)
        assertEquals(RealType.editorialMeta, RealType.Meta)
        assertEquals(RealType.editorialMeta, RealType.editorialMetaLabel)
    }

    @Test
    fun responsiveTitleScalingPreservesNormalWeightAndNegativeTracking() {
        val shortTitleStyle = RealType.responsiveEditorialTitle("Personal")
        assertEquals(54.sp, shortTitleStyle.fontSize)
        assertEquals(FontWeight.Normal, shortTitleStyle.fontWeight)

        val mediumTitleStyle = RealType.responsiveEditorialTitle("Work & Projects")
        assertEquals(44.sp, mediumTitleStyle.fontSize)
        assertEquals(FontWeight.Normal, mediumTitleStyle.fontWeight)
        assertEquals((-1.8).sp, mediumTitleStyle.letterSpacing)

        val longTitleStyle = RealType.responsiveEditorialTitle("Software Architecture & Engineering")
        assertEquals(36.sp, longTitleStyle.fontSize)
        assertEquals(FontWeight.Normal, longTitleStyle.fontWeight)
        assertEquals((-1.2).sp, longTitleStyle.letterSpacing)
        assertNotEquals(FontWeight.Bold, longTitleStyle.fontWeight)
    }
}
