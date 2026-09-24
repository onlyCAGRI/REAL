package com.cagri.bigboscagri.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.CategoryColorOverride
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.data.ReminderCategory

enum class Glyph { Calendar, Clock, Star, Note, Completed, Add }

data class CategoryStyle(
    val label: String,
    val glyph: Glyph,
    val base: Color,
    val upper: Color,
    val lower: Color
) {
    val tint: Color get() = upper
}

fun ReminderCategory.glyph(): Glyph = when (this) {
    ReminderCategory.Today -> Glyph.Calendar
    ReminderCategory.Planned -> Glyph.Clock
    ReminderCategory.Important -> Glyph.Star
    ReminderCategory.NoAlert -> Glyph.Note
    ReminderCategory.Completed -> Glyph.Completed
}

fun ReminderCategory.defaultStyle(): CategoryStyle = when (this) {
    ReminderCategory.Today -> CategoryStyle("Bugün", Glyph.Calendar, Color(0xFF141E4E), Color(0xFF23457D), Color(0xFF863D59))
    ReminderCategory.Planned -> CategoryStyle("Planlı", Glyph.Clock, Color(0xFF202142), Color(0xFF32655E), Color(0xFF803B4D))
    ReminderCategory.Important -> CategoryStyle("Önemli", Glyph.Star, Color(0xFF352137), Color(0xFF8E664A), Color(0xFF7B355F))
    ReminderCategory.NoAlert -> CategoryStyle("Uyarı yok", Glyph.Note, Color(0xFF222142), Color(0xFF414E83), Color(0xFF674878))
    ReminderCategory.Completed -> CategoryStyle("Tamamlananlar", Glyph.Completed, Color(0xFF152F35), Color(0xFF387D70), Color(0xFF333F77))
}

fun ReminderCategory.resolveStyle(override: CategoryColorOverride?): CategoryStyle {
    val default = defaultStyle()
    if (override == null) return default
    val startColor = ReminderColorChoices.getOrNull(override.start)?.color ?: return default
    val endColor = override.end?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
    return default.copy(
        base = Color(0xFF14151C),
        upper = startColor.copy(alpha = 0.85f),
        lower = endColor.copy(alpha = if (override.end != null) 0.70f else 0.60f)
    )
}

fun ReminderCategory.resolveStyle(colorIndex: Int?): CategoryStyle =
    resolveStyle(colorIndex?.let { CategoryColorOverride(it, null) })

private sealed interface GridItem {
    data class System(val style: CategoryStyle, val category: ReminderCategory) : GridItem
    data class Custom(val category: CustomCategory) : GridItem
    data object Add : GridItem
}

private val addCategoryStyle = CategoryStyle("Ekle", Glyph.Add, Color(0xFF1C1C1E), Color.Transparent, Color.Transparent)

@Composable
fun CategoryGrid(
    customCategories: List<CustomCategory> = emptyList(),
    builtInCategoryColors: Map<ReminderCategory, CategoryColorOverride> = emptyMap(),
    onOpenCategory: (ReminderCategory) -> Unit,
    onOpenCustomCategory: (CustomCategory) -> Unit = {},
    onAddCategory: () -> Unit,
    onEditBuiltInCategory: (ReminderCategory) -> Unit = {},
    selectedCategoryIds: Set<Long> = emptySet(),
    onToggleSelectCategory: (Long) -> Unit = {},
    onLongClickCategory: (Long) -> Unit = {},
    itemEntranceAlphas: List<Float>? = null,
    itemEntranceOffsetsY: List<Float>? = null,
    itemEntranceProgress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    reduceAnimations: Boolean = false
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isSelectionMode = selectedCategoryIds.isNotEmpty()
    val allItems = remember(customCategories, builtInCategoryColors) {
        buildList<GridItem> {
            ReminderCategory.entries.forEach { cat ->
                val style = cat.resolveStyle(builtInCategoryColors[cat])
                add(GridItem.System(style, cat))
            }
            customCategories.forEach { customCat ->
                add(GridItem.Custom(customCat))
            }
            add(GridItem.Add)
        }
    }

    val pageCount = (allItems.size + 5) / 6
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageItems = allItems.drop(page * 6).take(6)
            val row1 = pageItems.take(3)
            val row2 = pageItems.drop(3).take(3)

            fun getItemModifier(index: Int): Modifier {
                val base = Modifier.weight(1f)
                if (page == 0 && itemEntranceProgress != null) {
                    return base.graphicsLayer {
                        val progress = itemEntranceProgress()
                        val startFraction = (index * 45f) / 505f
                        val durationFraction = 280f / 505f
                        val rawFraction = ((progress - startFraction) / durationFraction).coerceIn(0f, 1f)
                        val motionEase = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f).transform(rawFraction)
                        val alphaEase = LinearOutSlowInEasing.transform(rawFraction)
                        alpha = alphaEase
                        translationY = with(density) { (24.dp * (1f - motionEase)).toPx() }
                    }
                }
                if (page == 0 && itemEntranceAlphas != null && itemEntranceOffsetsY != null) {
                    val a = itemEntranceAlphas.getOrNull(index) ?: 1f
                    val offY = itemEntranceOffsetsY.getOrNull(index) ?: 0f
                    return base.graphicsLayer {
                        alpha = a
                        translationY = with(density) { offY.dp.toPx() }
                    }
                }
                return base
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row1.forEachIndexed { colIndex, item ->
                        val mod = getItemModifier(colIndex)
                        when (item) {
                            is GridItem.System -> CategoryCard(
                                category = item.style,
                                onClick = { if (!isSelectionMode) onOpenCategory(item.category) },
                                onLongClick = { if (!isSelectionMode) onEditBuiltInCategory(item.category) },
                                modifier = mod,
                                reduceAnimations = reduceAnimations
                            )
                            is GridItem.Custom -> CustomCategoryCard(
                                category = item.category,
                                isSelectionMode = isSelectionMode,
                                isSelected = item.category.id in selectedCategoryIds,
                                onClick = {
                                    if (isSelectionMode) onToggleSelectCategory(item.category.id)
                                    else onOpenCustomCategory(item.category)
                                },
                                onLongClick = { onLongClickCategory(item.category.id) },
                                modifier = mod,
                                reduceAnimations = reduceAnimations
                            )
                            is GridItem.Add -> CategoryCard(
                                category = addCategoryStyle,
                                onClick = { if (!isSelectionMode) onAddCategory() },
                                modifier = mod,
                                reduceAnimations = reduceAnimations
                            )
                        }
                    }
                    repeat(3 - row1.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                if (row2.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row2.forEachIndexed { colIndex, item ->
                            val mod = getItemModifier(3 + colIndex)
                            when (item) {
                                is GridItem.System -> CategoryCard(
                                    category = item.style,
                                    onClick = { if (!isSelectionMode) onOpenCategory(item.category) },
                                    onLongClick = { if (!isSelectionMode) onEditBuiltInCategory(item.category) },
                                    modifier = mod,
                                    reduceAnimations = reduceAnimations
                                )
                                is GridItem.Custom -> CustomCategoryCard(
                                    category = item.category,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = item.category.id in selectedCategoryIds,
                                    onClick = {
                                        if (isSelectionMode) onToggleSelectCategory(item.category.id)
                                        else onOpenCustomCategory(item.category)
                                    },
                                    onLongClick = { onLongClickCategory(item.category.id) },
                                    modifier = mod,
                                    reduceAnimations = reduceAnimations
                                )
                                is GridItem.Add -> CategoryCard(
                                    category = addCategoryStyle,
                                    onClick = { if (!isSelectionMode) onAddCategory() },
                                    modifier = mod,
                                    reduceAnimations = reduceAnimations
                                )
                            }
                        }
                        repeat(3 - row2.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    // Empty row placeholder to maintain uniform 2-row height across all pages
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) {
                            Spacer(Modifier.weight(1f).height(110.dp))
                        }
                    }
                }
            }
        }
    }
}

private val CategoryCardShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    category: CategoryStyle,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier,
    reduceAnimations: Boolean = false
) {
    val liquid = LocalDesignStyle.current == com.cagri.bigboscagri.ui.DesignStyle.LiquidGlass
    val shape = CategoryCardShape
    val interactionSource = remember(category.label) { MutableInteractionSource() }
    Column(
        // Only the image is rounded: clipping this column also clips label glyphs.
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .pressScale(interactionSource, targetScale = 0.97f, reduceAnimations = reduceAnimations)
                .clip(shape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = category.label,
                            onLongClickLabel = "${category.label} rengini düzenle",
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = category.label,
                            onClick = onClick
                        )
                    }
                )
                .drawWithCache {
                    val topLight = Brush.radialGradient(
                        colors = listOf(category.upper, Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(category.lower, Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(category.base)
                        drawRect(topLight)
                        drawRect(bottomLight)
                        if (liquid) drawRect(Color.Black.copy(alpha = 0.28f))
                    }
                }
                .border(0.75.dp, Color.White.copy(alpha = if (liquid) 0.13f else 0.07f), shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryGlyph(category.glyph, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.label,
            color = Color(0xFFE7E7EB), fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 3.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomCategoryCard(
    category: CustomCategory,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    reduceAnimations: Boolean = false
) {
    val liquid = LocalDesignStyle.current == com.cagri.bigboscagri.ui.DesignStyle.LiquidGlass
    val shape = CategoryCardShape
    val startColor = ReminderColorChoices.getOrNull(category.accentStart)?.color ?: Color(0xFF233F73)
    val endColor = category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: startColor
    val icon = CategoryIcon.fromKey(category.iconKey)

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF3B82F6)
            isSelectionMode -> Color.White.copy(alpha = 0.20f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        animationSpec = tween(180),
        label = "categoryBorderColor"
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 0.75.dp,
        animationSpec = tween(180),
        label = "categoryBorderWidth"
    )
    val checkBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF3B82F6) else Color.Black.copy(alpha = 0.4f),
        animationSpec = tween(150),
        label = "checkBgColor"
    )
    val checkBorderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.45f),
        animationSpec = tween(150),
        label = "checkBorderColor"
    )
    val interactionSource = remember(category.id) { MutableInteractionSource() }

    Column(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .pressScale(interactionSource, targetScale = 0.97f, reduceAnimations = reduceAnimations)
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç") else category.name,
                    onLongClickLabel = if (!isSelectionMode) "Kategorileri seç" else null,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .drawWithCache {
                    val topLight = Brush.radialGradient(
                        colors = listOf(startColor.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.95f, -size.height * 0.1f),
                        radius = size.width * 1.12f
                    )
                    val bottomLight = Brush.radialGradient(
                        colors = listOf(endColor.copy(alpha = 0.70f), Color.Transparent),
                        center = Offset(-size.width * 0.05f, size.height * 1.1f),
                        radius = size.width * 1.15f
                    )
                    onDrawBehind {
                        drawRect(Color(0xFF14151C))
                        drawRect(topLight)
                        drawRect(bottomLight)
                        if (liquid) drawRect(Color.Black.copy(alpha = 0.28f))
                    }
                }
                .border(animatedBorderWidth, animatedBorderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            CategoryIconGlyph(icon, Modifier.size(32.dp), color = Color.White)

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(checkBgColor)
                        .border(1.dp, checkBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            val path = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.5f)
                                lineTo(size.width * 0.45f, size.height * 0.75f)
                                lineTo(size.width * 0.85f, size.height * 0.25f)
                            }
                            drawPath(path, Color.White, style = stroke)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.name,
            color = Color(0xFFE7E7EB), fontSize = 12.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 3.dp)
        )
    }
}

// Small repo-native glyphs keep the white icon style consistent without adding
// an icon-pack dependency. Coordinates use a 24-unit drawing space.
@Composable
fun CategoryGlyph(glyph: Glyph, modifier: Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
            when (glyph) {
                Glyph.Calendar -> {
                    drawRoundRect(tint, Offset(3.5f, 5f), Size(17f, 16f), CornerRadius(3f, 3f), style = stroke)
                    line(3.5f, 10f, 20.5f, 10f)
                    line(8f, 3f, 8f, 7f)
                    line(16f, 3f, 16f, 7f)
                    drawCircle(tint, 1.15f, Offset(8.5f, 14f))
                    drawCircle(tint, 1.15f, Offset(15.5f, 14f))
                    drawCircle(tint, 1.15f, Offset(8.5f, 17.5f))
                }
                Glyph.Clock -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 6.5f, 12f, 12.5f)
                    line(12f, 12.5f, 16.5f, 15f)
                }
                Glyph.Star -> {
                    val star = Path().apply {
                        moveTo(12f, 2f)
                        lineTo(15f, 8.4f)
                        lineTo(22f, 9.4f)
                        lineTo(17f, 14.4f)
                        lineTo(18.2f, 21.4f)
                        lineTo(12f, 18.1f)
                        lineTo(5.8f, 21.4f)
                        lineTo(7f, 14.4f)
                        lineTo(2f, 9.4f)
                        lineTo(9f, 8.4f)
                        close()
                    }
                    drawPath(star, tint)
                }
                Glyph.Note -> {
                    drawRoundRect(tint, Offset(5f, 3f), Size(14f, 18f), CornerRadius(2.5f, 2.5f), style = stroke)
                    line(9f, 8f, 15f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 12.5f, 16f)
                }
                Glyph.Completed -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(7.5f, 12f, 10.7f, 15.2f)
                    line(10.7f, 15.2f, 17f, 8.8f)
                }
                Glyph.Add -> {
                    line(12f, 4.5f, 12f, 19.5f)
                    line(4.5f, 12f, 19.5f, 12f)
                }
            }
        }
    }
}
