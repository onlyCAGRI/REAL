package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cagri.real.data.COUNTER_MAX_STEPS
import com.cagri.real.data.MarketAsset
import com.cagri.real.data.RealMarketClient
import com.cagri.real.data.ReminderAttachment
import com.cagri.real.data.counterConfig
import com.cagri.real.data.isCounterBlock
import com.cagri.real.data.marketAsset
import com.cagri.real.data.withCounter
import com.cagri.real.data.withMarketAsset
import com.cagri.real.ui.cardBlockSurface
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val WidgetShape = RoundedCornerShape(22.dp)
private val WidgetInk = Color(0xFFF3F3F5)
private val WidgetSoft = Color(0xFF9B9BA6)
private val WidgetBlue = Color(0xFF91B9FF)
private val WidgetUp = Color(0xFF5ED6A0)
private val WidgetDown = Color(0xFFFF8A80)
private val TurkishLocale = Locale.forLanguageTag("tr-TR")

/**
 * The dark REAL content surface of every widget block. It takes exactly the grid's height (placement
 * minHeight) and hands it to the content, so the block never grows on its own or leaves empty space.
 * Settings live outside: the same detached gear / × chip as text boxes, opening [WidgetSettingsDialog].
 */
@Composable
private fun WidgetSurface(
    editing: Boolean, showSettings: Boolean, onSettings: () -> Unit, onRemove: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier.fillMaxWidth().cardBlockSurface().clip(WidgetShape)
            .background(LiquidGlassTokens.contentColor)
            .border(0.7.dp, liquidContentEdge(), WidgetShape),
        propagateMinConstraints = true,
        content = content
    )
    if (editing && showSettings) CardTextSettingsControl(onRemove = onRemove, onSettings = onSettings)
}

// ---------------------------------------------------------------------------------------------
// Counter: a value box with step buttons on both sides (negatives left, positives right).
// Narrow (half width): the value on top and the step buttons in one row underneath.
// ---------------------------------------------------------------------------------------------

/**
 * [onChange] receives the updated attachment. While editing it is applied at once; outside editing
 * taps are collected and committed ~0.6 s after the last one, so fast tapping never drops a step.
 */
@Composable
fun CounterBlockView(
    attachment: ReminderAttachment,
    editing: Boolean,
    enabled: Boolean,
    reduceAnimations: Boolean,
    showSettings: Boolean,
    onSettings: () -> Unit,
    onChange: (ReminderAttachment) -> Unit,
    onRemove: () -> Unit
) {
    val config = attachment.counterConfig()
    val view = LocalView.current
    val currentAttachment by rememberUpdatedState(attachment)
    val currentChange by rememberUpdatedState(onChange)
    var value by remember(attachment.id) { mutableLongStateOf(config.value) }
    var committed by remember(attachment.id) { mutableLongStateOf(config.value) }
    // A value saved elsewhere (sync, settings reset) replaces ours unless a tap is still pending.
    LaunchedEffect(config.value) { if (value == committed) value = config.value; committed = config.value }
    LaunchedEffect(value) {
        if (value == committed) return@LaunchedEffect
        if (!editing) delay(600)
        val latest = currentAttachment
        currentChange(latest.withCounter(latest.counterConfig().copy(value = value)))
    }
    val negatives = config.steps.filter { it < 0 }.sortedDescending()
    val positives = config.steps.filter { it > 0 }
    val step: (Int) -> Unit = { s -> RealHaptics.tick(view); value += s }

    WidgetSurface(editing, showSettings, onSettings, onRemove) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val narrow = maxWidth < 250.dp
            val valueBox: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = if (narrow) 10.dp else 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(config.title, color = WidgetSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    RollingNumberText(
                        value.toIntSafe(), WidgetInk,
                        RealType.editorialSection.copy(fontSize = if (narrow) 34.sp else 44.sp, lineHeight = if (narrow) 38.sp else 48.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
                        reduceAnimations = reduceAnimations
                    )
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    valueBox(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                        (negatives + positives).forEach { s -> StepButton(s, enabled, 44.dp, step) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepColumn(negatives, enabled, step)
                    valueBox(Modifier.weight(1f).heightIn(min = 110.dp))
                    StepColumn(positives, enabled, step)
                }
            }
        }
    }
}

private fun Long.toIntSafe(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun StepColumn(steps: List<Int>, enabled: Boolean, onStep: (Int) -> Unit) {
    if (steps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { steps.forEach { StepButton(it, enabled, 54.dp, onStep) } }
}

@Composable
private fun StepButton(step: Int, enabled: Boolean, size: androidx.compose.ui.unit.Dp, onStep: (Int) -> Unit) {
    val positive = step > 0
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size)
            .pressScale(source, targetScale = 0.92f)
            .clip(CircleShape)
            .background(if (positive) WidgetBlue.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f))
            .border(0.7.dp, if (positive) WidgetBlue.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { onStep(step) }
            .semantics { contentDescription = if (positive) t("{0} artır", step) else t("{0} azalt", abs(step)) },
        contentAlignment = Alignment.Center
    ) {
        Text(stepLabel(step), color = if (positive) Color(0xFFDCE8FF) else WidgetInk,
            fontSize = if (size < 50.dp) 15.sp else 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun stepLabel(step: Int) = if (step > 0) "+$step" else "−${abs(step)}"

// ---------------------------------------------------------------------------------------------
// Market tile: name, big TRY price, 24h change pill and a sparkline that draws in from the left.
// Extra height goes to the space above the chart, so the chart always sits on the bottom edge.
// ---------------------------------------------------------------------------------------------

@Composable
fun MarketBlockView(
    attachment: ReminderAttachment,
    editing: Boolean,
    reduceAnimations: Boolean,
    showSettings: Boolean,
    onSettings: () -> Unit,
    onRemove: () -> Unit
) {
    val asset = attachment.marketAsset()
    val quotes by RealMarketClient.quotes.collectAsState()
    var failed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    LaunchedEffect(retryTick, asset) {
        // Refresh while the block is on screen; the client shares one request between every block.
        while (isActive) {
            failed = RealMarketClient.refresh(setOf(asset), force = retryTick > 0).isFailure &&
                RealMarketClient.quotes.value[asset.id] == null
            delay(5 * 60 * 1000L)
        }
    }
    val quote = quotes[asset.id]
    val change = quote?.change24h
    val trendColor = if ((change ?: 0.0) < 0) WidgetDown else WidgetUp
    val draw = remember(asset) { Animatable(if (reduceAnimations) 1f else 0f) }
    LaunchedEffect(asset, quote != null) {
        if (quote != null && !reduceAnimations && draw.value < 1f) draw.animateTo(1f, tween(900, easing = RealMotion.Emphasized))
    }

    WidgetSurface(editing, showSettings, onSettings, onRemove) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 250.dp
            Column(
                // Takes the grid height, so spare room opens above the chart and the chart stays at the bottom.
                Modifier.fillMaxWidth().heightIn(min = minHeight).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t(asset.label), color = WidgetSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (change != null) {
                            Text(
                                (if (change >= 0) "+" else "−") + String.format(TurkishLocale, "%.2f", abs(change)) + "%",
                                color = trendColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.clip(CircleShape).background(trendColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        quote?.let { formatTry(it.price, asset) } ?: "—",
                        color = WidgetInk,
                        style = RealType.editorialSection.copy(fontSize = if (narrow) 24.sp else 34.sp,
                            lineHeight = if (narrow) 28.sp else 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
                        maxLines = 1
                    )
                    Text(unitCaption(asset), color = WidgetSoft, fontSize = 12.sp)
                }
                Column {
                    Spacer(Modifier.height(8.dp))
                    Sparkline(quote?.sparkline.orEmpty(), trendColor, progress = { draw.value },
                        modifier = Modifier.fillMaxWidth().height(if (narrow) 40.dp else 54.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            failed -> t("Fiyat alınamadı · dokun")
                            quote == null -> t("Fiyat yükleniyor…")
                            quote.stale -> t("Son bilinen fiyat")
                            narrow -> t("24 saat · {0}", agoText(quote.updatedAt))
                            else -> t("Son 24 saat · CoinGecko · {0}", agoText(quote.updatedAt))
                        },
                        color = WidgetSoft.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (failed) Modifier.clickable { failed = false; retryTick++ } else Modifier
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: detached from the block, like "Metin biçimi" for text boxes.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSettingsDialog(attachment: ReminderAttachment, onChange: (ReminderAttachment) -> Unit, onDismiss: () -> Unit) {
    val currentAttachment by rememberUpdatedState(attachment)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().liquidDockChip(RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (attachment.isCounterBlock()) t("Sayaç ayarları") else t("Piyasa ayarları"),
                    color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(t("Bitti"), color = Color.White) }
            }
            if (attachment.isCounterBlock()) {
                val config = attachment.counterConfig()
                SettingsLabel(t("Ad"))
                var title by remember(attachment.id) { mutableStateOf(config.title) }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)) {
                    BasicTextField(
                        value = title,
                        onValueChange = { next ->
                            title = next.take(40)
                            onChange(currentAttachment.withCounter(currentAttachment.counterConfig().copy(title = title)))
                        },
                        singleLine = true, textStyle = TextStyle(color = WidgetInk, fontSize = 15.sp),
                        cursorBrush = SolidColor(WidgetBlue), modifier = Modifier.fillMaxWidth()
                    )
                }
                SettingsLabel(t("Butonlar · dokunarak kaldır"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    config.steps.forEach { s ->
                        val removable = config.steps.size > 1
                        Row(
                            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                                .clickable(enabled = removable, role = Role.Button) {
                                    onChange(currentAttachment.withCounter(currentAttachment.counterConfig().let { it.copy(steps = it.steps - s) }))
                                }
                                .semantics { contentDescription = t("{0} butonunu kaldır", stepLabel(s)) }
                                .padding(start = 14.dp, end = if (removable) 10.dp else 14.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stepLabel(s), color = WidgetInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (removable) { Spacer(Modifier.width(6.dp)); ActionIcon(ActionGlyph.Close, Modifier.size(10.dp), WidgetSoft) }
                        }
                    }
                }
                if (config.steps.size < COUNTER_MAX_STEPS) {
                    // Number keyboards have no minus/plus key: type the amount, the − / + buttons give the sign.
                    var draft by remember { mutableStateOf("") }
                    fun add(sign: Int) {
                        val amount = draft.trim().toIntOrNull()?.takeIf { it > 0 } ?: return
                        val latest = currentAttachment.counterConfig()
                        if (amount * sign !in latest.steps) onChange(currentAttachment.withCounter(latest.copy(steps = latest.steps + amount * sign)))
                        draft = ""
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)) {
                            if (draft.isEmpty()) Text(t("Yeni buton: miktar"), color = WidgetSoft.copy(alpha = 0.7f), fontSize = 15.sp)
                            BasicTextField(
                                value = draft, onValueChange = { draft = it.filter(Char::isDigit).take(4) },
                                singleLine = true, textStyle = TextStyle(color = WidgetInk, fontSize = 15.sp),
                                cursorBrush = SolidColor(WidgetBlue),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { add(1) }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        listOf(-1 to "−", 1 to "+").forEach { (sign, label) ->
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (sign > 0) WidgetBlue.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f))
                                    .clickable(enabled = draft.isNotEmpty(), role = Role.Button) { add(sign) }
                                    .semantics { contentDescription = if (sign > 0) t("Artı buton ekle") else t("Eksi buton ekle") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (draft.isNotEmpty()) WidgetInk else WidgetSoft.copy(alpha = 0.5f),
                                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Text(t("Sayacı sıfırla"), color = WidgetDown, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) {
                        onChange(currentAttachment.withCounter(currentAttachment.counterConfig().copy(value = 0)))
                    }.padding(vertical = 6.dp))
            } else {
                SettingsLabel(t("Gösterilen"))
                val asset = attachment.marketAsset()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MarketAsset.entries.forEach { option ->
                        val selected = option == asset
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                                .clickable(role = Role.Button) { if (!selected) onChange(currentAttachment.withMarketAsset(option)) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option.label, color = if (selected) Color.White else Color(0xFFB7B7C0), fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(unitCaption(option), color = WidgetSoft, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) = Text(text, color = WidgetSoft, fontSize = 12.sp)

private fun formatTry(price: Double, asset: MarketAsset): String {
    val digits = if (asset == MarketAsset.BTC) 0 else 2
    val format = NumberFormat.getNumberInstance(TurkishLocale).apply {
        minimumFractionDigits = digits; maximumFractionDigits = digits
    }
    return format.format(price) + " ₺"
}

private fun unitCaption(asset: MarketAsset): String = when (asset) {
    MarketAsset.BTC -> "1 BTC"
    MarketAsset.GOLD_GRAM -> t("1 gram altın")
    MarketAsset.EUR -> "1 €"
    MarketAsset.USD -> "1 $"
}

private fun agoText(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    return if (minutes < 1) t("az önce") else t("{0} dk önce", minutes)
}

/** Smooth area chart: soft gradient fill under a rounded line, revealed left to right by [progress]. */
@Composable
private fun Sparkline(values: List<Double>, color: Color, progress: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height * 0.6f), Offset(size.width, size.height * 0.6f), 1.dp.toPx())
            return@Canvas
        }
        val min = values.min(); val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val inset = 3.dp.toPx()
        val points = values.mapIndexed { i, v ->
            Offset(size.width * i / (values.size - 1), inset + (size.height - inset * 2) * (1f - ((v - min) / span).toFloat()))
        }
        val line = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val a = points[i - 1]; val b = points[i]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        clipRect(right = size.width * progress().coerceIn(0f, 1f)) {
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (progress() >= 1f) drawCircle(color, 3.5.dp.toPx(), points.last())
    }
}
