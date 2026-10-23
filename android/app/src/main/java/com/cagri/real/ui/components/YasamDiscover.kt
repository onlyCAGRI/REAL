package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Mono = androidx.compose.ui.text.font.FontFamily.Monospace
private class DiscoverTone(val top: Color, val bottom: Color, val text: Color)
private val WatchTone = DiscoverTone(Color(0xFF16264D), Color(0xFF0A0F1D), Color(0xFF8EA2D6))
private val PlayTone = DiscoverTone(Color(0xFF4D2510), Color(0xFF1D0E06), Color(0xFFD6A48E))
private val ListenTone = DiscoverTone(Color(0xFF3B1640), Color(0xFF140816), Color(0xFFC99AD1))
private val RateTone = DiscoverTone(Color(0xFF2B3A0E), Color(0xFF0E1405), Color(0xFFB9D47A))

/**
 * Yaşam "KEŞFET": four verbs in a 2×2 grid. The tile opens suggestions; its small line ("Listende 12 ›") opens the
 * saved list. Puanla opens Puanlarım and shows the latest scores as coloured squares.
 */
@Composable
internal fun YasamDiscoverGrid(
    actions: YasamActions,
    watchlistCount: Int?, playlistCount: Int?, musicCount: Int?,
    watchArt: List<String>, gameArt: List<String>, musicArt: List<String>,
    ratings: YasamRatings?,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(t("KEŞFET"), color = YasamMuted, fontSize = 12.sp, fontFamily = Mono, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            Text(t("bugün ne yapsak"), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiscoverTile(t("İzle"), WatchTone, listLine(watchlistCount), actions.openFilm, actions.openFilmList, Modifier.weight(1f)) {
                PosterStack(watchArt, RoundedCornerShape(5.dp), 22, 32)
            }
            DiscoverTile(t("Oyna"), PlayTone, listLine(playlistCount), actions.openGame, actions.openGameList, Modifier.weight(1f)) {
                PosterStack(gameArt, RoundedCornerShape(5.dp), 22, 32)
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiscoverTile(t("Dinle"), ListenTone, listLine(musicCount), actions.openMusic, actions.openMusicList, Modifier.weight(1f)) {
                PosterStack(musicArt, CircleShape, 26, 26)
            }
            DiscoverTile(t("Puanla"), RateTone, ratings?.average?.let { t("ort. {0}", scoreText(it)) } ?: t("Henüz yok"),
                actions.openRatings, null, Modifier.weight(1f)) {
                val recent = ratings?.recent.orEmpty()
                if (recent.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    recent.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(ratingColors(it).first)) }
                }
            }
        }
    }
}

private fun listLine(count: Int?) = if (count == null || count == 0) t("Listen boş") else t("Listende {0}", count)

@Composable
private fun DiscoverTile(verb: String, tone: DiscoverTone, line: String, onOpen: () -> Unit, onList: (() -> Unit)?,
                         modifier: Modifier, visual: @Composable () -> Unit) {
    Column(modifier.heightIn(min = 124.dp).clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(tone.top, tone.bottom)))
        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(22.dp))
        .clickable(role = Role.Button, onClickLabel = verb, onClick = onOpen).padding(14.dp)) {
        Text(verb, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, fontFamily = YasamThin)
        Text(line + if (onList != null) "  ›" else "", color = tone.text, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                .then(if (onList != null) Modifier.clickable(role = Role.Button, onClick = onList) else Modifier)
                .padding(vertical = 4.dp))
        Spacer(Modifier.weight(1f, fill = false).heightIn(min = 10.dp))
        Box(Modifier.padding(top = 8.dp)) { visual() }
    }
}

@Composable
private fun PosterStack(urls: List<String>, shape: Shape, w: Int, h: Int) {
    Box {
        urls.take(3).forEachIndexed { i, url ->
            AsyncImage(url, null, contentScale = ContentScale.Crop,
                modifier = Modifier.offset(x = (i * (w - 7)).dp).size(w.dp, h.dp).clip(shape)
                    .background(Color(0xFF26262C)).border(1.5.dp, Color.Black, shape))
        }
        if (urls.isEmpty()) Box(Modifier.size(w.dp, h.dp))
    }
}
