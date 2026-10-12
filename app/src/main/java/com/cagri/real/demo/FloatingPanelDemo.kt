package com.cagri.real.demo

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.components.*

private val PanelWhite = Color(0xFFF3F3F5)
private val PanelMuted = Color(0xFFABABB2)
private val WaterTint = Color(0xFF97BACB)

/** Isolated sample data. No repository, recorder, production routing or permissions. */
@Composable
fun FloatingPanelDemo(modifier: Modifier = Modifier, reduceBlur: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var voice by rememberSaveable { mutableStateOf(false) }
    var flow by rememberSaveable { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val reduceMotion = LocalLiquidReduceMotion.current
    val animatedFraction by animateFloatAsState(
        if (dragging) dragFraction else if (expanded) 1f else 0f,
        if (dragging || reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 360f),
        label = "panelExpansion"
    )
    val fraction = animatedFraction.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val travel = with(density) { 150.dp.toPx() }
    val flingThreshold = with(density) { 430.dp.toPx() }
    val gesture = Modifier.draggable(
        state = rememberDraggableState { delta ->
            dragFraction = (dragFraction - delta / travel).coerceIn(0f, 1f)
        },
        orientation = Orientation.Vertical,
        onDragStarted = {
            dragFraction = fraction
            dragging = true
        },
        onDragStopped = { velocity ->
            expanded = when {
                velocity < -flingThreshold -> true
                velocity > flingThreshold -> false
                else -> dragFraction >= 0.45f
            }
            dragging = false
        }
    )

    BackHandler(expanded) { expanded = false }

    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }
    val capture = !reduceBlur && Build.VERSION.SDK_INT >= 33

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // Only the page content is sampled by Liquid Glass. The floating controls never sample themselves.
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { scenePosition = it.positionInRoot() }
                .drawWithContent {
                    if (capture) {
                        scene.record { this@drawWithContent.drawContent() }
                        drawLayer(scene)
                    } else {
                        drawContent()
                    }
                }
        ) {
            CalmHome(
                onFlow = { flow = true },
                bottomSpace = (104 + 92 * fraction).dp
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
        ) {
            // Expanded: Add to REAL lifts above the navigation capsule, matching the reference behavior.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((78 * fraction).dp)
                    .graphicsLayer { alpha = fraction }
            ) {
                if (fraction > 0.02f) {
                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .then(gesture),
                        35f
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x66101012)),
                                contentAlignment = Alignment.Center
                            ) {
                                ActionIcon(ActionGlyph.Add, Modifier.size(21.dp), PanelWhite)
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Add to REAL",
                                    color = PanelWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Aklında ne var?",
                                    color = PanelMuted,
                                    fontSize = 11.sp
                                )
                            }
                            ActionIcon(ActionGlyph.ChevronRight, Modifier.size(19.dp), PanelMuted)
                        }
                    }
                }
            }

            if (fraction > 0.02f) Spacer(Modifier.height((8 * fraction).dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height((62 + 22 * fraction).dp)
                    .then(gesture)
                    .semantics {
                        stateDescription = if (expanded) "Geniş panel" else "Kompakt panel"
                    }
            ) {
                // Compact state: [calendar circle] [Add to REAL] [mic circle]
                if (fraction < 0.5f) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - fraction * 1.45f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier
                            .size(62.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Paneli aç"
                            ) { expanded = true }
                            .semantics { contentDescription = "Takvim" },
                        31f
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ActionIcon(ActionGlyph.Calendar, Modifier.size(25.dp), PanelWhite)
                        }
                    }

                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clickable(onClickLabel = "Add to REAL") { },
                        31f
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            ActionIcon(ActionGlyph.Add, Modifier.size(19.dp), PanelMuted)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Add to REAL",
                                color = PanelWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier.size(62.dp),
                        31f
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Hızlı ses girişini aç"
                                ) { voice = true }
                                .semantics { contentDescription = "Mikrofon" },
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(ActionGlyph.Mic, Modifier.size(26.dp), Color.White)
                        }
                    }
                }

                } else {
                // Expanded state: [Widget | Keşfet | Takvim] [mic circle]
                Row(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((fraction - 0.30f) / 0.70f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        42f
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        ) {
                            DemoRailItem(
                                label = "Widget",
                                glyph = ActionGlyph.Category,
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { }
                            )
                            DemoRailItem(
                                label = "Keşfet",
                                glyph = ActionGlyph.LiquidGlass,
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { }
                            )
                            DemoRailItem(
                                label = "Takvim",
                                glyph = ActionGlyph.Calendar,
                                selected = true,
                                modifier = Modifier.weight(1f),
                                onClick = { }
                            )
                        }
                    }

                    DemoGlass(
                        scene,
                        { scenePosition },
                        reduceBlur,
                        Modifier.size((62 + 22 * fraction).dp),
                        42f
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Hızlı ses girişini aç"
                                ) { voice = true }
                                .semantics { contentDescription = "Mikrofon" },
                            contentAlignment = Alignment.Center
                        ) {
                            ActionIcon(ActionGlyph.Mic, Modifier.size((26 + 3 * fraction).dp), Color.White)
                        }
                    }
                }
                }
            }
        }
    }

    if (voice) {
        AlertDialog(
            onDismissRequest = { voice = false },
            containerColor = Color(0xFF171719),
            icon = { ActionIcon(ActionGlyph.Mic, Modifier.size(28.dp), Color.White) },
            title = { Text("Hızlı ses", color = PanelWhite) },
            text = {
                Text(
                    "Sesle bir düşünce yakala.\n\nBu demo yalnızca giriş noktasını gösterir; mikrofon açılmaz ve kayıt alınmaz.",
                    color = PanelMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { voice = false }) { Text("Tamam", color = PanelWhite) }
            }
        )
    }

    if (flow) {
        AlertDialog(
            onDismissRequest = { flow = false },
            containerColor = Color(0xFF171719),
            title = { Text("Referans hareketi", color = PanelWhite) },
            text = {
                Text(
                    "Kompakt: Takvim · Add to REAL · Mikrofon.\n\nYukarı çek: Add to REAL üst kapsüle taşınır; altta Widget · Keşfet · Takvim açılır.\n\nAşağı çek: panel tekrar kompakt hale döner.",
                    color = PanelMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { flow = false }) { Text("Tamam", color = PanelWhite) }
            }
        )
    }
}

@Composable
private fun DemoRailItem(
    label: String,
    glyph: ActionGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(38.dp))
            .background(if (selected) Color(0xA6050507) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ActionIcon(
            glyph,
            Modifier.size(if (selected) 27.dp else 23.dp),
            if (selected) PanelWhite else PanelMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) PanelWhite else PanelMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun DemoGlass(scene: GraphicsLayer, position: () -> Offset, reduceBlur: Boolean,
    modifier: Modifier, radius: Float, content: @Composable BoxScope.() -> Unit) {
    LiquidGlassSurface(scene, position, modifier, corners = radius.dp,
        reduceDynamicBlur = reduceBlur, strength = LiquidGlassStrength.Regular,
        // Geometry belongs to the drag, so don't scale the entire capsule on touch.
        reduceAnimations = true, content = content)
}

@Composable
private fun RailTab(label: String, glyph: ActionGlyph, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(36.dp))
        .background(if (selected) Color(0x99030304) else Color.Transparent)
        .selectable(selected, role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ActionIcon(glyph, Modifier.size(23.dp), if (selected) PanelWhite else PanelMuted)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = if (selected) PanelWhite else PanelMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CalmHome(onFlow: () -> Unit, bottomSpace: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp).padding(top = 20.dp, bottom = bottomSpace)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("REAL", color = PanelWhite, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onFlow) { Text("Akış ↗", color = PanelMuted, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(20.dp))
        Text("28 EYLÜL · PAZARTESİ", color = PanelMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(7.dp))
        Text("Kartlarım", color = PanelWhite, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        HomeCard("Kişisel", "Kendine biraz zaman ayır.", "3 kart", Color(0xFF252B32), ActionGlyph.Note)
        Spacer(Modifier.height(10.dp))
        HomeCard("İş", "Bir sonraki adıma odaklan.", "2 kart", Color(0xFF242326), ActionGlyph.Cards)
        Spacer(Modifier.height(10.dp))
        HomeCard("Fikirler", "Aklında kalan küçük şeyler.", "4 kart", Color(0xFF232824), ActionGlyph.Star)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, count: String, tint: Color, glyph: ActionGlyph) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
        .background(Brush.linearGradient(listOf(tint, Color(0xFF111113))))
        .border(0.6.dp, Color(0x18FFFFFF), RoundedCornerShape(23.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(glyph, Modifier.size(19.dp), PanelMuted)
            Spacer(Modifier.width(9.dp))
            Text(title, color = PanelWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(count, color = PanelMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(15.dp))
        Text(subtitle, color = PanelMuted, fontSize = 12.sp)
    }
}

@Composable
private fun WeekCalendar(selected: Int, onDay: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Eylül — Ekim", color = PanelWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("2026", color = PanelMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("P", "S", "Ç", "P", "C", "C", "P").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).height(47.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (index == selected) Color(0xFFE3E4E5) else Color.Transparent)
                    .selectable(index == selected, role = Role.Tab, onClick = { onDay(index) })
                    .semantics { contentDescription = listOf("28 Eylül Pazartesi", "29 Eylül Salı", "30 Eylül Çarşamba", "1 Ekim Perşembe", "2 Ekim Cuma", "3 Ekim Cumartesi", "4 Ekim Pazar")[index] },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, color = if (index == selected) Color(0xFF404043) else PanelMuted, fontSize = 9.sp)
                    Text(listOf("28", "29", "30", "1", "2", "3", "4")[index],
                        color = if (index == selected) Color.Black else PanelWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (selected == 0) "14:30  Tasarım notları    ·    +1 plan" else "Bu gün için plan yok", color = PanelMuted, fontSize = 11.sp)
    }
}

@Composable
private fun CategoryUtilities(onBack: () -> Unit, bottomSpace: androidx.compose.ui.unit.Dp) {
    var drink by rememberSaveable { mutableIntStateOf(0) }
    var water by rememberSaveable { mutableIntStateOf(1250) }
    var coffee by rememberSaveable { mutableIntStateOf(200) }
    var beer by rememberSaveable { mutableIntStateOf(0) }
    var mood by rememberSaveable { mutableIntStateOf(3) }
    var detail by rememberSaveable { mutableStateOf(false) }
    val amount = listOf(water, coffee, beer)[drink]
    val goal = if (drink == 0) 2500 else 2000
    val fill by animateFloatAsState((amount.toFloat() / goal).coerceIn(0f, 1f),
        if (LocalLiquidReduceMotion.current) snap() else spring(stiffness = 180f), label = "drinkFill")
    val tint = listOf(WaterTint, Color(0xFFC6AE95), Color(0xFFC9B789))[drink]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
        .padding(top = 10.dp, bottom = bottomSpace)) {
        com.cagri.real.ui.components.RealBackButton(onBack, description = "Kartlarım'a dön")
        Text("Kategoriler", color = PanelWhite, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Günün küçük ritüelleri.", color = PanelMuted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        UtilityModule {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Drink", color = PanelWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("BUGÜN", color = PanelMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            DemoSegments(listOf("Su", "Kahve", "Bira"), drink, onSelect = { drink = it })
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$amount ml", color = PanelWhite, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    Text(if (drink == 0) "2.500 ml günlük hedef" else "Bugünkü toplam", color = PanelMuted, fontSize = 11.sp)
                }
                Box(Modifier.size(64.dp, 72.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF22282B)).border(0.8.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(18.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val level = size.height * (1f - fill)
                        val waveHeight = 8.dp.toPx()
                        val waterPath = Path().apply {
                            moveTo(0f, level)
                            cubicTo(size.width * 0.33f, level - waveHeight, size.width * 0.66f, level + waveHeight, size.width, level)
                            lineTo(size.width, size.height); lineTo(0f, size.height); close()
                        }
                        drawPath(waterPath, Brush.verticalGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.16f))))
                    }
                    Text("${(fill * 100).toInt()}%", Modifier.align(Alignment.Center), color = PanelWhite, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleButton("−", Modifier.width(48.dp), "${if (drink == 0) 250 else 100} ml azalt") {
                    when (drink) { 0 -> water = (water - 250).coerceAtLeast(0); 1 -> coffee = (coffee - 100).coerceAtLeast(0); else -> beer = (beer - 100).coerceAtLeast(0) }
                }
                SampleButton(if (drink == 0) "+ 250 ml" else "+ 100 ml", Modifier.weight(1f)) {
                    when (drink) { 0 -> water += 250; 1 -> coffee += 100; else -> beer += 100 }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        UtilityModule {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mood", color = PanelWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("BUGÜN", color = PanelMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Bugün nasıl hissediyorsun?", color = PanelMuted, fontSize = 12.sp)
            Spacer(Modifier.height(17.dp))
            val names = listOf("Kötü", "Zor", "Normal", "İyi", "Çok iyi")
            val icons = listOf(ActionGlyph.MoodBad, ActionGlyph.MoodHard, ActionGlyph.MoodNeutral, ActionGlyph.MoodGood, ActionGlyph.MoodVeryGood)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                icons.forEachIndexed { index, icon ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (mood == index) Color(0xFF303431) else Color.Transparent)
                        .selectable(mood == index, role = Role.RadioButton, onClick = { mood = index }).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        ActionIcon(icon, Modifier.size(27.dp), if (mood == index) Color(0xFFD5E0D4) else PanelMuted)
                        Spacer(Modifier.height(7.dp))
                        Text(names[index], color = if (mood == index) PanelWhite else PanelMuted, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Text("${names[mood]} · Şu an", color = PanelWhite, fontSize = 12.sp)
            TextButton(onClick = { detail = !detail }, contentPadding = PaddingValues(0.dp)) {
                Text(if (detail) "Daha az göster ↑" else "Günün akışı ↗", color = PanelMuted, fontSize = 11.sp)
            }
            if (detail) Text("09:10  Normal\n13:45  İyi\nŞu an  ${names[mood]}", color = PanelMuted, fontSize = 12.sp, lineHeight = 24.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Örnek veriler · yalnızca tasarım demosu", color = PanelMuted, fontSize = 10.sp)
    }
}

@Composable
private fun UtilityModule(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1C1D20), Color(0xFF101112))))
        .border(0.6.dp, Color(0x22FFFFFF), RoundedCornerShape(27.dp)).padding(18.dp), content = content)
}

@Composable
private fun SampleButton(label: String, modifier: Modifier, description: String = label, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF2B2D30))
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = PanelWhite, fontSize = 13.sp)
    }
}
