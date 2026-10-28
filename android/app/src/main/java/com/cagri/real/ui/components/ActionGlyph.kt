package com.cagri.real.ui.components

import com.cagri.real.data.JournalMood
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp

enum class ActionGlyph { Pin, Calendar, Back, Check, Undo, Edit, Share, Delete, Star, NowBar, Mic, Music, AudioWave, Play, Pause, Video, Cards, Close, Note, Category, Photo, Camera, File, Person, Cat, Add, Settings, Manage, Theme, BlurOff, MotionOff, ChevronRight, Repeat, Bell, BellOff, Minus, Checklist, DragHandle, Sort, LiquidGlass, WhatsApp, MoodVeryGood, MoodGood, MoodNeutral, MoodHard, MoodBad, MoodExcited, MoodCalm, MoodTired, MoodAngry, MoodFuckOff, Board, Paste, Phone, Compass, Binoculars, Poster, Sky }

fun journalMoodLabel(mood: JournalMood): String = mood.label

fun journalMoodGlyph(mood: JournalMood): ActionGlyph = when (mood) {
    JournalMood.VERY_GOOD, JournalMood.HAPPY -> ActionGlyph.MoodVeryGood
    JournalMood.GOOD -> ActionGlyph.MoodGood
    JournalMood.NEUTRAL, JournalMood.BORED -> ActionGlyph.MoodNeutral
    JournalMood.HARD, JournalMood.STRESSED, JournalMood.LOW -> ActionGlyph.MoodHard
    JournalMood.BAD, JournalMood.SAD -> ActionGlyph.MoodBad
    JournalMood.EXCITED -> ActionGlyph.MoodExcited
    JournalMood.CALM -> ActionGlyph.MoodCalm
    JournalMood.TIRED -> ActionGlyph.MoodTired
    JournalMood.ANGRY -> ActionGlyph.MoodAngry
    JournalMood.FUCK_OFF -> ActionGlyph.MoodFuckOff
}




@Composable
fun MoodGlyph(mood: JournalMood?, size: Dp, tint: Color, fallback: ActionGlyph = ActionGlyph.MoodNeutral) {
    ActionIcon(mood?.let(::journalMoodGlyph) ?: fallback, Modifier.size(size), tint)
}

@Composable
fun ActionIcon(glyph: ActionGlyph, modifier: Modifier, color: Color = Color.White, filled: Boolean = false) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.7f, cap = StrokeCap.Round)
            when (glyph) {
                ActionGlyph.Music -> {
                    line(9f, 17f, 9f, 5f)
                    line(9f, 5f, 20f, 3f)
                    line(20f, 3f, 20f, 15f)
                    drawOval(color, Offset(3f, 15f), Size(6f, 5f), style = stroke)
                    drawOval(color, Offset(14f, 13f), Size(6f, 5f), style = stroke)
                }
                ActionGlyph.Pin -> {
                    drawPath(Path().apply {
                        moveTo(12f, 22f); cubicTo(9f, 18f, 4.5f, 13f, 4.5f, 9f)
                        cubicTo(4.5f, 4.9f, 7.8f, 2f, 12f, 2f)
                        cubicTo(16.2f, 2f, 19.5f, 4.9f, 19.5f, 9f)
                        cubicTo(19.5f, 13f, 15f, 18f, 12f, 22f)
                        close()
                    }, color, style = stroke)
                    drawCircle(color, 2.5f, Offset(12f, 9f), style = stroke)
                }
                ActionGlyph.Calendar -> {
                    drawRoundRect(color, Offset(4f, 5f), Size(16f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); line(4f, 10f, 20f, 10f)
                    drawCircle(color, 1.2f, Offset(8f, 14f)); drawCircle(color, 1.2f, Offset(13f, 14f))
                }
                ActionGlyph.Back -> { line(15f, 4f, 7f, 12f); line(7f, 12f, 15f, 20f) }
                ActionGlyph.Check -> { line(4f, 12f, 9f, 17f); line(9f, 17f, 20f, 6f) }
                ActionGlyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                ActionGlyph.Phone -> drawPath(Path().apply {
                    moveTo(6.6f, 3f); lineTo(9.3f, 3f); lineTo(10.6f, 7.4f); lineTo(8.6f, 9.2f)
                    cubicTo(9.7f, 11.6f, 12.4f, 14.3f, 14.8f, 15.4f); lineTo(16.6f, 13.4f); lineTo(21f, 14.7f)
                    lineTo(21f, 17.4f); cubicTo(21f, 19.4f, 19.4f, 21f, 17.4f, 21f)
                    cubicTo(9.5f, 20.4f, 3.6f, 14.5f, 3f, 6.6f); cubicTo(3f, 4.6f, 4.6f, 3f, 6.6f, 3f); close()
                }, color, style = if (filled) Fill else stroke)
                ActionGlyph.Poster -> {
                    // A framed map: a winding road over a grid street, the title line underneath.
                    drawRoundRect(color, Offset(4.5f, 2.5f), Size(15f, 19f), CornerRadius(2.5f, 2.5f), style = stroke)
                    drawPath(Path().apply { moveTo(4.5f, 9f); cubicTo(9f, 6f, 11f, 13f, 15f, 9.5f); cubicTo(17f, 8f, 18.5f, 8.5f, 19.5f, 9f) }, color, style = stroke)
                    line(11f, 2.5f, 13f, 15f)
                    line(9f, 18f, 15f, 18f)
                }
                ActionGlyph.Sky -> {
                    // A star chart: the sky ring, a four-point star, two small stars.
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply {
                        moveTo(12f, 6.5f); quadraticBezierTo(12.6f, 11.4f, 17.5f, 12f); quadraticBezierTo(12.6f, 12.6f, 12f, 17.5f)
                        quadraticBezierTo(11.4f, 12.6f, 6.5f, 12f); quadraticBezierTo(11.4f, 11.4f, 12f, 6.5f); close()
                    }, color)
                    drawCircle(color, 0.9f, Offset(16.5f, 7.5f))
                    drawCircle(color, 0.7f, Offset(7.5f, 16.5f))
                }
                ActionGlyph.Compass -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply { moveTo(15.8f, 8.2f); lineTo(13.4f, 13.4f); lineTo(10.6f, 10.6f); close() }, color)
                    drawPath(Path().apply { moveTo(8.2f, 15.8f); lineTo(10.6f, 10.6f); lineTo(13.4f, 13.4f); close() }, color, style = Stroke(1.2f))
                }
                ActionGlyph.Binoculars -> {
                    drawCircle(color, 3.6f, Offset(6.6f, 15.6f), style = if (filled) Fill else stroke)
                    drawCircle(color, 3.6f, Offset(17.4f, 15.6f), style = if (filled) Fill else stroke)
                    drawPath(Path().apply { moveTo(3.2f, 14.6f); lineTo(5f, 6f); lineTo(9.4f, 6f); lineTo(10.2f, 14f) }, color, style = stroke)
                    drawPath(Path().apply { moveTo(20.8f, 14.6f); lineTo(19f, 6f); lineTo(14.6f, 6f); lineTo(13.8f, 14f) }, color, style = stroke)
                    line(10.2f, 11.5f, 13.8f, 11.5f)
                }
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
                ActionGlyph.NowBar -> {
                    drawRoundRect(
                        color,
                        Offset(3.5f, 7.5f),
                        Size(17f, 9f),
                        CornerRadius(4.5f, 4.5f),
                        style = stroke
                    )
                    drawRoundRect(
                        color,
                        Offset(7f, 10f),
                        Size(7f, 4f),
                        CornerRadius(2f, 2f),
                        style = if (filled) Fill else stroke
                    )
                    drawCircle(color, 1.15f, Offset(17.5f, 12f), style = if (filled) Fill else stroke)
                }
                ActionGlyph.Mic -> {
                    drawRoundRect(color, Offset(9f, 2f), Size(6f, 12f), CornerRadius(3f, 3f))
                    drawArc(color, 0f, 180f, false, Offset(5.5f, 6f), Size(13f, 12f), style = stroke)
                    line(12f, 18f, 12f, 22f)
                }
                ActionGlyph.AudioWave -> {
                    line(4f, 12f, 4f, 12f)
                    line(7f, 9f, 7f, 15f)
                    line(10f, 6f, 10f, 18f)
                    line(13f, 8f, 13f, 16f)
                    line(16f, 5f, 16f, 19f)
                    line(19f, 10f, 19f, 14f)
                }
                ActionGlyph.Play -> {
                    drawPath(Path().apply { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }, color, style = Fill)
                }
                ActionGlyph.Pause -> {
                    drawRoundRect(color, Offset(7f, 5f), Size(3.5f, 14f), CornerRadius(1f, 1f), style = Fill)
                    drawRoundRect(color, Offset(13.5f, 5f), Size(3.5f, 14f), CornerRadius(1f, 1f), style = Fill)
                }
                ActionGlyph.Video -> {
                    drawRoundRect(color, Offset(3f, 5f), Size(18f, 14f), CornerRadius(3f, 3f), style = stroke)
                    drawPath(Path().apply { moveTo(10f, 9f); lineTo(16f, 12f); lineTo(10f, 15f); close() }, color, style = Fill)
                }
                ActionGlyph.Cards -> {
                    // Two stacked overlapping cards
                    drawRoundRect(color, Offset(6.5f, 3.5f), Size(13f, 13f), CornerRadius(2.5f, 2.5f), style = stroke)
                    drawRoundRect(color, Offset(4.5f, 7.5f), Size(13f, 13f), CornerRadius(2.5f, 2.5f), style = stroke)
                }
                ActionGlyph.Note -> {
                    drawRoundRect(color, Offset(5f, 3f), Size(14f, 18f), CornerRadius(3f, 3f), style = stroke)
                    line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 12f, 16f)
                }
                ActionGlyph.Category -> {
                    // Tabler Icons: category (24x24, outline).
                    val tablerStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    drawRect(color, Offset(4f, 4f), Size(6f, 6f), style = tablerStroke)
                    drawRect(color, Offset(14f, 4f), Size(6f, 6f), style = tablerStroke)
                    drawRect(color, Offset(4f, 14f), Size(6f, 6f), style = tablerStroke)
                    drawCircle(color, radius = 3f, center = Offset(17f, 17f), style = tablerStroke)
                }
                ActionGlyph.Camera -> {
                    drawPath(Path().apply {
                        moveTo(3f, 7f); lineTo(7f, 7f); lineTo(9f, 4f); lineTo(15f, 4f)
                        lineTo(17f, 7f); lineTo(21f, 7f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                    }, color, style = stroke)
                    drawCircle(color, 4f, Offset(12f, 13f), style = stroke)
                }
                ActionGlyph.Photo -> {
                    // Tabler Icons: library-photo (24x24, outline).
                    val tablerStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    drawRoundRect(
                        color,
                        Offset(7f, 3f),
                        Size(14f, 14f),
                        CornerRadius(2.667f, 2.667f),
                        style = tablerStroke
                    )
                    drawPath(Path().apply {
                        moveTo(4.012f, 7.26f)
                        cubicTo(3.39f, 7.62f, 3f, 8.27f, 3f, 8.997f)
                        lineTo(3f, 18.997f)
                        cubicTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                        lineTo(15f, 21f)
                        cubicTo(15.75f, 21f, 16.158f, 20.615f, 16.5f, 20f)
                    }, color, style = tablerStroke)
                    drawCircle(color, radius = 0.9f, center = Offset(17f, 7f))
                    drawPath(Path().apply {
                        moveTo(7f, 13f)
                        lineTo(10.644f, 9.356f)
                        cubicTo(11.117f, 8.883f, 11.883f, 8.883f, 12.356f, 9.356f)
                        lineTo(16f, 13f)
                    }, color, style = tablerStroke)
                    drawPath(Path().apply {
                        moveTo(15f, 12f)
                        lineTo(16.644f, 10.356f)
                        cubicTo(17.117f, 9.883f, 17.883f, 9.883f, 18.356f, 10.356f)
                        lineTo(21f, 13f)
                    }, color, style = tablerStroke)
                }
                ActionGlyph.File -> {
                    // Tabler Icons: files (24x24, outline).
                    val tablerStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    drawPath(Path().apply {
                        moveTo(15f, 3f)
                        lineTo(15f, 7f)
                        cubicTo(15f, 7.55f, 15.45f, 8f, 16f, 8f)
                        lineTo(20f, 8f)
                    }, color, style = tablerStroke)
                    drawPath(Path().apply {
                        moveTo(18f, 17f)
                        lineTo(11f, 17f)
                        cubicTo(9.9f, 17f, 9f, 16.1f, 9f, 15f)
                        lineTo(9f, 5f)
                        cubicTo(9f, 3.9f, 9.9f, 3f, 11f, 3f)
                        lineTo(15f, 3f)
                        lineTo(20f, 8f)
                        lineTo(20f, 15f)
                        cubicTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
                    }, color, style = tablerStroke)
                    drawPath(Path().apply {
                        moveTo(16f, 17f)
                        lineTo(16f, 19f)
                        cubicTo(16f, 20.1f, 15.1f, 21f, 14f, 21f)
                        lineTo(7f, 21f)
                        cubicTo(5.9f, 21f, 5f, 20.1f, 5f, 19f)
                        lineTo(5f, 9f)
                        cubicTo(5f, 7.9f, 5.9f, 7f, 7f, 7f)
                        lineTo(9f, 7f)
                    }, color, style = tablerStroke)
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
                ActionGlyph.Cat -> {
                    drawPath(
                        Path().apply {
                            moveTo(6f, 8f)
                            lineTo(5f, 3.5f)
                            lineTo(9f, 6f)
                            cubicTo(10f, 5.5f, 11f, 5.2f, 12f, 5.2f)
                            cubicTo(13f, 5.2f, 14f, 5.5f, 15f, 6f)
                            lineTo(19f, 3.5f)
                            lineTo(18f, 8f)
                            cubicTo(19f, 9.2f, 19.5f, 10.8f, 19.5f, 12.5f)
                            cubicTo(19.5f, 16.8f, 16.2f, 20f, 12f, 20f)
                            cubicTo(7.8f, 20f, 4.5f, 16.8f, 4.5f, 12.5f)
                            cubicTo(4.5f, 10.8f, 5f, 9.2f, 6f, 8f)
                            close()
                        },
                        color,
                        style = stroke
                    )
                    drawCircle(color, radius = 0.9f, center = Offset(9f, 12f))
                    drawCircle(color, radius = 0.9f, center = Offset(15f, 12f))
                    drawPath(
                        Path().apply {
                            moveTo(10.3f, 15f)
                            quadraticTo(12f, 16.2f, 13.7f, 15f)
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
                    drawCircle(color, radius = 4.2f, center = Offset(12f, 12f), style = stroke)
                    val angles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
                    for (deg in angles) {
                        val rad = Math.toRadians(deg.toDouble())
                        val cos = Math.cos(rad).toFloat()
                        val sin = Math.sin(rad).toFloat()
                        val start = Offset(12f + 6.8f * cos, 12f + 6.8f * sin)
                        val end = Offset(12f + 9.5f * cos, 12f + 9.5f * sin)
                        drawLine(color, start, end, strokeWidth = 2f, cap = StrokeCap.Round)
                    }
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
                    drawPath(Path().apply {
                        moveTo(4f, 12f)
                        cubicTo(7f, 6.8f, 17f, 6.8f, 20f, 12f)
                        cubicTo(17f, 17.2f, 7f, 17.2f, 4f, 12f)
                        close()
                    }, color, style = stroke)
                    drawCircle(color, radius = 2.6f, center = Offset(12f, 12f), style = stroke)
                    drawLine(color, Offset(4f, 4f), Offset(20f, 20f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                ActionGlyph.LiquidGlass -> {
                    drawPath(Path().apply {
                        moveTo(9f, 5f)
                        cubicTo(9f, 9f, 10f, 13f, 15f, 13f)
                        cubicTo(10f, 13f, 9f, 17f, 9f, 21f)
                        cubicTo(9f, 17f, 8f, 13f, 3f, 13f)
                        cubicTo(8f, 13f, 9f, 9f, 9f, 5f)
                        close()
                    }, color, style = Fill)
                    drawPath(Path().apply {
                        moveTo(17.5f, 4f)
                        cubicTo(17.5f, 6.2f, 18.2f, 7.8f, 21f, 7.8f)
                        cubicTo(18.2f, 7.8f, 17.5f, 9.4f, 17.5f, 11.6f)
                        cubicTo(17.5f, 9.4f, 16.8f, 7.8f, 14f, 7.8f)
                        cubicTo(16.8f, 7.8f, 17.5f, 6.2f, 17.5f, 4f)
                        close()
                    }, color, style = Fill)
                }
                ActionGlyph.ChevronRight -> {
                    line(9f, 6f, 15f, 12f)
                    line(15f, 12f, 9f, 18f)
                }
                ActionGlyph.Repeat -> {
                    
                    line(6f, 7f, 18f, 7f)
                    line(15f, 4f, 18f, 7f)
                    line(15f, 10f, 18f, 7f)
                    drawArc(color, 270f, 90f, false, Offset(14f, 7f), Size(4f, 4f), style = stroke)
                    
                    line(18f, 17f, 6f, 17f)
                    line(9f, 14f, 6f, 17f)
                    line(9f, 20f, 6f, 17f)
                    drawArc(color, 90f, 90f, false, Offset(6f, 13f), Size(4f, 4f), style = stroke)
                }
                ActionGlyph.Bell -> {
                    drawPath(Path().apply {
                        moveTo(12f, 3.5f)
                        lineTo(12f, 5f)
                        moveTo(12f, 5f)
                        cubicTo(9f, 5.5f, 6.5f, 8f, 6.5f, 12f)
                        lineTo(5f, 16.5f)
                        lineTo(19f, 16.5f)
                        lineTo(17.5f, 12f)
                        cubicTo(17.5f, 8f, 15f, 5.5f, 12f, 5f)
                    }, color, style = stroke)
                    drawArc(color, 0f, 180f, false, Offset(10f, 17.5f), Size(4f, 3.5f), style = stroke)
                }
                ActionGlyph.BellOff -> {
                    drawPath(Path().apply {
                        moveTo(12f, 3.5f)
                        lineTo(12f, 5f)
                        moveTo(12f, 5f)
                        cubicTo(9f, 5.5f, 6.5f, 8f, 6.5f, 12f)
                        lineTo(5f, 16.5f)
                        lineTo(19f, 16.5f)
                        lineTo(17.5f, 12f)
                        cubicTo(17.5f, 8f, 15f, 5.5f, 12f, 5f)
                    }, color, style = stroke)
                    drawArc(color, 0f, 180f, false, Offset(10f, 17.5f), Size(4f, 3.5f), style = stroke)
                    line(4.5f, 4.5f, 19.5f, 19.5f)
                }
                ActionGlyph.Minus -> {
                    line(5f, 12f, 19f, 12f)
                }
                ActionGlyph.Checklist -> {
                    drawRoundRect(color, Offset(4f, 5.5f), Size(4.5f, 4.5f), CornerRadius(1.2f, 1.2f), style = stroke)
                    line(11.5f, 7.75f, 20f, 7.75f)

                    drawRoundRect(color, Offset(4f, 11.5f), Size(4.5f, 4.5f), CornerRadius(1.2f, 1.2f), style = stroke)
                    line(11.5f, 13.75f, 20f, 13.75f)

                    drawRoundRect(color, Offset(4f, 17.5f), Size(4.5f, 4.5f), CornerRadius(1.2f, 1.2f), style = stroke)
                    line(11.5f, 19.75f, 20f, 19.75f)
                }
                ActionGlyph.DragHandle -> {
                    line(5f, 7.5f, 19f, 7.5f)
                    line(5f, 12f, 19f, 12f)
                    line(5f, 16.5f, 19f, 16.5f)
                }
                ActionGlyph.Paste -> {
                    // Tabler Icons: clipboard (24x24, outline).
                    val tablerStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    drawRoundRect(color, Offset(5f, 5f), Size(14f, 16f), CornerRadius(2f, 2f), style = tablerStroke)
                    drawRoundRect(color, Offset(9f, 3f), Size(6f, 4f), CornerRadius(1.5f, 1.5f), style = tablerStroke)
                    line(9f, 12f, 15f, 12f); line(9f, 16f, 13f, 16f)
                }
                ActionGlyph.Board -> {
                    drawRoundRect(color, Offset(3f, 3f), Size(18f, 18f), CornerRadius(3f, 3f), style = stroke)
                    drawRoundRect(color, Offset(6f, 6f), Size(5f, 7f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(color, Offset(14f, 6f), Size(4f, 4f), CornerRadius(1f, 1f), style = stroke)
                    line(14f, 14f, 18f, 14f)
                    line(6f, 17f, 13f, 17f)
                }
                ActionGlyph.Sort -> {
                    // Tabler Icons: arrows-sort (24x24, outline).
                    line(3f, 9f, 7f, 5f)
                    line(7f, 5f, 11f, 9f)
                    line(7f, 5f, 7f, 19f)
                    line(13f, 15f, 17f, 19f)
                    line(17f, 19f, 21f, 15f)
                    line(17f, 5f, 17f, 19f)
                }
                ActionGlyph.WhatsApp -> {
                    
                    val bubblePath = Path().apply {
                        moveTo(12f, 3.5f)
                        cubicTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                        cubicTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                        cubicTo(10.4f, 20.5f, 8.9f, 20.0f, 7.6f, 19.2f)
                        lineTo(3.5f, 20.5f)
                        lineTo(4.8f, 16.5f)
                        cubicTo(3.9f, 15.2f, 3.5f, 13.6f, 3.5f, 12f)
                        cubicTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
                        close()
                    }
                    drawPath(bubblePath, color, style = stroke)

                    
                    val phonePath = Path().apply {
                        moveTo(8.5f, 8.8f)
                        cubicTo(8.7f, 8.4f, 9.1f, 8.2f, 9.5f, 8.3f)
                        lineTo(10.7f, 9.2f)
                        cubicTo(11.0f, 9.4f, 11.1f, 9.8f, 10.9f, 10.1f)
                        lineTo(10.3f, 10.8f)
                        cubicTo(10.2f, 10.9f, 10.2f, 11.1f, 10.3f, 11.3f)
                        cubicTo(10.8f, 12.2f, 11.8f, 13.2f, 12.7f, 13.7f)
                        cubicTo(12.9f, 13.8f, 13.1f, 13.8f, 13.2f, 13.7f)
                        lineTo(13.9f, 13.1f)
                        cubicTo(14.2f, 12.9f, 14.6f, 13.0f, 14.8f, 13.3f)
                        lineTo(15.7f, 14.5f)
                        cubicTo(15.8f, 14.9f, 15.6f, 15.3f, 15.2f, 15.5f)
                        lineTo(14.3f, 15.9f)
                        cubicTo(13.8f, 16.1f, 13.2f, 16.0f, 12.5f, 15.6f)
                        cubicTo(10.7f, 14.5f, 9.5f, 13.3f, 8.4f, 11.5f)
                        cubicTo(8.0f, 10.8f, 7.9f, 10.2f, 8.1f, 9.7f)
                        close()
                    }
                    drawPath(phonePath, color, style = Fill)
                }
                ActionGlyph.MoodVeryGood -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawArc(color, 15f, 150f, false, Offset(6.5f, 5.5f), Size(11f, 11f), style = stroke)
                    line(8f, 9.5f, 8.1f, 9.5f); line(16f, 9.5f, 16.1f, 9.5f)
                }
                ActionGlyph.MoodGood -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawArc(color, 30f, 120f, false, Offset(7f, 6.5f), Size(10f, 10f), style = stroke)
                    line(8f, 9.5f, 8.1f, 9.5f); line(16f, 9.5f, 16.1f, 9.5f)
                }
                ActionGlyph.MoodNeutral -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(8f, 15f, 16f, 15f)
                    line(8f, 9.5f, 8.1f, 9.5f); line(16f, 9.5f, 16.1f, 9.5f)
                }
                ActionGlyph.MoodHard -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawArc(color, 210f, 120f, false, Offset(7f, 10.5f), Size(10f, 10f), style = stroke)
                    line(8f, 9.5f, 8.1f, 9.5f); line(16f, 9.5f, 16.1f, 9.5f)
                }
                ActionGlyph.MoodBad -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawArc(color, 195f, 150f, false, Offset(6.5f, 11.5f), Size(11f, 11f), style = stroke)
                    line(8f, 9.5f, 8.1f, 9.5f); line(16f, 9.5f, 16.1f, 9.5f)
                }
                ActionGlyph.MoodExcited -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(7f, 8.5f, 9f, 9.5f); line(9f, 9.5f, 7f, 10.5f)
                    line(15f, 9.5f, 17f, 8.5f); line(15f, 9.5f, 17f, 10.5f)
                    drawOval(color, Offset(10f, 13f), Size(4f, 5f), style = stroke)
                }
                ActionGlyph.MoodCalm -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(6.5f, 9.5f, 9.5f, 9.5f); line(14.5f, 9.5f, 17.5f, 9.5f)
                    drawArc(color, 30f, 120f, false, Offset(8f, 10f), Size(8f, 7f), style = stroke)
                }
                ActionGlyph.MoodTired -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(6.5f, 10f, 9.5f, 10.5f); line(14.5f, 10.5f, 17.5f, 10f)
                    drawOval(color, Offset(10.5f, 13f), Size(3f, 4f), style = stroke)
                }
                ActionGlyph.MoodAngry -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(6.5f, 7.5f, 9.5f, 9f); line(14.5f, 9f, 17.5f, 7.5f)
                    line(8f, 10.5f, 9f, 10.5f); line(15f, 10.5f, 16f, 10.5f)
                    drawArc(color, 210f, 120f, false, Offset(8f, 13f), Size(8f, 7f), style = stroke)
                }
                ActionGlyph.MoodFuckOff -> {
                    // Tabler Icons: hand-middle-finger (24x24, outline).
                    val tablerStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    drawPath(
                        Path().apply {
                            // Index finger
                            moveTo(8f, 13f)
                            lineTo(8f, 10.5f)
                            cubicTo(8f, 9.677f, 8.677f, 9f, 9.5f, 9f)
                            cubicTo(10.323f, 9f, 11f, 9.677f, 11f, 10.5f)
                            lineTo(11f, 12f)

                            // Middle finger (extended)
                            moveTo(11f, 11.5f)
                            lineTo(11f, 3.5f)
                            cubicTo(11f, 2.677f, 11.677f, 2f, 12.5f, 2f)
                            cubicTo(13.323f, 2f, 14f, 2.677f, 14f, 3.5f)
                            lineTo(14f, 12f)

                            // Ring finger
                            moveTo(14f, 10.5f)
                            cubicTo(14f, 9.677f, 14.677f, 9f, 15.5f, 9f)
                            cubicTo(16.323f, 9f, 17f, 9.677f, 17f, 10.5f)
                            lineTo(17f, 12f)

                            // Little finger, palm, wrist & thumb
                            moveTo(17f, 11.5f)
                            cubicTo(17f, 10.677f, 17.677f, 10f, 18.5f, 10f)
                            cubicTo(19.323f, 10f, 20f, 10.677f, 20f, 11.5f)
                            lineTo(20f, 16f)
                            cubicTo(20f, 19.314f, 17.314f, 22f, 14f, 22f)
                            lineTo(12f, 22f)
                            cubicTo(10.192f, 22f, 8.305f, 20.984f, 7.196f, 19.3f)
                            cubicTo(7.13f, 19.2f, 7.065f, 19.1f, 7f, 19f)
                            cubicTo(6.688f, 18.521f, 5.593f, 16.612f, 3.714f, 13.272f)
                            cubicTo(3.318f, 12.568f, 3.558f, 11.665f, 4.25f, 11.25f)
                            cubicTo(4.982f, 10.811f, 5.926f, 10.927f, 6.53f, 11.53f)
                            lineTo(8f, 13f)
                        },
                        color,
                        style = tablerStroke
                    )
                }
            }
        }
    }
}
