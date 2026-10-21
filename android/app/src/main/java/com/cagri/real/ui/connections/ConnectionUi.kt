package com.cagri.real.ui.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kişi listesi, davet ve davet kabul ekranlarının ortak koyu "cam" görünümü. */
internal object ConnectionPalette {
    val Ink = Color(0xFF0B0B0D)
    val Glass = Color.White.copy(alpha = 0.06f)
    val Stroke = Color.White.copy(alpha = 0.10f)
    val Muted = Color(0xFF9A9AA2)
    val Accent = Color(0xFF6FD6FF)
    val Danger = Color(0xFFFF7A70)
    private val avatarTones = listOf(
        Color(0xFF6FD6FF), Color(0xFFFFB86F), Color(0xFFB59BFF), Color(0xFF7BE0A4), Color(0xFFFF8FB1), Color(0xFFF2E27A),
    )
    fun toneFor(key: String): Color = avatarTones[(key.hashCode().toLong() and 0x7fffffffL).toInt() % avatarTones.size]
}

/** İsmin baş harfi, kişiye özgü yumuşak renkte yuvarlak avatar; kişi profil resmini paylaştıysa ([pictureVersion]) o resim. */
@Composable
internal fun ConnectionAvatar(name: String, key: String, size: Dp = 44.dp, pictureVersion: Long? = null) {
    val tone = ConnectionPalette.toneFor(key)
    val context = androidx.compose.ui.platform.LocalContext.current
    val picture by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, key, pictureVersion) {
        value = pictureVersion?.let { com.cagri.real.platform.android.connections.ProfilePictures.load(context, key, it) }
    }
    Box(Modifier.size(size).clip(CircleShape)
        .background(Brush.linearGradient(listOf(tone.copy(alpha = .38f), tone.copy(alpha = .14f))))
        .border(1.dp, tone.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
        val bitmap = picture
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Text(name.trim().firstOrNull()?.uppercase() ?: "R", color = Color.White,
            fontSize = (size.value * .42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Dolu (beyaz) ya da cam hap düğme. */
@Composable
internal fun ConnectionPill(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    filled: Boolean = false, danger: Boolean = false, small: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val fg = when { filled -> Color.Black; danger -> ConnectionPalette.Danger; else -> Color.White }
    Box(
        modifier.alpha(if (enabled) 1f else .4f).heightIn(min = if (small) 36.dp else 48.dp).clip(shape)
            .background(if (filled) Color.White else ConnectionPalette.Glass)
            .then(if (filled) Modifier else Modifier.border(BorderStroke(1.dp, ConnectionPalette.Stroke), shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 22.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Cam yüzey: liste satırları ve paneller için. */
@Composable
internal fun ConnectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(ConnectionPalette.Glass)
        .border(1.dp, ConnectionPalette.Stroke, shape).padding(16.dp), content = content)
}

/** Durum / hata satırı. */
@Composable
internal fun ConnectionNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .05f))
        .padding(horizontal = 14.dp, vertical = 10.dp), color = ConnectionPalette.Muted, fontSize = 13.sp, lineHeight = 18.sp)
}
