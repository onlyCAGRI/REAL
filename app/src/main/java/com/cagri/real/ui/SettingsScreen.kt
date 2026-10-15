package com.cagri.real.ui

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
import com.cagri.real.ui.components.planner.PlannerDailyView
import com.cagri.real.ui.components.planner.rememberPlannerDailyViewPreference

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
    onToggleWidgetReminder: ((Long) -> Unit)? = null
) {
    val (dailyPlannerView, setDailyPlannerView) = rememberPlannerDailyViewPreference()
    var showDailyPlannerChoice by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(enabled = !showRealPro && !showDailyPlannerChoice && !showPetScenarios && !showLicensesDialog && !showClearMoodConfirm && !showAiPrivacy && !showDataUse && !showPrivacyRights && !showDataSources && !showDeveloperTools, onBack = onBack)

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

    if (showDailyPlannerChoice) {
        com.cagri.real.ui.components.RealAlertDialog(
            onDismissRequest = { showDailyPlannerChoice = false },
            title = { Text("Daily Planner View") },
            text = {
                Column {
                    PlannerDailyView.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = choice == dailyPlannerView,
                                role = Role.RadioButton,
                                onClick = {
                                    setDailyPlannerView(choice)
                                    showDailyPlannerChoice = false
                                }
                            ).padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = choice == dailyPlannerView,
                                onClick = null
                            )
                            Text(choice.label, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDailyPlannerChoice = false }) { Text("Kapat") } }
        )
    }

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

    BackHandler(enabled = showDeveloperTools && !showClearMoodConfirm && !showPetScenarios) { showDeveloperTools = false }

    key(showDeveloperTools) {
        SettingsLayout(DesignStyle.LiquidGlass, reduceDynamicBlur, {
            if (showDeveloperTools) showDeveloperTools = false else onBack()
        }) {
            if (showDeveloperTools) {
                SettingsSectionHeader("GELİŞTİRİCİ ARAÇLARI")
                SettingsSectionHeader("BAĞLAM KARTLARI TESTİ")

                SettingsGlassGroup {
                    val testCardTypes = listOf(
                        Triple(com.cagri.real.contextcards.ContextCardType.WATER, ActionGlyph.Bell, "Su kartını göster"),
                        Triple(com.cagri.real.contextcards.ContextCardType.MOOD, ActionGlyph.MoodNeutral, "Mood kartını göster"),
                        Triple(com.cagri.real.contextcards.ContextCardType.BOOK, ActionGlyph.Note, "Kitap kartını göster"),
                        Triple(com.cagri.real.contextcards.ContextCardType.MEDIA, ActionGlyph.Video, "Film & dizi kartını göster")
                    )
                    testCardTypes.forEachIndexed { index, (type, glyph, label) ->
                        if (index > 0) SettingsDivider()
                        SettingsActionRow(
                            icon = glyph,
                            title = label,
                            subtitle = "Saat ve limit kurallarını yok sayar; ana ekranın en altında görünür",
                            onClick = {
                                contextCardStore.setEnabled(true)
                                contextCardsEnabled = true
                                contextCardStore.force(type)
                                com.cagri.real.ui.components.RealToast.show(context, "Ana ekranın en altına bak")
                            }
                        )
                    }
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Edit,
                        title = "Kart geçmişini sıfırla",
                        subtitle = "Günlük limit, bekleme ve sessize alma sayaçlarını temizler",
                        onClick = {
                            contextCardStore.resetHistory()
                            com.cagri.real.ui.components.RealToast.show(context, "Kart geçmişi sıfırlandı")
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Theme,
                        title = "Karşılama ekranını önizle",
                        subtitle = "İlk açılış animasyonunu oynatır; hesabını ve ayarlarını değiştirmez",
                        onClick = { showWelcomePreview = true }
                    )
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

                if (realPetEnabled) {
                    SettingsSectionHeader("REAL PET")

                    SettingsGlassGroup {
                        SettingsActionRow(
                            icon = ActionGlyph.Cat,
                            title = "Pet senaryoları",
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
                            subtitle = "Yazdığın bazı kelimeler REAL Pet davranışlarını tetikleyebilir.",
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
                SettingsSectionHeader("GELİŞTİRİCİ VE DEMO")

                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Cards,
                        title = "Widget tasarımları",
                        subtitle = "Yaşam widget önizlemeleri",
                        onClick = { showWidgetDesigns = true }
                    )
                    SettingsDivider()
                    // Concept demo entry
                    SettingsActionRow(
                        icon = ActionGlyph.Edit,
                        title = "Tasarım demosu",
                        subtitle = "Takvim, kategoriler ve hızlı ses konsepti",
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
                            Text(if (supported) "Açık" else "Sistem tarafından bildirilmedi", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))

                            Text("Samsung Now Bar metadata", color = Color(0xFFCCCCD3), fontSize = 13.sp)
                            Text("Hazır", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 8.dp))

                            Text("Notification structure", color = Color(0xFFCCCCD3), fontSize = 13.sp)
                            Text(if (characteristics) "Uygun" else "Uygun değil", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 12.dp))

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
                                "Moods ve REAL kartlarındaki Mood kayıtlarını siler. Günlük kayıtlarına dokunmaz.",
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
                SettingsSectionHeader("GÖRÜNÜM")

                SettingsGlassGroup {
                    SettingsRow(
                        icon = ActionGlyph.MotionOff,
                        title = "Animasyonu azalt",
                        subtitle = "Arayüz hareketlerini ve geçiş efektlerini sakinleştirir",
                        checked = reduceAnimations,
                        onCheckedChange = onReduceAnimationsChange
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.BlurOff,
                        title = "Dinamik bulanıklığı azalt",
                        subtitle = "Bulanıklık efektleri yerine sabit koyu yüzeyler kullanır",
                        checked = reduceDynamicBlur,
                        onCheckedChange = onReduceDynamicBlurChange
                    )


                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.Cat,
                        title = "REAL Pet",
                        subtitle = "Geçici olarak devre dışı bırakıldı",
                        checked = false,
                        onCheckedChange = {},
                        enabled = false
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = ActionGlyph.Bell,
                        title = "Bağlam kartları",
                        subtitle = "Ana ekranın altında arada su, mood, kitap ve film önerisi gösterir",
                        checked = contextCardsEnabled,
                        onCheckedChange = {
                            contextCardsEnabled = it
                            contextCardStore.setEnabled(it)
                        }
                    )

                    SettingsDivider()

                    SettingsActionRow(
                        icon = ActionGlyph.Calendar,
                        title = "Daily Planner View",
                        subtitle = dailyPlannerView.label,
                        onClick = { showDailyPlannerChoice = true }
                    )

                    SettingsDivider()

                    SettingsActionRow(
                        icon = ActionGlyph.Calendar,
                        title = "Takvim bağlantısı",
                        subtitle = "Google / Samsung takvimlerini göster ve planlarını gönder",
                        onClick = onOpenCalendarConnection
                    )

                    SettingsDivider()
                    val settingsCardsContext = LocalContext.current

                    var plainHomeCards by remember { mutableStateOf(com.cagri.real.ui.components.HomeCardStyle.isPlain(settingsCardsContext)) }
                    SettingsRow(
                        icon = ActionGlyph.Check,
                        title = "Sade kartlar",
                        subtitle = "Ana ekranda tamamlama yuvarlağı yok; kartı sağa kaydırınca tamamlanır.",
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
                    SettingsActionRow(
                        icon = ActionGlyph.Music,
                        title = "Müzik uygulaması",
                        subtitle = musicSource.label + " · şarkılar burada açılır",
                        onClick = {
                            val next = if (musicSource == com.cagri.real.ui.components.MusicSource.Apple)
                                com.cagri.real.ui.components.MusicSource.Spotify else com.cagri.real.ui.components.MusicSource.Apple
                            com.cagri.real.ui.components.MusicSource.save(musicContext, next)
                            musicSource = next
                        }
                    )

                    SettingsDivider()

                    // Kapalı → 7 gün → 14 gün, one tap each.
                    val settingsContext = LocalContext.current
                    val homeCalendarDays = com.cagri.real.calendar.CalendarHomeDays.get(settingsContext)
                    SettingsActionRow(
                        icon = ActionGlyph.Calendar,
                        title = "Ana ekranda takvim",
                        subtitle = com.cagri.real.calendar.CalendarHomeDays.label(homeCalendarDays),
                        onClick = {
                            val options = com.cagri.real.calendar.CalendarHomeDays.options
                            com.cagri.real.calendar.CalendarHomeDays.set(settingsContext,
                                options[(options.indexOf(homeCalendarDays) + 1) % options.size])
                        }
                    )
                }

                // ==========================================
                // 2. BİLDİRİMLER VE DAVRANIŞ (Behavior)
                // ==========================================
                SettingsSectionHeader("BİLDİRİMLER VE DAVRANIŞ")

                SettingsGlassGroup {
                    // Notification permission
                    SettingsStatusRow(
                        icon = ActionGlyph.Bell,
                        title = "Bildirim izni",
                        status = if (notificationsAllowed) "Açık" else "Kapalı",
                        statusColor = if (notificationsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                        subtitle = if (notificationsAllowed) "Hatırlatıcı bildirimleri bu cihazda etkin."
                        else "Bildirimleri alabilmek için bildirim iznini açabilirsin.",
                        actionLabel = if (!notificationsAllowed) "İzni aç" else null,
                        onActionClick = if (!notificationsAllowed) onRequestNotifications else null
                    )

                    SettingsDivider()

                    // Reminder channel
                    SettingsStatusRow(
                        icon = ActionGlyph.Settings,
                        title = "Hatırlatıcı kanalı",
                        status = when {
                            isChannelBlocked -> "Kapalı"
                            isChannelVibrationEnabled -> "Açık • Titreşimli"
                            else -> "Açık • Titreşimsiz"
                        },
                        statusColor = when {
                            isChannelBlocked -> Color(0xFFA5A5AF)
                            isChannelVibrationEnabled -> Color(0xFF91B9FF)
                            else -> Color(0xFFA5A5AF)
                        },
                        subtitle = when {
                            isChannelBlocked -> "Hatırlatıcı bildirim kanalı sistem ayarlarından kapatılmış."
                            isChannelVibrationEnabled -> "Bildirim geldiğinde ses ve titreşim uyarısı verilir."
                            else -> "Kanal açık fakat titreşim sistem ayarlarından kapatılmış."
                        },
                        actionLabel = "Kanal ayarları",
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
                        title = "Tam saatinde hatırlatma",
                        status = if (exactAlarmsAllowed) "Açık" else "Kapalı",
                        statusColor = if (exactAlarmsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                        subtitle = if (exactAlarmsAllowed) "Alarmlar tam zamanında çalacak şekilde ayarlandı."
                        else "Android'in bildirim saatini geciktirmemesi için alarm iznini açabilirsin.",
                        actionLabel = if (!exactAlarmsAllowed) "İzni aç" else null,
                        onActionClick = if (!exactAlarmsAllowed) onRequestExactAlarms else null
                    )

                    SettingsDivider()

                    // Samsung Now Bar toggle
                    var nowBarEnabled by remember { mutableStateOf(NowBarReminderController.isEnabled(context)) }
                    SettingsRow(
                        icon = ActionGlyph.NowBar,
                        title = "Samsung Now Bar",
                        subtitle = "Geliştiriliyor. Açıkken kartın üstündeki Yapıştır butonunun yerinde Now Bar butonu olur.",
                        badge = "Geliştiriliyor",
                        checked = nowBarEnabled,
                        onCheckedChange = {
                            nowBarEnabled = it
                            NowBarReminderController.setEnabled(context, it)
                        }
                    )
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
                SettingsSectionHeader("GİZLİLİK")
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
                        title = "Açık kaynak lisansları",
                        subtitle = "Humation, Tabler Icons ve üçüncü taraf bildirimleri",
                        onClick = { showLicensesDialog = true }
                    )

                    SettingsDivider()
                    SettingsActionRow(
                        icon = ActionGlyph.Note,
                        title = "Veri kaynakları ve lisanslar",
                        subtitle = "TheTVDB, TVmaze ve IGDB",
                        onClick = { showDataSources = true }
                    )
                }

                SettingsSectionHeader("GELİŞTİRİCİ")
                SettingsGlassGroup {
                    SettingsActionRow(
                        icon = ActionGlyph.Settings,
                        title = "Geliştirici araçları",
                        subtitle = "Tanılama, testler ve tasarım demoları",
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
            title = { Text("Tüm mood kayıtlarını sil?") },
            text = {
                Text("Moods kayıtları ve REAL kartlarındaki Mood blokları silinecek. Günlükler korunacak.")
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
                    Text("Vazgeç")
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
        text = title,
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
        text = text,
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
        text = text,
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
                SettingsTitle(title)
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
                            fontWeight = FontWeight.Medium
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
                            text = status,
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
            Triple("Humation", "Humation 1 avatar varlıkları (MIT)", "licenses/Humation-MIT.txt"),
            Triple("Tabler Icons", "Tabler Icons vektör simge seti (MIT)", "licenses/Tabler-Icons-MIT.txt"),
            Triple("Inter Font", "Inter değişken yazı tipi (SIL OFL 1.1)", "licenses/Inter-OFL.txt")
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
            RealPetScenarioTestKind.KeywordKedi to "Keyword: Kedi",
            RealPetScenarioTestKind.KeywordMama to "Keyword: Mama / Feed"
        )
    }

    com.cagri.real.ui.components.RealAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Pet senaryoları",
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

private val catalogSourceNotices = listOf(
    CatalogSourceNotice(
        "TheTVDB",
        "Film bilgileri TheTVDB tarafından sağlanır. API kullanımı TheTVDB'nin proje lisansı ve kullanım koşullarına tabidir. Afişler ve oyuncu görselleri ilgili hak sahiplerine aittir; API erişimi görsel kullanım haklarını otomatik olarak vermez.",
        "https://thetvdb.com/",
        "https://www.thetvdb.com/api-information"
    ),
    CatalogSourceNotice(
        "TVmaze",
        "Dizi bilgileri TVmaze tarafından sağlanır. API verileri CC BY-SA lisansıyla sunulur; kaynak gösterme ve uyarlanmış materyal için aynı lisans koşullarını koruma yükümlülüğü vardır. Kapaklar ve oyuncu görselleri ilgili hak sahiplerine aittir.",
        "https://www.tvmaze.com/",
        "https://www.tvmaze.com/api#licensing"
    ),
    CatalogSourceNotice(
        "IGDB",
        "Oyun bilgileri ve kapaklar IGDB'den alınır. IGDB, ticari kullanım için partner başvurusu ve görünür kaynak gösterimi ister. API'nin ücretsiz sunulması bu başvuru koşulunu kaldırmaz. Oyun kapakları ve marka hakları ilgili hak sahiplerine aittir.",
        "https://www.igdb.com/",
        "https://api-docs.igdb.com/#business-related-faq"
    ),
    CatalogSourceNotice(
        "Apple iTunes",
        "Müzik araması, kapaklar ve önizleme bağlantıları Apple iTunes Search API'den alınır. Önizlemeler tanıtım amaçlıdır; indirilip kitaplığa kaydedilmez. Apple'ın önizleme, kaynak gösterimi ve mağazaya bağlantı koşulları geçerlidir.",
        "https://www.apple.com/itunes/",
        "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html"
    ),
    CatalogSourceNotice(
        "OpenFreeMap / OpenStreetMap",
        "Harita karoları OpenFreeMap üzerinden gelir. OpenMapTiles ve OpenStreetMap katkıda bulunanları harita verisinin kaynaklarıdır. Haritada görünen kaynak gösterimi korunmalıdır; bu sayfa harita üzerindeki kaynak gösteriminin yerine geçmez.",
        "https://openfreemap.org/",
        "https://osmfoundation.org/wiki/Licence/Attribution_Guidelines"
    ),
    CatalogSourceNotice(
        "Humation",
        "Çizim avatar düzenleyicisi Humation'ın açık kaynak kodunu ve görsellerini kullanır. Proje bunları MIT lisansıyla sunar. MIT telif ve lisans metninin dağıtımda korunması gerekir.",
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
        catch (_: Exception) { linkError = "Bağlantı açılamadı. Bir tarayıcı yüklü olduğundan emin ol." }
    }
    SettingsSectionHeader("VERİ KAYNAKLARI VE LİSANSLAR")
    catalogSourceNotices.forEach { source ->
        SettingsGlassGroup {
            SettingsActionRow(
                icon = ActionGlyph.Note,
                title = source.name,
                subtitle = "Resmî kaynağı aç",
                onClick = { open(source.website) }
            )
            Text(source.summary, color = Color(0xFFA3A3AB), fontSize = 13.sp,
                lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            SettingsDivider()
            SettingsActionRow(
                icon = ActionGlyph.Note,
                title = "Lisans ve kullanım koşulları",
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
        title = "Paylaş menüsü kısayolları",
        subtitle = "Başka bir uygulamada Paylaş deyince REAL kategorilerin menünün üst sırasında çıkar.",
        checked = enabled,
        onCheckedChange = { enabled = it; shortcuts.setEnabled(context, it); republish() }
    )
    if (enabled) {
        SettingsDivider()
        val names = selected.mapNotNull { id -> categories.firstOrNull { it.id == id }?.name }
        SettingsActionRow(
            icon = ActionGlyph.Category,
            title = "Kısayol kategorileri",
            subtitle = if (names.isEmpty()) "Otomatik · en çok kullandığın kategoriler" else names.joinToString(", "),
            onClick = { picking = true }
        )
    }
    if (picking) {
        com.cagri.real.ui.components.RealAlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Kısayol kategorileri") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text("En fazla ${shortcuts.MAX} kategori seç. Hiçbirini seçmezsen en çok kullandıkların çıkar.",
                        color = Color(0xFFA5A5AF), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    if (categories.isEmpty()) Text("Henüz kategori yok.", color = Color(0xFFA5A5AF), fontSize = 14.sp)
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
                TextButton(onClick = { shortcuts.setSelected(context, selected); picking = false; republish() }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { selected = shortcuts.selected(context); picking = false }) { Text("Vazgeç") } }
        )
    }
}
