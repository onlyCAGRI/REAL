package com.cagri.real.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cagri.real.ui.DesignStyle
import kotlin.math.ceil


@Composable
fun LiquidGlassSurface(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    modifier: Modifier = Modifier,
    corners: Dp = LiquidGlassTokens.capsuleRadius,
    reduceDynamicBlur: Boolean = false,
    strength: LiquidGlassStrength = LiquidGlassStrength.Regular,
    reduceAnimations: Boolean = LocalLiquidReduceMotion.current,
    role: LiquidGlassRole = LiquidGlassRole.Chrome,
    content: @Composable BoxScope.() -> Unit
) {
    val nested = LocalInsideLiquidGlass.current
    val dynamic = !nested && !reduceDynamicBlur && Build.VERSION.SDK_INT >= 33
    var position by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val materialMotion = !reduceAnimations && role != LiquidGlassRole.Sheet
    val response by animateFloatAsState(
        if (pressed && materialMotion) 1f else 0f,
        if (!materialMotion) snap() else tween(if (pressed) LiquidGlassTokens.pressMillis else LiquidGlassTokens.settleMillis),
        label = "liquidMaterialPress"
    )
    val shape = remember(corners) { RoundedCornerShape(corners) }
    val rim = remember(role) { if (role == LiquidGlassRole.Dock) liquidDockRimBrush() else liquidRimBrush() }
    val tint = remember(dynamic, strength, role) {
        val body = when {
            role == LiquidGlassRole.Sheet -> LiquidGlassTokens.sheetTint
            role == LiquidGlassRole.Dock -> LiquidGlassTokens.dockTintTop
            strength == LiquidGlassStrength.Regular -> LiquidGlassTokens.regularTint
            else -> LiquidGlassTokens.clearTint
        }
        if (dynamic && role == LiquidGlassRole.Dock) Brush.verticalGradient(listOf(LiquidGlassTokens.dockTintTop, LiquidGlassTokens.dockTintBottom))
        else if (dynamic) Brush.verticalGradient(listOf(body, body.copy(alpha = body.alpha + 0.08f)))
        else Brush.verticalGradient(listOf(LiquidGlassTokens.fallbackTop, LiquidGlassTokens.fallbackBottom))
    }
    Box(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            
            .pointerInput(materialMotion) {
                pressed = false
                if (materialMotion) {
                    try {
                        awaitPointerEventScope {
                            while (true) pressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                        }
                    } finally { pressed = false }
                }
            }
            .graphicsLayer {
                val s = 1f - (1f - LiquidGlassTokens.pressScale) * response
                scaleX = s; scaleY = s
            }
            .shadow(LiquidGlassTokens.shadow, shape, clip = false)
            .clip(shape)
            .border(if (role == LiquidGlassRole.Dock) LiquidGlassTokens.dockEdgeWidth else LiquidGlassTokens.edgeWidth, rim, shape)
    ) {
        if (dynamic) LiquidBackdrop(backdrop, { position - backdropPosition() }, corners, strength, role, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(tint))
        Box(Modifier.matchParentSize().drawWithCache {
            val light = Brush.radialGradient(
                listOf(Color.White.copy(alpha = when (role) { LiquidGlassRole.Sheet -> 0.045f; LiquidGlassRole.Dock -> 0.12f; else -> 0.07f }), Color.Transparent),
                Offset(size.width * 0.16f, -size.height * 0.4f), size.width.coerceAtLeast(1f)
            )
            onDrawBehind { drawRect(light, alpha = 0.55f + response * 0.45f) }
        })
        CompositionLocalProvider(LocalInsideLiquidGlass provides true) { content() }
    }
}


@Composable
fun LiquidGlassContentSurface(
    modifier: Modifier = Modifier,
    corners: Dp = LiquidGlassTokens.contentRadius,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember(corners) { RoundedCornerShape(corners) }
    val edge = remember { liquidContentEdge() }
    Box(modifier.clip(shape).background(LiquidGlassTokens.contentColor).border(0.6.dp, edge, shape), content = content)
}

@Composable
fun LiquidGlassIconButton(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    reduceDynamicBlur: Boolean = false,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(backdrop, backdropPosition, modifier.size(48.dp), 24.dp, reduceDynamicBlur) {
        IconButton(onClick = onClick, modifier = Modifier.matchParentSize().semantics { contentDescription = description }) { content() }
    }
}

@Composable
fun DesignIconButton(
    backdrop: GraphicsLayer,
    backdropPosition: () -> Offset,
    designStyle: DesignStyle,
    reduceDynamicBlur: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassSurface(backdrop, backdropPosition, modifier.size(48.dp), 24.dp, reduceDynamicBlur) {
            IconButton(onClick = onClick, interactionSource = interactionSource, modifier = Modifier.matchParentSize()) { content() }
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier, interactionSource = interactionSource) { content() }
    }
}


fun Modifier.liquidChrome(corners: Dp = 24.dp, selected: Boolean = false): Modifier {
    val shape = RoundedCornerShape(corners)
    return clip(shape)
        .background(Brush.verticalGradient(listOf(
            if (selected) LiquidGlassTokens.selectedTop else LiquidGlassTokens.staticTop,
            if (selected) LiquidGlassTokens.selectedBottom else LiquidGlassTokens.staticBottom
        )))
        .border(LiquidGlassTokens.edgeWidth, liquidRimBrush(selected), shape)
}

internal fun liquidRimBrush(selected: Boolean = false): Brush = Brush.linearGradient(
    0f to (if (selected) Color(0x88FFFFFF) else LiquidGlassTokens.edgeTop),
    0.22f to Color(0x1AFFFFFF),
    0.52f to LiquidGlassTokens.edgeMiddle,
    0.82f to LiquidGlassTokens.edgeBottom,
    1f to (if (selected) LiquidGlassTokens.selectionAccent.copy(alpha = 0.40f) else Color(0x38FFFFFF))
)

internal fun liquidDockRimBrush(): Brush = Brush.linearGradient(
    0f to LiquidGlassTokens.dockEdgeTop,
    0.25f to Color(0x26FFFFFF),
    0.55f to Color(0x14FFFFFF),
    0.8f to Color(0x4DFFFFFF),
    1f to LiquidGlassTokens.dockEdgeBottom
)

fun liquidContentEdge(): Brush = Brush.verticalGradient(listOf(
    LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom
))


fun Modifier.liquidHomeAtmosphere(): Modifier = drawWithCache {
    val atmosphere = Brush.verticalGradient(
        0f to Color.Transparent,
        0.56f to Color.Transparent,
        0.82f to Color(0xFF060707),
        1f to LiquidGlassTokens.atmospherePeak
    )
    onDrawBehind { drawRect(atmosphere) }
}

@RequiresApi(33)
@Composable
private fun LiquidBackdrop(
    backdrop: GraphicsLayer, offset: () -> Offset, corners: Dp,
    strength: LiquidGlassStrength, role: LiquidGlassRole, modifier: Modifier
) {
    val sample = rememberGraphicsLayer()
    
    val shader = remember { runCatching { RuntimeShader(LIQUID_SHADER) }.getOrNull() }
    if (shader == null) {
        Box(modifier.background(LiquidGlassTokens.fallbackBottom))
        return
    }
    Box(modifier.drawWithCache {
        val ratio = LiquidGlassTokens.sampleScale
        val padding = LiquidGlassTokens.samplePadding.toPx()
        val sampleSize = IntSize(ceil((size.width + padding * 2) * ratio).toInt().coerceIn(1, 4096), ceil((size.height + padding * 2) * ratio).toInt().coerceIn(1, 4096))
        shader.setFloatUniform("extent", size.width * ratio, size.height * ratio)
        shader.setFloatUniform("padding", padding * ratio)
        shader.setFloatUniform("corner", corners.toPx().coerceAtMost(size.minDimension / 2) * ratio)
        val dock = role == LiquidGlassRole.Dock
        shader.setFloatUniform("lensWidth", (if (dock) LiquidGlassTokens.dockLensWidth else LiquidGlassTokens.lensWidth).toPx() * ratio)
        shader.setFloatUniform("strength", (if (dock) LiquidGlassTokens.dockRefraction else LiquidGlassTokens.refraction).toPx() * ratio * (if (role == LiquidGlassRole.Sheet) 0.7f else 1f))
        shader.setFloatUniform("dispersion", if (dock) LiquidGlassTokens.dockDispersion else LiquidGlassTokens.dispersion)
        val blur = when {
            role == LiquidGlassRole.Sheet -> LiquidGlassTokens.sheetBlur
            dock -> LiquidGlassTokens.dockBlur
            strength == LiquidGlassStrength.Regular -> LiquidGlassTokens.regularBlur
            else -> LiquidGlassTokens.clearBlur
        }.toPx() * ratio
        val effect = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "scene"),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
        ).asComposeRenderEffect()
        sample.renderEffect = effect
        sample.clip = true
        onDrawBehind {
            val origin = offset()
            sample.record(size = sampleSize) {
                drawRect(Color.Black)
                scale(ratio, ratio, Offset.Zero) {
                    translate(padding - origin.x, padding - origin.y) { drawLayer(backdrop) }
                }
            }
            translate(-padding, -padding) { scale(1f / ratio, 1f / ratio, Offset.Zero) { drawLayer(sample) } }
        }
    })
}



private const val LIQUID_SHADER = """
uniform shader scene;
uniform float2 extent;
uniform float padding;
uniform float corner;
uniform float lensWidth;
uniform float strength;
uniform float dispersion;
half4 main(float2 coord) {
    float2 p = coord - float2(padding) - extent * 0.5;
    float2 q = abs(p) - (extent * 0.5 - float2(corner));
    float2 outside = max(q, float2(0.0));
    float distance = length(outside) + min(max(q.x, q.y), 0.0) - corner;
    float2 normal;
    if (length(outside) > 0.001) normal = normalize(outside) * sign(p);
    else normal = q.x > q.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y));
    float edge = 1.0 - smoothstep(0.0, lensWidth, max(-distance, 0.0));
    float2 shift = -normal * strength * edge * edge;
    float2 uv = coord + shift;
    // Slightly more diffusion toward the thick edge; color separation stays subpixel.
    float depth = edge * 0.65;
    half4 c = scene.eval(uv) * 0.5 + (scene.eval(uv + normal * depth) + scene.eval(uv - normal * depth)) * 0.25;
    half red = scene.eval(uv + shift * dispersion).r;
    half blue = scene.eval(uv - shift * dispersion).b;
    c.rgb = mix(c.rgb, half3(red, c.g, blue), half(0.25 * edge));
    float light = pow(max(dot(normal, normalize(float2(-0.45, -0.9))), 0.0), 3.0);
    c.rgb += half3(light * edge * edge * 0.065);
    return half4(c.rgb, 1.0);
}
"""
