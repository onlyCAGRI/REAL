package com.cagri.real.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.i18n.t
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.launch

// Settings sheets in the look of the permission sheet (NotificationPermissionSheet): the same glass surface with a
// soft glow at the top, grabber, large centred title and white pill button, without its hero icon.

private val SheetTitle = Color(0xFFF3F1F4)
private val SheetBody = Color(0xFF9B9BA4)

/**
 * Confirmation for a destructive step: the question, what happens, a white pill with the red action and Vazgeç
 * under it. Either button slides the sheet away first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); then() } }
    RealSheet(state = state, onDismiss = onDismiss, title = title, description = message) { entered ->
        Spacer(Modifier.height(30.dp))
        SheetPill(confirmLabel, Color(0xFFD93025), Modifier.staggerIn(entered, 220)) { close(onConfirm) }
        Spacer(Modifier.height(6.dp))
        SheetTextButton(t("Vazgeç"), Modifier.staggerIn(entered, 280)) { close() }
    }
}

/** An on/off extra under a [ChoiceSheet]'s options (Harita görünümü → Sade harita); flipping it keeps the sheet open. */
class ChoiceToggle(val label: String, val detail: String?, val checked: Boolean, val onChange: (Boolean) -> Unit)

/** One option of a [ChoiceSheet]: its label and an optional grey line under it. */
data class Choice<T>(val value: T, val label: String, val detail: String? = null)

/**
 * The picker for a setting with a few values: the options as one inset list with a blue check on the current one.
 * Picking closes the sheet after the choice is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceSheet(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    toggle: ChoiceToggle? = null
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    RealSheet(state = state, onDismiss = onDismiss, title = title, description = "") { entered ->
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.fillMaxWidth().staggerIn(entered, 150)
                .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
        ) {
            choices.forEachIndexed { index, choice ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 18.dp), 0.5.dp, Color.White.copy(alpha = 0.09f))
                val picked = choice.value == selected
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = picked, role = Role.RadioButton) {
                            if (!picked) onSelect(choice.value)
                            close()
                        }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(choice.label, style = RealType.cardBodyProminent, color = SheetTitle)
                        choice.detail?.let { Text(it, color = SheetBody, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    if (picked) ActionIcon(ActionGlyph.Check, Modifier.size(18.dp), color = Color(0xFF3B82F6))
                    else Spacer(Modifier.width(18.dp))
                }
            }
        }
        toggle?.let { extra ->
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).staggerIn(entered, 220)
                    .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
                    .toggleable(value = extra.checked, role = Role.Switch, onValueChange = extra.onChange)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(extra.label, style = RealType.cardBodyProminent, color = SheetTitle)
                    extra.detail?.let { Text(it, color = SheetBody, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp)) }
                }
                Switch(
                    checked = extra.checked, onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6), checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color(0xFF9A9AA2), uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.14f)
                    )
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetTextButton(t("Kapat"), Modifier.staggerIn(entered, 280)) { close() }
    }
}

/** The permission sheet's surface inside a modal bottom sheet (its own window, so it covers the whole screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealSheet(
    state: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    title: String,
    description: String,
    content: @Composable ColumnScope.(entered: Boolean) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = shape,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.68f),
        dragHandle = null,
        tonalElevation = 0.dp
    ) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(LiquidGlassTokens.contentColor)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.85f
                        )
                    )
                }
                .border(0.75.dp, liquidContentEdge(), shape)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(width = 38.dp, height = 4.dp).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(30.dp))
            Text(
                title,
                style = RealType.editorialSection.copy(fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.9).sp),
                color = SheetTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().staggerIn(entered, 80)
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    description,
                    style = RealType.cardBodyProminent,
                    color = SheetBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).staggerIn(entered, 150)
                )
            }
            content(entered)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetPill(label: String, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RealType.actionLabel.copy(fontSize = 15.sp), color = textColor)
    }
}

@Composable
private fun SheetTextButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RealType.actionLabel.copy(fontSize = 14.sp), color = SheetBody)
    }
}

/** Fades and lifts a child in after [delayMillis], as the permission sheet does. */
@Composable
private fun Modifier.staggerIn(entered: Boolean, delayMillis: Int): Modifier {
    val reduce = LocalLiquidReduceMotion.current
    val progress by animateFloatAsState(
        targetValue = if (entered || reduce) 1f else 0f,
        animationSpec = if (reduce) tween(0) else tween(440, delayMillis, RealMotion.Emphasized),
        label = "sheetStagger"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 18.dp.toPx()
    }
}
