package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

enum class ActionGlyph { Calendar, Back, Check, Undo, Edit, Share, Delete, Star, Mic, Close, Note, Person, Add, Settings, Manage, Theme, BlurOff, MotionOff }

@Composable
fun ActionIcon(glyph: ActionGlyph, modifier: Modifier, color: Color = Color.White, filled: Boolean = false) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.7f, cap = StrokeCap.Round)
            when (glyph) {
                ActionGlyph.Calendar -> {
                    drawRoundRect(color, Offset(4f, 5f), Size(16f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); line(4f, 10f, 20f, 10f)
                    drawCircle(color, 1.2f, Offset(8f, 14f)); drawCircle(color, 1.2f, Offset(13f, 14f))
                }
                ActionGlyph.Back -> { line(15f, 4f, 7f, 12f); line(7f, 12f, 15f, 20f) }
                ActionGlyph.Check -> { line(4f, 12f, 9f, 17f); line(9f, 17f, 20f, 6f) }
                ActionGlyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                ActionGlyph.Undo -> {
                    line(3f, 5f, 3f, 11f); line(3f, 11f, 9f, 11f)
                    drawPath(Path().apply { moveTo(3f, 11f); cubicTo(7f, 2f, 20f, 4f, 20f, 13f); cubicTo(20f, 18f, 15f, 21f, 10f, 19f) }, color, style = stroke)
                }
                ActionGlyph.Edit -> {
                    drawPath(Path().apply {
                        moveTo(4f, 15f); lineTo(15f, 4f); lineTo(20f, 9f); lineTo(9f, 20f)
                        lineTo(3f, 21f); close()
                    }, color, style = stroke)
                    line(12.5f, 6.5f, 17.5f, 11.5f)
                }
                ActionGlyph.Share -> {
                    line(6f, 12f, 18f, 5f); line(6f, 12f, 18f, 19f)
                    listOf(Offset(6f, 12f), Offset(18f, 5f), Offset(18f, 19f)).forEach { drawCircle(color, 2.7f, it) }
                }
                ActionGlyph.Delete -> {
                    line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
                    drawPath(Path().apply { moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f) }, color, style = stroke)
                    line(10f, 10f, 10f, 17f); line(14f, 10f, 14f, 17f)
                }
                ActionGlyph.Star -> {
                    drawPath(Path().apply {
                        moveTo(12f, 2f); lineTo(15f, 8.4f); lineTo(22f, 9.4f); lineTo(17f, 14.4f)
                        lineTo(18.2f, 21.4f); lineTo(12f, 18.1f); lineTo(5.8f, 21.4f)
                        lineTo(7f, 14.4f); lineTo(2f, 9.4f); lineTo(9f, 8.4f); close()
                    }, color, style = if (filled) Fill else stroke)
                }
                ActionGlyph.Mic -> {
                    drawRoundRect(color, Offset(9f, 2f), Size(6f, 12f), CornerRadius(3f, 3f))
                    drawArc(color, 0f, 180f, false, Offset(5.5f, 6f), Size(13f, 12f), style = stroke)
                    line(12f, 18f, 12f, 22f)
                }
                ActionGlyph.Note -> {
                    drawRoundRect(color, Offset(5f, 3f), Size(14f, 18f), CornerRadius(3f, 3f), style = stroke)
                    line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 12f, 16f)
                }
                ActionGlyph.Person -> {
                    drawCircle(color, radius = 3.2f, center = Offset(12f, 7.5f), style = if (filled) Fill else stroke)
                    drawPath(
                        Path().apply {
                            moveTo(5.2f, 19.5f)
                            cubicTo(5.8f, 14.8f, 8.6f, 12.8f, 12f, 12.8f)
                            cubicTo(15.4f, 12.8f, 18.2f, 14.8f, 18.8f, 19.5f)
                        },
                        color,
                        style = stroke
                    )
                }
                ActionGlyph.Add -> {
                    line(12f, 5f, 12f, 19f)
                    line(5f, 12f, 19f, 12f)
                }
                ActionGlyph.Settings -> {
                    for (index in 0 until 6) {
                        rotate(index * 60f, Offset(12f, 12f)) {
                            drawRoundRect(color, topLeft = Offset(11f, 1.9f), size = Size(2f, 4.2f), cornerRadius = CornerRadius(1f, 1f))
                        }
                    }
                    drawCircle(color, radius = 6.1f, center = Offset(12f, 12f), style = stroke)
                    drawCircle(color, radius = 2.3f, center = Offset(12f, 12f), style = stroke)
                }
                ActionGlyph.Manage -> {
                    drawCircle(color, radius = 2.6f, center = Offset(9f, 8f), style = stroke)
                    drawCircle(color, radius = 2.2f, center = Offset(15.8f, 9.5f), style = stroke)
                    drawPath(
                        Path().apply {
                            moveTo(4.8f, 18.8f)
                            cubicTo(5.4f, 14.9f, 7.7f, 13.3f, 10.2f, 13.3f)
                            cubicTo(12.2f, 13.3f, 13.9f, 14.3f, 15f, 16.6f)
                        },
                        color,
                        style = stroke
                    )
                    drawPath(
                        Path().apply {
                            moveTo(12.8f, 18.6f)
                            cubicTo(13.3f, 16.2f, 14.8f, 14.8f, 17f, 14.8f)
                            cubicTo(18.8f, 14.8f, 20.1f, 15.8f, 20.6f, 18.6f)
                        },
                        color,
                        style = stroke
                    )
                }
                ActionGlyph.Theme -> {
                    drawCircle(color, radius = 7.2f, center = Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply {
                        moveTo(12f, 4.8f); cubicTo(16.2f, 6.2f, 17.8f, 9.6f, 17.8f, 12f)
                        cubicTo(17.8f, 15.4f, 15.4f, 18.2f, 12f, 19.2f); close()
                    }, color, style = Fill)
                }
                ActionGlyph.BlurOff -> {
                    listOf(
                        Offset(6f, 7f), Offset(10f, 5f), Offset(14f, 7f),
                        Offset(8f, 11f), Offset(12f, 10f), Offset(16f, 11f),
                        Offset(6f, 15f), Offset(10f, 16f), Offset(14f, 15f), Offset(18f, 16f)
                    ).forEach { drawCircle(color, 1.05f, it) }
                    drawLine(color, Offset(4f, 4f), Offset(20f, 20f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                ActionGlyph.MotionOff -> {
                    drawCircle(color, radius = 7f, center = Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply {
                        moveTo(8.2f, 12.2f)
                        cubicTo(9.6f, 9.4f, 12.2f, 8.2f, 15.8f, 9.1f)
                        moveTo(8.7f, 15.5f)
                        cubicTo(11.5f, 16.9f, 14.6f, 16.1f, 16.4f, 13.8f)
                    }, color, style = stroke)
                    drawLine(color, Offset(4f, 4f), Offset(20f, 20f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
