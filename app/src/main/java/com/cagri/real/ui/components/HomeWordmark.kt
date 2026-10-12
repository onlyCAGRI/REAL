package com.cagri.real.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Static home branding. Launcher and welcome artwork stay independent. */
@Composable
fun HomeWordmark(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    contentDescription: String = "REAL"
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            "REAL", color = Color(0xFFF3F2F5), fontSize = 30.sp,
            fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp,
            maxLines = 1,
            modifier = Modifier.semantics { this.contentDescription = contentDescription }
        )
    }
}
