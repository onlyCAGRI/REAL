package com.cagri.real.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Kapak görseli yokken kapağın ve sırtın ortak zemin rengi (kitap kimliğinden türetilir). */
internal fun coverBaseColor(book: Book): Color {
    val colors = listOf(Color(0xFF394E5A), Color(0xFF765846), Color(0xFF4D6152), Color(0xFF695367))
    return colors[(book.id.hashCode().toLong() and 0x7fffffffL).toInt() % colors.size]
}

/** Front cover; metadata-only books still have a deliberate, readable cover. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, titleSize: TextUnit = 18.sp) {
    val base = coverBaseColor(book)
    Box(modifier.width(126.dp).height(190.dp).shadow(7.dp, RoundedCornerShape(2.dp))
        .clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(base, base.copy(alpha = .65f))))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Column(Modifier.fillMaxSize().padding(if (titleSize < 16.sp) 8.dp else 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(book.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                fontSize = titleSize, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = Color.White.copy(alpha = .7f), fontSize = if (titleSize < 16.sp) 8.sp else 10.sp, maxLines = 2)
        }
        if (!book.coverUrl.isNullOrBlank()) AsyncImage(book.coverUrl, book.title,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp)
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent))))
    }
}
