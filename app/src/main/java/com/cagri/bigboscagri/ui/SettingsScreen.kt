package com.cagri.bigboscagri.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.cagri.bigboscagri.ui.components.*

@Composable
fun SettingsScreen(reduceDynamicBlur: Boolean, onReduceDynamicBlurChange: (Boolean) -> Unit,
    reduceAnimations: Boolean, onReduceAnimationsChange: (Boolean) -> Unit,
    designStyle: DesignStyle, onDesignStyleChange: (DesignStyle) -> Unit, onBack: () -> Unit,
    exactAlarmsAllowed: Boolean = true, onRequestExactAlarms: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Geri" }) {
                ActionIcon(ActionGlyph.Back, Modifier.size(24.dp))
            }
            Text("Ayarlar", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        SettingsGroup("Tasarım") {
            Text("AMOLED siyah", color = Color(0xFFA3A3AC), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF27272B)).padding(4.dp)) {
                DesignStyle.entries.forEach { style ->
                    TextButton(onClick = { onDesignStyleChange(style) }, modifier = Modifier.weight(1f)
                        .background(if (style == designStyle) Color(0xFF45454B) else Color.Transparent, RoundedCornerShape(24.dp))) {
                        Text(if (style == DesignStyle.Ice) "Buz" else "Glass", color = Color.White)
                    }
                }
            }
            DesignPreview(designStyle, reduceDynamicBlur)
        }
        SettingsGroup("Performans") {
            SettingsToggle("Animasyonu azalt", "Ekran geçişlerini ve mikrofon hareketini azaltır.", reduceAnimations, onReduceAnimationsChange)
            HorizontalDivider(color = Color(0xFF303036))
            SettingsToggle("Dinamik bulanıklığı azalt", "Cam yüzeyleri düz koyu dolguya çevirir.", reduceDynamicBlur, onReduceDynamicBlurChange)
        }
        SettingsGroup("Bildirimler") {
            Text("Zamanlı hatırlatıcılar", color = Color.White, fontSize = 16.sp)
            Text(if (exactAlarmsAllowed) "Tam saatinde hatırlatma izni açık." else "Android'in bildirim saatini geciktirmemesi için Alarmlar ve hatırlatıcılar iznini açabilirsin.",
                color = Color(0xFFA5A5AF), fontSize = 13.sp, lineHeight = 19.sp)
            if (!exactAlarmsAllowed) TextButton(onClick = onRequestExactAlarms) { Text("İzni aç", color = Color(0xFF91B9FF)) }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color(0xFFA3A3AB), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF18181B)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun SettingsToggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
            Text(subtitle, color = Color(0xFF9797A2), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = value, onCheckedChange = null)
    }
}
@Composable
private fun DesignPreview(style: DesignStyle, reduceBlur: Boolean) {
    val scene = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(24.dp))) {
        Box(Modifier.matchParentSize().onGloballyPositioned { position = it.positionInRoot() }
            .drawWithContent {
                if (!reduceBlur && Build.VERSION.SDK_INT >= 31) { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
                else drawContent()
            }.background(Brush.linearGradient(listOf(Color(0xFF263D51), Color(0xFF703946), Color(0xFF142C30))))) {
            Text("Hatırlatıcılar", color = Color.White, fontSize = 25.sp, modifier = Modifier.padding(18.dp))
        }
        DesignPanel(scene, { position }, Modifier.align(Alignment.Center).padding(14.dp).fillMaxWidth(),
            corners = 28.dp, designStyle = style, reduceDynamicBlur = reduceBlur) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hatırlatıcı ekle", color = Color(0xFFE4E4EB), fontSize = 16.sp, modifier = Modifier.weight(1f))
                ActionIcon(ActionGlyph.Mic, Modifier.size(24.dp))
            }
        }
    }
}
