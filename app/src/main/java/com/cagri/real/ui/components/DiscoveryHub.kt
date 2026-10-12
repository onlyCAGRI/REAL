package com.cagri.real.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cagri.real.data.MediaBlock
import kotlinx.coroutines.launch

/**
 * Giriş ekranında sola kaydırınca mavi "Film & Dizi" sayfasından turuncu-kırmızı "Oyun" sayfasına geçilir.
 * Kaydırma yalnızca sayfa kendi giriş ekranındayken açıktır; deste/filtre ekranlarındaki jestlerle çakışmaz.
 */
@Composable
fun DiscoveryHub(reduceAnimations: Boolean, onDismiss: () -> Unit, onAddMedia: suspend (MediaBlock) -> Long?, reduceDynamicBlur: Boolean = false) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var filmAtEntry by remember { mutableStateOf(true) }
    var gameAtEntry by remember { mutableStateOf(true) }
    val atEntry = if (pager.currentPage == 0) filmAtEntry else gameAtEntry
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = atEntry) { index ->
            if (index == 0) MediaDiscoveryScreen(reduceAnimations, onDismiss = onDismiss, onAddMedia = onAddMedia,
                reduceDynamicBlur = reduceDynamicBlur, onAtEntry = { filmAtEntry = it })
            else GameDiscoveryScreen(reduceAnimations, onDismiss = { scope.launch { pager.animateScrollToPage(0) } }, onAtEntry = { gameAtEntry = it }, reduceDynamicBlur = reduceDynamicBlur)
        }
        if (atEntry) Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                val on = pager.currentPage == i
                val color by animateColorAsState(if (on) (if (i == 0) Color(0xFF2F6BFF) else Color(0xFFF26A1B)) else Color.White.copy(alpha = .3f), label = "hub-dot")
                Box(Modifier.size(if (on) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
        }
    }
}
