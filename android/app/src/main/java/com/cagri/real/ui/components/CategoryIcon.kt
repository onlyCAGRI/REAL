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

/** Ayarlar → "Sade kartlar" (deneysel): Home cards without the circle; a right swipe completes them. Device-local. */
object HomeCardStyle {
    private val state = androidx.compose.runtime.mutableIntStateOf(-1)
    private fun prefs(context: android.content.Context) = com.cagri.real.platform.android.sync.SyncLocal.preferences(
        context.applicationContext, "real_home_cards", android.content.Context.MODE_PRIVATE)
    fun isPlain(context: android.content.Context): Boolean {
        if (state.intValue < 0) state.intValue = if (prefs(context).getBoolean("plain", false)) 1 else 0
        return state.intValue == 1
    }
    fun setPlain(context: android.content.Context, plain: Boolean) {
        prefs(context).edit().putBoolean("plain", plain).apply()
        state.intValue = if (plain) 1 else 0
    }
}

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
    GIFT("GIFT", "Hediye"),
    BIRTHDAY("BIRTHDAY", "Doğum günü"),
    RACING("RACING", "Formula"),
    RACING_SIDE("RACING_SIDE", "Formula yandan"),
    VEHICLE("VEHICLE", "Araba"),
    BALL("BALL", "Maç"),
    MUSIC("MUSIC", "Müzik"),
    MOVIE("MOVIE", "Film ve dizi"),
    GAMING("GAMING", "Oyun"),
    BOOK("BOOK", "Kitap"),
    MEDICINE("MEDICINE", "İlaç"),
    PLANT("PLANT", "Bitki"),
    CODE("CODE", "Yazılım"),
    CAMERA("CAMERA", "Fotoğraf"),
    EVENT("EVENT", "Etkinlik"),
    FRIENDS("FRIENDS", "Arkadaşlar"),
    /** A category does not need an icon. */
    NONE("NONE", "İkon yok");

    companion object {
        val selectableEntries: List<CategoryIcon>
            get() = listOf(NONE) + entries.filterNot { it == PET || it == GIFT || it == NONE } + listOf(PET, GIFT)

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
                CategoryIcon.NONE -> Unit
                // SVG-path icons (CategoryIconPaths).
                CategoryIcon.BIRTHDAY, CategoryIcon.RACING, CategoryIcon.RACING_SIDE, CategoryIcon.VEHICLE, CategoryIcon.BALL, CategoryIcon.MUSIC, CategoryIcon.MOVIE, CategoryIcon.GAMING, CategoryIcon.BOOK, CategoryIcon.MEDICINE, CategoryIcon.PLANT, CategoryIcon.CODE, CategoryIcon.CAMERA, CategoryIcon.EVENT, CategoryIcon.FRIENDS -> drawPath(categoryIconComposePath(icon), color, style = stroke)
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


/**
 * The newer icons as SVG path data on the same 24×24 grid and stroke, so the app and notifications draw them from one
 * source. Keys must never be "CAR" or "GAME": those are old keys read as PET / GIFT.
 */
internal val CategoryIconPaths: Map<CategoryIcon, String> = mapOf(
    CategoryIcon.BIRTHDAY to "M4 20.5h16M5.5 20.5V14a1.5 1.5 0 0 1 1.5-1.5h10a1.5 1.5 0 0 1 1.5 1.5v6.5M5.5 16.3c1.4.9 2.9.9 4.33 0s2.9-.9 4.33 0 2.9.9 4.34 0M9 12.5V10M12 12.5V9.5M15 12.5V10M9 7.4v.2M12 6.9v.2M15 7.4v.2",
    CategoryIcon.RACING to "M6.5 4h11M7 21h10M11.2 4h1.6l.7 6 1.5 1.6v6.6l-1 1.8H10l-1-1.8v-6.6l1.5-1.6zM5.6 6.4v4.4M18.4 6.4v4.4M5.2 13.8v4.8M18.8 13.8v4.8M5.6 8.6h4.8M13.6 8.6h4.8M5.2 16.2H9M15 16.2h3.8",
    CategoryIcon.RACING_SIDE to "M2.5 8.5h3.5v2.5M4.2 11v2.6h3.6l1.9-2.6h1.5l1 1.6h2l7.8 2.7v1.2H21M9.1 16.8h6.4M21 18h1.8M9.1 16.3a2.3 2.3 0 1 1-4.6 0 2.3 2.3 0 1 1 4.6 0zM20.3 16.3a2.3 2.3 0 1 1-4.6 0 2.3 2.3 0 1 1 4.6 0z",
    CategoryIcon.VEHICLE to "M5.5 16.5H4v-4l2.2-.6L8.5 8h6.2l3 3.6 2.3.6v4.3h-1.5M9.5 16.5h5M8.6 11.9h9M9.5 16.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0zM18.5 16.5a2 2 0 1 1-4 0 2 2 0 1 1 4 0z",
    CategoryIcon.BALL to "M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 1 1 17 0zM12 8.3l3.5 2.5-1.3 4.1H9.8l-1.3-4.1zM12 3.5v4.8M15.5 10.8l4.5-1.5M14.2 14.9l2.8 3.9M9.8 14.9L7 18.8M8.5 10.8L4 9.3",
    CategoryIcon.MUSIC to "M9 18V6l10-2v12M9 9.5l10-2M9 18a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0zM19 16a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0z",
    CategoryIcon.MOVIE to "M4 10h16v9.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10l-.6-3.3 15.6-2.8.6 3.2M8.2 6.1l1.9 3.4M12.7 5.3l1.9 3.4",
    CategoryIcon.GAMING to "M7 7h10a4.5 4.5 0 0 1 4.4 5.4l-.9 4.4a2.3 2.3 0 0 1-4 1L14.6 16H9.4l-1.9 1.8a2.3 2.3 0 0 1-4-1l-.9-4.4A4.5 4.5 0 0 1 7 7zM7.5 10.5v3M6 12h3M15.5 11.2v.1M17.5 13v.1",
    CategoryIcon.BOOK to "M5 19.5V4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5zM5 19.5A1.5 1.5 0 0 0 6.5 21H19v-3M9 7h6",
    CategoryIcon.MEDICINE to "M8.2 19.6l11.4-11.4a3.6 3.6 0 0 0-5.1-5.1L3.1 14.5a3.6 3.6 0 0 0 5.1 5.1zM9.5 9.5l3.7 3.7",
    CategoryIcon.PLANT to "M12 21v-9M12 14c-4.5 0-7-2.5-7-7 4.5 0 7 2.5 7 7zM12 11.5c0-3.8 2.3-6.5 6.5-6.5 0 4-2.5 6.5-6.5 6.5z",
    CategoryIcon.CODE to "M8.5 7.5L4 12l4.5 4.5M15.5 7.5L20 12l-4.5 4.5M13.5 5l-3 14",
    CategoryIcon.CAMERA to "M4 8.5A1.5 1.5 0 0 1 5.5 7h2.7l1.5-2h4.6l1.5 2h2.7A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5zM15.5 13a3.5 3.5 0 1 1-7 0 3.5 3.5 0 1 1 7 0z",
    CategoryIcon.EVENT to "M5 6.5h14a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7.5a1 1 0 0 1 1-1zM4 10.5h16M8 4.5v4M16 4.5v4M8 14h.01M12 14h.01M16 14h.01",
    CategoryIcon.FRIENDS to "M12 8a3 3 0 1 1-6 0 3 3 0 1 1 6 0zM3.5 19.5c0-3.3 2.4-5.5 5.5-5.5s5.5 2.2 5.5 5.5M16 10.5a2.5 2.5 0 1 0 0-5M17 14c2.2.4 3.5 2.4 3.5 5.5"
)

private val composePathCache = HashMap<CategoryIcon, Path>()

internal fun categoryIconComposePath(icon: CategoryIcon): Path = synchronized(composePathCache) {
    composePathCache.getOrPut(icon) {
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(CategoryIconPaths.getValue(icon)).toPath()
    }
}
