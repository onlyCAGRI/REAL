package com.cagri.bigboscagri.ui

import com.cagri.bigboscagri.BuildConfig
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.bigboscagri.data.CustomCategory
import com.cagri.bigboscagri.notifications.NowBarProbe
import com.cagri.bigboscagri.notifications.NowBarReminderController
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
import com.cagri.bigboscagri.notifications.ReminderNotifications
import com.cagri.bigboscagri.ui.components.*

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
    customCategories: List<CustomCategory> = emptyList(),
    categoryOrderKeys: List<String> = emptyList(),
    onCategoryOrderChange: (List<String>) -> Unit = {},
    onPetScenarioTest: (RealPetScenarioTestKind) -> Unit = {},
    onClearAllMoods: () -> Unit = {}
) {
    var showCategoryOrderEditor by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showClearMoodConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = !showCategoryOrderEditor && !showLicensesDialog && !showClearMoodConfirm, onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val reminderChannel = remember(isResumed) {
        ReminderNotifications.getReminderChannel(context)
    }
    val isChannelBlocked = reminderChannel != null && reminderChannel.importance == NotificationManager.IMPORTANCE_NONE
    val isChannelVibrationEnabled = reminderChannel != null && !isChannelBlocked && reminderChannel.shouldVibrate()

    val isLiquid = designStyle == DesignStyle.LiquidGlass
    val cardShape = RoundedCornerShape(22.dp)
    val cardBackground = if (isLiquid) LiquidGlassTokens.contentColor else Color(0xFF161619)
    val cardBorderColor = if (isLiquid) Color.White.copy(alpha = 0.08f) else Color(0xFF28282C)

    SettingsLayout(designStyle, reduceDynamicBlur, onBack) {

        // ==========================================
        // 1. GÖRÜNÜM (Appearance)
        // ==========================================
        SettingsSectionHeader("GÖRÜNÜM")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardBackground)
                .border(0.75.dp, cardBorderColor, cardShape)
        ) {
            // Appearance style selector (compact segmented control)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ActionIcon(ActionGlyph.LiquidGlass, Modifier.size(20.dp), color = Color.White)
                    Text(
                        "Görünüm stili",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(10.dp))

                SettingsAppearanceSelector(
                    designStyle = designStyle,
                    onDesignStyleChange = onDesignStyleChange,
                    reduceAnimations = reduceAnimations
                )
            }

            SettingsDivider()

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
                subtitle = "Geliştirme aşamasında buglar mevcuttur",
                checked = realPetEnabled,
                onCheckedChange = onRealPetEnabledChange
            )
        }

        if (realPetEnabled) {
            SettingsSectionHeader("PET SENARYOLARI")

            val petScenarios = listOf(
                Triple(RealPetScenarioTestKind.AlreadySleeping, "Zaten uyuyor", "Kediyi bulunduğu yerde uyuyan bir ziyaretçi gibi başlatır."),
                Triple(RealPetScenarioTestKind.WalkInAndWatch, "Gel, yürü ve izle", "Bir yüzeye gider, yürür ve oturup etrafı izler."),
                Triple(RealPetScenarioTestKind.RunAcross, "Ekrandan koş", "Bulunduğu yüzeyde veya zeminde hızlıca koşar."),
                Triple(RealPetScenarioTestKind.CardHop, "Karttan karta atla", "Yakındaki reminder kartları arasında ileri jump animasyonuyla geçer."),
                Triple(RealPetScenarioTestKind.CategoryPatrol, "Kategorilerde dolaş", "Kategori kartlarının yalnızca üst kenarlarında gezer."),
                Triple(RealPetScenarioTestKind.ComposerVisit, "Add to REAL ziyareti", "Add to REAL alanına gider ve kısa süre orada oturur."),
                Triple(RealPetScenarioTestKind.ComposerPlaceholderPrank, "Add to MEAW şakası", "Add to REAL yazısını meaw varyasyonlarından biriyle değiştirir."),
                Triple(RealPetScenarioTestKind.SitAndObserve, "Otur ve izle", "Sit animasyonuyla uzun süre oturup göz kırpar, sonra kalkar."),
                Triple(RealPetScenarioTestKind.DetailExplorer, "Detay kartlarını gez", "Detay ekranındaki görünür içerik kartları arasında dolaşır."),
                Triple(RealPetScenarioTestKind.PeekAndRun, "Köşeden bak ve kaç", "Köşeden sadece kafasını uzatır, sonra geri çekilip gider."),
                Triple(RealPetScenarioTestKind.AttentionVisit, "İlgi iste", "Miyavlayıp birkaç dokunuş bekleyen dikkat senaryosunu çalıştırır."),
                Triple(RealPetScenarioTestKind.AvatarRaid, "Avatar ziyareti", "Avatarın yanına gidip onu selamlar ve bir süre yanında durur."),
                Triple(RealPetScenarioTestKind.MischiefVisit, "Yazı şakası", "Uygun kategori/metin yüzeyinde küçük bir yazı şakası dener."),
                Triple(RealPetScenarioTestKind.QuietVisit, "Sessiz ziyaret", "Şaka yapmadan dolaşıp oturan sakin bir ziyaret oynatır."),
                Triple(RealPetScenarioTestKind.PoopAndRun, "💩 bırak ve kaç", "Gerçek bir 💩 kartı oluşturup olay yerinden kaçar."),
                Triple(RealPetScenarioTestKind.DoubleTapSit, "Double tap → Otur", "30s oturup etrafı izler, ayağa kalkıp yürüyerek/koşarak çıkar.")
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBackground)
                    .border(0.75.dp, cardBorderColor, cardShape)
            ) {
                petScenarios.forEachIndexed { index, (kind, title, subtitle) ->
                    SettingsActionRow(
                        icon = ActionGlyph.Cat,
                        title = title,
                        subtitle = subtitle,
                        onClick = {
                            onPetScenarioTest(kind)
                            onBack()
                        }
                    )
                    if (index != petScenarios.lastIndex) SettingsDivider()
                }
            }
        }

        // ==========================================
        // 2. KATEGORİLER (Categories)
        // ==========================================
        SettingsSectionHeader("KATEGORİLER")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardBackground)
                .border(0.75.dp, cardBorderColor, cardShape)
        ) {
            SettingsActionRow(
                icon = ActionGlyph.Category,
                title = "Kategori sıralaması",
                subtitle = "Ana ekrandaki koleksiyonların sırasını düzenle",
                onClick = { showCategoryOrderEditor = true }
            )
        }

        // ==========================================
        // 3. BİLDİRİMLER VE DAVRANIŞ (Behavior)
        // ==========================================
        SettingsSectionHeader("BİLDİRİMLER VE DAVRANIŞ")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardBackground)
                .border(0.75.dp, cardBorderColor, cardShape)
        ) {
            // Notification permission
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(ActionGlyph.Bell, Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bildirim izni", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                            Text(
                                if (notificationsAllowed) "Açık" else "Kapalı",
                                color = if (notificationsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            if (notificationsAllowed) "Hatırlatıcı bildirimleri bu cihazda etkin."
                            else "Bildirimleri alabilmek için bildirim iznini açabilirsin.",
                            color = Color(0xFFA5A5AF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (!notificationsAllowed) {
                    TextButton(
                        onClick = onRequestNotifications,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    ) {
                        Text("İzni aç", color = Color(0xFF91B9FF), fontSize = 13.sp)
                    }
                }
            }

            SettingsDivider()

            // Reminder channel
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(ActionGlyph.Settings, Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Hatırlatıcı kanalı", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                            Text(
                                when {
                                    isChannelBlocked -> "Kapalı"
                                    isChannelVibrationEnabled -> "Açık • Titreşimli"
                                    else -> "Açık • Titreşimsiz"
                                },
                                color = when {
                                    isChannelBlocked -> Color(0xFFA5A5AF)
                                    isChannelVibrationEnabled -> Color(0xFF91B9FF)
                                    else -> Color(0xFFA5A5AF)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            when {
                                isChannelBlocked -> "Hatırlatıcı bildirim kanalı sistem ayarlarından kapatılmış."
                                isChannelVibrationEnabled -> "Bildirim geldiğinde ses ve titreşim uyarısı verilir."
                                else -> "Kanal açık fakat titreşim sistem ayarlarından kapatılmış."
                            },
                            color = Color(0xFFA5A5AF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                TextButton(
                    onClick = {
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
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                ) {
                    Text("Kanal ayarları", color = Color(0xFF91B9FF), fontSize = 13.sp)
                }
            }

            SettingsDivider()

            // Exact alarms
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(ActionGlyph.Calendar, Modifier.size(20.dp), color = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tam saatinde hatırlatma", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                            Text(
                                if (exactAlarmsAllowed) "Açık" else "Kapalı",
                                color = if (exactAlarmsAllowed) Color(0xFF91B9FF) else Color(0xFFA5A5AF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            if (exactAlarmsAllowed) "Alarmlar tam zamanında çalacak şekilde ayarlandı."
                            else "Android'in bildirim saatini geciktirmemesi için alarm iznini açabilirsin.",
                            color = Color(0xFFA5A5AF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (!exactAlarmsAllowed) {
                    TextButton(
                        onClick = onRequestExactAlarms,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    ) {
                        Text("İzni aç", color = Color(0xFF91B9FF), fontSize = 13.sp)
                    }
                }
            }

            SettingsDivider()

            // Samsung Now Bar toggle
            var nowBarEnabled by remember { mutableStateOf(NowBarReminderController.isEnabled(context)) }
            SettingsRow(
                icon = ActionGlyph.NowBar,
                title = "Samsung Now Bar",
                subtitle = "Hatırlatıcıları Now Bar'da canlı takip et (Deneysel)",
                checked = nowBarEnabled,
                onCheckedChange = {
                    nowBarEnabled = it
                    NowBarReminderController.setEnabled(context, it)
                }
            )
        }

        // ==========================================
        // 4. GELİŞTİRİCİ VE DEMO (Design / Demo)
        // ==========================================
        SettingsSectionHeader("GELİŞTİRİCİ VE DEMO")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardBackground)
                .border(0.75.dp, cardBorderColor, cardShape)
        ) {
            // Concept demo entry
            SettingsActionRow(
                icon = ActionGlyph.Edit,
                title = "Tasarım demosu · Not ve kartlar",
                subtitle = "Bağımsız konsept ve bileşen arayüzü",
                onClick = {
                    context.startActivity(Intent(context, com.cagri.bigboscagri.demo.CategoryConceptDemoActivity::class.java))
                }
            )

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
                        "Ana Mood Tracker ve REAL kartlarındaki Mood kayıtlarını siler. Günlük kayıtlarına dokunmaz.",
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

        // ==========================================
        // 5. HAKKINDA (About)
        // ==========================================
        SettingsSectionHeader("HAKKINDA")

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(cardBackground)
                .border(0.75.dp, cardBorderColor, cardShape)
        ) {
            // Version info row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionIcon(ActionGlyph.Checklist, Modifier.size(20.dp), color = Color.White)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("REAL", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Minimalist, AMOLED odaklı kişisel hatırlatıcı", color = Color(0xFFA3A3AB), fontSize = 12.sp)
                }
                Text("v${BuildConfig.VERSION_NAME}", color = Color(0xFF7A7A85), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            SettingsDivider()

            // Open source licenses
            SettingsActionRow(
                icon = ActionGlyph.Note,
                title = "Açık kaynak lisansları",
                subtitle = "Humation, Tabler Icons ve üçüncü taraf bildirimleri",
                onClick = { showLicensesDialog = true }
            )
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showCategoryOrderEditor) {
        CategoryOrderEditorDialog(
            customCategories = customCategories,
            currentOrderKeys = categoryOrderKeys,
            onDismiss = { showCategoryOrderEditor = false },
            onSave = { orderedKeys ->
                onCategoryOrderChange(orderedKeys)
                showCategoryOrderEditor = false
            }
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showClearMoodConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMoodConfirm = false },
            title = { Text("Tüm mood kayıtlarını sil?") },
            text = {
                Text("Ana Mood Tracker kayıtları ve REAL kartlarındaki Mood blokları silinecek. Günlükler korunacak.")
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
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFFA3A3AB),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFF26262B),
        thickness = 0.75.dp,
        modifier = Modifier.padding(start = 50.dp, end = 16.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ActionGlyph,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(icon, Modifier.size(20.dp), color = Color.White)
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFF9797A2),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF3B82F6),
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF28282C),
                uncheckedBorderColor = Color.Transparent
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(icon, Modifier.size(20.dp), color = Color.White)
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = subtitle,
                color = Color(0xFF9797A2),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        ActionIcon(ActionGlyph.ChevronRight, Modifier.size(16.dp), color = Color(0xFF75757F))
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

    AlertDialog(
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
private fun SettingsAppearanceSelector(
    designStyle: DesignStyle,
    onDesignStyleChange: (DesignStyle) -> Unit,
    reduceAnimations: Boolean
) {
    val isLiquid = designStyle == DesignStyle.LiquidGlass
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLiquid) Color.White.copy(alpha = 0.05f) else Color(0xFF222226))
            .padding(3.dp)
    ) {
        val tabWidth = maxWidth / 2
        val targetOffset = if (designStyle == DesignStyle.Glass) 0.dp else tabWidth

        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (reduceAnimations) snap() else tween(
                durationMillis = 280,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            ),
            label = "AppearanceSelectorIndicatorOffset"
        )

        // Sliding indicator capsule
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (isLiquid) Modifier.liquidChrome(10.dp, selected = true)
                    else Modifier.background(Color(0xFF34343A))
                )
        )

        // Fixed stationary labels on top
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val styles = listOf(
                "Focus" to DesignStyle.Glass,
                "Liquid" to DesignStyle.LiquidGlass
            )
            styles.forEach { (label, style) ->
                val isSelected = designStyle == style
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (designStyle != style) {
                                onDesignStyleChange(style)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFFA3A3AB),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryOrderEditorDialog(
    customCategories: List<CustomCategory>,
    currentOrderKeys: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val initialKeys = remember(currentOrderKeys, customCategories) {
        resolveCategoryOrderKeys(currentOrderKeys, customCategories)
    }
    val draft = remember(initialKeys) { mutableStateListOf<String>().apply { addAll(initialKeys) } }
    val itemsByKey = remember(customCategories) {
        resolveCategoryOrder(emptyList(), customCategories).associateBy { it.orderKey }
    }
    val lazyListState = rememberLazyListState()

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Kategori sıralaması", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sıralamak için basılı tut ve sürükle.",
                    color = Color(0xFF9D9DA7),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item ->
                                        offset.y.toInt() in item.offset..(item.offset + item.size)
                                    }
                                    ?.also { item ->
                                        draggedIndex = item.index
                                        dragOffsetY = 0f
                                    }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                dragOffsetY += dragAmount.y

                                val currentItem = lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == currentIdx } ?: return@detectDragGesturesAfterLongPress

                                val currentMid = currentItem.offset + dragOffsetY + currentItem.size / 2f
                                val targetItem = lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { other ->
                                        other.index != currentIdx &&
                                        currentMid in other.offset.toFloat()..(other.offset + other.size).toFloat()
                                    }

                                if (targetItem != null && targetItem.index in 0..draft.lastIndex) {
                                    val targetIdx = targetItem.index
                                    val moved = draft.removeAt(currentIdx)
                                    draft.add(targetIdx, moved)
                                    dragOffsetY += (currentItem.offset - targetItem.offset)
                                    draggedIndex = targetIdx
                                }
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffsetY = 0f
                                onSave(draft.toList())
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffsetY = 0f
                            }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(draft, key = { _, key -> key }) { index, key ->
                    val item = itemsByKey[key] ?: return@itemsIndexed
                    val title = when (item) {
                        is OrderedCategoryItem.BuiltIn -> item.category.title
                        is OrderedCategoryItem.Custom -> item.category.name
                    }
                    val kind = if (item is OrderedCategoryItem.BuiltIn) "Sistem" else "Özel"
                    val (startColor, endColor) = when (item) {
                        is OrderedCategoryItem.BuiltIn -> {
                            val defStyle = item.category.defaultStyle()
                            defStyle.upper to defStyle.lower
                        }
                        is OrderedCategoryItem.Custom -> {
                            val start = item.category.accentStart?.let { ReminderColorChoices.getOrNull(it)?.color } ?: Color(0xFF3B82F6)
                            val end = item.category.accentEnd?.let { ReminderColorChoices.getOrNull(it)?.color } ?: start
                            start to end
                        }
                    }

                    val isDragging = draggedIndex == index
                    val rowBackground = if (isDragging) Color(0xFF282A33) else Color(0xFF202126)
                    val rowBorderColor = if (isDragging) Color(0xFF91B9FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)

                    Row(
                        Modifier
                            .animateItem()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                            }
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(rowBackground)
                            .border(0.75.dp, rowBorderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(startColor, endColor))),
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is OrderedCategoryItem.BuiltIn -> CategoryGlyph(item.category.glyph(), Modifier.size(14.dp), Color.White)
                                is OrderedCategoryItem.Custom -> CategoryIconGlyph(CategoryIcon.fromKey(item.category.iconKey), Modifier.size(14.dp), Color.White)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(kind, color = Color(0xFF858691), fontSize = 11.sp)
                        }
                        ActionIcon(
                            ActionGlyph.DragHandle,
                            Modifier.size(20.dp),
                            color = if (isDragging) Color(0xFF91B9FF) else Color(0xFF75757F)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft.toList())
                onDismiss()
            }) {
                Text("Tamam", color = Color(0xFF91B9FF), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val defaultKeys = resolveCategoryOrderKeys(emptyList(), customCategories)
                    draft.clear()
                    draft.addAll(defaultKeys)
                    onSave(defaultKeys)
                }) {
                    Text("Varsayılan", color = Color(0xFFB6B6C0))
                }
                TextButton(onClick = onDismiss) {
                    Text("Kapat", color = Color(0xFFB6B6C0))
                }
            }
        },
        containerColor = Color(0xFF15161A),
        tonalElevation = 0.dp
    )
}
