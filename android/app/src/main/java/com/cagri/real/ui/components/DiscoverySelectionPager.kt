package com.cagri.real.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.motion.RealEntranceEase
import kotlinx.coroutines.launch

/** Extra choices stay in the same horizontal area, rather than opening a section below. */
@Composable
internal fun DiscoverySelectionPager(
    titles: List<String>,
    selectedCounts: List<Int>,
    reduceAnimations: Boolean,
    accent: Color = Color(0xFF2F6BFF),
    content: @Composable (Int, () -> Unit) -> Unit
) {
    if (titles.isEmpty()) return
    val pager = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    fun goTo(index: Int) {
        scope.launch {
            if (reduceAnimations) pager.scrollToPage(index)
            else pager.animateScrollToPage(index, animationSpec = tween(360, easing = RealEntranceEase))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(titles[pager.currentPage], color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
            if (titles.size > 1) Text("${pager.currentPage + 1}/${titles.size}  ↔",
                color = Color.White.copy(alpha = .4f), fontSize = 11.sp)
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().then(
                if (reduceAnimations) Modifier else Modifier.animateContentSize(tween(220, easing = RealEntranceEase))
            ),
            verticalAlignment = Alignment.Top,
            key = { it }
        ) { index ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content(index) { if (index < titles.lastIndex) goTo(index + 1) }
            }
        }
        if (titles.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            titles.forEachIndexed { index, title ->
                val active = pager.currentPage == index
                val count = selectedCounts.getOrElse(index) { 0 }
                Box(Modifier.size(32.dp).clip(CircleShape)
                    .clickable { goTo(index) }
                    .semantics {
                        contentDescription = "$title, ${index + 1}. sayfa, $count seçili"
                        selected = active
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (active) 7.dp else 5.dp).clip(CircleShape)
                        .background(if (active || count > 0) accent else Color.White.copy(alpha = .2f)))
                }
            }
        }
    }
}
