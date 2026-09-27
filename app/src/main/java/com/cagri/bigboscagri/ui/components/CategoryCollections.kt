package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.ExperimentalSharedTransitionApi

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope

private const val COLLECTIONS_PREVIEW_COUNT = 5
private val COLLECTIONS_BASE_OVERLAP = 32.dp
private val COLLECTIONS_UNDERLAP_EXTENSION = 40.dp

/** Adapted from the approved demo tile; existing category callbacks remain authoritative. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryCollections(
    reminders: List<ReminderItem>, today: LocalDate,
    customCategories: List<CustomCategory>, builtInCategoryColors: Map<ReminderCategory, CategoryColorOverride>,
    onOpenCategory: (ReminderCategory) -> Unit, onOpenCustomCategory: (CustomCategory) -> Unit,
    onAddCategory: () -> Unit, onEditBuiltInCategory: (ReminderCategory) -> Unit,
    selectedCategoryIds: Set<Long>, onToggleSelectCategory: (Long) -> Unit,
    onLongClickCategory: (Long) -> Unit,
    moodEntries: List<MoodTrackerEntry> = emptyList(), onOpenMoodTracker: () -> Unit = {},
    listState: LazyListState? = null, modifier: Modifier = Modifier, reduceAnimations: Boolean = false,
    categoryOrderKeys: List<String> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAllPetOverride by remember { mutableStateOf<String?>(null) }
    val collectionPetPrank = RealPetRuntime.prank
    LaunchedEffect(collectionPetPrank?.token, expanded, reduceAnimations) {
        val prank = collectionPetPrank ?: return@LaunchedEffect
        if (prank.perchId != REAL_PET_PERCH_SHOW_ALL || prank.type != RealPetPrankType.ShowAllTextMeow) {
            return@LaunchedEffect
        }
        RealPetRuntime.consumePrank(prank.token)
        // The cat has already jumped/meowed on this text before the prank is emitted.
        // Change straight to a full meow instead of spelling M -> ME -> MEAW every time.
        showAllPetOverride = listOf("MEAW", "MEAWW", "MEAWWW", "MEAWWWW")[prank.token % 4]
        delay(if (reduceAnimations) 500L else 1_650L)
        showAllPetOverride = null
    }
    val selectionMode = selectedCategoryIds.isNotEmpty()
    val visibleReminders = remember(reminders, customCategories) {
        val hiddenIds = customCategories.filter { it.hideFromGeneralLists }.map { it.id }.toSet()
        reminders.filter { it.customCategoryId !in hiddenIds }
    }
    val builtInCounts = remember(reminders, visibleReminders, today) {
        ReminderCategory.entries.associateWith { category ->
            // "Yalnızca kategori içinde göster" hides a card from general reminder lists,
            // but journals must still be discoverable in Günlüklerim.
            val source = if (category == ReminderCategory.JournalEntries) reminders else visibleReminders
            source.inCategory(category, today).size
        }
    }
    val customCounts = remember(reminders) { reminders.groupingBy { it.customCategoryId }.eachCount() }
    val reminderMoodEntries = remember(reminders) {
        reminders.mapNotNull { reminder ->
            val mood = reminder.mood ?: return@mapNotNull null
            MoodTrackerEntry(
                id = Long.MIN_VALUE + reminder.id,
                mood = mood,
                details = reminder.moodDetails ?: MoodDetails(
                    scope = MoodScope.DAILY,
                    loggedAt = reminder.updatedAt
                )
            )
        }
    }
    val trackerEntries = remember(moodEntries, reminderMoodEntries) {
        (moodEntries + reminderMoodEntries).sortedByDescending { it.details.loggedAt }
    }
    val showAll = expanded || selectionMode
    Column(modifier.then(if (reduceAnimations) Modifier else Modifier.animateContentSize(spring(dampingRatio = 1f, stiffness = 400f)))) {
        // Keep each card's top position stable while extending its painted body farther
        // underneath the following card. This creates the continuous stacked-deck look
        // without making the whole category section taller.
        val orderedCategories = remember(categoryOrderKeys, customCategories) {
            resolveCategoryOrder(categoryOrderKeys, customCategories)
        }
        // Mood Tracker is a permanent system card, but it now behaves like a normal
        // deck card instead of chasing the last slot. Its canonical default position is #5:
        // collapsed = 4 normal categories + Mood Tracker; expanded = first 4 categories,
        // Mood Tracker, then the remaining categories.
        val previewCategoryCount = (COLLECTIONS_PREVIEW_COUNT - 1).coerceAtLeast(0)
        val visibleCategories = if (showAll) orderedCategories else orderedCategories.take(previewCategoryCount)
        val moodStackIndex = minOf(previewCategoryCount, visibleCategories.size)
        val visibleStackCount = visibleCategories.size + 1

        val density = LocalDensity.current
        val stackOffsetPx = with(density) { 60.dp.toPx() }
        val topInsetPx = with(density) { 40.dp.toPx() }
        val localYs = remember(visibleStackCount) { FloatArray(visibleStackCount) }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(-(COLLECTIONS_BASE_OVERLAP + COLLECTIONS_UNDERLAP_EXTENSION))
        ) {
            repeat(visibleStackCount) { stackIndex ->
                if (stackIndex == moodStackIndex) {
                    key("mood_tracker_system_card") {
                        MoodTrackerCategoryCard(
                            entries = trackerEntries,
                            modifier = Modifier.zIndex(stackIndex.toFloat()),
                            reduceAnimations = reduceAnimations,
                            enabled = !selectionMode,
                            globalIndex = stackIndex,
                            visibleCategoryCount = visibleStackCount,
                            localYs = localYs,
                            listState = listState,
                            topInsetPx = topInsetPx,
                            stackOffsetPx = stackOffsetPx,
                            extendUnderNext = stackIndex < visibleStackCount - 1,
                            onClick = onOpenMoodTracker
                        )
                    }
                } else {
                    val categoryIndex = if (stackIndex < moodStackIndex) stackIndex else stackIndex - 1
                    val ordered = visibleCategories[categoryIndex]
                    when (ordered) {
                        is OrderedCategoryItem.BuiltIn -> {
                            val category = ordered.category
                            key(category) {
                                CollectionCategoryCard(
                                    modifier = Modifier.zIndex(stackIndex.toFloat()),
                                    globalIndex = stackIndex,
                                    visibleCategoryCount = visibleStackCount,
                                    localYs = localYs,
                                    listState = listState,
                                    topInsetPx = topInsetPx,
                                    stackOffsetPx = stackOffsetPx,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    category = category, override = builtInCategoryColors[category],
                                    count = builtInCounts[category] ?: 0, reduceAnimations = reduceAnimations,
                                    extendUnderNext = stackIndex < visibleStackCount - 1,
                                    onClick = {
                                        if (!selectionMode) {
                                            RealPetRuntime.requestPlaceOn(REAL_PET_PERCH_CATEGORY_HERO)
                                            onOpenCategory(category)
                                        }
                                    },
                                    onLongClick = { if (!selectionMode) onEditBuiltInCategory(category) })
                            }
                        }
                        is OrderedCategoryItem.Custom -> {
                            val category = ordered.category
                            key(category.id) {
                                CollectionCategoryCard(
                                    modifier = Modifier.zIndex(stackIndex.toFloat()),
                                    globalIndex = stackIndex,
                                    visibleCategoryCount = visibleStackCount,
                                    localYs = localYs,
                                    listState = listState,
                                    topInsetPx = topInsetPx,
                                    stackOffsetPx = stackOffsetPx,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    customCategory = category, count = customCounts[category.id] ?: 0,
                                    selectionMode = selectionMode, isSelected = category.id in selectedCategoryIds,
                                    reduceAnimations = reduceAnimations,
                                    extendUnderNext = stackIndex < visibleStackCount - 1,
                                    onClick = {
                                        if (selectionMode) {
                                            onToggleSelectCategory(category.id)
                                        } else {
                                            RealPetRuntime.requestPlaceOn(REAL_PET_PERCH_CATEGORY_HERO)
                                            onOpenCustomCategory(category)
                                        }
                                    },
                                    onLongClick = { onLongClickCategory(category.id) })
                            }
                        }
                    }
                }
            }
        }
        if (showAll && !selectionMode) {
            TextButton(onClick = onAddCategory, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
                .border(0.75.dp, Color(0xFF35353A), RoundedCornerShape(24.dp))) {
                Text("＋  Yeni kategori", color = Color(0xFFCCCCD3), modifier = Modifier.padding(6.dp))
            }
        }
        if (!selectionMode) TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .realPetPerch(REAL_PET_PERCH_SHOW_ALL)
        ) {
            Text(
                showAllPetOverride ?: if (expanded) "Daha az göster" else "Tümünü göster",
                color = Color(0xFFB7B7C0),
                fontSize = 13.sp
            )
        }
    }
}


@Composable
private fun MoodTrackerCategoryCard(
    entries: List<MoodTrackerEntry>,
    modifier: Modifier = Modifier,
    reduceAnimations: Boolean,
    enabled: Boolean,
    globalIndex: Int,
    visibleCategoryCount: Int,
    localYs: FloatArray,
    listState: LazyListState?,
    topInsetPx: Float,
    stackOffsetPx: Float,
    extendUnderNext: Boolean,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val zone = remember { ZoneId.systemDefault() }
    val lastSevenDays = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val dayEntries = remember(entries, today, zone) {
        lastSevenDays.associateWith { date ->
            val sameDay = entries.filter {
                Instant.ofEpochMilli(it.details.loggedAt).atZone(zone).toLocalDate() == date
            }
            sameDay.filter { it.details.scope == MoodScope.DAILY }.maxByOrNull { it.details.loggedAt }
                ?: sameDay.maxByOrNull { it.details.loggedAt }
        }
    }
    val recentEntries = remember(entries) {
        entries.sortedByDescending { it.details.loggedAt }.take(5)
    }
    val latest = recentEntries.firstOrNull()
    val recentTints = remember(recentEntries) {
        recentEntries.map { moodV2Tint(it.mood) }
    }
    val fallbackAccent = Color(0xFF9AA8FF)
    val accent = remember(recentTints) {
        if (recentTints.isEmpty()) {
            fallbackAccent
        } else {
            var r = 0f
            var g = 0f
            var b = 0f
            var totalWeight = 0f
            recentTints.forEachIndexed { index, tint ->
                val weight = 1f / (index + 1)
                r += tint.red * weight
                g += tint.green * weight
                b += tint.blue * weight
                totalWeight += weight
            }
            Color(r / totalWeight, g / totalWeight, b / totalWeight, 1f)
        }
    }
    val cardBrush = remember(recentTints) {
        when {
            recentTints.isEmpty() -> Brush.linearGradient(
                listOf(
                    lerp(Color(0xFF171923), fallbackAccent, 0.42f),
                    lerp(Color(0xFF12141B), fallbackAccent, 0.20f),
                    Color(0xFF111217)
                )
            )
            recentTints.size == 1 -> Brush.linearGradient(
                listOf(
                    lerp(Color(0xFF171923), recentTints[0], 0.38f),
                    lerp(Color(0xFF12141B), recentTints[0], 0.20f),
                    Color(0xFF111217)
                )
            )
            recentTints.size == 2 -> Brush.linearGradient(
                listOf(
                    lerp(Color(0xFF171923), recentTints[0], 0.38f),
                    lerp(Color(0xFF141620), lerp(recentTints[0], recentTints[1], 0.55f), 0.27f),
                    lerp(Color(0xFF111217), lerp(recentTints[1], recentTints[0], 0.28f), 0.20f)
                )
            )
            else -> {
                val count = recentTints.size
                val colors = recentTints.mapIndexed { index, tint ->
                    val fraction = index.toFloat() / (count - 1)
                    val localTint = if (index == 0) tint else lerp(tint, recentTints[0], 0.28f)
                    val base = lerp(Color(0xFF171923), Color(0xFF111217), fraction)
                    val alpha = 0.38f - 0.18f * fraction
                    lerp(base, localTint, alpha)
                }
                Brush.linearGradient(colors)
            }
        }
    }
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    var localY by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .onPlaced { coordinates ->
                localY = coordinates.positionInParent().y
                cardHeightPx = coordinates.size.height.toFloat()
                if (globalIndex < localYs.size) localYs[globalIndex] = localY
            }
            // Mood Tracker now participates in the exact same stacked-card pinning model
            // as category cards. This keeps it visually stable in slot #5 instead of
            // behaving like a detached overlay.
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.58f
                if (listState != null && cardHeightPx > 0f && localYs.size == visibleCategoryCount) {
                    val containerItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "categories" }
                    val lastCardLocalY = localYs.last()
                    if (containerItem != null && lastCardLocalY > 0f) {
                        val containerY = containerItem.offset.toFloat()
                        val naturalWorldY = containerY + localY
                        val basePinnedWorldY = topInsetPx + globalIndex * stackOffsetPx
                        val lastCardNaturalWorldY = containerY + lastCardLocalY
                        val maxVisualY = lastCardNaturalWorldY - (visibleCategoryCount - 1 - globalIndex) * stackOffsetPx
                        val rawVisualY = maxOf(naturalWorldY, basePinnedWorldY)
                        val finalWorldY = minOf(rawVisualY, maxVisualY)
                        translationY = finalWorldY - naturalWorldY
                    }
                }
            }
            .then(if (enabled) Modifier.pressScale(interaction, targetScale = 0.985f, reduceAnimations = reduceAnimations) else Modifier)
            .clip(shape)
            .background(Color(0xFF14151C))
            .background(cardBrush)
            .border(0.9.dp, accent.copy(alpha = 0.48f), shape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                ) else Modifier
            )
            .heightIn(min = 112.dp)
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = 16.dp,
                bottom = 46.dp + (if (extendUnderNext) COLLECTIONS_UNDERLAP_EXTENSION else 0.dp)
            )
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Mood Tracker",
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f).offset(x = 10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("↗", color = Color.White, fontSize = 28.sp)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryPill(
                text = latest?.let { "Son: ${moodV2FamilyTitle(it.mood)}" } ?: "İlk izini bırak",
                foreground = Color.White,
                reduceAnimations = reduceAnimations
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                lastSevenDays.forEach { date ->
                    val entry = dayEntries[date]
                    val dotColor = entry?.let { moodV2Tint(it.mood) } ?: Color.White.copy(alpha = 0.18f)
                    Box(
                        Modifier
                            .size(if (date == today) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .then(
                                if (date == today) Modifier.border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.60f),
                                    CircleShape
                                ) else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPill(
    text: String,
    foreground: Color,
    modifier: Modifier = Modifier,
    petPerchId: String? = null,
    reduceAnimations: Boolean = false
) {
    val pillBorder = foreground.copy(alpha = 0.42f)
    var petTextOverride by remember(text, petPerchId) { mutableStateOf<String?>(null) }
    val petPrank = RealPetRuntime.prank
    LaunchedEffect(petPrank?.token, petPerchId, text, reduceAnimations) {
        val prank = petPrank ?: return@LaunchedEffect
        if (petPerchId == null || prank.perchId != petPerchId || prank.type != RealPetPrankType.CategoryPillTextMeow) {
            return@LaunchedEffect
        }
        RealPetRuntime.consumePrank(prank.token)
        val target = if (text.endsWith(" kart")) {
            val count = text.removeSuffix(" kart")
            listOf("$count pati", "$count miav", "$count meaw")[prank.token % 3]
        } else {
            "MEAWWW"
        }
        // The physical jump/meow ritual happens in RealPetLayer first; the text itself
        // switches immediately to the finished joke.
        petTextOverride = target
        delay(if (reduceAnimations) 500L else 1_550L)
        petTextOverride = null
    }

    Text(
        text = petTextOverride ?: text,
        color = foreground,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .then(if (petPerchId != null) Modifier.realPetPerch(petPerchId) else Modifier)
            .clip(RoundedCornerShape(50))
            .border(1.dp, pillBorder, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CollectionCategoryCard(
    category: ReminderCategory? = null, customCategory: CustomCategory? = null,
    override: CategoryColorOverride? = null, count: Int,
    modifier: Modifier = Modifier, hero: Boolean = false,
    descriptionPreview: String? = null, showCount: Boolean = true,
    selectionMode: Boolean = false, isSelected: Boolean = false, reduceAnimations: Boolean = false,
    extendUnderNext: Boolean = false,
    globalIndex: Int = 0,
    visibleCategoryCount: Int = 1,
    localYs: FloatArray? = null,
    listState: LazyListState? = null,
    topInsetPx: Float = 0f,
    stackOffsetPx: Float = 0f,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null
) {
    val savedDescription by rememberCategoryDescription(categoryDescriptionKey(category, customCategory))
    val description = limitCategoryDescription(descriptionPreview ?: savedDescription)
    val defaults = (category ?: ReminderCategory.NoAlert).defaultStyle()
    val startIndex = customCategory?.accentStart ?: override?.start
    val endIndex = customCategory?.accentEnd ?: override?.end
    val start = startIndex?.let { ReminderColorChoices.getOrNull(it)?.color } ?: defaults.upper
    val end = endIndex?.let { ReminderColorChoices.getOrNull(it)?.color }
        ?: if (startIndex != null) start else defaults.lower
    val blendFraction = customCategory?.gradientBlendFraction ?: override?.gradientBlendFraction
    val customSolidColorArgb = customCategory?.customSolidColorArgb ?: override?.customSolidColorArgb
    val cardBrush = remember(start, end, endIndex, blendFraction, customSolidColorArgb) {
        when {
            customSolidColorArgb != null -> {
                val solid = Color(customSolidColorArgb)
                Brush.linearGradient(listOf(solid, solid))
            }
            endIndex != null -> {
                val fraction = resolveGradientBlendFraction(blendFraction)
                val bridge = lerp(start, end, fraction)
                Brush.linearGradient(
                    0.0f to start,
                    0.5f to bridge,
                    1.0f to end
                )
            }
            else -> Brush.linearGradient(listOf(start, end))
        }
    }
        
    val foreground = if (customCategory?.useDarkText == true || override?.useDarkText == true) Color(0xFF101010) else Color.White
    
    // We update these variables when onPlaced is called
    var localY by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }

    val textSurface = Color.Transparent
    val title = customCategory?.name ?: category?.title.orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (hero) 30.dp else 26.dp)
    
    val sharedBoundsKey = "category_card_${customCategory?.id ?: category?.name}"
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && !reduceAnimations) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sharedBoundsKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    val targetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible
                    // Shared bounds has two participants. Distinguish navigation direction by
                    // combining visibility with whether this is the detail hero:
                    // forward: home card exits + hero enters -> keep the smooth spring
                    // back: hero exits + home card enters -> snap immediately so the overlay
                    // cannot linger above the deck and block touches.
                    val isForwardSharedMotion =
                        (hero && targetVisible) || (!hero && !targetVisible)
                    if (isForwardSharedMotion) {
                        spring(dampingRatio = 0.85f, stiffness = 300f)
                    } else {
                        snap()
                    }
                },
                clipInOverlayDuringTransition = OverlayClip(shape),
                zIndexInOverlay = run {
                    val targetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible
                    val isForwardSharedMotion =
                        (hero && targetVisible) || (!hero && !targetVisible)
                    if (isForwardSharedMotion) 1f else 0f
                }
            )
        }
    } else Modifier
    
    val petPerchId = if (hero) {
        REAL_PET_PERCH_CATEGORY_HERO
    } else {
        realPetCategoryPerchId(customCategory?.let { "custom:${it.id}" } ?: "builtin:${category?.name.orEmpty()}")
    }

    var petTitleOverride by remember(title, petPerchId) { mutableStateOf<String?>(null) }
    val petPrank = RealPetRuntime.prank
    LaunchedEffect(petPrank?.token, petPerchId) {
        val prank = petPrank ?: return@LaunchedEffect
        if (prank.perchId != petPerchId || prank.type != RealPetPrankType.CategoryTitleMeow) {
            return@LaunchedEffect
        }

        // This never touches the category model. Navigating away disposes the override, so
        // returning to the screen always restores the real category name.
        RealPetRuntime.consumePrank(prank.token)
        petTitleOverride = listOf("MEAW", "MEAWW", "MEAWWW", "MEAWWWW")[prank.token % 4]
        delay(if (reduceAnimations) 800L else 4_200L)
        petTitleOverride = null
    }

    Column(modifier.fillMaxWidth()
        .onPlaced { 
            localY = it.positionInParent().y 
            cardHeightPx = it.size.height.toFloat()
            if (localYs != null && globalIndex < localYs.size) {
                localYs[globalIndex] = localY
            }
        }
        .graphicsLayer {
            if (listState != null && cardHeightPx > 0f && localYs != null && localYs.size == visibleCategoryCount) {
                val containerItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "categories" }
                val lastCardLocalY = localYs.last()
                if (containerItem != null && lastCardLocalY > 0f) {
                    val containerY = containerItem.offset.toFloat()
                    
                    val naturalWorldY = containerY + localY
                    val basePinnedWorldY = topInsetPx + globalIndex * stackOffsetPx
                    
                    val lastCardNaturalWorldY = containerY + lastCardLocalY
                    
                    val maxVisualY = lastCardNaturalWorldY - (visibleCategoryCount - 1 - globalIndex) * stackOffsetPx
                    val rawVisualY = maxOf(naturalWorldY, basePinnedWorldY)
                    
                    val finalWorldY = minOf(rawVisualY, maxVisualY)
                    translationY = finalWorldY - naturalWorldY
                }
            }
        }
        .realPetPerch(petPerchId)
        .then(if (onClick != null) Modifier.pressScale(interaction, targetScale = 0.985f, reduceAnimations = reduceAnimations) else Modifier)
        .then(sharedModifier)
        .clip(shape)
        .background(Color(0xFF14151C))
        .background(cardBrush)
        .border(
            if (isSelected) 2.dp else 0.75.dp,
            if (isSelected) Color(0xFF94B8F7).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.20f),
            shape
        )
        .then(if (onClick != null) Modifier.combinedClickable(interactionSource = interaction,
            indication = LocalIndication.current, role = Role.Button, onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = if (customCategory != null) "Kategorileri seç" else "Kategori rengini düzenle") else Modifier)
        .then(if (selectionMode) Modifier.semantics { selected = isSelected } else Modifier)
        .heightIn(min = if (hero) 156.dp else 108.dp)
        .padding(
            start = 22.dp,
            end = 22.dp,
            top = if (hero) 26.dp else 16.dp,
            bottom = if (hero) 36.dp else 46.dp + (if (extendUnderNext) COLLECTIONS_UNDERLAP_EXTENSION else 0.dp)
        )) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text(
                petTitleOverride ?: title,
                color = foreground,
                fontSize = if (hero) 32.sp else 30.sp,
                lineHeight = if (hero) 38.sp else 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = 10.dp, y = 0.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("↗", color = foreground, fontSize = 28.sp)
        }
        Spacer(Modifier.height(if (hero) 12.dp else 14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showCount) {
                CategoryPill(
                    text = "$count kart",
                    foreground = foreground,
                    petPerchId = realPetCategoryPillPerchId(petPerchId, "count"),
                    reduceAnimations = reduceAnimations
                )
            }
            if (description.isNotBlank()) {
                if (showCount) Spacer(Modifier.width(8.dp))
                CategoryPill(
                    text = description,
                    foreground = foreground,
                    modifier = Modifier.widthIn(max = 190.dp),
                    petPerchId = realPetCategoryPillPerchId(petPerchId, "description"),
                    reduceAnimations = reduceAnimations
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (selectionMode) Text(if (isSelected) "  ✓ Seçili" else "  ○ Seç", color = foreground,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(textSurface).padding(4.dp), fontSize = 13.sp)
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(textSurface).padding(3.dp)) {
                if (customCategory != null) CategoryIconGlyph(CategoryIcon.fromKey(customCategory.iconKey), Modifier.size(21.dp), color = foreground)
                else CategoryGlyph(category?.glyph() ?: Glyph.Note, Modifier.size(21.dp), tint = foreground)
            }
        }
    }
}
