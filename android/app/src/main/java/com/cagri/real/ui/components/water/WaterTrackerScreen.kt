package com.cagri.real.ui.components.water

import com.cagri.real.i18n.t
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.cagri.real.data.BeverageType
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Own window keeps composer, keyboard and pet input beneath the water screen. */
@Composable
internal fun WaterTrackerScreen(
    state: WaterTrackerState,
    reduceAnimations: Boolean,
    visible: Boolean = true,
    reduceDynamicBlur: Boolean = false,
    onDismiss: () -> Unit
) {
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf(BeverageType.WATER.persistKey) }
    val beverage = BeverageType.fromKey(selectedKey)
    val palette = remember(beverage) { beverage.appearance() }
    val entries = state.todayEntries.filter { it.beverage == beverage }
    val totalMl = state.totalFor(beverage)
    val isWater = beverage == BeverageType.WATER
    val targetMl = state.goalFor(beverage)
    val rawProgress = state.progressFor(beverage)
    val fill = rawProgress.coerceIn(0f, MAX_VISUAL_OVERFLOW_PROGRESS)
    val displayPercent = (rawProgress * 100f).roundToInt().coerceAtLeast(0)
    val canWrite = state.ready && !state.busy
    val clockFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val density = LocalDensity.current
    val scene = rememberGraphicsLayer()
    var scenePosition by remember { mutableStateOf(Offset.Zero) }
    val designStyle = LocalDesignStyle.current
    val captureBackdrop = shouldCaptureBackdrop(designStyle, reduceDynamicBlur, Build.VERSION.SDK_INT)
    val screenProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceAnimations) snap() else tween(
            durationMillis = 220,
            easing = RealMotion.Emphasized
        ),
        label = "drinksDestination"
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false
    )) {
        BackHandler(onBack = onDismiss)
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val oldDim = window?.attributes?.dimAmount
            val oldAnimations = window?.attributes?.windowAnimations
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            onDispose {
                if (oldDim != null) window.setDimAmount(oldDim)
                if (oldAnimations != null) window.setWindowAnimations(oldAnimations)
            }
        }
        BeverageSystemBars()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * (1f - screenProgress)
                }
                .background(palette.background.first())
        ) {
            WaterSurface(
                fill,
                reduceAnimations,
                // Record only the liquid scene; glass must remain outside this layer.
                Modifier.matchParentSize()
                    .onGloballyPositioned { scenePosition = it.positionInRoot() }
                    .drawWithContent {
                        if (captureBackdrop) {
                            scene.record { this@drawWithContent.drawContent() }
                            drawLayer(scene)
                        } else drawContent()
                    },
                beverage,
                if (isWater) null else beverageAmountText(beverage, totalMl)
            )
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(palette.background.first())
            )
            val historyHeight = (maxHeight * 0.26f).coerceIn(120.dp, 230.dp)
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BeverageType.entries.forEach { option ->
                        val active = beverage == option
                        val tabInteractionSource = remember(option) { MutableInteractionSource() }
                        Box(
                            Modifier
                                .clickable(
                                    interactionSource = tabInteractionSource,
                                    indication = null,
                                    role = Role.Tab
                                ) { selectedKey = option.persistKey }
                                .padding(vertical = 6.dp)
                                .semantics { selected = active },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option.label,
                                color = if (active) Color.White else Color.White.copy(alpha = 0.40f),
                                fontSize = 16.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        com.cagri.real.ui.components.RealBackButton(onDismiss, description = t("İçecek ekranını kapat"))
                        Spacer(Modifier.height(16.dp))
                        Text(beverage.label, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        BeverageGlassAmount(
                            text = if (isWater) t("Bugün: {0} / {1} ml", totalMl, targetMl) else t("Bugün: {0}", beverageAmountText(beverage, totalMl)),
                            reduceAnimations = reduceAnimations,
                            modifier = Modifier
                                .clickable(enabled = canWrite && isWater, role = Role.Button) { goalDialog = true }
                                .semantics { contentDescription = if (isWater) t("Su hedefini değiştir. Bugün {0}, hedef {1} mililitre", totalMl, targetMl) else t("Bugün {0} mililitre {1}", totalMl, beverage.label) }
                                .padding(vertical = 9.dp)
                        )
                        if (state.error != null) {
                            Text(state.error.orEmpty(), color = Color(0xFFFFCEBB), fontSize = 14.sp,
                                modifier = Modifier.clickable { state.retry() }.padding(vertical = 8.dp))
                        } else if (!state.ready) {
                            Text(t("Yükleniyor…"), color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
                        }
                    }
                }
                // Quick amounts stay intentionally compact; the temporary custom amount button is removed.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    beverage.quickAmounts.forEach { amount ->
                        QuickWaterAdd(
                            backdrop = scene,
                            backdropPosition = { scenePosition },
                            reduceDynamicBlur = reduceDynamicBlur,
                            reduceAnimations = reduceAnimations,
                            amountLabel = beverageAmountText(beverage, amount.toLong()),
                            beverageLabel = beverage.label,
                            enabled = canWrite,
                            modifier = Modifier.weight(1f)
                        ) { state.add(amount, beverage) }
                    }
                }
                BeverageDockGlass(
                    backdrop = scene,
                    backdropPosition = { scenePosition },
                    reduceDynamicBlur = reduceDynamicBlur,
                    reduceAnimations = reduceAnimations,
                    corners = 30.dp,
                    modifier = Modifier.fillMaxWidth().height(historyHeight)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t("Bugün"), color = Color.White, fontSize = 26.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (isWater) "$displayPercent%"
                                else if (rawProgress >= 1f) "$displayPercent%"
                                else beverageAmountText(beverage, totalMl),
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 14.sp
                            )
                        }
                        if (entries.isEmpty()) {
                            Text(t("İlk yudumunu ekle."), color = Color(0xFFA3A6AA), fontSize = 15.sp,
                                modifier = Modifier.padding(24.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(entries, key = { it.id }) { entry ->
                                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${entry.beverage.label} (+${beverageAmountText(entry.beverage, entry.amountMl.toLong())})",
                                            color = Color(0xFFE0E2E3), fontSize = 15.sp,
                                            modifier = Modifier.weight(1f))
                                        Text(Instant.ofEpochMilli(entry.createdAt).atZone(state.zone).format(clockFormat),
                                            color = Color.White.copy(alpha = 0.70f), fontSize = 13.sp)
                                        Box(Modifier.size(44.dp).clip(CircleShape)
                                            .clickable(enabled = canWrite, role = Role.Button) { state.remove(entry.id) }
                                            .semantics { contentDescription = t("{0} mililitre kaydını geri al", entry.amountMl) },
                                            contentAlignment = Alignment.Center) {
                                            Text("−", color = Color(0xFFB9BEC0), fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (goalDialog) WaterAmountDialog(t("Günlük su hedefi"), t("Hedef (ml)"), state.snapshot.goalMl.toString(), 100..10_000,
            onDismiss = { goalDialog = false }, onConfirm = { state.goal(it); goalDialog = false })
    }
}


private fun beverageAmountText(beverage: BeverageType, amountMl: Long): String {
    if (beverage != BeverageType.BEER) return "$amountMl ml"
    return if (amountMl % 10L == 0L) {
        t("{0} cl", amountMl / 10L)
    } else {
        val cl = amountMl / 10.0
        "${String.format(java.util.Locale.getDefault(), "%.1f", cl)} cl"
    }
}

@Composable
private fun BeverageDockGlass(
    backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean, reduceAnimations: Boolean,
    modifier: Modifier = Modifier, corners: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    when (LocalDesignStyle.current) {
        DesignStyle.LiquidGlass -> LiquidGlassSurface(
            backdrop, backdropPosition, modifier, corners,
            reduceDynamicBlur = reduceDynamicBlur, reduceAnimations = reduceAnimations,
            role = LiquidGlassRole.Dock, content = content
        )
        DesignStyle.Ice -> IcePanel(backdrop, backdropPosition, modifier, corners,
            reduceDynamicBlur = reduceDynamicBlur, content = content)
        else -> GlassPanel(backdrop, backdropPosition, modifier, corners,
            reduceDynamicBlur = reduceDynamicBlur, content = content)
    }
}

@Composable
private fun BeverageGlassAmount(text: String, reduceAnimations: Boolean, modifier: Modifier) {
    // A quiet highlight inside the glyphs, with no capsule or background.
    // The animated phase is read only while drawing, so this text is no longer recomposed
    // (and its brush/style rebuilt) on every frame; only its draw pass is invalidated.
    val phaseState: State<Float>? = if (reduceAnimations) null else {
        val transition = rememberInfiniteTransition(label = "drinkTextReflection")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drinkTextLight"
        )
    }
    val span = with(LocalDensity.current) { 320.dp.toPx() }
    Text(
        text,
        color = Color.White,
        fontSize = 20.sp,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val phase = phaseState?.value ?: 0.5f
                // SrcIn keeps only the glyph coverage, so the gradient shows exactly inside the letters.
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.72f), Color.White, Color.White.copy(alpha = 0.78f)),
                        start = Offset(-span * 0.6f + span * phase, 0f),
                        end = Offset(span * 0.4f + span * phase, span * 0.12f)
                    ),
                    blendMode = BlendMode.SrcIn
                )
            }
    )
}

@Composable
private fun QuickWaterAdd(
    backdrop: GraphicsLayer, backdropPosition: () -> Offset,
    reduceDynamicBlur: Boolean, reduceAnimations: Boolean,
    amountLabel: String, beverageLabel: String, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    BeverageDockGlass(backdrop, backdropPosition, reduceDynamicBlur, reduceAnimations,
        modifier = modifier.heightIn(min = 50.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .clip(RoundedCornerShape(26.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = t("{0} {1} ekle", amountLabel, beverageLabel) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("+", color = Color.White.copy(alpha = if (enabled) 0.95f else 0.4f), fontSize = 24.sp)
            Text(amountLabel, color = Color.White.copy(alpha = if (enabled) 0.95f else 0.4f),
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WaterAmountDialog(
    title: String, label: String, initialValue: String, range: IntRange,
    onDismiss: () -> Unit, onConfirm: (Int) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val amount = value.toIntOrNull()
    val valid = amount != null && amount in range
    val panelShape = RoundedCornerShape(30.dp)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF314A50).copy(alpha = 0.94f),
                            Color(0xFF172326).copy(alpha = 0.96f),
                            Color(0xFF0D1214).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(0.9.dp, Color.White.copy(alpha = 0.18f), panelShape)
                .padding(22.dp)
        ) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { typed -> value = typed.filter { it in '0'..'9' }.take(5) },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = value.isNotEmpty() && !valid,
                supportingText = { Text(t("{0}–{1} ml", range.first, range.last)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(0.7.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("Vazgeç"), color = Color.White.copy(alpha = 0.76f), fontSize = 14.sp)
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = if (valid) 0.16f else 0.06f))
                        .border(0.7.dp, Color.White.copy(alpha = if (valid) 0.20f else 0.08f), RoundedCornerShape(24.dp))
                        .clickable(enabled = valid, role = Role.Button) {
                            amount?.takeIf { it in range }?.let(onConfirm)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t("Kaydet"), color = Color.White.copy(alpha = if (valid) 0.96f else 0.38f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
