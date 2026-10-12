package com.cagri.real.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

enum class CategoryIcon(val key: String, val label: String) {
    WORK("WORK", "İş"),
    HOME("HOME", "Ev"),
    SHOPPING("SHOPPING", "Alışveriş"),
    HEALTH("HEALTH", "Sağlık"),
    SPORT("SPORT", "Spor"),
    MONEY("MONEY", "Finans"),
    SCHOOL("SCHOOL", "Eğitim"),
    TRAVEL("TRAVEL", "Seyahat"),
    FAMILY("FAMILY", "Aile"),
    PET("PET", "Evcil hayvan"),
    FOOD("FOOD", "Yemek"),
    IDEA("IDEA", "Fikir"),
    HEART("HEART", "Favori"),
    STAR("STAR", "Yıldız"),
    FILE("FILE", "Belge"),
    LOCATION("LOCATION", "Konum"),
    BOOKMARK("BOOKMARK", "Kaydedilen"),
    GIFT("GIFT", "Hediye");

    companion object {
        val selectableEntries: List<CategoryIcon>
            get() = entries.filterNot { it == PET || it == GIFT } + listOf(PET, GIFT)

        fun fromKey(key: String): CategoryIcon {
            
            return when {
                key.equals("CAR", ignoreCase = true) -> PET
                key.equals("GAME", ignoreCase = true) -> GIFT
                else -> entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WORK
            }
        }
    }
}

@Composable
fun CategoryIconGlyph(
    icon: CategoryIcon,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
            when (icon) {
                CategoryIcon.WORK -> {
                    drawRoundRect(color, Offset(3.5f, 7.5f), Size(17f, 12.5f), CornerRadius(2.5f, 2.5f), style = stroke)
                    drawRoundRect(color, Offset(8.5f, 4f), Size(7f, 3.5f), CornerRadius(1.5f, 1.5f), style = stroke)
                    line(3.5f, 12f, 20.5f, 12f)
                    line(11f, 12f, 13f, 12f)
                }
                CategoryIcon.HOME -> {
                    val roof = Path().apply {
                        moveTo(3.5f, 11f)
                        lineTo(12f, 4f)
                        lineTo(20.5f, 11f)
                    }
                    drawPath(roof, color, style = stroke)
                    val house = Path().apply {
                        moveTo(5.5f, 10.5f)
                        lineTo(5.5f, 20f)
                        lineTo(18.5f, 20f)
                        lineTo(18.5f, 10.5f)
                    }
                    drawPath(house, color, style = stroke)
                    val door = Path().apply {
                        moveTo(9.5f, 20f)
                        lineTo(9.5f, 14.5f)
                        lineTo(14.5f, 14.5f)
                        lineTo(14.5f, 20f)
                    }
                    drawPath(door, color, style = stroke)
                }
                CategoryIcon.SHOPPING -> {
                    drawCircle(color, 1.3f, Offset(8.5f, 19.5f))
                    drawCircle(color, 1.3f, Offset(17.5f, 19.5f))
                    val basket = Path().apply {
                        moveTo(3.5f, 4.5f)
                        lineTo(6.5f, 4.5f)
                        lineTo(8.5f, 15.5f)
                        lineTo(18.5f, 15.5f)
                        lineTo(20.5f, 7.5f)
                        lineTo(7f, 7.5f)
                    }
                    drawPath(basket, color, style = stroke)
                }
                CategoryIcon.HEALTH -> {
                    
                    val pharmacyCross = Path().apply {
                        moveTo(9f, 3.5f)
                        lineTo(15f, 3.5f)
                        lineTo(15f, 9f)
                        lineTo(20.5f, 9f)
                        lineTo(20.5f, 15f)
                        lineTo(15f, 15f)
                        lineTo(15f, 20.5f)
                        lineTo(9f, 20.5f)
                        lineTo(9f, 15f)
                        lineTo(3.5f, 15f)
                        lineTo(3.5f, 9f)
                        lineTo(9f, 9f)
                        close()
                    }
                    drawPath(pharmacyCross, color, style = stroke)
                }
                CategoryIcon.SPORT -> {
                    line(6.5f, 12f, 17.5f, 12f)
                    line(6.5f, 8.5f, 6.5f, 15.5f)
                    line(4f, 9.5f, 4f, 14.5f)
                    line(17.5f, 8.5f, 17.5f, 15.5f)
                    line(20f, 9.5f, 20f, 14.5f)
                }
                CategoryIcon.MONEY -> {
                    line(12f, 3.5f, 12f, 20.5f)
                    val money = Path().apply {
                        moveTo(16.8f, 6.5f)
                        cubicTo(15.4f, 5.2f, 13.8f, 4.7f, 12f, 4.7f)
                        cubicTo(8.6f, 4.7f, 6.8f, 6.4f, 6.8f, 8.7f)
                        cubicTo(6.8f, 11.3f, 8.9f, 12.1f, 12.2f, 12.7f)
                        cubicTo(15.5f, 13.3f, 17.2f, 14.2f, 17.2f, 16.5f)
                        cubicTo(17.2f, 18.9f, 15.3f, 20.1f, 12f, 20.1f)
                        cubicTo(9.8f, 20.1f, 7.9f, 19.4f, 6.4f, 17.9f)
                    }
                    drawPath(money, color, style = stroke)
                }
                CategoryIcon.SCHOOL -> {
                    val book = Path().apply {
                        moveTo(12f, 6.5f)
                        cubicTo(9.5f, 5f, 5.5f, 5f, 3.5f, 6f)
                        lineTo(3.5f, 18.5f)
                        cubicTo(5.5f, 17.5f, 9.5f, 17.5f, 12f, 19f)
                        cubicTo(14.5f, 17.5f, 18.5f, 17.5f, 20.5f, 18.5f)
                        lineTo(20.5f, 6f)
                        cubicTo(18.5f, 5f, 14.5f, 5f, 12f, 6.5f)
                    }
                    drawPath(book, color, style = stroke)
                    line(12f, 6.5f, 12f, 19f)
                }
                CategoryIcon.TRAVEL -> {
                    drawRoundRect(color, Offset(5.5f, 7.5f), Size(13f, 13f), CornerRadius(2.5f, 2.5f), style = stroke)
                    drawRoundRect(color, Offset(9.5f, 4f), Size(5f, 3.5f), CornerRadius(1.2f, 1.2f), style = stroke)
                    line(9.5f, 7.5f, 9.5f, 20.5f)
                    line(14.5f, 7.5f, 14.5f, 20.5f)
                }
                CategoryIcon.FAMILY -> {
                    drawCircle(color, 3.2f, Offset(12f, 7f), style = stroke)
                    val body = Path().apply {
                        moveTo(5.5f, 19.5f)
                        cubicTo(5.5f, 15f, 8.5f, 13.5f, 12f, 13.5f)
                        cubicTo(15.5f, 13.5f, 18.5f, 15f, 18.5f, 19.5f)
                    }
                    drawPath(body, color, style = stroke)
                }
                CategoryIcon.PET -> {
                    
                    drawOval(
                        color = color,
                        topLeft = Offset(8.15f, 3.6f),
                        size = Size(3.4f, 5.0f),
                        style = stroke
                    )
                    drawOval(
                        color = color,
                        topLeft = Offset(12.45f, 3.6f),
                        size = Size(3.4f, 5.0f),
                        style = stroke
                    )
                    drawOval(
                        color = color,
                        topLeft = Offset(3.25f, 8.1f),
                        size = Size(3.4f, 5.0f),
                        style = stroke
                    )
                    drawOval(
                        color = color,
                        topLeft = Offset(17.35f, 8.1f),
                        size = Size(3.4f, 5.0f),
                        style = stroke
                    )
                    val pawPad = Path().apply {
                        moveTo(14.7f, 13.5f)
                        cubicTo(13.6f, 11.5f, 13.25f, 11f, 12f, 11f)
                        cubicTo(10.7f, 11f, 10.25f, 11.75f, 9.16f, 13.75f)
                        cubicTo(8.2f, 15.45f, 5.85f, 15.6f, 5.7f, 18f)
                        cubicTo(5.7f, 19.2f, 6.5f, 20f, 7.5f, 20f)
                        cubicTo(8.75f, 20f, 10.5f, 19f, 12f, 19f)
                        cubicTo(13.5f, 19f, 15.25f, 20f, 16.5f, 20f)
                        cubicTo(17.5f, 20f, 18.3f, 19.2f, 18.3f, 18f)
                        cubicTo(18.15f, 15.6f, 15.65f, 15.45f, 14.7f, 13.5f)
                    }
                    drawPath(pawPad, color, style = stroke)
                }
                CategoryIcon.FOOD -> {
                    val cup = Path().apply {
                        moveTo(5f, 7.5f)
                        lineTo(16f, 7.5f)
                        lineTo(15f, 15.5f)
                        cubicTo(15f, 17.5f, 6f, 17.5f, 6f, 15.5f)
                        close()
                    }
                    drawPath(cup, color, style = stroke)
                    val handle = Path().apply {
                        moveTo(15.5f, 9.5f)
                        cubicTo(18.5f, 9.5f, 18.5f, 14f, 14.5f, 14f)
                    }
                    drawPath(handle, color, style = stroke)
                    line(4f, 20f, 17f, 20f)
                }
                CategoryIcon.IDEA -> {
                    val bulb = Path().apply {
                        moveTo(8.5f, 14.5f)
                        cubicTo(6f, 12f, 6f, 7.5f, 12f, 5.5f)
                        cubicTo(18f, 7.5f, 18f, 12f, 15.5f, 14.5f)
                    }
                    drawPath(bulb, color, style = stroke)
                    line(9.5f, 17f, 14.5f, 17f)
                    line(10.5f, 19.5f, 13.5f, 19.5f)
                }
                CategoryIcon.HEART -> {
                    val heart = Path().apply {
                        moveTo(12f, 19.5f)
                        cubicTo(6.5f, 15f, 4f, 11f, 4f, 8f)
                        cubicTo(4f, 5f, 6.5f, 4f, 9f, 4.5f)
                        cubicTo(10.5f, 5.2f, 11.5f, 6.5f, 12f, 7.5f)
                        cubicTo(12.5f, 6.5f, 13.5f, 5.2f, 15f, 4.5f)
                        cubicTo(17.5f, 4f, 20f, 5f, 20f, 8f)
                        cubicTo(20f, 11f, 17.5f, 15f, 12f, 19.5f)
                        close()
                    }
                    drawPath(heart, color, style = stroke)
                }
                CategoryIcon.STAR -> {
                    val star = Path().apply {
                        moveTo(12f, 2.5f)
                        lineTo(14.8f, 8.5f)
                        lineTo(21.5f, 9.4f)
                        lineTo(16.6f, 14.2f)
                        lineTo(17.8f, 20.9f)
                        lineTo(12f, 17.7f)
                        lineTo(6.2f, 20.9f)
                        lineTo(7.4f, 14.2f)
                        lineTo(2.5f, 9.4f)
                        lineTo(9.2f, 8.5f)
                        close()
                    }
                    drawPath(star, color, style = stroke)
                }
                CategoryIcon.FILE -> {
                    val doc = Path().apply {
                        moveTo(6f, 3.5f)
                        lineTo(14f, 3.5f)
                        lineTo(18.5f, 8f)
                        lineTo(18.5f, 20.5f)
                        lineTo(6f, 20.5f)
                        close()
                    }
                    drawPath(doc, color, style = stroke)
                    line(14f, 3.5f, 14f, 8f)
                    line(14f, 8f, 18.5f, 8f)
                    line(9f, 12f, 15.5f, 12f)
                    line(9f, 16f, 13.5f, 16f)
                }
                CategoryIcon.LOCATION -> {
                    val pin = Path().apply {
                        moveTo(12f, 21f)
                        cubicTo(10.1f, 18.7f, 5.5f, 14.1f, 5.5f, 9.5f)
                        cubicTo(5.5f, 5.8f, 8.4f, 3f, 12f, 3f)
                        cubicTo(15.6f, 3f, 18.5f, 5.8f, 18.5f, 9.5f)
                        cubicTo(18.5f, 14.1f, 13.9f, 18.7f, 12f, 21f)
                        close()
                    }
                    drawPath(pin, color, style = stroke)
                    drawCircle(color, 2.2f, Offset(12f, 9.5f), style = stroke)
                }
                CategoryIcon.BOOKMARK -> {
                    val bookmark = Path().apply {
                        moveTo(7f, 3.5f)
                        lineTo(17f, 3.5f)
                        lineTo(17f, 20.5f)
                        lineTo(12f, 17f)
                        lineTo(7f, 20.5f)
                        close()
                    }
                    drawPath(bookmark, color, style = stroke)
                }
                CategoryIcon.GIFT -> {
                    
                    drawRoundRect(
                        color,
                        Offset(3f, 8f),
                        Size(18f, 4f),
                        CornerRadius(1.2f, 1.2f),
                        style = stroke
                    )
                    val giftBody = Path().apply {
                        moveTo(5f, 12f)
                        lineTo(5f, 19f)
                        cubicTo(5f, 20.1f, 5.9f, 21f, 7f, 21f)
                        lineTo(17f, 21f)
                        cubicTo(18.1f, 21f, 19f, 20.1f, 19f, 19f)
                        lineTo(19f, 12f)
                    }
                    drawPath(giftBody, color, style = stroke)
                    line(12f, 8f, 12f, 21f)
                    val leftBow = Path().apply {
                        moveTo(12f, 8f)
                        cubicTo(10.6f, 5f, 9.1f, 3f, 7.5f, 3f)
                        cubicTo(5.9f, 3f, 5f, 4.1f, 5f, 5.5f)
                        cubicTo(5f, 7f, 6.1f, 8f, 7.5f, 8f)
                        close()
                    }
                    drawPath(leftBow, color, style = stroke)
                    val rightBow = Path().apply {
                        moveTo(12f, 8f)
                        cubicTo(13.4f, 5f, 14.9f, 3f, 16.5f, 3f)
                        cubicTo(18.1f, 3f, 19f, 4.1f, 19f, 5.5f)
                        cubicTo(19f, 7f, 17.9f, 8f, 16.5f, 8f)
                        close()
                    }
                    drawPath(rightBow, color, style = stroke)
                }
            }
        }
    }
}
