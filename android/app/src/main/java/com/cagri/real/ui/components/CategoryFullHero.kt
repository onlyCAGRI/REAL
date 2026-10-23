package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.CategoryColorOverride
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderCategory
import com.cagri.real.data.resolveGradientBlendFraction

/** A category's colour (gradient or solid) and the text colour that reads on it. */
internal data class CategoryPalette(val brush: Brush, val foreground: Color, val accent: Color)

/**
 * The category's own colours, as on its home card. The text colour is chosen automatically from how light the colour
 * is (black on light colours, white on dark ones), averaged over the gradient's two ends.
 */
internal fun categoryPalette(category: ReminderCategory?, custom: CustomCategory?, override: CategoryColorOverride?): CategoryPalette {
    val defaults = (category ?: ReminderCategory.NoAlert).defaultStyle()
    val startIndex = custom?.accentStart ?: override?.start
    val endIndex = custom?.accentEnd ?: override?.end
    val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color } ?: defaults.upper
    val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color } ?: if (startIndex != null) start else defaults.lower
    val solid = (custom?.customSolidColorArgb ?: override?.customSolidColorArgb)?.let { Color(it) }
    val brush = when {
        solid != null -> SolidColor(solid)
        endIndex != null -> {
            val bridge = lerp(start, end, resolveGradientBlendFraction(custom?.gradientBlendFraction ?: override?.gradientBlendFraction))
            Brush.linearGradient(0f to start, 0.5f to bridge, 1f to end)
        }
        else -> Brush.linearGradient(listOf(start, end))
    }
    val light = if (solid != null) solid.luminance() else (start.luminance() + end.luminance()) / 2f
    return CategoryPalette(brush, if (light > 0.42f) Color(0xFF101010) else Color.White, solid ?: start)
}

/** Hedefler: a small target (two rings and a centre dot). */
@Composable
private fun GoalTargetGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val c = center
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx())
        drawCircle(color, size.minDimension * 0.46f, c, style = stroke)
        drawCircle(color, size.minDimension * 0.27f, c, style = stroke)
        drawCircle(color, size.minDimension * 0.09f, c)
    }
}

private fun glassFill(foreground: Color) = if (foreground == Color.White) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.36f)
private fun glassRim(foreground: Color) = if (foreground == Color.White) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.12f)

@Composable
private fun HeroRoundButton(
    foreground: Color, description: String, size: Dp = 40.dp, onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size)
            .pressScale(source, reduceAnimations = LocalLiquidReduceMotion.current, targetScale = 0.94f)
            .clip(CircleShape).background(glassFill(foreground)).border(0.8.dp, glassRim(foreground), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center, content = content
    )
}

/**
 * Category screen header (Design-Threads style): the category colour runs full width from under the status bar with
 * rounded bottom corners. Top row: back, search in this category, ••• (opens a round pencil and a round filter button
 * under it). Then the category description, the big name, the card counts and, on the right, the progress ring and the
 * sort arrow ([actions]). The ring opens the progress inside the header, drawn in the header's own colours.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun CategoryFullHero(
    category: ReminderCategory?,
    customCategory: CustomCategory?,
    override: CategoryColorOverride?,
    count: Int,
    todayCount: Int,
    /** One short note after the count: "2 gecikmiş" (alert = true), "1 tanesi bugün" or "sıradaki Cmt 15:00". */
    detail: Pair<String, Boolean>? = null,
    statusTop: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    progress: CategoryProgressStats?,
    progressOpen: Boolean,
    reduceAnimations: Boolean,
    hasFilter: Boolean,
    onFilter: () -> Unit,
    onEdit: () -> Unit,
    editLabel: String,
    actions: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    /** Custom categories: opens the category's goals (the old Today's Focus card route). */
    onGoals: (() -> Unit)? = null,
    /** Ortak kategori: the people in this category (friend button in the ••• menu). */
    onPeople: (() -> Unit)? = null,
    /** Ayraç ekle (owner and full members of a shared category; null hides it). */
    onAddDivider: (() -> Unit)? = null,
    /** Home → category: the tapped category card grows into this header (same key as the home card). */
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    val heroShape = RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && !reduceAnimations) {
        val entering = animatedVisibilityScope.transition.targetState == androidx.compose.animation.EnterExitState.Visible
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "category_card_${customCategory?.id ?: category?.name}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f) },
                clipInOverlayDuringTransition = OverlayClip(heroShape),
                renderInOverlayDuringTransition = entering,
                zIndexInOverlay = if (entering) 1f else 0f
            )
        }
    } else Modifier
    val palette = remember(category, customCategory, override) { categoryPalette(category, customCategory, override) }
    val fg = palette.foreground
    val savedDescription by rememberCategoryDescription(categoryDescriptionKey(category, customCategory))
    // A shared category from someone else shows its owner's description.
    val description = limitCategoryDescription(customCategory?.takeIf { it.isSharedMirror }?.sharedDescription.orEmpty().ifEmpty { savedDescription })
    val title = customCategory?.name ?: category?.title.orEmpty()
    val focus = LocalFocusManager.current
    Column(
        modifier.fillMaxWidth()
            // Runs up under the status bar: the list's top padding is pulled back into the hero.
            .layout { measurable, constraints ->
                val top = statusTop.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - top).coerceAtLeast(0)) { placeable.place(0, -top) }
            }
            .then(sharedModifier)
            .clip(heroShape)
            .background(Color(0xFF14151C))
            .background(palette.brush)
            .drawWithCache {
                val light = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.9f
                )
                onDrawBehind { drawRect(light) }
            }
            .padding(top = statusTop + 10.dp, start = 18.dp, end = 18.dp, bottom = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroRoundButton(fg, t("Ana sayfaya dön"), onClick = onBack) {
                ActionIcon(ActionGlyph.Back, Modifier.size(20.dp), fg)
            }
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(glassFill(fg)).border(0.8.dp, glassRim(fg), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(t("Bu kategoride ara"), color = fg.copy(alpha = 0.72f), fontSize = 14.sp, maxLines = 1)
                    BasicTextField(
                        value = query, onValueChange = { onQueryChange(it.take(80)) }, singleLine = true,
                        textStyle = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(fg),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = t("Bu kategoride ara") }
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(28.dp).clip(CircleShape).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(14.dp), fg)
                    }
                }
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                HeroRoundButton(fg, if (menuOpen) t("Seçenekleri kapat") else t("Seçenekler"), onClick = { menuOpen = !menuOpen }) {
                    Text("•••", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (hasFilter) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).background(fg, CircleShape))
                }
                val shown = remember { MutableTransitionState(false) }
                shown.targetState = menuOpen
                if (shown.currentState || shown.targetState) {
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.roundToPx() }),
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            (listOf(0, 1) + listOfNotNull(if (onGoals != null) 2 else null, if (onPeople != null) 3 else null, if (onAddDivider != null) 4 else null)).forEach { index ->
                                AnimatedVisibility(
                                    visibleState = shown,
                                    enter = if (reduceAnimations) fadeIn(tween(0)) else
                                        fadeIn(tween(160, delayMillis = index * 50)) + scaleIn(tween(220, delayMillis = index * 50), initialScale = 0.6f),
                                    exit = if (reduceAnimations) fadeOut(tween(0)) else fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.6f)
                                ) {
                                    // Over the header colour: same glass, a touch more opaque so it reads on the cards too.
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accent).background(glassFill(fg))) {
                                        HeroRoundButton(
                                            fg, when (index) { 0 -> editLabel; 2 -> t("Hedefler"); 3 -> t("Kişiler"); 4 -> t("Ayraç ekle"); else -> if (hasFilter) t("Renk filtresi (açık)") else t("Renge göre filtrele") },
                                            onClick = { menuOpen = false; when (index) { 0 -> onEdit(); 2 -> onGoals?.invoke(); 3 -> onPeople?.invoke(); 4 -> onAddDivider?.invoke(); else -> onFilter() } }
                                        ) {
                                            when (index) {
                                                0 -> ActionIcon(ActionGlyph.Edit, Modifier.size(18.dp), fg)
                                                2 -> GoalTargetGlyph(fg)
                                                3 -> CategoryIconGlyph(CategoryIcon.FRIENDS, Modifier.size(18.dp), color = fg)
                                                // Ayraç: a line broken by a short title mark.
                                                4 -> androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                                                    val y = size.height / 2; val w = 1.6.dp.toPx()
                                                    drawLine(fg, Offset(0f, y), Offset(size.width * 0.28f, y), w)
                                                    drawLine(fg, Offset(size.width * 0.72f, y), Offset(size.width, y), w)
                                                    drawLine(fg, Offset(size.width * 0.4f, y), Offset(size.width * 0.6f, y), w * 2.2f)
                                                }
                                                else -> FilterGlyph(fg, hasFilter)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (customCategory != null) {
                val icon = CategoryIcon.fromKey(customCategory.iconKey)
                if (icon != CategoryIcon.NONE) CategoryIconGlyph(icon, Modifier.size(16.dp), color = fg.copy(alpha = 0.85f))
            } else CategoryGlyph(category?.glyph() ?: Glyph.Note, Modifier.size(16.dp), tint = fg.copy(alpha = 0.85f))
            Text(description.ifBlank { t("Kategori") }, color = fg.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, color = fg, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val note = detail ?: if (todayCount > 0) t("{0} tanesi bugün", todayCount) to false else null
            // Overdue reads in a soft red that still shows on light and dark category colours.
            val alertColor = if (fg.luminance() > 0.5f) Color(0xFFFFB4AB) else Color(0xFF9C1C1C)
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(t("{0} kart", count))
                    if (note != null) {
                        append("  •  ")
                        if (note.second) withStyle(androidx.compose.ui.text.SpanStyle(color = alertColor)) { append(note.first) }
                        else append(note.first)
                    }
                },
                color = fg.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            // Ortak kategori: the friend mark left of the progress ring; a tap opens Kişiler.
            if (customCategory?.isShared == true) {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable(enabled = onPeople != null) { onPeople?.invoke() }
                    .semantics { contentDescription = t("Ortak kategori · Kişiler") }, contentAlignment = Alignment.Center) {
                    CategoryIconGlyph(CategoryIcon.FRIENDS, Modifier.size(19.dp), color = fg)
                }
                Spacer(Modifier.width(4.dp))
            }
            actions(fg)
        }
        AnimatedVisibility(
            visible = progressOpen && progress != null,
            enter = if (reduceAnimations) fadeIn(tween(0)) else fadeIn(tween(220)) + expandVertically(tween(280)),
            exit = if (reduceAnimations) fadeOut(tween(0)) else fadeOut(tween(160)) + shrinkVertically(tween(220))
        ) {
            if (progress != null) HeroProgress(progress, fg, reduceAnimations)
        }
    }
}

/** Funnel: three lines getting shorter; filled dot when a colour filter is on. */
@Composable
private fun FilterGlyph(color: Color, active: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val cx = size.width / 2f
        listOf(16f to 5f, 10f to 10f, 4f to 15f).forEach { (w, y) ->
            val half = w.dp.toPx() / 2f
            drawLine(color, Offset(cx - half, y.dp.toPx()), Offset(cx + half, y.dp.toPx()), stroke, StrokeCap.Round)
        }
        if (active) drawCircle(color, 2.6.dp.toPx(), Offset(size.width - 3.dp.toPx(), 3.dp.toPx()))
    }
}

/**
 * The category's progress inside its header: a big percentage, a glass track that fills in the text colour, and
 * three glass counters (done, left, done today).
 */
@Composable
private fun HeroProgress(stats: CategoryProgressStats, fg: Color, reduceAnimations: Boolean) {
    val fill = remember { Animatable(if (reduceAnimations) stats.fraction else 0f) }
    LaunchedEffect(stats.fraction, reduceAnimations) {
        if (reduceAnimations) fill.snapTo(stats.fraction)
        else fill.animateTo(stats.fraction, tween(850, delayMillis = 80, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(fill.value * 100f).toInt()}", color = fg, fontSize = 46.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
            Text("%", color = fg.copy(alpha = 0.7f), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(if (stats.total == 0) t("Kayıt yok") else t("{0} / {1} tamamlandı", stats.done, stats.total),
                color = fg.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(glassFill(fg))) {
            Box(Modifier.fillMaxWidth(fill.value.coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(fg.copy(alpha = 0.9f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(t("Tamamlanan") to stats.done, t("Kalan") to stats.remaining, t("Bugün") to stats.doneToday).forEach { (label, value) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(glassFill(fg))
                        .border(0.8.dp, glassRim(fg), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(label, color = fg.copy(alpha = 0.75f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("$value", color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The thin bar in the category colour that takes the hero's place once it has scrolled away. */
@Composable
internal fun CategoryCompactBar(
    category: ReminderCategory?,
    customCategory: CustomCategory?,
    override: CategoryColorOverride?,
    statusTop: Dp,
    onBack: () -> Unit,
    actions: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = remember(category, customCategory, override) { categoryPalette(category, customCategory, override) }
    val fg = palette.foreground
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Color(0xFF14151C)).background(palette.brush)
            .padding(top = statusTop + 6.dp, start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeroRoundButton(fg, t("Ana sayfaya dön"), size = 36.dp, onClick = onBack) { ActionIcon(ActionGlyph.Back, Modifier.size(18.dp), fg) }
        Text(customCategory?.name ?: category?.title.orEmpty(), color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions(fg)
        Spacer(Modifier.width(2.dp))
    }
}
