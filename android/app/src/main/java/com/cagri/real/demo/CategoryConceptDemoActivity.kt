package com.cagri.real.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import com.cagri.real.ui.theme.RealTheme

/** Temporary, offline concept entry. No repository or production navigation dependencies. */
class CategoryConceptDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RealTheme {
                CompositionLocalProvider(LocalLiquidReduceMotion provides intent.getBooleanExtra("reduce_motion", false)) {
                    CategoryConceptDemoScreen(onClose = { finish() }, reduceBlur = intent.getBooleanExtra("reduce_blur", false))
                }
            }
        }
    }
}
