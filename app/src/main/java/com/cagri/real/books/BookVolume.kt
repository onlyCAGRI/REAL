package com.cagri.real.books

import android.graphics.Paint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A projected front cover and an illustrated spine; no extra image, AI call or running animation. */
@Composable
internal fun BookVolume(book: Book, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val density = LocalDensity.current
    val colors = listOf(Color(0xFF394E5A), Color(0xFF765846), Color(0xFF4D6152), Color(0xFF695367))
    val base = colors[(book.id.hashCode().toLong() and 0x7fffffffL).toInt() % colors.size]
    val paint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.sp.toPx() }
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        }
    }
    val spineTitle = remember(book.title, density) {
        // TextUtils uses a TextPaint; measure once instead of doing it during every draw.
        TextUtils.ellipsize(book.title, android.text.TextPaint(paint), with(density) { 114.dp.toPx() }, TextUtils.TruncateAt.END).toString()
    }
    Box(modifier.clickable(onClick = onClick).semantics { contentDescription = "${book.title}, ${book.author}" }) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 116f
            fun p(value: Float) = value * unit
            val spine = Path().apply {
                moveTo(p(7f), p(19f)); lineTo(p(25f), p(8f))
                lineTo(p(25f), p(158f)); lineTo(p(7f), p(166f)); close()
            }
            drawPath(spine, Brush.horizontalGradient(listOf(base.copy(alpha = .78f), base, Color.Black.copy(alpha = .75f))))
            val top = Path().apply {
                moveTo(p(7f), p(19f)); lineTo(p(25f), p(8f))
                lineTo(p(97f), p(32f)); lineTo(p(79f), p(43f)); close()
            }
            drawPath(top, Color(0xFFD4D0C7))
            drawLine(Color.White.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(p(8f), p(21f)),
                androidx.compose.ui.geometry.Offset(p(8f), p(164f)), p(.7f))
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.translate(p(13f), p(31f))
            canvas.rotate(90f)
            canvas.drawText(spineTitle, 0f, 0f, paint)
            canvas.restore()
        }
        // A projective quadrilateral transforms the ACTUAL cover, not only its side decoration.
        // Left edge meets the spine; right edge recedes and the top/bottom edges visibly slope.
        Box(Modifier.offset(x = 25.dp, y = 8.dp).width(86.dp).height(150.dp).drawWithCache {
            val w = size.width; val h = size.height
            val matrix = android.graphics.Matrix().apply {
                setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0,
                    floatArrayOf(0f, 0f, w * .84f, h * .16f, w * .84f, h * .94f, 0f, h), 0, 4)
            }
            onDrawWithContent {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                try { canvas.concat(matrix); drawContent() } finally { canvas.restore() }
            }
        }) { BookCover(book, Modifier.fillMaxSize(), titleSize = 12.sp) }
    }
}
