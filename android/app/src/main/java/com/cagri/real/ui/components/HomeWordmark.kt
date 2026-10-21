package com.cagri.real.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cagri.real.R

/** Home branding: the welcome screen's wordmark at header size (947×412 artwork, so 30dp tall ≈ 69dp wide). */
@Composable
fun HomeWordmark(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    contentDescription: String = "REAL"
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Image(
            painter = painterResource(R.drawable.real_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(30.dp)
                .width(69.dp)
                .semantics { this.contentDescription = contentDescription }
        )
    }
}
