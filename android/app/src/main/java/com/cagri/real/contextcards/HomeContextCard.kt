package com.cagri.real.contextcards

import android.content.Context
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.cagri.real.ui.components.RealHaptics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.books.BookShelfStore
import com.cagri.real.data.BeverageType
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.WaterTrackerRepository
import com.cagri.real.discovery.MediaRatingRepository
import com.cagri.real.platform.android.sync.SyncLocal
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime

private val WaterAccent = Color(0xFF5FB4E6)
private val MoodAccent = Color(0xFFB69BE8)
private val BookAccent = Color(0xFFD5B786)
private val MediaAccent = Color(0xFFDDD4C6)
private val WatchAccent = Color(0xFFE8B04F)
private val ListenAccent = Color(0xFF7FD3A8)
private val PlayAccent = Color(0xFF8FA8FF)
private val JournalAccent = Color(0xFFF2A6A0)

internal fun ContextCardType.accent(): Color = when (this) {
    ContextCardType.WATER -> WaterAccent
    ContextCardType.MOOD -> MoodAccent
    ContextCardType.BOOK -> BookAccent
    ContextCardType.MEDIA -> MediaAccent
    ContextCardType.WATCH -> WatchAccent
    ContextCardType.LISTEN -> ListenAccent
    ContextCardType.PLAY -> PlayAccent
    ContextCardType.JOURNAL -> JournalAccent
}

private suspend fun <T> safely(block: suspend () -> T): T? =
    try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

/** "season:number" keys of rated episodes -> the highest one. */
private fun lastEpisodeOf(keys: Collection<String>): Pair<Int, Int>? = keys.mapNotNull { key ->
    val parts = key.split(":")
    val season = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
    val number = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
    season to number
}.maxWithOrNull(compareBy({ it.first }, { it.second }))

/**
 * A rare, gentle nudge (at most two a day, one at a time) shown at the TOP of the Home cards, looking like a card. It only reads existing module data; the only thing it ever writes outside its own preferences is the
 * optional "+250 ml" quick action, which goes through the normal [WaterTrackerRepository].
 */
@Composable
fun HomeContextCard(
    moodEntries: List<MoodTrackerEntry>,
    reduceAnimations: Boolean,
    /** Opens the module behind a card (water, mood, books, discovery, ratings, a new journal). */
    onOpen: (ContextCardType) -> Unit,
    modifier: Modifier = Modifier,
    /** null = journal data not known yet; the journal card waits. */
    journalWrittenToday: Boolean? = null,
    active: Boolean = true,
    content: @Composable (List<ContextCardType>, @Composable () -> Unit) -> Unit = { _, renderCard -> renderCard() }
) {
    val appContext = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "guest" }
    val store = remember(appContext) {
        ContextCardStore(SyncLocal.preferences(appContext, ContextCardStore.PREFERENCES_NAME, Context.MODE_PRIVATE))
    }
    val waterRepository = remember(appContext) {
        WaterTrackerRepository(SyncLocal.preferences(appContext, WaterTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE))
    }
    val bookStore = remember(appContext, uid) { BookShelfStore(appContext, uid) }
    val ratings = remember(appContext, uid) { MediaRatingRepository(appContext, uid) }
    val installedAt = remember(appContext) {
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime }.getOrNull()
    }

    var cards by remember { mutableStateOf<List<ContextCard>>(emptyList()) }
    var lastCards by remember { mutableStateOf<List<ContextCard>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    val externalTick by ContextCardBus.tick.collectAsState()
    var actionInProgress by remember { mutableStateOf(false) }
    val waterProgress = remember { Animatable(0.02f) }
    val waterCard = cards.firstOrNull { it.type == ContextCardType.WATER }

    LaunchedEffect(waterCard?.progress, actionInProgress) {
        if (!actionInProgress && waterCard != null) {
            waterProgress.snapTo((waterCard.progress ?: 0f).coerceIn(0.02f, 1f))
        }
    }

    LaunchedEffect(owner, moodEntries, journalWrittenToday, refresh, externalTick, actionInProgress, active) {
        // Repository/bus updates must not remove a card halfway through its quick-action animation.
        if (!active) {
            cards = emptyList()
            return@LaunchedEffect
        }
        if (actionInProgress) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = ZonedDateTime.now()
                val today = now.toLocalDate()
                val decision = withContext(Dispatchers.IO) {
                    val water = safely { waterRepository.load() }
                    val waterMl = water?.entriesOn(today, now.zone)
                        ?.filter { it.beverage == BeverageType.WATER }?.sumOf { it.amountMl }
                    val moodsToday = moodEntries.filter {
                        Instant.ofEpochMilli(it.details.loggedAt).atZone(now.zone).toLocalDate() == today
                    }
                    val book = safely { bookStore.load().lastOrNull { bookStore.page(it) > 0 } }
                    // A series moves to the end of the list whenever an episode is rated: the last one is the current one.
                    val series = safely { ratings.peek().lastOrNull { it.series && it.episodes.isNotEmpty() } }
                    val signals = ContextCardSignals(
                        now = now,
                        waterTodayMl = waterMl,
                        waterGoalMl = water?.goalMl ?: 2_000,
                        moodLoggedToday = moodsToday.isNotEmpty(),
                        lastMoodToday = moodsToday.firstOrNull()?.mood,
                        unfinishedBookTitle = book?.title,
                        unfinishedBookAuthor = book?.author,
                        installedAt = installedAt,
                        seriesInProgress = series?.media?.title,
                        lastEpisode = series?.let { lastEpisodeOf(it.episodes.keys) },
                        journalWrittenToday = journalWrittenToday
                    )
                    val result = ContextCardEngine.decide(signals, store.load())
                    if (result.activate.isNotEmpty()) {
                        store.update(notify = false) { it.activated(result.activate, now.toInstant().toEpochMilli(), today.toEpochDay()) }
                    }
                    result
                }
                if (!actionInProgress) {
                    if (decision.cards.isNotEmpty()) lastCards = decision.cards
                    cards = decision.cards
                }
                delay(60_000L)
            }
        }
    }

    suspend fun complete(type: ContextCardType, dismissed: Boolean, afterwards: () -> Unit = {}) {
        val nowMs = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            store.update(notify = false) { if (dismissed) it.dismissed(type, nowMs) else it.completed(type, nowMs) }
        }
        cards = cards.filterNot { it.type == type }
        afterwards()
        refresh++
    }

    fun finish(type: ContextCardType, dismissed: Boolean, afterwards: () -> Unit = {}) {
        if (actionInProgress || cards.none { it.type == type }) return
        actionInProgress = true
        scope.launch {
            try {
                complete(type, dismissed, afterwards)
            } finally {
                actionInProgress = false
            }
        }
    }

    fun addWater(shown: ContextCard) {
        if (actionInProgress || cards.none { it.type == ContextCardType.WATER }) return
        actionInProgress = true
        scope.launch {
            try {
                val added = safely { withContext(Dispatchers.IO) { waterRepository.add(250) } } ?: return@launch
                val now = ZonedDateTime.now()
                val totalMl = added.entriesOn(now.toLocalDate(), now.zone)
                    .filter { it.beverage == BeverageType.WATER }.sumOf { it.amountMl }
                val updated = ContextCardEngine.build(
                    ContextCardType.WATER,
                    ContextCardSignals(now = now, waterTodayMl = totalMl, waterGoalMl = added.goalMl)
                )
                waterProgress.snapTo((shown.progress ?: 0f).coerceIn(0.02f, 1f))
                cards = cards.map { if (it.type == ContextCardType.WATER) updated else it }
                lastCards = cards
                val target = (updated.progress ?: 0f).coerceIn(0.02f, 1f)
                if (reduceAnimations) waterProgress.snapTo(target)
                else waterProgress.animateTo(target, tween(520, easing = FastOutSlowInEasing))
                // Dismiss only when the actual progress animation has finished.
                complete(ContextCardType.WATER, dismissed = false)
            } finally {
                actionInProgress = false
            }
        }
    }

    content(if (active) cards.map { it.type } else emptyList()) {
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = cards.isNotEmpty(),
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(260)) + expandVertically(tween(260)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(180)) + shrinkVertically(tween(200))
        ) {
            // Normally a single card; the Settings "show all" test hook stacks every type.
            Column(Modifier.fillMaxWidth()) {
                cards.ifEmpty { lastCards }.forEach { card ->
                    key(card.type) {
                        val live = cards.any { it.type == card.type }
                        ContextCardTile(
                            card = card,
                            enabled = !actionInProgress && live,
                            reduceAnimations = reduceAnimations,
                            progressValue = { waterProgress.value },
                            onOpen = { finish(card.type, dismissed = false) { onOpen(card.type) } },
                            onWater = { addWater(card) },
                            onDismiss = { finish(card.type, dismissed = true) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

private val TileShape = RoundedCornerShape(22.dp)

/**
 * Looks like one of the person's own cards (same shape and place in the list), tinted with its colour so it reads as
 * a REAL suggestion. Tap opens it, a swipe to the left puts it away ("Şimdi değil"); the water card has a +250 ml pill.
 */
@Composable
private fun ContextCardTile(
    card: ContextCard,
    enabled: Boolean,
    reduceAnimations: Boolean,
    progressValue: () -> Float,
    onOpen: () -> Unit,
    onWater: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = card.type.accent()
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val threshold = with(density) { 96.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (-offset.value / (widthPx.coerceAtLeast(1) * 0.9f)).coerceIn(0f, 1f)
            }
            .pointerInput(enabled, reduceAnimations) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offset.value < -threshold) {
                            scope.launch {
                                if (!reduceAnimations) offset.animateTo(-widthPx.toFloat(), tween(180))
                                onDismiss()
                            }
                        } else scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                        armed = false
                    },
                    onDragCancel = { armed = false; scope.launch { offset.animateTo(0f) } }
                ) { change, dx ->
                    val next = (offset.value + dx).coerceAtMost(0f)
                    if (next != offset.value) change.consume()
                    val nowArmed = next < -threshold
                    if (nowArmed && !armed) RealHaptics.tick(view)
                    armed = nowArmed
                    scope.launch { offset.snapTo(next) }
                }
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(card.secondaryLabel) { onDismiss(); true })
            }
            .clip(TileShape)
            .background(Color(0xFF141416))
            .background(accent.copy(alpha = .10f))
            .border(0.5.dp, accent.copy(alpha = .35f), TileShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = card.primaryLabel, onClick = onOpen)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, color = Color(0xFFF2F2F4), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.message, color = Color.White.copy(alpha = .6f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text("REAL önerisi · ${card.eyebrow.lowercase()}", color = accent.copy(alpha = .8f), fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp))
                card.progress?.let { progress ->
                    Box(
                        Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp)
                            .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .10f))
                            .drawBehind {
                                // Read animated state in draw, without recomposing the whole card every frame.
                                val fraction = if (card.type == ContextCardType.WATER) progressValue() else progress
                                drawRect(accent, size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height))
                            }
                    ) {}
                }
            }
            Spacer(Modifier.width(10.dp))
            if (card.type == ContextCardType.WATER) {
                Text(
                    card.primaryLabel, color = Color.Black, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onWater)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                Text("›", color = accent, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
