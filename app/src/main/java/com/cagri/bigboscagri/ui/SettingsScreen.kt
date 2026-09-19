package com.cagri.bigboscagri.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top App Bar: Only "Ayarlar" centered with back button
        Box(
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
                .background(Color(0xFF18181B))
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
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.28f),
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF27272B))
                        .padding(3.dp)
                ) {
                    val styles = listOf(
                        "Glass" to DesignStyle.Glass,
                        "Buz" to DesignStyle.Ice
                    )
                    styles.forEach { (label, style) ->
                        val isSelected = designStyle == style
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { onDesignStyleChange(style) }
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

            HorizontalDivider(
                color = Color(0xFF26262B),
                thickness = 0.75.dp,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
            )

            // Item 3: Animasyonu azalt
            SettingsRow(
                icon = ActionGlyph.MotionOff,
                title = "Animasyonu azalt",
                subtitle = "Ana oynatıcının gradyanını kaymak yerine dondurur",
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
                subtitle = "Uygulama genelinde buzlu camı düz dolgularla değiştirir",
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
                .background(Color(0xFF18181B))
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

            // Item 2: Tam saatinde hatırlatma
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
