package com.cagri.bigboscagri

import com.cagri.bigboscagri.ui.DesignStyle
import com.cagri.bigboscagri.ui.components.shouldCaptureBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassStyleTest {
    @Test fun existingPreferencesKeepTheirMeaning() {
        assertEquals(DesignStyle.Glass, DesignStyle.fromPreference("glass"))
        assertEquals(DesignStyle.Ice, DesignStyle.fromPreference("ice"))
        assertEquals("glass", DesignStyle.Glass.preferenceKey)
        assertEquals("ice", DesignStyle.Ice.preferenceKey)
    }

    @Test fun liquidPreferenceSurvivesRoundTrip() {
        assertEquals("liquid_glass", DesignStyle.LiquidGlass.preferenceKey)
        DesignStyle.entries.forEach { assertEquals(it, DesignStyle.fromPreference(it.preferenceKey)) }
    }

    @Test fun missingOrUnknownPreferenceUsesOriginalDefault() {
        listOf(null, "", "future_style").forEach {
            assertEquals(DesignStyle.Glass, DesignStyle.fromPreference(it))
        }
    }

    @Test fun reducedBlurDisablesEveryStyleCapture() {
        DesignStyle.entries.forEach { style ->
            listOf(26, 31, 33, 36).forEach { sdk -> assertFalse(shouldCaptureBackdrop(style, true, sdk)) }
        }
    }

    @Test fun liquidRequiresRuntimeShaderButOriginalBlurKeepsApi31Support() {
        assertFalse(shouldCaptureBackdrop(DesignStyle.LiquidGlass, false, 32))
        assertTrue(shouldCaptureBackdrop(DesignStyle.LiquidGlass, false, 33))
        listOf(DesignStyle.Glass, DesignStyle.Ice).forEach {
            assertFalse(shouldCaptureBackdrop(it, false, 30))
            assertTrue(shouldCaptureBackdrop(it, false, 31))
        }
    }
}
