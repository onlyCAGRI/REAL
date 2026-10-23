package com.cagri.real.ui.components.water

import com.cagri.real.i18n.t
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.data.BeverageType
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun WaterSurface(
    progress: Float, reduceAnimations: Boolean, modifier: Modifier = Modifier,
    beverage: BeverageType = BeverageType.WATER, amountLabel: String? = null
) {
    val engine = remember(beverage) { WaterSurfaceEngine(progress, smoothWater = true) }
    val palette = remember(beverage) { beverage.appearance() }
    // Reused every frame instead of allocating two 81-point paths and rebuilding static gradient
    // shaders 60-120 times a second; a ShaderBrush keeps its shader for as long as the size is unchanged.
    val surface = remember { Path() }
    val water = remember { Path() }
    val drawResources = remember { WaterDrawResources() }
    val dryNumber = rememberGraphicsLayer()
    val lensNumber = rememberGraphicsLayer()
    val deepNumber = rememberGraphicsLayer()
    val plainNumber = rememberGraphicsLayer()
    val numberCache = remember(dryNumber, lensNumber, deepNumber, plainNumber) {
        WaterNumberCache(dryNumber, lensNumber, deepNumber, plainNumber)
    }
    val backgroundBrush = remember(palette) { Brush.verticalGradient(palette.background) }
    val waterLiquidBrush = remember(palette) { Brush.verticalGradient(palette.liquid) }
    val movingLiquidBrush = remember(palette) { Brush.verticalGradient(palette.liquid, startY = 0f, endY = 1f) }
    val lensBrush = remember { Brush.verticalGradient(
        listOf(Color(0xFF00B7DD).copy(alpha = 0.30f), Color.Transparent), startY = 0f, endY = 1f
    ) }
    val owner = LocalLifecycleOwner.current
    val visualOverflowFrozen = progress >= MAX_VISUAL_OVERFLOW_PROGRESS - 0.001f
    var frame by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    // ── Much larger percentage text matching the reference app ──
    val numberStyle = remember(beverage) { TextStyle(
            color = if (beverage == BeverageType.BEER) Color(0xFF3A2B12) else Color(0xFFF3FCFC),
            fontSize = 72.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-3).sp
        ) }
    val percentLabel = remember(progress) { "${(progress * 100).roundToInt().coerceAtLeast(0)}%" }
    val numberLayout = remember(textMeasurer, numberStyle, amountLabel, percentLabel) {
        val label = amountLabel ?: percentLabel
        textMeasurer.measure(label, numberStyle)
    }
    LaunchedEffect(engine, progress, reduceAnimations, visualOverflowFrozen) {
        if (!visualOverflowFrozen && progress > engine.targetFill) engine.disturb(0.55f, 0.9f)
        engine.targetFill = progress
        if (reduceAnimations || visualOverflowFrozen) {
            engine.step(0f, true)
            frame++
        }
    }
    WaterTiltSensor(engine, enabled = !reduceAnimations && !visualOverflowFrozen)
    LaunchedEffect(engine, owner, reduceAnimations, visualOverflowFrozen) {
        if (!reduceAnimations && !visualOverflowFrozen) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val frameGate = WaterFrameGate()
            while (true) {
                withFrameNanos { now ->
                    frameGate.elapsedSeconds(now)?.let { elapsed ->
                        engine.step(elapsed, false)
                        frame++
                    }
                }
            }
        }
    }
    Canvas(modifier
        .semantics { contentDescription = if (amountLabel == null) t("Su seviyesi yüzde {0}", (progress * 100).roundToInt()) else t("{0}: {1}", beverage.label, amountLabel) }
        .pointerInput(engine, reduceAnimations, visualOverflowFrozen) {
            detectTapGestures {
                if (!reduceAnimations && !visualOverflowFrozen) {
                    engine.disturb(it.x / size.width.coerceAtLeast(1))
                }
            }
        }
        .pointerInput(engine, reduceAnimations, visualOverflowFrozen) {
            detectDragGestures { change, drag ->
                if (!reduceAnimations && !visualOverflowFrozen) {
                    change.consume()
                    engine.disturb(change.position.x / size.width.coerceAtLeast(1),
                        (kotlin.math.abs(drag.x) + kotlin.math.abs(drag.y)).div(100f).coerceAtMost(0.3f))
                }
            }
        }
    ) {
        @Suppress("UNUSED_VARIABLE") val redraw = frame // Read in draw phase, not composition.
        val w = size.width
        val h = size.height
        engine.aspectRatio = w / h.coerceAtLeast(1f)
        drawRect(backgroundBrush)
        // Leave room for the title/total above a full vessel on compact displays.
        val headerSpace = minOf(148f * density, h * 0.56f)
        // Above 100% the surface is allowed to keep travelling upward. Once it has
        // completely cleared the canvas, keep it parked just off-screen instead of
        // doing unbounded visual work while the stored ml total continues to grow.
        val offscreenSurfaceY = -72f * density
        fun surfaceY(x: Float): Float {
            val natural = engine.surface(x, reduceAnimations || visualOverflowFrozen)
            val rawY = headerSpace + (natural - 0.24f) / 0.76f * (h - headerSpace)
            return rawY.coerceAtLeast(offscreenSurfaceY)
        }
        surface.rewind()
        water.rewind()
        for (index in 0..80) {
            val x = index / 80f
            val y = surfaceY(x)
            if (index == 0) { surface.moveTo(0f, y); water.moveTo(0f, y) }
            else { surface.lineTo(x * w, y); water.lineTo(x * w, y) }
        }
        water.lineTo(w, h); water.lineTo(0f, h); water.close()
        val centerY = surfaceY(0.5f)
        // Number position is computed early: every glass/light effect below is sized and
        // anchored to it, not to the screen, so the effect only ever touches the percentage.
        val layout = numberLayout
        val textX = 24f * density
        val textY = (surfaceY((textX + layout.size.width / 2f) / w.coerceAtLeast(1f)) -
            8f * density)
            .coerceAtMost((h - layout.size.height - 10f * density).coerceAtLeast(0f))
        clipPath(water) {
            if (beverage == BeverageType.WATER) {
                // Same 0..height gradient as before; the brush (and its shader) is simply cached.
                drawRect(waterLiquidBrush)
            } else {
                drawWaterVerticalFill(movingLiquidBrush, startY = centerY - h * 0.08f, endY = h)
            }
            if (beverage == BeverageType.WATER) {
                // Same underlying gradient above and below; a translucent lens reveals the water.
                drawWaterVerticalFill(lensBrush, startY = centerY - 16f * density, endY = centerY + h * 0.45f)
            }

            // ── Caustic light patterns under water (animated light spots) ──
            if (beverage == BeverageType.WATER && !reduceAnimations) {
                val causticTime = engine.time * 0.3f
                for (i in 0..11) {
                    val cx = (sin(causticTime * 0.4f + i * 1.7f) * 0.3f + 0.5f + i * 0.08f) % 1f * w
                    val cy = centerY + h * (0.08f + (i % 5) * 0.12f + sin(causticTime * 0.5f + i * 2.1f) * 0.04f)
                    val radius = (22f + sin(causticTime + i * 0.9f) * 14f) * density
                    val alpha = 0.025f + sin(causticTime * 0.7f + i) * 0.015f
                    drawWaterSoftLight(drawResources.softLight, Offset(cx, cy), radius, alpha.coerceIn(0.01f, 0.05f))
                }
            }

            // Broad moving light bands, rather than a flat progress-fill rectangle.
            for (index in 0..5) {
                val phase = if (reduceAnimations) 0f else engine.time * 0.15f
                val y = centerY + h * (index * 0.17f + sin(phase + index) * 0.04f)
                val light = drawResources.lightBands[index].apply {
                    rewind()
                    moveTo(-w * 0.15f, y + h * 0.18f)
                    cubicTo(w * 0.25f, y + h * 0.10f, w * 0.5f, y, w * 1.15f, y - h * 0.09f)
                    lineTo(w * 1.15f, y - h * 0.03f)
                    cubicTo(w * 0.6f, y + h * 0.04f, w * 0.3f, y + h * 0.20f, -w * 0.15f, y + h * 0.24f)
                    close()
                }
                drawPath(light, Color.White.copy(alpha = if (beverage == BeverageType.WATER) 0.018f else 0.025f + index * 0.003f))
            }
            if (!reduceAnimations && (engine.energy > 0.025f || (beverage == BeverageType.BEER && engine.fill > 0f))) {
                for (index in 0..17) {
                    val travel = (engine.time * (0.15f + (index % 3) * 0.035f) + index * 0.061f) % 1f
                    val x = ((index * 0.137f + sin(engine.time + index) * 0.012f) % 1f) * w
                    val y = h - travel * (h - centerY + 45f)
                    drawCircle(palette.bubble.copy(alpha = (engine.energy * 0.22f + if (beverage == BeverageType.BEER) 0.28f else 0f).coerceAtMost(0.5f)),
                        radius = (1.5f + index % 4) * density, center = Offset(x, y), style = Stroke(0.7f * density))
                }
            }
        }
        drawPath(surface, palette.edge.copy(alpha = 0.32f), style = Stroke(1.2f * density))
        if (beverage == BeverageType.BEER && engine.fill > 0.001f) {
            drawBeerFoam(engine, reduceAnimations, drawResources.foam, ::surfaceY)
        }

        // Soft bloom behind the number, reinforcing the lens/refraction feel from the reference.
        drawWaterSoftLight(drawResources.softLight,
            Offset(textX + layout.size.width * 0.5f, textY + layout.size.height * 0.5f),
            layout.size.width * 0.65f, 0.10f)
        drawWaterGlassNumber(layout, Offset(textX, textY), water, engine, reduceAnimations,
            ink = if (beverage == BeverageType.BEER) Color(0xFF3A2B12) else Color(0xFFE3F7FA),
            numberCache = numberCache, softLight = drawResources.softLight, surfaceY = ::surfaceY)
    }
}
