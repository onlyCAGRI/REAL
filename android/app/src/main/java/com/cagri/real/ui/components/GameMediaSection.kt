package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.network.WorkerClient
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Gameplay media from IGDB (screenshots, YouTube videos, gameplay first). */
private class GameMedia(val screenshots: List<String>, val videos: List<Pair<String, String>>)

private val mediaCache = HashMap<String, GameMedia>()

private suspend fun loadGameMedia(providerId: String): GameMedia = withContext(Dispatchers.IO) {
    mediaCache[providerId]?.let { return@withContext it }
    val body = JSONObject().put("provider", "IGDB").put("providerId", providerId).toString()
    val response = WorkerClient(AndroidBackend.workerTransport).postJson("${AndroidBackend.ORIGIN}/api/game-details", body, 10_000, 20_000, readErrorBody = false)
    check(response.statusCode in 200..299) { t("Görseller şu anda alınamadı.") }
    val game = JSONObject(response.body).optJSONObject("game") ?: JSONObject()
    val shots = game.optJSONArray("screenshots")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { u -> u.startsWith("https://") } } }.orEmpty()
    val videos = game.optJSONArray("videos")?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> o.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.to(o.optString("name")) } }
    }.orEmpty()
    GameMedia(shots, videos).also { mediaCache[providerId] = it }
}

/**
 * "Oynanışı gör" in the game detail: a button that opens, in the same panel, a large stage and a strip of gameplay
 * screenshots under it; whatever is tapped shows on the stage. (Videos are left out for now: YouTube's player was too
 * busy for the panel.)
 */
@Composable
internal fun GameMediaSection(providerId: String, accent: Color) {
    val scope = rememberCoroutineScope()
    var open by remember(providerId) { mutableStateOf(false) }
    var media by remember(providerId) { mutableStateOf<GameMedia?>(null) }
    var error by remember(providerId) { mutableStateOf<String?>(null) }
    var loading by remember(providerId) { mutableStateOf(false) }
    var selected by remember(providerId) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, accent.copy(alpha = .7f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button) {
                open = !open
                if (open && media == null && !loading) {
                    loading = true; error = null
                    scope.launch {
                        try { media = loadGameMedia(providerId) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: t("Görseller şu anda alınamadı.") }
                        loading = false
                    }
                }
            }.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "▾" else "▶", color = accent, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (open) t("Oynanışı gizle") else t("Oynanışı gör"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                val m = media
                when {
                    loading -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF151518)),
                        contentAlignment = Alignment.Center) { RealSpinner(Modifier.size(28.dp), color = Color.White) }
                    error != null -> Text(error!!, color = Color(0xFFE6BCB1), fontSize = 13.sp)
                    m == null || m.screenshots.isEmpty() ->
                        Text(t("Bu oyun için görsel yok."), color = Color(0xFF85858E), fontSize = 13.sp)
                    else -> {
                        val shots = m.screenshots
                        // The stage swipes too; the strip and the stage follow each other.
                        val pager = rememberPagerState { shots.size }
                        LaunchedEffect(pager.currentPage) { selected = pager.currentPage }
                        LaunchedEffect(selected) { if (pager.currentPage != selected) pager.animateScrollToPage(selected) }
                        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
                            pageSpacing = 8.dp) { page ->
                            AsyncImage(shots[page], null, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        }
                        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(shots) { i, shot ->
                                Box(Modifier.width(112.dp).heightIn(min = 63.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                                    .border(if (i == selected) 2.dp else 0.dp, if (i == selected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { selected = i }) {
                                    AsyncImage(shot, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
