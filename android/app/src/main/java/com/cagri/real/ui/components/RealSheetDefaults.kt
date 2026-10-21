package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One look for every Material bottom sheet: same ink, corners, scrim and handle as the account/journal sheets. */
object RealSheetDefaults {
    val Ink = Color(0xFF0B0B0D)
    val Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Scrim = Color.Black.copy(alpha = 0.6f)

    @Composable
    fun DragHandle() {
        Box(
            Modifier.padding(top = 12.dp, bottom = 4.dp).size(36.dp, 4.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
        )
    }
}
