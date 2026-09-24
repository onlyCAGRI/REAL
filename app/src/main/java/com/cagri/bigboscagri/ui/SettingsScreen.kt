package com.cagri.bigboscagri.ui

import com.cagri.bigboscagri.ui.components.liquidChrome
import com.cagri.bigboscagri.ui.components.SettingsLayout
import com.cagri.bigboscagri.ui.components.LiquidAppearanceSelector
import com.cagri.bigboscagri.ui.components.LiquidGlassTokens
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cagri.bigboscagri.notifications.NowBarReminderController
import com.cagri.bigboscagri.notifications.NowBarProbe
import com.cagri.bigboscagri.notifications.ReminderNotifications
import com.cagri.bigboscagri.ui.components.ActionGlyph
import com.cagri.bigboscagri.ui.components.ActionIcon

@Composable
fun SettingsScreen(
    reduceDynamicBlur: Boolean,
    onReduceDynamicBlurChange: (Boolean) -> Unit,
    reduceAnimations: Boolean,
    onReduceAnimationsChange: (Boolean) -> Unit,
    designStyle: DesignStyle,
    onDesignStyleChange: (DesignStyle) -> Unit,
    onBack: () -> Unit,
    notificationsAllowed: Boolean = true,
    onRequestNotifications: () -> Unit = {},
    exactAlarmsAllowed: Boolean = true,
    onRequestExactAlarms: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val reminderChannel = remember(isResumed) {
        ReminderNotifications.getReminderChannel(context)
    }
    val isChannelBlocked = reminderChannel != null && reminderChannel.importance == NotificationManager.IMPORTANCE_NONE
    val isChannelVibrationEnabled = reminderChannel != null && !isChannelBlocked && reminderChannel.shouldVibrate()

    val liquid = designStyle == DesignStyle.LiquidGlass
    SettingsLayout(designStyle, reduceDynamicBlur, onBack) {
        // Top App Bar: Only "Ayarlar" centered with back button
        if (!liquid) Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .semantics { contentDescription = "Geri" }
            ) {
                ActionIcon(ActionGlyph.Back, Modifier.size(24.dp), color = Color.White)
            }
            Text(
                "Ayarlar",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Section Title: GÖRÜNÜM
        Text(
            "GÖRÜNÜM",
            color = Color(0xFFA3A3AB),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )

        // Large Premium Glass Card
        val cardShape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(if (liquid) LiquidGlassTokens.contentColor else Color(0xFF18181B))
                .border(0.75.dp, Color.White.copy(alpha = 0.08f), cardShape)
        ) {
            // Item 1: Tema with Segmented Control (Sistem | Açık | Koyu - Koyu sabit, diğerleri disabled)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ActionIcon(ActionGlyph.Theme, Modifier.size(22.dp), color = Color.White)
                    Text(
                        "Tema",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF27272B))
                        .padding(3.dp)
                ) {
                    val themeOptions = listOf("Sistem", "Açık", "Koyu")
                    themeOptions.forEach { option ->
                        val isSelected = option == "Koyu"
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(if (liquid && isSelected) Modifier.liquidChrome(12.dp, selected = true) else Modifier.background(if (isSelected) Color.White else Color.Transparent))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) (if (liquid) Color.White else Color.Black) else Color.White.copy(alpha = 0.28f),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
            )

            // Item 2: Görünüm stili (Glass | Buz)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ActionIcon(ActionGlyph.LiquidGlass, Modifier.size(22.dp), color = Color.White)
                    Text(
                        "Görünüm stili",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (liquid) {
                    LiquidAppearanceSelector(designStyle, onDesignStyleChange)
                } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF27272B))
                        .padding(3.dp)
                ) {
                    val styles = listOf(
                        "Glass" to DesignStyle.Glass,
                        "Buz" to DesignStyle.Ice,
                        "Liquid Glass" to DesignStyle.LiquidGlass
                    )
                    styles.forEach { (label, style) ->
                        val isSelected = designStyle == style
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .selectable(selected = isSelected, role = Role.RadioButton) { onDesignStyleChange(style) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color(0xFFA3A3AB),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                }
            }

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
            )

            // Item 3: Animasyonu azalt
            SettingsRow(
                icon = ActionGlyph.MotionOff,
                title = "Animasyonu azalt",
                subtitle = if (liquid) "Camın basış ve ışık hareketlerini azaltır" else "Ana oynatıcının gradyanını kaymak yerine dondurur",
                checked = reduceAnimations,
                onCheckedChange = onReduceAnimationsChange
            )

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
            )

            // Item 4: Dinamik bulanıklığı azalt
            SettingsRow(
                icon = ActionGlyph.BlurOff,
                title = "Dinamik bulanıklığı azalt",
                subtitle = if (liquid) "Bulanıklık ve kırılma yerine sabit koyu cam kullanır" else "Uygulama genelinde buzlu camı düz dolgularla değiştirir",
                checked = reduceDynamicBlur,
                onCheckedChange = onReduceDynamicBlurChange
            )
        }

        Spacer(Modifier.height(20.dp))

        // BİLDİRİMLER Section
        Text(
            "BİLDİRİMLER",
            color = Color(0xFFA3A3AB),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(if (liquid) LiquidGlassTokens.contentColor else Color(0xFF18181B))
                .border(0.75.dp, Color.White.copy(alpha = 0.08f), cardShape)
                .padding(16.dp)
        ) {
            // Item 1: Bildirim izni
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Bell, Modifier.size(22.dp), color = Color.White)
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
                        if (notificationsAllowed) "Hatırlatıcı bildirimleri bu cihazda gösterilebilir."
                        else "Hatırlatıcı bildirimlerini alabilmek için bildirim iznini açabilirsin.",
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
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                ) {
                    Text("İzni aç", color = Color(0xFF91B9FF))
                }
            }

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 36.dp, end = 0.dp, top = 12.dp, bottom = 12.dp)
            )

            // Item 2: Hatırlatıcı kanalı ve titreşim durumu
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Settings, Modifier.size(22.dp), color = Color.White)
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
                            // Fallback ignored
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            ) {
                Text("Kanal ayarları", color = Color(0xFF91B9FF))
            }

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 36.dp, end = 0.dp, top = 12.dp, bottom = 12.dp)
            )

            // Item 3: Tam saatinde hatırlatma
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(ActionGlyph.Calendar, Modifier.size(22.dp), color = Color.White)
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
                        else "Android'in bildirim saatini geciktirmemesi için Alarmlar ve hatırlatıcılar iznini açabilirsin.",
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
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                ) {
                    Text("İzni aç", color = Color(0xFF91B9FF))
                }
            }

            Spacer(Modifier.height(40.dp))

            // Real Labs / R&D Section
            Text(
                "Now Bar R&D",
                color = Color(0xFFA5A5AF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141416))
                    .border(0.8.dp, Color(0xFF28282C), RoundedCornerShape(24.dp))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                    var nowBarEnabled by remember { mutableStateOf(NowBarReminderController.isEnabled(context)) }
                    SettingsRow(
                        icon = ActionGlyph.Bell,
                        title = "Samsung Now Bar",
                        subtitle = "Hatırlatıcıları Now Bar'da canlı takip et (Deneysel)",
                        checked = nowBarEnabled,
                        onCheckedChange = {
                            nowBarEnabled = it
                            NowBarReminderController.setEnabled(context, it)
                        }
                    )

                    if (nowBarEnabled) {
                        Text(
                            "Not: Cihazın Geliştirici Seçenekleri'nden 'Tüm uygulamalar için canlı bildirimler' ayarını açmanız gerekebilir.",
                            color = Color(0xFF91B9FF),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF26262B), thickness = 0.75.dp, modifier = Modifier.padding(vertical = 12.dp))

                    val supported = NowBarProbe.canPostPromoted(context)
                    val characteristics = NowBarProbe.hasPromotableCharacteristics(context)

                    Text("Android promoted access", color = Color.White, fontSize = 14.sp)
                    Text(if (supported) "Açık" else "Sistem tarafından bildirilmedi", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))

                    Spacer(Modifier.height(12.dp))
                    Text("Samsung Now Bar metadata", color = Color.White, fontSize = 14.sp)
                    Text("Hazır", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))

                    Spacer(Modifier.height(12.dp))
                    Text("Notification structure", color = Color.White, fontSize = 14.sp)
                    Text(if (characteristics) "Uygun" else "Uygun değil", color = Color(0xFFA5A5AF), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { NowBarProbe.postTest(context) }) {
                            Text("Testi başlat", color = Color(0xFF91B9FF))
                        }
                        TextButton(onClick = { NowBarProbe.cancelTest(context) }) {
                            Text("Bitir", color = Color(0xFF91B9FF))
                        }
                        if (supported) {
                            val settingsIntent = NowBarProbe.buildSettingsIntent(context)
                            if (settingsIntent != null) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { context.startActivity(settingsIntent) }) {
                                    Text("Ayarları aç", color = Color(0xFF91B9FF))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
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
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(icon, Modifier.size(22.dp), color = Color.White)
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
                checkedTrackColor = Color(0xFF3888FF),
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF2C2C30),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
