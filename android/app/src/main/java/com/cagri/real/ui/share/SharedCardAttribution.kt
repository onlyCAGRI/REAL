package com.cagri.real.ui.share

import com.cagri.real.i18n.t
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SharedCardAttribution(sender: String?) {
    if (!sender.isNullOrBlank()) Text(
        text = t("Sent by {0}", sender), color = Color(0xFF99999F), fontSize = 11.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
    )
}
