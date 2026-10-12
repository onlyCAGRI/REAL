package com.cagri.real

import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.shouldCaptureBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassStyleTest {
    @Test fun legacyPreferencesMigrateToLiquidGlass() {
        assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference("glass"))
        assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference("ice"))
        assertEquals("glass", DesignStyle.Glass.preferenceKey)
        assertEquals("ice", DesignStyle.Ice.preferenceKey)
    }

    @Test fun liquidPreferenceSurvivesRoundTrip() {
        assertEquals("liquid_glass", DesignStyle.LiquidGlass.preferenceKey)
        assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference(DesignStyle.Glass.preferenceKey))
        assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference(DesignStyle.LiquidGlass.preferenceKey))
        assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference(DesignStyle.Ice.preferenceKey))
    }

    @Test fun missingOrUnknownPreferenceUsesLiquidGlassDefault() {
        listOf(null, "", "future_style").forEach {
            assertEquals(DesignStyle.LiquidGlass, DesignStyle.fromPreference(it))
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
