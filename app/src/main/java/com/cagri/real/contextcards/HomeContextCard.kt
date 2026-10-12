package com.cagri.real.contextcards

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.books.BookShelfStore
import com.cagri.real.data.BeverageType
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.WaterTrackerRepository
import com.cagri.real.platform.android.sync.SyncLocal
import com.cagri.real.ui.components.WidgetGlowCard
import com.cagri.real.ui.theme.RealType
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

internal fun ContextCardType.accent(): Color = when (this) {
    ContextCardType.WATER -> WaterAccent
    ContextCardType.MOOD -> MoodAccent
    ContextCardType.BOOK -> BookAccent
    ContextCardType.MEDIA -> MediaAccent
}

private suspend fun <T> safely(block: suspend () -> T): T? =
    try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

/**
 * Rare, gentle nudge shown as the LAST item of the Home list.
 * It only reads existing module data; the only thing it ever writes outside its own preferences is
 * the optional "+250 ml" quick action, which goes through the normal [WaterTrackerRepository].
 */
@Composable
fun HomeContextCard(
    moodEntries: List<MoodTrackerEntry>,
    reduceAnimations: Boolean,
    onOpenWater: () -> Unit,
    onOpenMood: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    content: @Composable (ContextCardType?, @Composable () -> Unit) -> Unit = { _, renderCard -> renderCard() }
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

    var card by remember { mutableStateOf<ContextCard?>(null) }
    var lastCard by remember { mutableStateOf<ContextCard?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val externalTick by ContextCardBus.tick.collectAsState()
    var actionInProgress by remember { mutableStateOf(false) }
    val waterProgress = remember { Animatable(0.02f) }

    LaunchedEffect(card?.type, card?.progress, actionInProgress) {
        if (!actionInProgress && card?.type == ContextCardType.WATER) {
            waterProgress.snapTo((card?.progress ?: 0f).coerceIn(0.02f, 1f))
        }
    }

    LaunchedEffect(owner, moodEntries, refresh, externalTick, actionInProgress, active) {
        // Repository/bus updates must not remove the card halfway through its quick-action animation.
        if (!active) {
            card = null
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
                    val signals = ContextCardSignals(
                        now = now,
                        waterTodayMl = waterMl,
                        waterGoalMl = water?.goalMl ?: 2_000,
                        moodLoggedToday = moodsToday.isNotEmpty(),
                        lastMoodToday = moodsToday.firstOrNull()?.mood,
                        unfinishedBookTitle = book?.title,
                        unfinishedBookAuthor = book?.author
                    )
                    val result = ContextCardEngine.decide(signals, store.load())
                    result.activate?.let { type ->
                        store.update(notify = false) { it.activated(type, now.toInstant().toEpochMilli(), today.toEpochDay()) }
                    }
                    result
                }
                if (!actionInProgress) {
                    decision.card?.let { lastCard = it }
                    card = decision.card
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
        card = null
        afterwards()
        refresh++
    }

    fun finish(type: ContextCardType, dismissed: Boolean, afterwards: () -> Unit = {}) {
        if (actionInProgress || card == null) return
        actionInProgress = true
        scope.launch {
            try {
                complete(type, dismissed, afterwards)
            } finally {
                actionInProgress = false
            }
        }
    }

    fun open(type: ContextCardType) = when (type) {
        ContextCardType.WATER -> onOpenWater()
        ContextCardType.MOOD -> onOpenMood()
        ContextCardType.BOOK -> onOpenBooks()
        ContextCardType.MEDIA -> onOpenMedia()
    }

    content(if (active) card?.type else null) {
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = card != null,
            enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(260)) + expandVertically(tween(260)),
            exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(180)) + shrinkVertically(tween(200))
        ) {
            val shown = card ?: lastCard
            if (shown != null) {
                ContextCardContent(
                    card = shown,
                    reduceAnimations = reduceAnimations,
                    enabled = !actionInProgress && card != null,
                    progressValue = { waterProgress.value },
                    onOpen = { finish(shown.type, dismissed = false) { open(shown.type) } },
                    onPrimary = {
                        if (shown.type == ContextCardType.WATER) {
                            if (!actionInProgress && card != null) {
                                actionInProgress = true
                                scope.launch {
                                    try {
                                        val added = safely { withContext(Dispatchers.IO) { waterRepository.add(250) } }
                                            ?: return@launch
                                        val now = ZonedDateTime.now()
                                        val totalMl = added.entriesOn(now.toLocalDate(), now.zone)
                                            .filter { it.beverage == BeverageType.WATER }.sumOf { it.amountMl }
                                        val updated = ContextCardEngine.build(
                                            ContextCardType.WATER,
                                            ContextCardSignals(now = now, waterTodayMl = totalMl, waterGoalMl = added.goalMl)
                                        )
                                        waterProgress.snapTo((shown.progress ?: 0f).coerceIn(0.02f, 1f))
                                        card = updated
                                        lastCard = updated
                                        val target = (updated.progress ?: 0f).coerceIn(0.02f, 1f)
                                        if (reduceAnimations) waterProgress.snapTo(target)
                                        else waterProgress.animateTo(target, tween(520, easing = FastOutSlowInEasing))
                                        // Dismiss only when the actual progress animation has finished.
                                        complete(shown.type, dismissed = false)
                                    } finally {
                                        actionInProgress = false
                                    }
                                }
                            }
                        } else finish(shown.type, dismissed = false) { open(shown.type) }
                    },
                    onSecondary = { finish(shown.type, dismissed = true) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun ContextCardContent(
    card: ContextCard,
    reduceAnimations: Boolean,
    enabled: Boolean,
    progressValue: () -> Float,
    onOpen: () -> Unit,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = card.type.accent()
    WidgetGlowCard(accent, reduceAnimations, 0, modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(card.eyebrow, color = accent, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(card.title, color = Color.White, style = RealType.editorialSection)
        Text(card.message, color = Color.White.copy(alpha = .65f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        card.progress?.let { progress ->
            Box(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f))
                    .drawBehind {
                        // Read animated state in draw, without recomposing the whole card every frame.
                        val fraction = if (card.type == ContextCardType.WATER) progressValue() else progress
                        drawRect(accent, size = Size(size.width * fraction.coerceIn(0.02f, 1f), size.height))
                    }
            ) {}
        }
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                card.primaryLabel, color = Color.Black, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onPrimary)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
            Text(
                card.secondaryLabel, color = Color.White.copy(alpha = .55f), fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSecondary)
                    .padding(horizontal = 6.dp, vertical = 10.dp)
            )
        }
    }
}
