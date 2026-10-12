package com.cagri.real.ui.motion

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

/** One short title reveal for the first visible batch, never for scroll/prefetch or a restored route. */
internal class CategoryTitleMotion(
    private val clock: Animatable<Float, AnimationVector1D>,
    private val initialIds: State<List<Long>>,
    private val enabled: Boolean,
    private val blurEnabled: Boolean
) {
    fun title(id: Long): Modifier = if (!enabled) Modifier else Modifier.graphicsLayer {
        val index = initialIds.value.indexOf(id)
        val p = if (index < 0) 1f else ((clock.value - minOf(index, 3) * 25f) / 300f).coerceIn(0f, 1f)
        val e = RealEntranceEase.transform(p)
        alpha = 0.85f + 0.15f * e
        val radius = (1f - e) * 5.dp.toPx()
        renderEffect = if (blurEnabled && Build.VERSION.SDK_INT >= 31 && radius > 0.5f)
            BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

@Composable
internal fun rememberCategoryTitleMotion(
    list: LazyListState,
    enabled: Boolean,
    reduceMotion: Boolean,
    reduceBlur: Boolean = false,
    delayMs: Long = 0L
): CategoryTitleMotion {
    var played by rememberSaveable { mutableStateOf(false) }
    val initialIds = remember { mutableStateOf<List<Long>>(emptyList()) }
    val clock = remember { Animatable(400f) }
    var interrupted by remember { mutableStateOf(false) }
    LaunchedEffect(list, enabled, reduceMotion, delayMs) {
        if (!enabled || reduceMotion || played) {
            clock.snapTo(400f)
            return@LaunchedEffect
        }
        val info = snapshotFlow { list.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        // Mark the entry consumed even when all visible items are headers or placeholders.
        played = true
        initialIds.value = info.visibleItemsInfo.filter {
            it.offset < info.viewportEndOffset && it.offset + it.size > info.viewportStartOffset
        }.mapNotNull { item ->
            when (val key = item.key) {
                is Long -> key
                is String -> if (key.startsWith("goal:")) key.removePrefix("goal:").toLongOrNull() else null
                else -> null
            }
        }
        if (initialIds.value.isNotEmpty() && !list.isScrollInProgress) {
            clock.snapTo(0f)
            if (delayMs > 0L) delay(delayMs)
            if (!interrupted) clock.animateTo(400f, tween(400, easing = androidx.compose.animation.core.LinearEasing))
            else clock.snapTo(400f)
        }
    }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) { interrupted = true; clock.snapTo(400f) }
    }
    return remember(clock, enabled, reduceMotion, reduceBlur) {
        CategoryTitleMotion(clock, initialIds, enabled && !reduceMotion, !reduceBlur)
    }
}
