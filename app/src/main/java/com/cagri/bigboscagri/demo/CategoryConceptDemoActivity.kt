package com.cagri.bigboscagri.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cagri.bigboscagri.ui.theme.BigBosTheme

/** Temporary, offline concept entry. No repository or production navigation dependencies. */
class CategoryConceptDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BigBosTheme { CategoryConceptDemoScreen(onClose = { finish() }) } }
    }
}
