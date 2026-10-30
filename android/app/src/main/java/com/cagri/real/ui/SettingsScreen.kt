package com.cagri.real.ui

import com.cagri.real.i18n.t
import com.cagri.real.BuildConfig
import kotlinx.coroutines.launch
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.real.data.CustomCategory
import com.cagri.real.notifications.NowBarProbe
import com.cagri.real.notifications.NowBarReminderController
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.cagri.real.notifications.ReminderNotifications
import com.cagri.real.ui.components.*

@Composable
fun SettingsScreen(
    reduceDynamicBlur: Boolean,
    onReduceDynamicBlurChange: (Boolean) -> Unit,
    reduceAnimations: Boolean,
    onReduceAnimationsChange: (Boolean) -> Unit,
    realPetEnabled: Boolean,
    onRealPetEnabledChange: (Boolean) -> Unit,
    designStyle: DesignStyle,
    onDesignStyleChange: (DesignStyle) -> Unit,
    onBack: () -> Unit,
    notificationsAllowed: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {},
    onPetScenarioTest: (RealPetScenarioTestKind) -> Unit = {},
    onOpenCalendarConnection: () -> Unit = {},
    onClearAllMoods: () -> Unit = {},
    widgetReminders: List<com.cagri.real.data.ReminderItem> = emptyList(),
    widgetMoodEntries: List<com.cagri.real.data.MoodTrackerEntry> = emptyList(),
    onToggleWidgetReminder: ((Long) -> Unit)? = null,
    onOpenReminderDetail: ((Long) -> Unit)? = null
) {
    var showPetScenarios by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showClearMoodConfirm by remember { mutableStateOf(false) }
    var showRealPro by rememberSaveable { mutableStateOf(false) }
    var showAiPrivacy by rememberSaveable { mutableStateOf(false) }
    var showDataUse by rememberSaveable { mutableStateOf(false) }
    var showPrivacyRights by rememberSaveable { mutableStateOf(false) }
    var showDataSources by rememberSaveable { mutableStateOf(false) }
    var showDeveloperTools by rememberSaveable { mutableStateOf(false) }
    var showWidgetDesigns by rememberSaveable { mutableStateOf(false) }
    if (showWidgetDesigns) {
        BackHandler { showWidgetDesigns = false }
        SettingsLayout(DesignStyle.LiquidGlass, reduceDynamicBlur, { showWidgetDesigns = false }) {
            SettingsSectionHeader("WIDGET TASARIMLARI")
            YasamLauncherCards(widgetReminders, widgetMoodEntries, reduceAnimations, onToggleWidgetReminder)
        }
        return
    }
    BackHandler(enabled = !showRealPro && !showPetScenarios && !showLicensesDialog && !showClearMoodConfirm && !showAiPrivacy && !showDataUse && !showPrivacyRights && !showDataSources && !showDeveloperTools, onBack = onBack)

    if (showRealPro) {
        com.cagri.real.ui.subscription.RealProRoute(reduceAnimations = reduceAnimations, onDismiss = { showRealPro = false })
    }

    if (showPrivacyRights) {
        com.cagri.real.ui.privacy.PrivacyRightsDialog { showPrivacyRights = false }
    }
    if (showDataUse) {
        com.cagri.real.ui.privacy.RealDataUseDialog { showDataUse = false }
    }

    if (showDataSources) {
        BackHandler { showDataSources = false }
        SettingsLayout(DesignStyle.LiquidGlass, reduceDynamicBlur, { showDataSources = false }) {
            CatalogSourcesSection()
            Spacer(Modifier.height(40.dp))
        }
        return
    }

    if (showAiPrivacy) {
        val controller = com.cagri.real.ui.auth.LocalAuthUiController.current
        val user = controller?.user?.collectAsState()?.value
        com.cagri.real.ui.ai.AiPrivacyDialog(user?.id) { showAiPrivacy = false }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val reminderChannel = remember(isResumed) {
        ReminderNotifications.getReminderChannel(context)
    }
    val isChannelBlocked = reminderChannel != null && reminderChannel.importance == NotificationManager.IMPORTANCE_NONE
    val isChannelVibrationEnabled = reminderChannel != null && !isChannelBlocked && reminderChannel.shouldVibrate()

    val contextCardStore = remember {
        com.cagri.real.contextcards.ContextCardStore(
            com.cagri.real.platform.android.sync.SyncLocal.preferences(
                context.applicationContext,
                com.cagri.real.contextcards.ContextCardStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
        )
    }
    var contextCardsEnabled by remember { mutableStateOf(contextCardStore.load().enabled) }
    var showWelcomePreview by remember { mutableStateOf(false) }
    var showNumberTower by remember { mutableStateOf(false) }

    BackHandler(enabled = showDeveloperTools && !showClearMoodConfirm && !showPetScenarios) { showDeveloperTools = false }

    key(showDeveloperTools) {
        SettingsLayout(DesignStyle.LiquidGlass, reduceDynamicBlur, {
            if (showDeveloperTools) showDeveloperTools = false else onBack()
        }) {
            if (showDeveloperTools) {
                SettingsSectionHeader(t("GELİŞTİRİCİ ARAÇLARI"))
                SettingsSectionHeader(t("VARINCA / AYRILINCA TESTİ"))
                SettingsGlassGroup {
                    LocationArrivalDeveloperTestSection(
                        existingReminders = widgetReminders,
                        onOpenCardDetail = onOpenReminderDetail
                    )
                }
                SettingsSectionHeader(t("BAĞLAM KARTLARI TESTİ"))

                SettingsGlassGroup {
                    val testCardTypes = listOf(
                        Triple(com.cagri.real.contextcards.ContextCardType.WATER, ActionGlyph.Bell, t("Su kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.MOOD, ActionGlyph.MoodNeutral, t("Mood kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.BOOK, ActionGlyph.Note, t("Kitap kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.MEDIA, ActionGlyph.Video, t("Film & dizi kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.WATCH, ActionGlyph.Play, t("Sıradaki bölüm kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.LISTEN, ActionGlyph.Music, t("Müzik kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.PLAY, ActionGlyph.Cards, t("Oyun kartını göster")),
                        Triple(com.cagri.real.contextcards.ContextCardType.JOURNAL, ActionGlyph.Edit, t("Günlük kartını göster"))
                    )
                    SettingsActionRow(
                        icon = ActionGlyph.Board,
                        title = t("Tüm kartları göster"),
                        subtitle = t("Sadece test: bütün kart türleri altta yan yana dizilir (normalde tek kart gelir)"),
                        onClick = {
                            contextCardStore.setEnabled(true)
                            contextCardsEnabled = true
                            contextCardStore.forceAll()
                            com.cagri.real.ui.components.RealToast.show(context, t("Ana ekranın en altına bak"))
                        }
                    )
                    SettingsDivider()
                    testCardTypes.forEachIndexed { index, (type, glyph, label) ->
                        if (index > 0) SettingsDivider()
                        SettingsActionRow(
                            icon = glyph,
                            title = label,
                            subtitle = t("Saat ve limit kurallarını yok sayar; ana ekranın en altında görünür"),
                            onClick = {
                                contextCardStore.setEnabled(true)
                                contextCardsEnabled = true
                                contextCardStore.force(type)
                                com.cagri.real.ui.components.RealToast.show(context, t("Ana ekranın en altına bak"))
                            }
                        )
                    }
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Edit,
                        title = t("Kart geçmişini sıfırla"),
                        subtitle = t("Günlük limit, bekleme ve sessize alma sayaçlarını temizler"),
                        onClick = {
                            contextCardStore.resetHistory()
                            com.cagri.real.ui.components.RealToast.show(context, t("Kart geçmişi sıfırlandı"))
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Theme,
                        title = t("Karşılama ekranını önizle"),
                        subtitle = t("İlk açılış animasyonunu oynatır; hesabını ve ayarlarını değiştirmez"),
                        onClick = { showWelcomePreview = true }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Cards,
                        title = t("Sayı Kulesi"),
                        subtitle = t("Mini oyun: gelen sayıyı 10 yuvaya küçükten büyüğe diz"),
                        onClick = { showNumberTower = true }
                    )
                }
                if (showNumberTower) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showNumberTower = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
                    ) {
                        com.cagri.real.ui.components.YasamArcadeHost(reduceAnimations = reduceAnimations, onClose = { showNumberTower = false })
                    }
                }
                if (showWelcomePreview) {
                    // Full-screen replay of the first-launch welcome; finishing or Back just closes it.
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showWelcomePreview = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
                    ) {
                        WelcomeScreen(reduceAnimations = false, onComplete = { showWelcomePreview = false })
                    }
                }

                if (RealPetRuntime.mode != RealPetMode.Kapali) {
                    SettingsSectionHeader("REAL PET")

                    SettingsGlassGroup {
                        SettingsActionRow(
                            icon = ActionGlyph.Cat,
                            title = t("Pet senaryoları"),
                            subtitle = "",
                            onClick = { showPetScenarios = true }
                        )

                        SettingsDivider()

                        val petPrefs = remember { context.getSharedPreferences("real_pet_prefs", Context.MODE_PRIVATE) }
                        var petKeywordsEnabled by remember {
                            mutableStateOf(petPrefs.getBoolean("pet_keywords_enabled", false))
                        }
                        LaunchedEffect(petKeywordsEnabled) {
                            RealPetRuntime.keywordsEnabled.value = petKeywordsEnabled
                        }
                        SettingsRow(
                            icon = ActionGlyph.Cat,
                            title = "Kelime tetikleyicileri",
                            subtitle = t("Yazdığın bazı kelimeler REAL Pet davranışlarını tetikleyebilir."),
                            badge = "Deneysel",
                            checked = petKeywordsEnabled,
                            onCheckedChange = {
                                petKeywordsEnabled = it
                                petPrefs.edit().putBoolean("pet_keywords_enabled", it).apply()
                                RealPetRuntime.keywordsEnabled.value = it
                            }
                        )
                    }
                }

                // ==========================================
                // 4. GELİŞTİRİCİ VE DEMO (Design / Demo)
                // ==========================================
                SettingsSectionHeader(t("GELİŞTİRİCİ VE DEMO"))

                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Cards,
                        title = t("Widget tasarımları"),
                        subtitle = t("Yaşam widget önizlemeleri"),
                        onClick = { showWidgetDesigns = true }
                    )
                    SettingsDivider()
                    // Concept demo entry
                    SettingsActionRow(
                        icon = ActionGlyph.Edit,
                        title = t("Tasarım demosu"),
                        subtitle = t("Takvim, kategoriler ve hızlı ses konsepti"),
                        onClick = {
                            context.startActivity(Intent(context, com.cagri.real.demo.CategoryConceptDemoActivity::class.java).apply {
                                putExtra("reduce_motion", reduceAnimations)
                                putExtra("reduce_blur", reduceDynamicBlur)
                            })
                        }
                    )

                    SettingsDivider()

                    AiConnectionDiagnostics()
                    SettingsDivider()

                    // Now Bar R&D / Diagnostics
                    var rAndDExpanded by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { rAndDExpanded = !rAndDExpanded }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ActionIcon(ActionGlyph.NowBar, Modifier.size(20.dp), color = Color(0xFF91B9FF))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("Now Bar R&D Teşhis ve Test", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                    Text("Sistem bildirim ve Now Bar entegrasyon kontrolü", color = Color(0xFFA5A5AF), fontSize = 12.sp)
                                }
                            }
                            Text(
                                if (rAndDExpanded) "▲" else "▼",
                                color = Color(0xFF888892),
                                fontSize = 12.sp
                            )
                        }

                        if (rAndDExpanded) {
                            Spacer(Modifier.height(14.dp))
                            val supported = NowBarProbe.canPostPromoted(context)
                            val characteristics = NowBarProbe.hasPromotableCharacteristics(context)

                            Text("Android promoted access", color = Color(0xFFCCCCD3), fontSize = 13.sp)
                            Text(if (supported) t("Açık") else t("Sistem tarafından bildirilmedi"), color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))

                            Text("Samsung Now Bar metadata", color = Color(0xFFCCCCD3), fontSize = 13.sp)
                            Text("Hazır", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))

                            Text("Notification structure", color = Color(0xFFCCCCD3), fontSize = 13.sp)
                            Text(if (characteristics) t("Uygun") else t("Uygun değil"), color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { NowBarProbe.postTest(context) }) {
                                    Text("Testi başlat", color = Color(0xFF91B9FF), fontSize = 13.sp)
                                }
                                TextButton(onClick = { NowBarProbe.cancelTest(context) }) {
                                    Text("Bitir", color = Color(0xFF91B9FF), fontSize = 13.sp)
                                }
                                if (supported) {
                                    val settingsIntent = NowBarProbe.buildSettingsIntent(context)
                                    if (settingsIntent != null) {
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { context.startActivity(settingsIntent) }) {
                                            Text("Ayarları aç", color = Color(0xFF91B9FF), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SettingsDivider()

                    var moodTestExpanded by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { moodTestExpanded = !moodTestExpanded }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ActionIcon(ActionGlyph.MoodNeutral, Modifier.size(20.dp), color = Color(0xFFB8A7FF))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("Mood Test ve Veri", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                    Text("Mood kayıtlarını geliştirme sırasında sıfırla", color = Color(0xFFA5A5AF), fontSize = 12.sp)
                                }
                            }
                            Text(
                                if (moodTestExpanded) "▲" else "▼",
                                color = Color(0xFF888892),
                                fontSize = 12.sp
                            )
                        }

                        if (moodTestExpanded) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                t("Moods ve REAL kartlarındaki Mood kayıtlarını siler. Günlük kayıtlarına dokunmaz."),
                                color = Color(0xFF92929C),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            TextButton(
                                onClick = { showClearMoodConfirm = true },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text("Tüm moodları sil", color = Color(0xFFFF6B72), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

            } else {


                // ==========================================
                // 1. GÖRÜNÜM (Appearance)
                // ==========================================
                SettingsSectionHeader(t("GÖRÜNÜM"))

                SettingsGlassGroup {
                    var showLanguageDialog by remember { mutableStateOf(false) }
                    val languageLabel = when (com.cagri.real.i18n.I18n.selected) {
                        com.cagri.real.i18n.AppLanguage.System -> com.cagri.real.i18n.t("Telefonun dili")
                        com.cagri.real.i18n.AppLanguage.Turkish -> t("Türkçe")
                        com.cagri.real.i18n.AppLanguage.English -> "English"
                    }
                    SettingsChoiceRow(
                        icon = ActionGlyph.Theme,
                        title = t("Dil"),
                        value = languageLabel,
                        onClick = { showLanguageDialog = true }
                    )
                    if (showLanguageDialog) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = t("Uygulama dilini seç"),
                            choices = listOf(
                                com.cagri.real.ui.components.Choice(com.cagri.real.i18n.AppLanguage.System, t("Telefonun dili")),
                                com.cagri.real.ui.components.Choice(com.cagri.real.i18n.AppLanguage.Turkish, t("Türkçe")),
                                com.cagri.real.ui.components.Choice(com.cagri.real.i18n.AppLanguage.English, "English")
                            ),
                            selected = com.cagri.real.i18n.I18n.selected,
                            onSelect = { com.cagri.real.i18n.I18n.set(context, it) },
                            onDismiss = { showLanguageDialog = false }
                        )
                    }

                    SettingsDivider()

                    var showMapSheet by remember { mutableStateOf(false) }
                    val mapDark = com.cagri.real.ui.components.MapAppearance.isDark(context)
                    SettingsChoiceRow(
                        icon = ActionGlyph.Pin,
                        title = t("Harita görünümü"),
                        value = (if (mapDark) t("Koyu") else t("Açık")) +
                            (if (com.cagri.real.ui.components.MapAppearance.isPlain(context)) " · " + t("Sade") else ""),
                        onClick = { showMapSheet = true }
                    )
                    if (showMapSheet) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = t("Harita görünümü"),
                            choices = listOf(
                                com.cagri.real.ui.components.Choice(false, t("Açık"), t("Renkli: yeşil alanlar, mavi deniz")),
                                com.cagri.real.ui.components.Choice(true, t("Koyu"), t("Siyah zemin, belirgin deniz"))
                            ),
                            selected = mapDark,
                            onSelect = { com.cagri.real.ui.components.MapAppearance.setDark(context, it) },
                            onDismiss = { showMapSheet = false },
                            toggle = com.cagri.real.ui.components.ChoiceToggle(
                                label = t("Sade harita"),
                                detail = t("Haritada mekan adlarını gizler"),
                                checked = com.cagri.real.ui.components.MapAppearance.isPlain(context),
                                onChange = { com.cagri.real.ui.components.MapAppearance.setPlain(context, it) }
                            )
                        )
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.MotionOff,
                        title = "Animasyonu azalt",
                        subtitle = t("Arayüz hareketlerini ve geçiş efektlerini sakinleştirir"),
                        checked = reduceAnimations,
                        onCheckedChange = onReduceAnimationsChange
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.BlurOff,
                        title = t("Dinamik bulanıklığı azalt"),
                        subtitle = t("Bulanıklık efektleri yerine sabit koyu yüzeyler kullanır"),
                        checked = reduceDynamicBlur,
                        onCheckedChange = onReduceDynamicBlurChange
                    )


                    if (REAL_PET_AVAILABLE) {
                    SettingsDivider()

                    val petPrefs = remember(context) { context.getSharedPreferences("real_pet_prefs", Context.MODE_PRIVATE) }
                    remember(petPrefs) {
                        RealPetRuntime.bindPreferences(petPrefs)
                        true
                    }
                    val currentPetMode = RealPetRuntime.mode
                    val petModeSubtitle = when (currentPetMode) {
                        RealPetMode.Kapali -> t("Kapalı")
                        RealPetMode.Sakin -> t("Sakin")
                        RealPetMode.Normal -> t("Normal")
                        RealPetMode.Yaramaz -> t("Yaramaz")
                    }
                    var showPetSheet by remember { mutableStateOf(false) }
                    SettingsChoiceRow(
                        icon = ActionGlyph.Cat,
                        title = "REAL Pet",
                        value = petModeSubtitle,
                        onClick = { showPetSheet = true }
                    )
                    if (showPetSheet) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = "REAL Pet",
                            choices = listOf(
                                com.cagri.real.ui.components.Choice(RealPetMode.Kapali, t("Kapalı")),
                                com.cagri.real.ui.components.Choice(RealPetMode.Sakin, t("Sakin")),
                                com.cagri.real.ui.components.Choice(RealPetMode.Normal, t("Normal")),
                                com.cagri.real.ui.components.Choice(RealPetMode.Yaramaz, t("Yaramaz"))
                            ),
                            selected = currentPetMode,
                            onSelect = { modeOption ->
                                RealPetRuntime.mode = modeOption
                                petPrefs.edit().putString(RealPetBrain.KEY_MODE, modeOption.storageKey).apply()
                            },
                            onDismiss = { showPetSheet = false }
                        )
                    }
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.Bell,
                        title = t("Bağlam kartları"),
                        subtitle = t("Ana ekranın altında arada su, mood, kitap ve film önerisi gösterir"),
                        checked = contextCardsEnabled,
                        onCheckedChange = {
                            contextCardsEnabled = it
                            contextCardStore.setEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsActionRow(
                        icon = ActionGlyph.Calendar,
                        title = t("Takvim bağlantısı"),
                        subtitle = t("Google / Samsung takvimlerini göster ve planlarını gönder"),
                        onClick = onOpenCalendarConnection
                    )

                    SettingsDivider()
                    val settingsCardsContext = LocalContext.current

                    var plainHomeCards by remember { mutableStateOf(com.cagri.real.ui.components.HomeCardStyle.isPlain(settingsCardsContext)) }
                    SettingsRow(
                        icon = ActionGlyph.Check,
                        title = "Sade kartlar",
                        subtitle = t("Ana ekranda tamamlama yuvarlağı yok; kartı sağa kaydırınca tamamlanır."),
                        badge = "Deneysel",
                        checked = plainHomeCards,
                        onCheckedChange = {
                            plainHomeCards = it
                            com.cagri.real.ui.components.HomeCardStyle.setPlain(settingsCardsContext, it)
                        }
                    )

                    SettingsDivider()

                    ShareShortcutSettings()

                    SettingsDivider()

                    // Müzik uygulaması: where songs open (music discovery's up-swipe, music search default).
                    val musicContext = LocalContext.current
                    var musicSource by remember { mutableStateOf(com.cagri.real.ui.components.MusicSource.saved(musicContext)) }
                    var showMusicSheet by remember { mutableStateOf(false) }
                    SettingsChoiceRow(
                        icon = ActionGlyph.Music,
                        title = t("Müzik uygulaması"),
                        value = musicSource.label,
                        onClick = { showMusicSheet = true }
                    )
                    if (showMusicSheet) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = t("Müzik uygulaması"),
                            choices = com.cagri.real.ui.components.MusicSource.entries.map {
                                com.cagri.real.ui.components.Choice(it, it.label, t("Şarkılar burada açılır"))
                            },
                            selected = musicSource,
                            onSelect = {
                                com.cagri.real.ui.components.MusicSource.save(musicContext, it)
                                musicSource = it
                            },
                            onDismiss = { showMusicSheet = false }
                        )
                    }

                    SettingsDivider()

                    // Apple Music hesabı: "Sana özel" in Müzik keşfi, right swipe also adds to the library.
                    var appleMusicLinked by remember { mutableStateOf(com.cagri.real.discovery.AppleMusicAccount.isConnected(musicContext)) }
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                        appleMusicLinked = com.cagri.real.discovery.AppleMusicAccount.isConnected(musicContext)
                        onPauseOrDispose { }
                    }
                    SettingsActionRow(
                        icon = ActionGlyph.Music,
                        title = t("Apple Music hesabı"),
                        subtitle = if (appleMusicLinked) t("Bağlı · dokun, bağlantıyı kes") else t("Bağla · sana özel öneriler, REAL çalma listesi"),
                        onClick = {
                            if (appleMusicLinked) {
                                com.cagri.real.discovery.AppleMusicAccount.disconnect(musicContext)
                                appleMusicLinked = false
                            } else com.cagri.real.discovery.AppleMusicAccount.connect(musicContext)
                        }
                    )

                    SettingsDivider()

                    // Öneriler (müzik, film / dizi, oyun): geçilenler yeniden gelebilir, desteler baştan kurulur; kayıtlı listeler ve
                    // İzledim / Oynadım kalır.
                    var confirmMusicReset by remember { mutableStateOf(false) }
                    SettingsActionRow(
                        icon = ActionGlyph.Music,
                        title = t("Önerileri sıfırla"),
                        subtitle = t("Müzik, film, dizi ve oyunda geçtiklerin yeniden gelebilir; listelerin silinmez"),
                        onClick = { confirmMusicReset = true }
                    )
                    if (confirmMusicReset) {
                        val musicScope = rememberCoroutineScope()
                        com.cagri.real.ui.components.ConfirmSheet(
                            title = t("Önerileri sıfırlamak istediğine emin misin?"),
                            message = t("Müzik, film, dizi ve oyunda geçtiklerin ve mevcut desteler temizlenir. Kaydettiğin listeler ile İzledim / Oynadım dediklerin kalır."),
                            confirmLabel = t("Sıfırla"),
                            onDismiss = { confirmMusicReset = false },
                            onConfirm = {
                                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
                                    musicScope.launch {
                                        val ok = runCatching {
                                            com.cagri.real.discovery.MusicDiscoveryRepository(musicContext, uid).resetSeen()
                                            if (uid != "guest") {
                                                com.cagri.real.discovery.MediaDiscoveryRepository(musicContext, uid).resetSeen()
                                                com.cagri.real.discovery.GameDiscoveryRepository(musicContext, uid).resetSeen()
                                            }
                                        }.isSuccess
                                        com.cagri.real.ui.components.RealToast.show(musicContext, if (ok) t("Öneriler sıfırlandı") else t("Sıfırlanamadı"))
                                    }
                            }
                        )
                    }

                    SettingsDivider()

                    // Puanlarım: film / dizi / bölüm puanlarını siler (şimdilik test için).
                    var confirmRatingReset by remember { mutableStateOf(false) }
                    val ratingScope = rememberCoroutineScope()
                    SettingsActionRow(
                        icon = ActionGlyph.Delete,
                        title = t("Puanlarımı sıfırla"),
                        subtitle = t("Film, dizi ve bölüm puanlarının hepsini siler"),
                        onClick = { confirmRatingReset = true }
                    )
                    if (confirmRatingReset) {
                        com.cagri.real.ui.components.ConfirmSheet(
                            title = t("Puanları sıfırlamak istediğine emin misin?"),
                            message = t("Puanlarım listesi boşalır. Bu geri alınamaz."),
                            confirmLabel = t("Sıfırla"),
                            onDismiss = { confirmRatingReset = false },
                            onConfirm = {
                                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid != null) ratingScope.launch {
                                        val ok = runCatching { com.cagri.real.discovery.MediaRatingRepository(musicContext, uid).reset() }.isSuccess
                                        com.cagri.real.ui.components.RealToast.show(musicContext, if (ok) t("Puanlar sıfırlandı") else t("Sıfırlanamadı"))
                                    }
                            }
                        )
                    }

                    SettingsDivider()

                    // Kapalı / 7 gün / 14 gün, picked in a sheet.
                    val settingsContext = LocalContext.current
                    val homeCalendarDays = com.cagri.real.calendar.CalendarHomeDays.get(settingsContext)
                    var showCalendarSheet by remember { mutableStateOf(false) }
                    SettingsChoiceRow(
                        icon = ActionGlyph.Calendar,
                        title = t("Ana ekranda takvim"),
                        value = com.cagri.real.calendar.CalendarHomeDays.label(homeCalendarDays),
                        onClick = { showCalendarSheet = true }
                    )
                    if (showCalendarSheet) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = t("Ana ekranda takvim"),
                            choices = com.cagri.real.calendar.CalendarHomeDays.options.map {
                                com.cagri.real.ui.components.Choice(it, com.cagri.real.calendar.CalendarHomeDays.label(it))
                            },
                            selected = homeCalendarDays,
                            onSelect = { com.cagri.real.calendar.CalendarHomeDays.set(settingsContext, it) },
                            onDismiss = { showCalendarSheet = false }
                        )
                    }
                }

                // ==========================================
                // 2. BİLDİRİMLER VE DAVRANIŞ (Behavior)
                // ==========================================
                SettingsSectionHeader(t("BİLDİRİMLER VE DAVRANIŞ"))

                SettingsGlassGroup {
                    // Notification permission
                    SettingsStatusRow(
                        icon = ActionGlyph.Bell,
                        title = "Bildirim izni",
                        status = if (notificationsAllowed) t("Açık") else t("Kapalı"),
                        statusColor = if (notificationsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                        subtitle = if (notificationsAllowed) t("Hatırlatıcı bildirimleri bu cihazda etkin.")
                        else t("Bildirimleri alabilmek için bildirim iznini açabilirsin."),
                        actionLabel = if (!notificationsAllowed) t("İzni aç") else null,
                        onActionClick = if (!notificationsAllowed) onRequestNotifications else null
                    )

                    SettingsDivider()

                    // Reminder channel
                    SettingsStatusRow(
                        icon = ActionGlyph.Settings,
                        title = t("Hatırlatıcı kanalı"),
                        status = when {
                            isChannelBlocked -> t("Kapalı")
                            isChannelVibrationEnabled -> t("Açık • Titreşimli")
                            else -> t("Açık • Titreşimsiz")
                        },
                        statusColor = when {
                            isChannelBlocked -> Color(0xFFA5A5AF)
                            isChannelVibrationEnabled -> Color(0xFF91B9FF)
                            else -> Color(0xFFA5A5AF)
                        },
                        subtitle = when {
                            isChannelBlocked -> t("Hatırlatıcı bildirim kanalı sistem ayarlarından kapatılmış.")
                            isChannelVibrationEnabled -> t("Bildirim geldiğinde ses ve titreşim uyarısı verilir.")
                            else -> t("Kanal açık fakat titreşim sistem ayarlarından kapatılmış.")
                        },
                        actionLabel = t("Kanal ayarları"),
                        onActionClick = {
                            try {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL_ID)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    )

                    SettingsDivider()

                    // Exact alarms
                    SettingsStatusRow(
                        icon = ActionGlyph.Calendar,
                        title = t("Tam saatinde hatırlatma"),
                        status = if (exactAlarmsAllowed) t("Açık") else t("Kapalı"),
                        statusColor = if (exactAlarmsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                        subtitle = if (exactAlarmsAllowed) t("Alarmlar tam zamanında çalacak şekilde ayarlandı.")
                        else t("Android'in bildirim saatini geciktirmemesi için alarm iznini açabilirsin."),
                        actionLabel = if (!exactAlarmsAllowed) t("İzni aç") else null,
                        onActionClick = if (!exactAlarmsAllowed) onRequestExactAlarms else null
                    )

                    SettingsDivider()

                    // Alarm cards: sound (the phone's ringtone by default) and snooze length.
                    var alarmSound by remember { mutableStateOf(com.cagri.real.notifications.RealAlarm.soundTitle(context)) }
                    var alarmSnooze by remember { mutableStateOf(com.cagri.real.notifications.RealAlarm.snoozeMinutes(context)) }
                    val alarmSoundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val data = result.data ?: return@rememberLauncherForActivityResult
                        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
                        @Suppress("DEPRECATION")
                        val picked = data.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                        com.cagri.real.notifications.RealAlarm.setSound(context, picked)
                        alarmSound = com.cagri.real.notifications.RealAlarm.soundTitle(context)
                    }
                    SettingsActionRow(
                        icon = ActionGlyph.Bell,
                        title = "Alarm sesi",
                        subtitle = alarmSound,
                        onClick = {
                            val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    android.media.RingtoneManager.TYPE_RINGTONE or android.media.RingtoneManager.TYPE_ALARM)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sesi")
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, android.provider.Settings.System.DEFAULT_RINGTONE_URI)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, com.cagri.real.notifications.RealAlarm.soundUri(context))
                            }
                            runCatching { alarmSoundPicker.launch(intent) }
                        }
                    )

                    SettingsDivider()

                    var showSnoozeSheet by remember { mutableStateOf(false) }
                    SettingsChoiceRow(
                        icon = ActionGlyph.Repeat,
                        title = t("Alarm erteleme"),
                        value = t("{0} dakika", alarmSnooze),
                        onClick = { showSnoozeSheet = true }
                    )
                    if (showSnoozeSheet) {
                        com.cagri.real.ui.components.ChoiceSheet(
                            title = t("Alarm erteleme"),
                            choices = com.cagri.real.notifications.RealAlarm.SNOOZE_CHOICES.map {
                                com.cagri.real.ui.components.Choice(it, t("{0} dakika", it))
                            },
                            selected = alarmSnooze,
                            onSelect = {
                                alarmSnooze = it
                                com.cagri.real.notifications.RealAlarm.setSnoozeMinutes(context, it)
                            },
                            onDismiss = { showSnoozeSheet = false }
                        )
                    }

                    // Optional: full screen also while the phone is in use; re-read when coming back from the system page.
                    var overPhone by remember { mutableStateOf(com.cagri.real.notifications.RealAlarm.canShowOverPhone(context)) }
                    val alarmLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                    DisposableEffect(alarmLifecycle) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) overPhone = com.cagri.real.notifications.RealAlarm.canShowOverPhone(context)
                        }
                        alarmLifecycle.addObserver(observer)
                        onDispose { alarmLifecycle.removeObserver(observer) }
                    }
                    SettingsDivider()
                    SettingsStatusRow(
                        icon = ActionGlyph.Bell,
                        title = t("Telefon açıkken tam ekran alarm"),
                        status = if (overPhone) t("Açık") else t("Kapalı"),
                        statusColor = if (overPhone) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                        subtitle = if (overPhone) t("Alarm, telefonu kullanırken de tam ekran açılır.")
                        else t("Kapalıyken telefonu kullanırken alarm üstte bildirim olarak görünür."),
                        actionLabel = if (overPhone) "Kapat" else t("İzni aç"),
                        onActionClick = { com.cagri.real.notifications.RealAlarm.openOverPhoneSettings(context) }
                    )

                    if (!com.cagri.real.notifications.RealAlarm.canRingFullScreen(context)) {
                        SettingsDivider()
                        SettingsStatusRow(
                            icon = ActionGlyph.Bell,
                            title = "Tam ekran alarm",
                            status = t("Kapalı"),
                            statusColor = Color(0xFFA5A5AF),
                            subtitle = t("Alarmların kilit ekranında tam ekran açılması için bu izni aç."),
                            actionLabel = t("İzni aç"),
                            onActionClick = { com.cagri.real.notifications.RealAlarm.openFullScreenSettings(context) }
                        )
                    }

                    // Samsung Now Bar toggle: only on Samsung phones, where Now Bar exists.
                    if (NowBarReminderController.isSupportedDevice) {
                    SettingsDivider()
                    var nowBarEnabled by remember { mutableStateOf(NowBarReminderController.isEnabled(context)) }
                    SettingsRow(
                        icon = ActionGlyph.NowBar,
                        title = "Samsung Now Bar",
                        subtitle = t("Açıkken kartın üstündeki Yapıştır butonunun yerinde Now Bar butonu olur."),
                        badge = t("Geliştiriliyor"),
                        checked = nowBarEnabled,
                        onCheckedChange = {
                            nowBarEnabled = it
                            NowBarReminderController.setEnabled(context, it)
                        }
                    )
                    }
                }

                // ==========================================
                // 5. HAKKINDA (About)
                // ==========================================
                SettingsSectionHeader("REAL PRO")
                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Star,
                        title = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_pro_title),
                        subtitle = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_pro_settings_summary),
                        onClick = { showRealPro = true }
                    )
                }
                SettingsSectionHeader(t("GİZLİLİK"))
                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_title),
                        subtitle = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_settings_subtitle),
                        onClick = { showDataUse = true }
                    )
                    SettingsDivider()

                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_ai),
                        subtitle = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_ai_subtitle),
                        onClick = { showAiPrivacy = true }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_rights),
                        subtitle = androidx.compose.ui.res.stringResource(com.cagri.real.R.string.real_privacy_rights_subtitle),
                        onClick = { showPrivacyRights = true }
                    )
                    SettingsDivider()
                    // Off by default: a real photo goes to Kişilerim and shared-category people only when this is on.
                    // An avatar is always shared (it is not a photo of the person).
                    var sharePhoto by remember { mutableStateOf(com.cagri.real.platform.android.connections.ProfilePictures.sharePhoto(context)) }
                    val pictureScope = rememberCoroutineScope()
                    SettingsRow(
                        icon = ActionGlyph.Person,
                        title = t("Fotoğrafımı kişilerimle paylaş"),
                        subtitle = t("Profil fotoğrafın Kişilerim'de ve ortak kategorilerde görünür. Avatar her zaman görünür."),
                        checked = sharePhoto,
                        onCheckedChange = { on ->
                            sharePhoto = on
                            com.cagri.real.platform.android.connections.ProfilePictures.setSharePhoto(context, on)
                            pictureScope.launch { com.cagri.real.platform.android.connections.ProfilePictures.push(context.applicationContext) }
                        }
                    )
                }

                SettingsSectionHeader("HAKKINDA")

                SettingsGlassGroup {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(ActionGlyph.Checklist, Modifier.size(20.dp), color = Color.White)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "REAL",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                color = Color(0xFFA3A3AB),
                                fontSize = 12.sp
                            )
                        }
                    }

                    SettingsDivider()

                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = t("Açık kaynak lisansları"),
                        subtitle = t("Humation, Tabler Icons ve üçüncü taraf bildirimleri"),
                        onClick = { showLicensesDialog = true }
                    )

                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = t("Veri kaynakları ve lisanslar"),
                        subtitle = "TheTVDB, TVmaze ve IGDB",
                        onClick = { showDataSources = true }
                    )
                }

                SettingsSectionHeader(t("GELİŞTİRİCİ"))
                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Settings,
                        title = t("Geliştirici araçları"),
                        subtitle = t("Tanılama, testler ve tasarım demoları"),
                        onClick = { showDeveloperTools = true }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showPetScenarios) {
        PetScenariosDialog(
            onDismiss = { showPetScenarios = false },
            onPetScenarioTest = { kind ->
                showPetScenarios = false
                onPetScenarioTest(kind)
                onBack()
            }
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showClearMoodConfirm) {
        com.cagri.real.ui.components.RealAlertDialog(
            onDismissRequest = { showClearMoodConfirm = false },
            title = { Text(t("Tüm mood kayıtlarını sil?")) },
            text = {
                Text(t("Moods kayıtları ve REAL kartlarındaki Mood blokları silinecek. Günlükler korunacak."))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearMoodConfirm = false
                        onClearAllMoods()
                    }
                ) {
                    Text("Sil", color = Color(0xFFFF6B72), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMoodConfirm = false }) {
                    Text(t("Vazgeç"))
                }
            }
        )
    }
}

@Composable
private fun SettingsGlassGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidChrome(corners = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = com.cagri.real.i18n.t(title),
        color = Color(0xFFA3A3AB).copy(alpha = 0.9f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 9.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.07f),
        thickness = 0.75.dp,
        // Starts under the text, past the icon tile (14 pad + 36 tile + 14 gap).
        modifier = Modifier.padding(start = 64.dp, end = 16.dp)
    )
}

/** Small liquid-glass tile that holds a row icon. */
@Composable
private fun SettingsIconTile(icon: ActionGlyph) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f))
                )
            )
            .border(0.75.dp, liquidRimBrush(), shape),
        contentAlignment = Alignment.Center
    ) {
        ActionIcon(icon, Modifier.size(19.dp), color = Color.White)
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text = com.cagri.real.i18n.t(text),
        color = Color.White,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.2).sp,
        fontWeight = FontWeight.Normal
    )
}

@Composable
private fun SettingsSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = com.cagri.real.i18n.t(text),
        color = Color(0xFF9A9AA5),
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = modifier.padding(top = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ActionGlyph,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else {
                    Modifier.alpha(0.5f)
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The title may shrink (ellipsis) but never wraps letter by letter next to the badge.
                Box(Modifier.weight(1f, fill = false)) { SettingsTitle(title) }
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.20f))
                            .border(0.75.dp, Color(0xFF91B9FF).copy(alpha = 0.28f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color(0xFF91B9FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) SettingsSubtitle(subtitle)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3B82F6),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF9A9AA2),
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

/** A setting with a few values: the current one in grey on the right; tapping opens a ChoiceSheet. */
@Composable
private fun SettingsChoiceRow(
    icon: ActionGlyph,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { SettingsTitle(title) }
        Text(value, color = Color(0xFF9A9AA4), fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 6.dp))
        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), color = Color(0xFF75757F))
    }
}

@Composable
private fun SettingsActionRow(
    icon: ActionGlyph,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            SettingsTitle(title)
            if (subtitle.isNotBlank()) SettingsSubtitle(subtitle)
        }
        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), color = Color(0xFF75757F))
    }
}

@Composable
private fun SettingsStatusRow(
    icon: ActionGlyph,
    title: String,
    status: String,
    statusColor: Color,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SettingsIconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                ) {
                    Box(Modifier.weight(1f).padding(end = 8.dp)) { SettingsTitle(title) }
                    // Status as a small tinted pill instead of loose coloured text.
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f))
                            .border(0.75.dp, statusColor.copy(alpha = 0.30f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = com.cagri.real.i18n.t(status),
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                SettingsSubtitle(subtitle, Modifier.padding(top = 0.dp))
            }
        }
        if (actionLabel != null && onActionClick != null) {
            val pill = CircleShape
            Box(
                Modifier
                    .align(Alignment.End)
                    .padding(top = 10.dp)
                    .clip(pill)
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(0.75.dp, liquidRimBrush(), pill)
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = Color(0xFF91B9FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableIntStateOf(0) }
    val licenseEntries = remember {
        listOf(
            Triple("Humation", t("Humation 1 avatar varlıkları (MIT)"), "licenses/Humation-MIT.txt"),
            Triple("Tabler Icons", t("Tabler Icons vektör simge seti (MIT)"), "licenses/Tabler-Icons-MIT.txt"),
            Triple("Inter Font", t("Inter değişken yazı tipi (SIL OFL 1.1)"), "licenses/Inter-OFL.txt")
        )
    }

    val currentContent = remember(selectedIndex) {
        val entry = licenseEntries[selectedIndex]
        runCatching {
            context.assets.open(entry.third).bufferedReader().use { it.readText() }
        }.getOrDefault("Lisans metni okunamadı.")
    }

    com.cagri.real.ui.components.RealAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Açık kaynak lisansları", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(licenseEntries[selectedIndex].second, color = Color(0xFF9D9DA7), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF202024))
                        .padding(2.dp)
                ) {
                    licenseEntries.forEachIndexed { index, entry ->
                        val isSelected = index == selectedIndex
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF333338) else Color.Transparent)
                                .clickable { selectedIndex = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                entry.first,
                                color = if (isSelected) Color.White else Color(0xFF90909A),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F0F12))
                        .border(0.75.dp, Color(0xFF28282C), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = currentContent,
                        color = Color(0xFFCBCBD4),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF91B9FF), fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFF15161A),
        tonalElevation = 0.dp
    )
}

@Composable
private fun PetScenariosDialog(
    onDismiss: () -> Unit,
    onPetScenarioTest: (RealPetScenarioTestKind) -> Unit
) {
    val scenarios = remember {
        listOf(
            RealPetScenarioTestKind.Walk to "Walk",
            RealPetScenarioTestKind.Run to "Run",
            RealPetScenarioTestKind.Sleep to "Sleep",
            RealPetScenarioTestKind.Sit to "Sit",
            RealPetScenarioTestKind.Hearts to "Hearts",
            RealPetScenarioTestKind.AttentionVisit to "Ask for love",
            RealPetScenarioTestKind.Drink to "Drink",
            RealPetScenarioTestKind.Purr to "Purr",
            RealPetScenarioTestKind.Groom to "Groom",
            RealPetScenarioTestKind.Paw to "Paw",
            RealPetScenarioTestKind.Meow to "Meow",
            RealPetScenarioTestKind.RunAcross to "Run across",
            RealPetScenarioTestKind.FoundSitting to "Found sitting (app open)",
            RealPetScenarioTestKind.AlreadySleeping to "Found sleeping (app open)",
            RealPetScenarioTestKind.Leave to "Leave",
            RealPetScenarioTestKind.KeywordKedi to "Keyword: Kedi",
            RealPetScenarioTestKind.KeywordMama to "Keyword: Mama / Feed"
        )
    }

    com.cagri.real.ui.components.RealAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                t("Pet senaryoları"),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(scenarios) { _, (kind, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF202126))
                            .clickable { onPetScenarioTest(kind) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIcon(ActionGlyph.Cat, Modifier.size(20.dp), Color(0xFFE7E7EB))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = title,
                            color = Color(0xFFF4F4F6),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFFB6B6C0))
            }
        },
        containerColor = Color(0xFF15161A),
        tonalElevation = 0.dp
    )
}


/** Static attribution. Reading this page sends no catalog or AI request. */
private data class CatalogSourceNotice(
    val name: String,
    val summary: String,
    val website: String,
    val terms: String
)

private val catalogSourceNotices get() = listOf(
    CatalogSourceNotice(
        "TheTVDB",
        t("Film bilgileri TheTVDB tarafından sağlanır. API kullanımı TheTVDB'nin proje lisansı ve kullanım koşullarına tabidir. Afişler ve oyuncu görselleri ilgili hak sahiplerine aittir; API erişimi görsel kullanım haklarını otomatik olarak vermez."),
        "https://thetvdb.com/",
        "https://www.thetvdb.com/api-information"
    ),
    CatalogSourceNotice(
        "TVmaze",
        t("Dizi bilgileri TVmaze tarafından sağlanır. API verileri CC BY-SA lisansıyla sunulur; kaynak gösterme ve uyarlanmış materyal için aynı lisans koşullarını koruma yükümlülüğü vardır. Kapaklar ve oyuncu görselleri ilgili hak sahiplerine aittir."),
        "https://www.tvmaze.com/",
        "https://www.tvmaze.com/api#licensing"
    ),
    CatalogSourceNotice(
        "IGDB",
        t("Oyun bilgileri ve kapaklar IGDB'den alınır. IGDB, ticari kullanım için partner başvurusu ve görünür kaynak gösterimi ister. API'nin ücretsiz sunulması bu başvuru koşulunu kaldırmaz. Oyun kapakları ve marka hakları ilgili hak sahiplerine aittir."),
        "https://www.igdb.com/",
        "https://api-docs.igdb.com/#business-related-faq"
    ),
    CatalogSourceNotice(
        "Apple iTunes",
        t("Müzik araması, kapaklar ve önizleme bağlantıları Apple iTunes Search API'den alınır. Önizlemeler tanıtım amaçlıdır; indirilip kitaplığa kaydedilmez. Apple'ın önizleme, kaynak gösterimi ve mağazaya bağlantı koşulları geçerlidir."),
        "https://www.apple.com/itunes/",
        "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html"
    ),
    CatalogSourceNotice(
        "OpenFreeMap / OpenStreetMap",
        t("Harita karoları OpenFreeMap üzerinden gelir. OpenMapTiles ve OpenStreetMap katkıda bulunanları harita verisinin kaynaklarıdır. Haritada görünen kaynak gösterimi korunmalıdır; bu sayfa harita üzerindeki kaynak gösteriminin yerine geçmez."),
        "https://openfreemap.org/",
        "https://osmfoundation.org/wiki/Licence/Attribution_Guidelines"
    ),
    CatalogSourceNotice(
        "Humation",
        t("Çizim avatar düzenleyicisi Humation'ın açık kaynak kodunu ve görsellerini kullanır. Proje bunları MIT lisansıyla sunar. MIT telif ve lisans metninin dağıtımda korunması gerekir."),
        "https://humation.app/",
        "https://github.com/humation-labs/humation"
    )
)

@Composable
private fun CatalogSourcesSection() {
    val uri = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        linkError = null
        try { uri.openUri(url) }
        catch (_: Exception) { linkError = t("Bağlantı açılamadı. Bir tarayıcı yüklü olduğundan emin ol.") }
    }
    SettingsSectionHeader(t("VERİ KAYNAKLARI VE LİSANSLAR"))
    catalogSourceNotices.forEach { source ->
        SettingsGlassGroup {
            SettingsActionRow(
                icon = ActionGlyph.Note,
                title = source.name,
                subtitle = t("Resmî kaynağı aç"),
                onClick = { open(source.website) }
            )
            Text(source.summary, color = Color(0xFFA3A3AB), fontSize = 13.sp,
                lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            SettingsDivider()
            SettingsActionRow(
                icon = ActionGlyph.Note,
                title = t("Lisans ve kullanım koşulları"),
                subtitle = source.name,
                onClick = { open(source.terms) }
            )
        }
    }
    linkError?.let {
        Text(it, color = Color(0xFFFFC8B8), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

/** Paylaş menüsü kısayolları: on/off, and which categories appear in Android's share menu (up to 4; none = most used). */
@Composable
private fun ShareShortcutSettings() {
    val context = LocalContext.current
    val shortcuts = com.cagri.real.platform.android.content.ShareShortcuts
    var enabled by remember { mutableStateOf(shortcuts.isEnabled(context)) }
    var selected by remember { mutableStateOf(shortcuts.selected(context)) }
    var picking by remember { mutableStateOf(false) }
    val categories = remember { com.cagri.real.data.CategoryRepository(context).load().filter { !it.isReadOnlyShared } }
    val scope = rememberCoroutineScope()
    fun republish() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            shortcuts.publish(context, com.cagri.real.data.CategoryRepository(context).load(),
                com.cagri.real.data.ReminderRepository(context).load())
        }
    }
    SettingsRow(
        icon = ActionGlyph.Share,
        title = t("Paylaş menüsü kısayolları"),
        subtitle = t("Başka bir uygulamada Paylaş deyince REAL kategorilerin menünün üst sırasında çıkar."),
        checked = enabled,
        onCheckedChange = { enabled = it; shortcuts.setEnabled(context, it); republish() }
    )
    if (enabled) {
        SettingsDivider()
        val names = selected.mapNotNull { id -> categories.firstOrNull { it.id == id }?.name }
        SettingsActionRow(
            icon = ActionGlyph.Category,
            title = t("Kısayol kategorileri"),
            subtitle = if (names.isEmpty()) t("Otomatik · en çok kullandığın kategoriler") else names.joinToString(", "),
            onClick = { picking = true }
        )
    }
    if (picking) {
        com.cagri.real.ui.components.RealAlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(t("Kısayol kategorileri")) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(t("En fazla {0} kategori seç. Hiçbirini seçmezsen en çok kullandıkların çıkar.", shortcuts.MAX),
                        color = Color(0xFFA5A5AF), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    if (categories.isEmpty()) Text(t("Henüz kategori yok."), color = Color(0xFFA5A5AF), fontSize = 14.sp)
                    categories.forEach { c ->
                        val on = c.id in selected
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = on || selected.size < shortcuts.MAX) {
                                selected = if (on) selected - c.id else selected + c.id
                            }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                                .border(1.2.dp, if (on) Color.White else Color(0xFF77777F), CircleShape), contentAlignment = Alignment.Center) {
                                if (on) ActionIcon(ActionGlyph.Check, Modifier.size(12.dp), Color.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, color = if (on || selected.size < shortcuts.MAX) Color.White else Color(0xFF66666E), fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shortcuts.setSelected(context, selected); picking = false; republish() }) { Text(t("Kaydet")) }
            },
            dismissButton = { TextButton(onClick = { selected = shortcuts.selected(context); picking = false }) { Text(t("Vazgeç")) } }
        )
    }
}
