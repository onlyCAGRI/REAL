package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cagri.real.ui.LocalDetailHeroInset

/** True inside the full-bleed cover at the top of a card ("En üstteki içeriği büyüt"). */
@Composable
internal fun isDetailHero(): Boolean = LocalDetailHeroInset.current > 0.dp

/**
 * Catalog block as the card's cover ("En üstteki içeriği büyüt"): the poster / artwork on top over its own blurred
 * light, reaching up under the status bar (the back / star buttons float over the top corners), then the title, the
 * details and [content] (controls, "…'de aç" with the source at the bottom right).
 */
@Composable
internal fun CatalogHeroCover(
    imageUrl: String?,
    posterAspect: Float,
    title: String,
    subtitle: String?,
    placeholder: ActionGlyph,
    ambience: Color,
    onAmbience: (Color) -> Unit = {},
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val inset = LocalDetailHeroInset.current
    val total = com.cagri.real.ui.LocalCardBlockHeight.current
    val base = if (ambience.alpha > 0f) lerp(Color(0xFF111114), ambience, 0.34f) else Color(0xFF16161A)
    val request = remember(imageUrl, context) { ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build() }
    Column(
        Modifier.fillMaxWidth()
            .then(if (total > 0.dp) Modifier.heightIn(min = total) else Modifier)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(base, Color(0xFF0D0D0F))))
    ) {
        // The picture gets most of the cover (the text below adds its own height), never a thumbnail.
        Box(Modifier.fillMaxWidth().height(if (total > 0.dp) maxOf(total * 0.66f, 340.dp) else 340.dp)) {
            // The same picture, large and soft, lights the cover behind the poster.
            AsyncImage(imageUrl, null, Modifier.fillMaxSize().blur(36.dp).graphicsLayer { alpha = 0.55f }, contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f), 0.55f to Color.Transparent, 1f to base)))
            Box(
                Modifier.align(Alignment.Center)
                    .padding(top = inset + 8.dp, bottom = 6.dp, start = 32.dp, end = 32.dp)
                    .fillMaxHeight()
                    .aspectRatio(posterAspect, matchHeightConstraintsFirst = true)
                    .shadow(22.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF26262C)),
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(placeholder, Modifier.size(34.dp), Color(0xFFB8B8C2))
                AsyncImage(request, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onSuccess = { result -> onAmbience(averageColor(result.result.drawable)) })
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color(0xFFF4F4F6), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color(0xFFB8B8C2), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color(0xFF8E8E98), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** A tiny sample of an already-loaded picture; no second request or palette dependency. */
internal fun averageColor(drawable: android.graphics.drawable.Drawable): Color = runCatching {
    val bitmap = drawable.toBitmap(12, 12)
    var red = 0f; var green = 0f; var blue = 0f; var weight = 0f
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
        val pixel = bitmap.getPixel(x, y)
        val a = android.graphics.Color.alpha(pixel) / 255f
        red += android.graphics.Color.red(pixel) * a
        green += android.graphics.Color.green(pixel) * a
        blue += android.graphics.Color.blue(pixel) * a
        weight += a
    }
    if (weight > 0f) Color(red / weight / 255f, green / weight / 255f, blue / weight / 255f) else Color.Transparent
}.getOrDefault(Color.Transparent)

/** "…'de aç" as a wide pill with the source quietly at the bottom right. */
@Composable
internal fun HeroOpenRow(label: String, onOpen: () -> Unit, source: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Color(0xFFE2E2E8), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.10f))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
                .padding(vertical = 13.dp))
        source()
    }
}
