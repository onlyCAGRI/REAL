package com.cagri.real.calendar

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.components.DesignPanel
import com.cagri.real.ui.components.shouldCaptureBackdrop

private class CalendarGlassScene(val layer: GraphicsLayer, val position: () -> Offset, val reduceBlur: Boolean)
private val LocalCalendarGlass = staticCompositionLocalOf<CalendarGlassScene> { error("CalendarGlassFrame required") }

/** Reuses REAL's renderer and reduce-motion/blur behavior, with a separately captured ambient layer. */
@Composable
internal fun CalendarGlassFrame(
    reduceBlur: Boolean = false, modifier: Modifier = Modifier.fillMaxSize(), content: @Composable BoxScope.() -> Unit
) {
    val layer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val capture = shouldCaptureBackdrop(DesignStyle.LiquidGlass, reduceBlur, Build.VERSION.SDK_INT)
    Box(modifier.background(Color.Black)) {
        // matchParentSize: the ambient layer follows the frame, so a dialog frame can wrap its content.
        Box(Modifier.matchParentSize().onGloballyPositioned { position = it.positionInRoot() }
            .drawWithContent {
                if (capture) { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                else drawContent()
            }.background(Brush.verticalGradient(listOf(Color(0xFF202126), Color.Black, Color(0xFF141519)))))
        CompositionLocalProvider(LocalCalendarGlass provides CalendarGlassScene(layer, { position }, reduceBlur),
            LocalContentColor provides Color.White) { content() }
    }
}

@Composable
internal fun CalendarGlassPanel(modifier: Modifier = Modifier, corners: Dp = 24.dp,
    sheet: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val scene = LocalCalendarGlass.current
    DesignPanel(scene.layer, scene.position, modifier, corners, scene.reduceBlur, DesignStyle.LiquidGlass,
        liquidDynamic = true, liquidSheet = sheet, content = content)
}

/**
 * Dialog = one rounded glass card that wraps its content. It used to put a full-screen frame inside the
 * Dialog window, which showed up as a tall dark slab behind a smaller card.
 */
@Composable
internal fun CalendarGlassDialog(onDismiss: () -> Unit, title: String, reduceBlur: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CalendarGlassFrame(
            reduceBlur,
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
        ) {
            CalendarGlassPanel(Modifier.fillMaxWidth(), corners = 30.dp, sheet = true) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp).heightIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                    actions()
                }
            }
        }
    }
}

internal val CalendarAccent = Color(0xFF78B9FF)
internal val CalendarMuted = Color(0xFF9B9BA6)

/** primary = the one main action on a screen: same glass, with a soft blue tint. */
@Composable
internal fun CalendarGlassButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, content: @Composable RowScope.() -> Unit) {
    CalendarGlassPanel(modifier, corners = 22.dp) {
        if (primary && enabled) Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp))
            .background(CalendarAccent.copy(alpha = 0.20f)))
        TextButton(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (primary) Color(0xFFCFE4FF) else Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            ),
            content = content
        )
    }
}
