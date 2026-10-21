package com.cagri.real

import com.cagri.real.ui.components.LiquidGlassRole
import com.cagri.real.ui.components.LiquidGlassTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidDockGlassTest {
    @Test fun dockLensNeverSamplesOutsideThePaddedBackdrop() {
        assertTrue(LiquidGlassTokens.dockRefraction < LiquidGlassTokens.samplePadding)
        assertTrue(LiquidGlassTokens.dockLensWidth.value > 0f)
    }

    @Test fun dockGlassIsClearerThanRegularGlass() {
        assertTrue(LiquidGlassTokens.dockTintTop.alpha < LiquidGlassTokens.regularTint.alpha)
        assertTrue(LiquidGlassTokens.dockTintBottom.alpha < LiquidGlassTokens.regularTint.alpha)
    }

    @Test fun dockRoleExists() {
        assertTrue(LiquidGlassRole.entries.contains(LiquidGlassRole.Dock))
    }
}
