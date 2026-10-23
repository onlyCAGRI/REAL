package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.discovery.MediaRatingRepository
import com.cagri.real.discovery.RatingEntry
import com.cagri.real.discovery.SeriesEpisode
import com.cagri.real.ui.DesignStyle
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Score colours: bordo (bad) → orange → olive → lime (legend). Background + readable text. */
internal fun ratingColors(v: Double?): Pair<Color, Color> = when {
    v == null -> Color(0xFF1E1E1E) to Color(0xFF555555)
    v <= 5 -> Color(0xFF5A1A1F) to Color(0xFFF3B4B8)
    v <= 6 -> Color(0xFF8A3A1C) to Color(0xFFFFD2BD)
    v <= 7 -> Color(0xFF9A6A1A) to Color(0xFFFFE6B0)
    v <= 8 -> Color(0xFF5D7A22) to Color(0xFFE6F5C8)
    v <= 9 -> Color(0xFF9CC63A) to Color(0xFF1C2A05)
    else -> Color(0xFFD9F27A) to Color(0xFF1C2A05)
}
private val RatingScale = listOf(Color(0xFF5A1A1F), Color(0xFF8A3A1C), Color(0xFF9A6A1A), Color(0xFF5D7A22), Color(0xFF9CC63A), Color(0xFFD9F27A))
private val Faded = Color(0xFF5C5C5C)
internal fun scoreText(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',')
private fun verdict(v: Double) = when { v >= 9 -> t("Efsane"); v >= 7 -> t("İyi"); v >= 5 -> t("İdare eder"); else -> t("Beğenmedim") }

private data class Period(val short: String, val long: String, val days: Int?)
private val Periods get() = listOf(Period("7", t("Son 7 gün"), 7), Period("30", t("Son 30 gün"), 30), Period(t("3 ay"), t("Son 3 ay"), 90),
    Period(t("6 ay"), t("Son 6 ay"), 180), Period(t("1 yıl"), t("Son 1 yıl"), 365), Period(t("Tümü"), t("Tümü"), null))

private sealed interface RateTarget {
    data class Film(val media: MediaBlock, val initial: Double?, val past: Boolean, val inList: Boolean) : RateTarget
    data class Episode(val media: MediaBlock, val episode: SeriesEpisode, val initial: Double?) : RateTarget
    data class Season(val media: MediaBlock, val season: Int, val episodes: List<SeriesEpisode>) : RateTarget
}

/**
 * "Puanlarım": watched films and series in one list, newest first, each against the person's own average.
 * Period picker: faded options at the top right; the chosen one slides to the middle. It scrolls away with the list.
 */
@Composable
internal fun RatingsScreen(reduceAnimations: Boolean, onDismiss: () -> Unit, designStyle: DesignStyle = DesignStyle.Glass) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val repo = remember(context, uid) { uid?.let { MediaRatingRepository(context, it) } }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RatingEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(Periods.lastIndex) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<RateTarget?>(null) }
    var searching by remember { mutableStateOf(false) }
    var addPast by remember { mutableStateOf(true) }
    LaunchedEffect(repo) {
        if (repo == null) {
            error = t("Puanlar için REAL'e giriş yap."); entries = emptyList()
            com.cagri.real.ui.auth.SignInRequired.needed(com.cagri.real.ui.auth.SignInRequired.Reason.Ratings)
            return@LaunchedEffect
        }
        entries = try { repo.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = t("Puanlar okunamadı."); emptyList() }
    }
    fun save(block: suspend MediaRatingRepository.() -> List<RatingEntry>) {
        val r = repo ?: return
        scope.launch {
            try { entries = r.block(); error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: t("Kaydedilemedi.") }
        }
    }
    val series = entries?.firstOrNull { it.key == seriesKey }
    BackHandler { if (seriesKey != null) seriesKey = null else onDismiss() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0C0C0C)).pointerInput(Unit) { detectTapGestures { } }) {
        val all = entries.orEmpty()
        val days = Periods[period].days
        val now = System.currentTimeMillis()
        val inPeriod = all.filter { e -> days == null || (e.addedAt != null && now - e.addedAt <= TimeUnit.DAYS.toMillis(days.toLong())) }
            .sortedWith(compareByDescending<RatingEntry> { it.addedAt ?: Long.MIN_VALUE }.thenByDescending { it.order })
        // Genres of the whole list, most common first: the row stays the same when the period changes, so a genre and
        // a period can be chosen together.
        val genres = remember(all) { all.flatMap(::genresOf).groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
            .let { list -> genre?.takeIf { it !in list }?.let { list + it } ?: list }
        val shown = genre?.let { g -> inPeriod.filter { g in genresOf(it) } } ?: inPeriod
        val rated = shown.mapNotNull { it.value }
        val avg = rated.takeIf { it.isNotEmpty() }?.average()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Spacer(Modifier.height(44.dp))
                    val count = animateFloatAsState((avg ?: 0.0).toFloat(), if (reduceAnimations) snap() else tween(500), label = "ratingAvg")
                    Text(if (avg == null) "–" else scoreText((count.value * 10).roundToInt() / 10.0), color = Color.White, fontSize = 56.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    Text("ortalaman", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
                    // One row: genres pull in from the left of "Hepsi", periods from the right of "Tümü".
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)) {
                        val genreLabels = remember(genres) { genres.reversed() + t("Hepsi") }
                        Box(Modifier.weight(1f)) {
                            AnchoredPicker(genreLabels, genreLabels.indexOf(genre ?: t("Hepsi")).takeIf { it >= 0 } ?: genreLabels.lastIndex,
                                rtl = false, reduce = reduceAnimations) { i -> genre = genreLabels[i].takeIf { i != genreLabels.lastIndex } }
                        }
                        Box(Modifier.weight(1f)) {
                            AnchoredPicker(PeriodOrder.map { PeriodLabels[it] }, PeriodOrder.indexOf(period), rtl = true,
                                reduce = reduceAnimations) { period = PeriodOrder[it] }
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }
                    if (shown.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(t("altında"), t("ortalama"), t("üstünde")).forEach { Text(it, color = Color(0xFF555555), fontSize = 10.sp) }
                    }
                }
            }
            if (entries != null && shown.isEmpty()) item(key = "empty") {
                Text(if (days == null) t("Keşifte İzledim dediğin film ve diziler burada puanlanır. İzlediklerini aşağıdan da ekleyebilirsin.")
                    else t("Bu aralıkta eklenen yok."), color = Color(0xFF777777), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 20.dp))
            }
            items(shown, key = { it.key }) { e ->
                RatingRow(e, avg, now) {
                    if (e.series) seriesKey = e.key else target = RateTarget.Film(e.media, e.score, past = false, inList = true)
                }
            }
            tasteLine(inPeriod)?.let { line -> item(key = "taste") {
                Text(line, color = Color(0xFF8A8A8A), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp))
            } }
            item(key = "add") {
                // "Ekle": watched now (today's date); "Geçmişten ekle": no date ("eskiden").
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false to t("Ekle"), true to t("Geçmişten ekle")).forEach { (past, label) ->
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button) { addPast = past; searching = true }.padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(ActionGlyph.Add, Modifier.size(16.dp), Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        RealBackButton(onDismiss, Modifier.statusBarsPadding().padding(start = 20.dp, top = 10.dp), t("Yaşam'a dön"))

        AnimatedVisibility(series != null,
            enter = if (reduceAnimations) EnterTransition.None else slideInHorizontally(tween(380)) { it } + fadeIn(tween(200)),
            exit = if (reduceAnimations) ExitTransition.None else slideOutHorizontally(tween(320)) { it } + fadeOut(tween(200))) {
            var last by remember { mutableStateOf(series) }
            if (series != null) last = series
            last?.let { s -> SeriesGrid(s, repo, onBack = { seriesKey = null }, onRate = { target = it },
                onRemove = { seriesKey = null; save { remove(s.key) } }) }
        }
    }

    if (searching) MediaSearchSheet(designStyle, onDismiss = { searching = false }) { media ->
        searching = false
        val past = addPast
        if (media.type == MediaType.TV) { save { add(media, past = past) }; seriesKey = com.cagri.real.discovery.ratingKey(media) }
        else target = RateTarget.Film(media, entries?.firstOrNull { it.key == com.cagri.real.discovery.ratingKey(media) }?.score, past = past, inList = false)
    }
    target?.let { t ->
        when (t) {
            is RateTarget.Film -> {
                val film = rememberFilmReminder(repo, t.media)
                RatingSheet(t.media.title, listOfNotNull(t.media.year?.toString(), t("Film")).joinToString(" · "),
                    film.text, t.initial, onDismiss = { target = null },
                    onRemove = if (t.inList) ({ target = null; save { remove(com.cagri.real.discovery.ratingKey(t.media)) } }) else null,
                    removeLabel = t("Listeden çıkar"), reminderLoading = film.loading, onSkipLoading = film.skip) { v ->
                    target = null; save { rate(t.media, v, t.past) }
                }
            }
            is RateTarget.Episode -> {
                // No Turkish from TheTVDB: this one episode is translated (once, then kept on the phone).
                // Until it arrives the sheet shows a quiet placeholder, never the English text first.
                var episode by remember(t) { mutableStateOf(t.episode) }
                var loading by remember(t) { mutableStateOf(!t.episode.translated && repo != null) }
                LaunchedEffect(t) {
                    if (loading) {
                        val translated = awaitAtMost(TRANSLATION_WAIT_MS) { repo?.translate(t.media, t.episode) }
                        // A tap on the placeholder already showed the original text; it is not swapped afterwards.
                        if (loading) { translated?.let { episode = it }; loading = false }
                    }
                }
                RatingSheet(episode.name, "S${t.episode.season} · B${t.episode.number} · ${t.media.title}",
                episode.summary, t.initial, onDismiss = { target = null }, reminderLoading = loading, titleLoading = loading,
                onSkipLoading = { loading = false },
                onRemove = if (t.initial != null) ({ target = null; save { rateEpisodes(t.media, mapOf(t.episode.key to null)) } }) else null) { v ->
                target = null; save { rateEpisodes(t.media, mapOf(t.episode.key to v)) }
            }
            }
            is RateTarget.Season -> RatingSheet(t("{0}. sezonun tamamı", t.season), t.media.title,
                t("Bütün bölümlere aynı puan verilir. Sonra istediğin bölümü tek tek değiştirebilirsin."), null,
                onDismiss = { target = null }, onRemove = null) { v ->
                target = null; save { rateEpisodes(t.media, t.episodes.associate { it.key to v }) }
            }
        }
    }
}

/** Turkish genre labels of a film / series ("Drama, Sci-Fi" → Dram, Bilim kurgu). */
private fun genresOf(e: RatingEntry): List<String> = e.media.genre.orEmpty().split(',', '/', '|')
    .mapNotNull { com.cagri.real.discovery.DiscoveryRanking.displayGenre(it) }.distinct()

/** "En sevdiğin tür …": genres with at least two scores; null until there are two such genres. */
private fun tasteLine(list: List<RatingEntry>): String? {
    val byGenre = list.flatMap { e -> e.value?.let { v -> genresOf(e).map { it to v } }.orEmpty() }.groupBy({ it.first }, { it.second })
        .filterValues { it.size >= 2 }.mapValues { it.value.average() }
    if (byGenre.size < 2) return null
    val best = byGenre.maxBy { it.value }; val worst = byGenre.minBy { it.value }
    fun f(v: Double) = scoreText((v * 10).roundToInt() / 10.0)
    return t("En sevdiğin tür {0} ({1}), en az sevdiğin {2} ({3}).", best.key, f(best.value), worst.key, f(worst.value))
}

private val PeriodLabels get() = Periods.map { if (it.days != null && it.days <= 30) t("{0} gün", it.short) else t(it.short) }
/** Picker order: "Tümü" at the edge, then 7 gün, 30 gün, 3 ay… as they come in from the right (nearest first). */
private val PeriodOrder = Periods.indices.filter { Periods[it].days != null }.reversed() + Periods.indexOfFirst { it.days == null }

/**
 * A picker with its default ("Hepsi" / "Tümü") at one edge and the other options waiting off screen beyond it
 * (left of "Hepsi"; right of "Tümü" when [rtl]). Pulling the row brings them in; whichever word reaches the edge is
 * chosen while scrolling, and the row snaps to it. The default is the last label.
 */
@Composable
private fun AnchoredPicker(labels: List<String>, selected: Int, rtl: Boolean, reduce: Boolean, onPick: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, labels.lastIndex))
    val scope = rememberCoroutineScope()
    val latestPick by androidx.compose.runtime.rememberUpdatedState(onPick)
    // The item nearest the anchor edge (offsets run from the start edge, the right one in rtl).
    val chosen by remember(labels) {
        androidx.compose.runtime.derivedStateOf { state.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index ?: labels.lastIndex }
    }
    // Only a drag or a tap picks; a list that changed under the row does not.
    LaunchedEffect(chosen) { if (state.isScrollInProgress && chosen != selected) latestPick(chosen) }
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress && chosen != selected) latestPick(chosen) }
    LaunchedEffect(labels) { state.scrollToItem(selected.coerceIn(0, labels.lastIndex)) }
    val fadePx = with(LocalDensity.current) { 90.dp.toPx() }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            LazyRow(state = state, modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = maxWidth),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start)) {
                itemsIndexed(labels) { i, label ->
                    val on = i == chosen
                    val color by animateColorAsState(if (on) Color.White else Faded, if (reduce) snap() else tween(180), label = "anchored")
                    Text(label, color = color, fontSize = if (on) 14.sp else 12.sp, maxLines = 1,
                        modifier = Modifier
                            // Only the chosen word stays; the others fade out as they move away from the edge.
                            .graphicsLayer {
                                val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                                alpha = if (item == null) 0f else (1f - abs(item.offset) / fadePx).coerceIn(0f, 1f)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { scope.launch { if (reduce) state.scrollToItem(i) else state.animateScrollToItem(i) } }
                            .padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RatingRow(e: RatingEntry, avg: Double?, now: Long, onClick: () -> Unit) {
    val v = e.value
    val (tone, _) = ratingColors(v)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MediaArtwork(e.media, Modifier.width(36.dp).height(52.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.media.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.series) Text("dizi", color = Color(0xFF9A9A9A), fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp)
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                Spacer(Modifier.weight(1f))
                Text(v?.let(::scoreText) ?: if (e.series) t("Bölümleri puanla") else t("Puan ver"),
                    color = if (v != null) tone else Color(0xFF9A9A9A), fontSize = if (v != null) 15.sp else 12.sp, fontWeight = FontWeight.Medium)
            }
            val diff = if (v != null && avg != null) v - avg else null
            Box(Modifier.fillMaxWidth().padding(top = 7.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF161616))) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val half = maxWidth / 2
                    if (diff != null) {
                        val w = half * (abs(diff) / 4.6).coerceAtMost(1.0).toFloat()
                        Box(Modifier.offset(x = if (diff >= 0) half else half - w).width(w).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(tone))
                    }
                    Box(Modifier.offset(x = half).width(1.dp).fillMaxHeight().background(Color(0xFF444444)))
                }
            }
            val whenText = e.addedAt?.let { at ->
                java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
            } ?: "eskiden"
            Text(listOfNotNull(diff?.let { (if (it >= 0) "+" else "−") + scoreText((abs(it) * 10).roundToInt() / 10.0) }, whenText).joinToString(" · "),
                color = Color(0xFF666666), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Series page: seasons × episodes heat grid; tap an episode to rate it, tap a season to rate all of it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesGrid(entry: RatingEntry, repo: MediaRatingRepository?, onBack: () -> Unit, onRate: (RateTarget) -> Unit,
                       onRemove: () -> Unit) {
    var confirmRemove by remember(entry.key) { mutableStateOf(false) }
    if (confirmRemove) RealAlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(t("Diziyi listeden çıkarmak istediğine emin misin?")) },
        text = { Text(t("{0} ve bölüm puanları Puanlarım'dan silinir.", entry.media.title), color = Color(0xFFA5A5AF), fontSize = 14.sp) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(t("Çıkar"), color = Color(0xFFFF8A80)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) { Text(t("Vazgeç"), color = Color.White) } }
    )
    var episodes by remember(entry.key) { mutableStateOf<List<SeriesEpisode>?>(null) }
    var error by remember(entry.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.key) {
        try { episodes = repo?.episodes(entry.media) ?: emptyList() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0C0C0C)).pointerInput(Unit) { detectTapGestures { } }) {
        // The poster's own light behind the top (blurred on Android 12+; plain dark before).
        if (android.os.Build.VERSION.SDK_INT >= 31 && !entry.media.artworkUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(entry.media.artworkUrl, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(420.dp).blur(70.dp).alpha(0.45f))
            Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0C0C0C)))))
        }
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 58.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MediaArtwork(entry.media, Modifier.width(132.dp).height(192.dp))
                    Text(entry.media.title, color = Color.White, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                    val list = episodes
                    Text(listOfNotNull(entry.media.year?.toString(),
                        if (list == null) t("Bölümler yükleniyor…") else t("{0} sezon · {1} bölüm", list.map { it.season }.distinct().size, list.size)).joinToString(" · "),
                        color = Color(0xFF999999), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
                        Text(entry.value?.let(::scoreText) ?: "–", color = if (entry.value != null) ratingColors(entry.value).first else Color(0xFF555555),
                            fontSize = 44.sp, fontWeight = FontWeight.Medium, lineHeight = 44.sp)
                        Text(if (entry.value != null) t("  bölüm ortalaman") else t("  henüz bölüm puanı yok"), color = Color(0xFF888888), fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 7.dp))
                    }
                }
                error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
            }
            val bySeason = episodes.orEmpty().groupBy { it.season }.toSortedMap()
            items(bySeason.entries.toList(), key = { it.key }) { (season, eps) ->
                val scores = eps.mapNotNull { entry.episodes[it.key] }
                val sAvg = scores.takeIf { it.isNotEmpty() }?.average()
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("S$season", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.width(30.dp).clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onRate(RateTarget.Season(entry.media, season, eps)) }.padding(vertical = 7.dp))
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        eps.forEach { ep ->
                            val score = entry.episodes[ep.key]
                            val (bg, fg) = ratingColors(score)
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                                .clickable(role = Role.Button) { onRate(RateTarget.Episode(entry.media, ep, score)) }, contentAlignment = Alignment.Center) {
                                Text(score?.let { it.toInt().toString() } ?: "·", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text(sAvg?.let { scoreText((it * 10).roundToInt() / 10.0) } ?: "–", color = if (sAvg != null) ratingColors(sAvg).first else Color(0xFF555555),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(38.dp).padding(top = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            item {
                if (!episodes.isNullOrEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Kötü"), color = Color(0xFF777777), fontSize = 10.sp)
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(RatingScale)))
                        Text(t("Efsane"), color = Color(0xFF777777), fontSize = 10.sp)
                    }
                }
                Text(t("Listeden çıkar"), color = Color(0xFFFF8A80), fontSize = 14.sp,
                    modifier = Modifier.padding(top = 28.dp, bottom = 40.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { confirmRemove = true }.padding(vertical = 8.dp))
            }
        }
        RealBackButton(onBack, Modifier.statusBarsPadding().padding(start = 20.dp, top = 10.dp), t("Puanlarım'a dön"))
    }
}

/**
 * Rating sheet: big number, verdict, a short reminder (episode / film summary) and 20 bars (0,5 steps) to drag or tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(title: String, meta: String, reminder: String?, initial: Double?, onDismiss: () -> Unit,
                         onRemove: (() -> Unit)?, removeLabel: String = t("Puanı kaldır"), reminderLoading: Boolean = false,
                         titleLoading: Boolean = false, onSkipLoading: (() -> Unit)? = null, onSave: (Double) -> Unit) {
    var value by remember { mutableDoubleStateOf(initial ?: 7.0) }
    var expanded by remember { mutableStateOf(false) }
    val reduce = LocalLiquidReduceMotion.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RealSheetDefaults.Ink, contentColor = Color.White,
        scrimColor = RealSheetDefaults.Scrim, shape = RealSheetDefaults.Shape, dragHandle = { RealSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(meta, color = Color(0xFF888888), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Tapping a placeholder shows the original (untranslated) text right away.
            val skip = Modifier.clickable(enabled = onSkipLoading != null) { onSkipLoading?.invoke() }
            if (titleLoading) SkeletonLine(0.55f, 18.dp, Modifier.padding(top = 8.dp, bottom = 4.dp).then(skip))
            else Text(title, color = Color.White, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (reminderLoading) Column(Modifier.padding(top = 10.dp).fillMaxWidth().then(skip), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonLine(1f, 11.dp); SkeletonLine(0.92f, 11.dp); SkeletonLine(0.6f, 11.dp)
            } else if (!reminder.isNullOrBlank()) {
                // Tap opens it smoothly; a long text then scrolls inside a bounded box instead of pushing the sheet.
                var overflows by remember(reminder) { mutableStateOf(false) }
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().animateContentSize(if (reduce) snap() else tween(320))
                    .clip(RoundedCornerShape(8.dp)).clickable(enabled = overflows || expanded) { expanded = !expanded }) {
                    if (expanded) Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(reminder, color = Color(0xFFB0B0B0), fontSize = 13.sp, lineHeight = 19.sp)
                    } else Text(reminder, color = Color(0xFF9A9A9A), fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, onTextLayout = { overflows = it.hasVisualOverflow })
                    if (overflows || expanded) Text(if (expanded) t("Daha az") else t("Devamı"), color = Color(0xFF6A6A6A), fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Text(scoreText(value), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Medium, lineHeight = 64.sp)
                Text(" / 10", color = Color(0xFF888888), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Text(verdict(value), color = ratingColors(value).first.let { if (value <= 7) Color(0xFFBBBBBB) else it }, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp).height(72.dp)) {
                val widthPx = constraints.maxWidth.toFloat()
                val view = androidx.compose.ui.platform.LocalView.current
                // Each half point the finger passes ticks, like a ruler under the thumb.
                fun pick(x: Float) {
                    val next = (ceil((x / widthPx).coerceIn(0.001f, 1f) * 20) / 2.0).coerceIn(0.5, 10.0)
                    if (next != value) { value = next; RealHaptics.tick(view) }
                }
                Row(Modifier.fillMaxSize()
                    .pointerInput(widthPx) { detectTapGestures { pick(it.x) } }
                    .pointerInput(widthPx) { detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> change.consume(); pick(change.position.x) } },
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    val tone = ratingColors(value).first
                    for (i in 1..20) {
                        Box(Modifier.weight(1f).fillMaxHeight(0.25f + i * 0.0375f).clip(RoundedCornerShape(2.dp))
                            .background(if (i / 2.0 <= value) tone else Color(0xFF2A2A2A)))
                    }
                }
            }
            Text(t("Sürükle veya bas"), color = Color(0xFF666666), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Listeden çıkar" needs the room at large font sizes (it wrapped and got clipped at 1 : 2).
                SheetButton(if (onRemove != null) removeLabel else t("Vazgeç"), Modifier.weight(1f), filled = false) { (onRemove ?: onDismiss)() }
                SheetButton(t("Kaydet"), Modifier.weight(1.3f), filled = true) { onSave(value) }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
        .then(if (filled) Modifier.background(Color.White) else Modifier.border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(24.dp)))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.Black else Color(0xFFBBBBBB), fontSize = 15.sp, fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

/** Film reminder in Turkish (TheTVDB, else AI once); loading = true until it is known. Falls back to the stored text. */
@Composable
internal fun rememberFilmReminder(repo: MediaRatingRepository?, media: MediaBlock): FilmReminder {
    var text by remember(media.id) { mutableStateOf<String?>(null) }
    var loading by remember(media.id) { mutableStateOf(repo != null) }
    LaunchedEffect(media.id) {
        if (repo == null) { text = media.description; return@LaunchedEffect }
        val result = awaitAtMost(TRANSLATION_WAIT_MS) { repo.filmSummary(media) } ?: media.description
        if (loading) { text = result; loading = false }
    }
    return FilmReminder(if (loading) null else text, loading) { if (loading) { text = media.description; loading = false } }
}

/** A translation that is not back within this time gives way to the original text. */
private const val TRANSLATION_WAIT_MS = 3_000L
private val translationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

/** Runs [work] outside the sheet, so a slow translation still finishes and is kept for next time; waits at most [millis]. */
private suspend fun <T> awaitAtMost(millis: Long, work: suspend () -> T?): T? {
    val job = translationScope.async { runCatching { work() }.getOrNull() }
    return kotlinx.coroutines.withTimeoutOrNull(millis) { job.await() }
}

/** [skip]: show the original text now (a tap on the placeholder). */
internal class FilmReminder(val text: String?, val loading: Boolean, val skip: () -> Unit)

/** Quiet placeholder line that breathes while a translation loads. */
@Composable
private fun SkeletonLine(fraction: Float, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(0.05f, 0.12f, androidx.compose.animation.core.infiniteRepeatable(
        tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "skeletonAlpha")
    Box(modifier.fillMaxWidth(fraction).height(height).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = alpha)))
}
