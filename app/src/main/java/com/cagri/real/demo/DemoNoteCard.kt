package com.cagri.real.demo

import com.cagri.real.R

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NoteSource(val label: String, val color: Color)
private val noteSources = listOf(
    NoteSource("RENK", Color(0xFF655277)),
    NoteSource("GÖRSEL", Color(0xFF375D62)),
    NoteSource("URL", Color(0xFF334C72)),
    NoteSource("YOUTUBE", Color(0xFF733E45)),
    NoteSource("SİTE / WEB", Color(0xFF395E53)),
    NoteSource("DATA SOURCE", Color(0xFF946A30))
)
private val sourceHeight = 268.dp
private val sourceStripHeight = 44.dp

/** Local-only deck. No production model, networking or attachment import. */
@Composable
internal fun DemoNoteCard(modifier: Modifier = Modifier) {
    var activeSource by rememberSaveable { mutableIntStateOf(1) }
    var textExpanded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("Küçük bir kaçamak") }
    var body by rememberSaveable { mutableStateOf("Bir sabah yola çık. Yeni bir sokak, güzel bir kahve ve acele etmeden geçen birkaç saat.") }
    val context = LocalContext.current
    val imageAccent by produceState(initialValue = Color(0xFF687D81), context) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.demo_note_landscape)
        if (drawable != null) {
            val bitmap = drawable.toBitmap(120, 160)
            val extracted = withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                palette.getDarkMutedColor(palette.getMutedColor(palette.getDominantColor(0xFF687D81.toInt())))
            }
            bitmap.recycle()
            value = Color(extracted)
        }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Bir not, katmanlarıyla.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("Kaynak şeridine dokun, öne getir. Not başlığına dokun, aç.", color = Color(0xFFAAAAB1), fontSize = 12.sp)
        NoteDeck(activeSource, { activeSource = it; textExpanded = false }, textExpanded,
            { textExpanded = !textExpanded }, title, body, imageAccent)
        OutlinedTextField(title, onValueChange = { title = it }, label = { Text("Başlık") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), singleLine = true)
        OutlinedTextField(body, onValueChange = { body = it }, label = { Text("Not metni") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), minLines = 3, maxLines = 8)
        Text("Yerel demo · kaynak önizlemeleri örnektir. Kartlarına ve Günlük’e kaydedilmez.",
            color = Color(0xFF888890), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NoteDeck(
    activeSource: Int, onSourceSelected: (Int) -> Unit,
    textExpanded: Boolean, onToggleText: () -> Unit,
    title: String, body: String, imageAccent: Color
) {
    val order = remember(activeSource) { noteSources.indices.filter { it != activeSource } + activeSource }
    val activeTop = sourceStripHeight * (noteSources.size - 1)
    val textTop by animateDpAsState(if (textExpanded) activeTop + 52.dp else activeTop + sourceHeight - 16.dp,
        spring(dampingRatio = 1f, stiffness = 340f), label = "Text promotion")
    val textHeight by animateDpAsState(if (textExpanded) 320.dp else 64.dp,
        spring(dampingRatio = 1f, stiffness = 340f), label = "Text container")
    // Layout follows both animated dimensions, so no cropped lower edge on expansion.
    Box(Modifier.fillMaxWidth().height(textTop + textHeight + 12.dp)) {
        noteSources.forEachIndexed { id, source ->
            key(id) {
                val rank = order.indexOf(id)
                val top by animateDpAsState(sourceStripHeight * rank,
                    spring(dampingRatio = 0.95f, stiffness = 300f), label = "Source position $id")
                val scale by animateFloatAsState(0.94f + rank * 0.012f,
                    spring(dampingRatio = 1f, stiffness = 340f), label = "Source scale $id")
                SourceCard(id, source, id == activeSource, imageAccent,
                    Modifier.fillMaxWidth().offset { IntOffset(0, top.roundToPx()) }
                        .zIndex(rank.toFloat()).graphicsLayer { scaleX = scale },
                    onPromote = { onSourceSelected(id) })
            }
        }
        NoteTextLayer(title, body, textExpanded, onToggleText,
            Modifier.fillMaxWidth().offset { IntOffset(0, textTop.roundToPx()) }
                .height(textHeight).zIndex(20f))
    }
}

@Composable
private fun SourceCard(
    id: Int, source: NoteSource, active: Boolean, imageAccent: Color,
    modifier: Modifier, onPromote: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val contentAlpha by animateFloatAsState(if (active) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 400f), label = "Source content")
    Column(modifier.height(sourceHeight).clip(shape).background(source.color)
        .border(0.8.dp, if (id == 1) imageAccent else Color.White.copy(alpha = 0.23f), shape)) {
        Row(Modifier.fillMaxWidth().height(sourceStripHeight)
            .clickable(role = Role.Button, onClickLabel = "${source.label} kartını öne getir", onClick = onPromote)
            .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(source.label, color = Color.White, fontSize = 11.sp, letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (active) "●" else "↗", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
        // Back cards expose only the header and don't retain hidden clickable previews.
        if (active || contentAlpha > 0.01f) {
            Box(Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = contentAlpha }) {
                SourcePreview(id)
            }
        }
    }
}

@Composable
private fun SourcePreview(id: Int) {
    when (id) {
        0 -> Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF82678D), Color(0xFF4D466E))))
            .padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Bir renk,\nbir duygu.", color = Color.White, fontSize = 31.sp, lineHeight = 36.sp)
            Text("MOR / 01", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        1 -> Image(painterResource(R.drawable.demo_note_landscape), contentDescription = "Yerel manzara illüstrasyonu",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        2 -> Column(Modifier.fillMaxSize().background(Color(0xFF172334)).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("↗", color = Color(0xFFB8CDEF), fontSize = 38.sp)
            Column {
                Text("Yola çıkmadan önce", color = Color.White, fontSize = 23.sp)
                Spacer(Modifier.height(8.dp))
                Text("example.org / rota-notlari", color = Color(0xFFB8CDEF), fontSize = 12.sp)
            }
        }
        3 -> Column(Modifier.fillMaxSize().background(Color(0xFF211619))) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Image(painterResource(R.drawable.demo_note_landscape), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text("▶", color = Color.White, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFFD73238)).padding(horizontal = 19.dp, vertical = 5.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("Yavaş bir hafta sonu", color = Color.White, fontSize = 19.sp)
                Text("YouTube · Örnek video", color = Color(0xFFB6A4A7), fontSize = 11.sp)
            }
        }
        4 -> Column(Modifier.fillMaxSize().background(Color(0xFFDFE7E0))) {
            Image(painterResource(R.drawable.demo_note_landscape), null,
                Modifier.fillMaxWidth().height(98.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(18.dp)) {
                Text("Kıyı boyunca", color = Color(0xFF1E342F), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Bir şehir rehberinden notlar", color = Color(0xFF3B574F), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("example.com · Site önizlemesi", color = Color(0xFF3B574F), fontSize = 11.sp)
            }
        }
        else -> Column(Modifier.fillMaxSize().background(Color(0xFFE7B758)).padding(23.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("Fikirlerini tek bir yerde\nbir araya getir.", color = Color(0xFF3C2B0C), fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("data\nsources", color = Color(0xFF30230C), fontSize = 34.sp, lineHeight = 34.sp)
                Text("✳", color = Color(0xFF30230C), fontSize = 45.sp)
            }
        }
    }
}

@Composable
private fun NoteTextLayer(title: String, body: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(25.dp)
    Column(modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF28282C), Color(0xFF111114))))
        .border(0.8.dp, Color.White.copy(alpha = 0.3f), shape)) {
        Row(Modifier.fillMaxWidth().height(64.dp)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Notu küçült" else "Notu aç", onClick = onToggle)
            .padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.ifBlank { "Not başlığı" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(if (expanded) "⌄" else "⌃", color = Color(0xFFB6B6BD), fontSize = 22.sp)
        }
        if (expanded) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Text("NOT / FİKİRLER", color = Color(0xFF9999A4), fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(18.dp))
            Text(title.ifBlank { "Not başlığı" }, color = Color.White, fontSize = 27.sp, lineHeight = 33.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Text(body.ifBlank { "Bir düşünce ekle…" }, color = Color(0xFFE0E0E5), fontSize = 16.sp, lineHeight = 25.sp)
        }
    }
}

