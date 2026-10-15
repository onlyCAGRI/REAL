package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import com.cagri.real.ui.motion.RealEntranceMotion
import com.cagri.real.ui.motion.RealEntrancePop
import com.cagri.real.ui.motion.realEntrance
import kotlinx.coroutines.delay
import com.cagri.real.ui.motion.rememberRealEntrance

import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.cagri.real.ui.DesignStyle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.cagri.real.ui.motion.RealEntranceEase
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cagri.real.data.CategoryColorOverride
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.ReminderItem
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Yaşam": Home'dan açılan modül merkezi. Moods, Drinks, Kitaplar, Film/Dizi/Oyun ve Günlük
 * modüllerinin repository'leri ve iç kodları burada değişmez; ekran yalnız okur ve mevcut
 * eylemleri çağırır. Bölümler ayrı küçük composable'lardır (Yasam*.kt).
 *
 * NOTLAR / SONRAYA KALANLAR
 * - Derlenmedi; ilk derlemede küçük import/tip düzeltmeleri çıkabilir.
 * - [onMicClick]: yalnız parametre. Sesli komutlar (ör. "Su içtim") mevcut altyapıya sonra bağlanacak.
 * - Sahneler basit: Sabah -> Su tam ekranı, Akşam -> günlük (onNewJournal ?: Günlüklerim modu),
 *   Okuma -> kaldığı kitap. Sabahtaki mood sorusu ve Akşamdaki mood + kitap sonraya kaldı.
 * - [onNewJournal]: verilmezse yeni günlük yerine Yaşam içindeki Günlüklerim modu açılır.
 * - Günlüklerim ayrı ekran değildir: [YasamSection.Journals] Yaşam'ın bir modudur (hero kutu kalır,
 *   cümle "Günlüklerim" olur). Satıra dokunma [onOpenJournalCard] ile ReminderScreen'de kartın detayını açar;
 *   detay Yaşam'ın üstünde olduğu için Yaşam geçici kapanır ve [startInJournals] ile aynı modda geri gelir.
 * - [journalColor] artık kullanılmıyor (kutu tonu sabit #FF8FB1); çağrı uyumu için duruyor.
 * - Eski kartlar (WaterTrackerCategoryCard, MovieSeriesWidgetCard) silindi; MoodTrackerCategoryCard, CollectionCategoryCard
 *   ve BooksWidgetCard bu ekranda kullanılmıyor. Su tam ekranı YasamWaterHost ile açılır.
 * - Home'daki "Widget" buton etiketi HomeLiquidDock içinde; o dosya pakette yoktu, değiştirilmedi.
 * - "Yaşam" başlığı görünür değil (taslak gibi): paneTitle + geri butonu erişilebilirlik metni.
 */
@Composable
fun HomeWidgetScreen(
    moodEntries: List<MoodTrackerEntry>,
    reminders: List<ReminderItem> = emptyList(),
    onDismiss: () -> Unit,
    onOpenMoodTracker: () -> Unit,
    journalColor: CategoryColorOverride? = null,
    reduceAnimations: Boolean,
    modifier: Modifier = Modifier,
    reduceDynamicBlur: Boolean = false,
    onToggleReminder: ((Long) -> Unit)? = null,
    onAddMedia: suspend (com.cagri.real.data.MediaBlock) -> Long? = { null },
    onAddMusic: suspend (com.cagri.real.data.MusicBlock) -> Long? = { null },
    onAddGame: suspend (com.cagri.real.data.CardGameBlock) -> Long? = { null },
    onBooksOpenChange: (Boolean) -> Unit = {},
    /** Deep link from the Home context card: "books", "media" or "water". Opens that module directly. */
    initialDestination: String? = null,
    onInitialDestinationConsumed: () -> Unit = {},
    /** Mood ⊕ butonu: doğrudan mood kayıt akışı. Verilmezse mood geçmişi açılır. */
    onAddMood: () -> Unit = onOpenMoodTracker,
    /** Günlük ✎ butonu: yeni günlük kartı. Verilmezse Yaşam içindeki Günlüklerim modu açılır. */
    onNewJournal: (() -> Unit)? = null,
    /** Günlüğü olan kartlar (`journal != null`), bağımsız günlükler dahil. Günlüklerim listesi ve hero önizlemesi bunu okur. */
    journalReminders: List<ReminderItem> = emptyList(),
    /** Günlüklerim'de bir satıra dokunma: kartın detayını Yaşam'ın üstünde açar (ReminderScreen). */
    onOpenJournalCard: (Long) -> Unit = {},
    /** Detaydan dönüşte Yaşam doğrudan Günlüklerim modunda açılır. */
    startInJournals: Boolean = false,
    onStartInJournalsConsumed: () -> Unit = {},
    /** Mikrofon butonu; sesli komut altyapısına sonradan bağlanacak. */
    onMicClick: () -> Unit = {},
    /** Alt mikrofonun cam stili (Home dock ile aynı). */
    designStyle: DesignStyle = DesignStyle.Glass
) {
    val directKind = remember { initialDestination }
    // Keşif hedefi: film önerisi, izlenecek listem, oyun önerisi, oynanacak listem. "media" deep link'i Film'e gider.
    var discovery by rememberSaveable { mutableStateOf<YasamDiscoveryTarget?>(if (initialDestination == "media") YasamDiscoveryTarget.Film else null) }
    // Yaşam'ın modu. Early return'lerden ÖNCE tutulur ki Kitap/Medya/Su dönüşünde aynı modda kalsın.
    var section by rememberSaveable { mutableStateOf(if (startInJournals) YasamSection.Journals else YasamSection.Home) }
    var showBooks by rememberSaveable { mutableStateOf(initialDestination == "books") }
    var showWater by rememberSaveable { mutableStateOf(initialDestination == "water") }
    var resumeBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // İlk açılış animasyonu (mikrofondan dairesel açılış) bir kez oynar; Kitap/Medya dönüşünde tekrarlanmaz.
    // Günlük detayından dönüşte dairesel açılış tekrarlanmaz.
    var opened by rememberSaveable { mutableStateOf(startInJournals) }
    var widgetContentGeneration by remember { mutableIntStateOf(0) }
    val holder = rememberYasamHolder()
    LaunchedEffect(Unit) { onInitialDestinationConsumed(); onStartInJournalsConsumed() }
    // Üst ekran, kitap açıkken navigation bar boşluğunu bırakır (kitap alta kadar çizilsin diye).
    LaunchedEffect(showBooks) { onBooksOpenChange(showBooks) }
    DisposableEffect(Unit) { onDispose { onBooksOpenChange(false) } }
    // Veri bir kez okunur; modülden dönüşte (generation) ve keşif ekranından dönüşte (liste sayıları) yeniden okunur. Polling yok.
    LaunchedEffect(holder, widgetContentGeneration, discovery) { if (discovery == null) holder.load() }
    if (showBooks) {
        com.cagri.real.books.BooksModuleScreen(
            reduceAnimations,
            onDismiss = {
                showBooks = false
                resumeBookId = null
                widgetContentGeneration++
                if (directKind == "books") onDismiss()
            },
            resumeBookId = resumeBookId
        )
        return
    }
    // Günlüklerim modunda geri, Yaşam'ı kapatmaz: normal Yaşam'a döner (heroTopic değişmez).
    val handleBack: () -> Unit = { if (section == YasamSection.Journals) section = YasamSection.Home else onDismiss() }
    BackHandler(enabled = discovery == null, onBack = handleBack)
    val playOpen = remember { !opened }
    val open = rememberRealEntrance(reduceAnimations, totalMs = 900, play = playOpen)
    LaunchedEffect(Unit) { delay(1000); opened = true }
    // İlk açılışta içerik, daire ekranı açarken hazır beklesin diye 250 ms gecikir.
    val baseMs = if (playOpen && widgetContentGeneration == 0) 250 else 0
    val entrance = rememberRealEntrance(reduceAnimations, identity = widgetContentGeneration, totalMs = 1800)
    // Cümle konusu her açılışta (ve modül dönüşünde) değişir.
    val appContext = LocalContext.current.applicationContext
    // rememberSaveable: dönme/süreç yeniden kurulmasında cümle konusu değişmesin; son konu ayrıca kalıcı tutulur.
    val heroTopic = rememberSaveable(widgetContentGeneration) { YasamHeroTopics.next(appContext) }

    val moodView = remember(moodEntries) { yasamMoodView(moodEntries) }
    val glow = animateColorAsState(
        yasamMoodTone(moodView.last), if (reduceAnimations) snap() else tween(700), label = "yasamGlow"
    )
    // Öneriler de Günlüklerim ile aynı satır çıkış saatini kullanır.
    var pendingDiscovery by remember { mutableStateOf<YasamDiscoveryTarget?>(null) }
    val discoveryLeave = remember { Animatable(0f) }
    LaunchedEffect(pendingDiscovery, reduceAnimations) {
        val target = pendingDiscovery
        if (target == null) {
            discoveryLeave.snapTo(0f)
            return@LaunchedEffect
        }
        if (!reduceAnimations) {
            discoveryLeave.animateTo(YasamLeaveTotalMs.toFloat(),
                tween(YasamLeaveTotalMs, easing = LinearEasing))
        }
        discovery = target
        pendingDiscovery = null
    }
    BackHandler(enabled = pendingDiscovery != null) { pendingDiscovery = null }
    val actions = YasamActions(
        openWater = { showWater = true },
        addWater = { holder.addWater() },
        openMood = onOpenMoodTracker,
        addMood = onAddMood,
        openBooks = { resumeBookId = null; showBooks = true },
        resumeBook = { resumeBookId = holder.book?.book?.id; showBooks = true },
        openFilm = { if (pendingDiscovery == null) pendingDiscovery = YasamDiscoveryTarget.Film },
        openGame = { if (pendingDiscovery == null) pendingDiscovery = YasamDiscoveryTarget.Game },
        openFilmList = { discovery = YasamDiscoveryTarget.FilmList },
        openGameList = { discovery = YasamDiscoveryTarget.GameList },
        openMusic = { if (pendingDiscovery == null) pendingDiscovery = YasamDiscoveryTarget.Music },
        openMusicList = { discovery = YasamDiscoveryTarget.MusicList },
        openJournals = { section = YasamSection.Journals },
        newJournal = onNewJournal ?: { section = YasamSection.Journals }
    )

    // Cam mikrofon için sahne yakalama: cam yakalanan kutunun DIŞINDA olmalı (kendi katmanını çizmesin).
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }
    var lastDiscovery by rememberSaveable { mutableStateOf(YasamDiscoveryTarget.Film) }
    LaunchedEffect(discovery) { discovery?.let { lastDiscovery = it } }
    val discoveryTarget = discovery ?: lastDiscovery
    val journalMotion = discoveryTarget == YasamDiscoveryTarget.Film || discoveryTarget == YasamDiscoveryTarget.Game ||
        discoveryTarget == YasamDiscoveryTarget.Music
    var homeWidth by remember { mutableIntStateOf(0) }
    val discoverySlide = animateFloatAsState(
        if (discovery != null && !journalMotion) 1f else 0f,
        if (reduceAnimations) snap() else tween(420, easing = RealEntranceEase),
        label = "yasamDiscoverySlide"
    )
    val homeState = rememberSaveableStateHolder()
    val homeOnscreen by remember(journalMotion, discovery) {
        derivedStateOf { if (journalMotion) discovery == null else discoverySlide.value < 1f }
    }
    Box(
        modifier
            .fillMaxSize()
            // Make the full-screen destination a pointer hit target so taps in its
            // empty/padded areas never fall through to clickable cards on the Home screen.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Final)
                }
            }
            .semantics { paneTitle = if (section == YasamSection.Journals) "Journal" else "Yaşam" }
    ) {
        if (homeOnscreen) homeState.SaveableStateProvider("yasam-home") {
        Box(Modifier.fillMaxSize().onSizeChanged { homeWidth = it.width }
            .graphicsLayer { translationX = -homeWidth * discoverySlide.value }
            .pointerInput(pendingDiscovery) {
                if (pendingDiscovery != null) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .then(if (discovery != null) Modifier.clearAndSetSemantics { } else Modifier)) {

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)) {
                        scene.record { this@drawWithContent.drawContent() }
                        drawLayer(scene)
                    } else drawContent()
                }
                .background(Color.Black)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                key(widgetContentGeneration) {
                    YasamScrollContent(
                        modifier = Modifier.weight(1f),
                        entrance = entrance,
                        baseMs = baseMs,
                        heroTopic = heroTopic,
                        onBack = handleBack,
                        section = section,
                        tone = glow.value,
                        holder = holder,
                        moodView = moodView,
                        moodEntries = moodEntries,
                        reminders = reminders,
                        journalReminders = journalReminders,
                        onOpenJournalCard = onOpenJournalCard,
                        tab = tab,
                        onTab = { tab = it },
                        reduceAnimations = reduceAnimations,
                        actions = actions,
                        onToggleReminder = onToggleReminder,
                        externalLeave = if (pendingDiscovery != null) discoveryLeave else null
                    )
                }
            }
        }
        // İçerik mikrofonun arkasından kayar; cam o içeriği gösterir.
        YasamMic(
            onMicClick = onMicClick,
            backdrop = scene,
            backdropPosition = { scenePosition },
            designStyle = designStyle,
            reduceDynamicBlur = reduceDynamicBlur,
            reduceAnimations = reduceAnimations,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .realEntrance(open, startMs = 0, durationMs = 420, fromScale = 0.6f, easing = RealEntrancePop)
        )
        if (showWater) {
            YasamWaterHost(
                reduceAnimations = reduceAnimations,
                reduceDynamicBlur = reduceDynamicBlur,
                onClosed = {
                    showWater = false
                    widgetContentGeneration++
                    if (directKind == "water") onDismiss()
                }
            )
        }
        }
        }
        AnimatedVisibility(
            visible = discovery != null,
            enter = if (reduceAnimations || journalMotion) EnterTransition.None else
                slideInHorizontally(tween(420, easing = RealEntranceEase)) { it } + fadeIn(tween(240)),
            exit = if (reduceAnimations || journalMotion) ExitTransition.None else
                slideOutHorizontally(tween(420, easing = RealEntranceEase)) { it } + fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            key(discoveryTarget) {
                val closeDiscovery = { discovery = null; if (directKind == "media") onDismiss() }
                val film = discoveryTarget == YasamDiscoveryTarget.Film || discoveryTarget == YasamDiscoveryTarget.FilmList
                val music = discoveryTarget == YasamDiscoveryTarget.Music || discoveryTarget == YasamDiscoveryTarget.MusicList
                val filterEntrance = rememberRealEntrance(reduceAnimations, totalMs = 1800)
                val filterBodyModifier = if (journalMotion) Modifier.realEntrance(
                    filterEntrance, startMs = 300, durationMs = 500, slideY = 16.dp
                ) else Modifier
                val filterHeader: @Composable () -> Unit = {
                    YasamDiscoveryHero(
                        title = if (music) "Ne dinlesek" else if (film) "Ne izlesek" else "Ne oynasak",
                        tone = if (music) MusicTone else if (film) Color(0xFF2F6BFF) else Color(0xFFF26A1B),
                        reduceAnimations = reduceAnimations,
                        onBack = closeDiscovery
                    )
                }
                if (music) MusicDiscoveryScreen(
                    reduceAnimations, onDismiss = closeDiscovery, onAddMusic = onAddMusic,
                    reduceDynamicBlur = reduceDynamicBlur,
                    startInList = discoveryTarget == YasamDiscoveryTarget.MusicList,
                    filterHeader = filterHeader, filterBodyModifier = filterBodyModifier
                ) else if (film) MediaDiscoveryScreen(
                    reduceAnimations, onDismiss = closeDiscovery, onAddMedia = onAddMedia,
                    reduceDynamicBlur = reduceDynamicBlur,
                    startInWatchlist = discoveryTarget == YasamDiscoveryTarget.FilmList,
                    startInFilters = discoveryTarget == YasamDiscoveryTarget.Film,
                    filterHeader = filterHeader, filterBodyModifier = filterBodyModifier
                ) else GameDiscoveryScreen(
                    reduceAnimations, onDismiss = closeDiscovery, onAddGame = onAddGame,
                    reduceDynamicBlur = reduceDynamicBlur,
                    startInList = discoveryTarget == YasamDiscoveryTarget.GameList,
                    startInFilters = discoveryTarget == YasamDiscoveryTarget.Game,
                    filterHeader = filterHeader, filterBodyModifier = filterBodyModifier
                )
            }
        }
    }
}

@Composable
private fun YasamDiscoveryHero(title: String, tone: Color, reduceAnimations: Boolean, onBack: () -> Unit) {
    val entrance = rememberRealEntrance(reduceAnimations, totalMs = 900)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            YasamHeroSentence(
                greeting = "", sentence = title, tone = tone, hour = remember { LocalTime.now().hour },
                reduceAnimations = reduceAnimations, entrance = entrance, wordsStartMs = 80, topInset = 0.dp, compact = true
            )
            YasamBackButton(onBack, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 8.dp),
                description = "Yaşam'a dön")
        }
        androidx.compose.material3.Text(
            "Birlikte uyanlar önce gelir.", color = YasamMuted,
            style = com.cagri.real.ui.theme.RealType.editorialSubtitle,
            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun rememberYasamHolder(): YasamHolder {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
    return remember(context, uid) { YasamHolder(context, uid, scope) }
}

private fun yasamMoodView(entries: List<MoodTrackerEntry>) = YasamMoodView(
    last = YasamSentence.lastMood(entries),
    yesterday = YasamSentence.yesterdayMood(entries, LocalDate.now(), ZoneId.systemDefault()),
    points = YasamSentence.moodPoints(entries)
)

/** Yaşam'ın modları: normal Yaşam ve Yaşam içindeki Günlüklerim (ayrı ekran değil). */
internal enum class YasamSection { Home, Journals }

/** "Öneri al" hedefleri: film tür seçimi, kayıtlı izlenecek listesi, oyun tür seçimi, kayıtlı oynanacak listesi. */
internal enum class YasamDiscoveryTarget { Film, FilmList, Game, GameList, Music, MusicList }

@Composable
private fun YasamScrollContent(
    modifier: Modifier,
    entrance: RealEntranceMotion,
    baseMs: Int,
    heroTopic: Int,
    onBack: () -> Unit,
    section: YasamSection,
    tone: Color,
    holder: YasamHolder,
    moodView: YasamMoodView,
    moodEntries: List<MoodTrackerEntry>,
    reminders: List<ReminderItem>,
    journalReminders: List<ReminderItem>,
    onOpenJournalCard: (Long) -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    reduceAnimations: Boolean,
    actions: YasamActions,
    onToggleReminder: ((Long) -> Unit)?,
    externalLeave: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null
) {
    val hour = remember { LocalTime.now().hour }
    val water = holder.water
    val book = holder.book
    val mediaPrompt = if (hour < 17) "Bugün ne izleyelim?" else "Bu akşam ne izleyelim?"
    // En son günlük girişi (yalnız okunur): journalReminders içindeki journal.text / journal.createdAt.
    // Bağımsız günlükler de dahildir (ReminderScreen genel listeden çıkardığı için ayrı verilir).
    val journalPreview = remember(journalReminders) {
        journalReminders.mapNotNull { r -> r.journal?.let { it.text to it.createdAt } }
            .filter { it.first.isNotBlank() }
            .maxByOrNull { it.second ?: 0L }
            ?.let { yasamJournalPreview(it.first, it.second) }
    }
    val homeSentence = remember(heroTopic, water, moodView, book, mediaPrompt, journalPreview) {
        yasamHeroSentence(heroTopic, water, moodView, book, mediaPrompt, journalPreview)
    }

    // Görünen mod, istenen modun biraz gerisinden gelir: Home → Journals'ta önce Home satırları kademeli çıkar,
    // sonra mod değişir, hero cümlesi ve yeni içerik açılış saatiyle yeniden oynar. Saatler graphicsLayer/draw
    // aşamasında okunur: kare başına recomposition yok. reduceAnimations'ta her şey anında.
    var shown by remember { mutableStateOf(section) }
    var switched by remember { mutableStateOf(false) }
    val leave = remember { Animatable(0f) }
    val scroll = rememberScrollState()
    LaunchedEffect(section, reduceAnimations) {
        if (shown == section) {
            // Çıkış sürerken geri basıldıysa yarım solmuş satırları geri getir.
            leave.snapTo(0f)
            return@LaunchedEffect
        }
        if (!reduceAnimations && shown == YasamSection.Home) {
            leave.snapTo(0f)
            leave.animateTo(YasamLeaveTotalMs.toFloat(), tween(YasamLeaveTotalMs, easing = LinearEasing))
        }
        scroll.scrollTo(0)
        switched = true
        shown = section
        leave.snapTo(0f)
    }
    // İlk açılışta ana saat (daire açılışıyla uyumlu gecikme); mod değişiminden sonra moda özel yeni saat.
    val sectionEntrance = rememberRealEntrance(reduceAnimations, identity = shown, totalMs = 1800, play = switched)
    val body = if (switched) sectionEntrance else entrance
    val bodyBase = if (switched) 0 else baseMs
    val rowDeparture = externalLeave ?: leave
    val journalsMode = shown == YasamSection.Journals
    val sentence = if (journalsMode) "Journal" else homeSentence
    val label = if (journalsMode) "${journalReminders.size} GİRİŞ" else YasamSentence.greeting(hour)

    Column(modifier.fillMaxWidth().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).realEntrance(entrance, startMs = baseMs + 40, durationMs = 560, fromScale = 0.96f, easing = RealEntrancePop)) {
            YasamHeroSentence(
                greeting = label, sentence = sentence, tone = tone, hour = hour,
                reduceAnimations = reduceAnimations, entrance = body, wordsStartMs = bodyBase + 200,
                topInset = 20.dp
            )
            // Geri tuşu artık kutunun üstünde (kartla birlikte açılır ve kayar).
            YasamBackButton(
                onBack, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 8.dp),
                description = if (journalsMode) "Yaşam'a dön" else "Geri"
            )
        }
        if (journalsMode) {
            YasamJournalList(
                journals = journalReminders,
                entrance = body,
                reduceAnimations = reduceAnimations,
                onWrite = actions.newJournal,
                onOpen = onOpenJournalCard,
                baseMs = bodyBase + 300
            )
        } else {
            Box(Modifier.fillMaxWidth().realEntrance(body, startMs = bodyBase + 130, durationMs = 420, slideY = 8.dp).rowLeave(rowDeparture, 0)) {
                YasamIconTabs(tab, onTab, reduceAnimations)
            }
            YasamTiles(
                entrance = body, baseMs = bodyBase, journal = journalPreview,
                tab = tab, water = water, book = book, mood = moodView,
                mediaPrompt = mediaPrompt,
                reduceAnimations = reduceAnimations, actions = actions,
                moodEntries = moodEntries,
                watchlistCount = holder.watchlistCount, playlistCount = holder.playlistCount, musicCount = holder.musicCount,
                leave = rowDeparture
            )

        }
        // Son kutu mikrofonun altında kalmasın.
        Spacer(Modifier.height(130.dp))
    }
}
