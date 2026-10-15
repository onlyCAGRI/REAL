package com.cagri.real.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class HomeBackdropPreset(val key: String, val title: String, val tint: Color) {
    BLACK("black", "AMOLED siyah", Color.Black),
    PURPLE("purple", "Mor sis", Color(0xFFAA86DF)),
    BLUE("blue", "Mavi sis", Color(0xFF6C9AD6)),
    GRAY("gray", "Gri sis", Color(0xFF9BA7AD))
}

data class HomeBackdropChoice(
    val preset: HomeBackdropPreset = HomeBackdropPreset.BLACK,
    val intensity: Float = 0.55f
)

/** Device-local appearance only. One listener per mounted consumer, no polling or sync writes. */
@Composable
fun rememberHomeBackdropChoice(): Pair<HomeBackdropChoice, (HomeBackdropChoice) -> Unit> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("real_home_backdrop", Context.MODE_PRIVATE) }
    fun read(): HomeBackdropChoice {
        val preset = HomeBackdropPreset.entries.firstOrNull { it.key == prefs.getString("preset", "black") }
            ?: HomeBackdropPreset.BLACK
        val stored = prefs.getFloat("intensity", 0.55f)
        return HomeBackdropChoice(preset, if (stored.isFinite()) stored.coerceIn(0f, 1f) else 0.55f)
    }
    var choice by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "preset" || key == "intensity") choice = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Recheck after registration, including a save while this consumer was absent.
        choice = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return choice to { value ->
        val safe = value.copy(intensity = if (value.intensity.isFinite()) value.intensity.coerceIn(0f, 1f) else 0.55f)
        if (safe != choice) {
            choice = safe
            prefs.edit().putString("preset", safe.preset.key).putFloat("intensity", safe.intensity).apply()
        }
    }
}

/** Cached gradients in viewport coordinates: independent of list scroll and animation clocks. */
fun Modifier.homeBackdrop(choice: HomeBackdropChoice): Modifier = drawWithCache {
    val strength = choice.intensity.coerceIn(0f, 1f)
    val tint = choice.preset.tint
    val radius = maxOf(size.width, size.height) * 0.76f
    val lowerLeft = Brush.radialGradient(
        listOf(tint.copy(alpha = strength * 0.38f), tint.copy(alpha = strength * 0.12f), Color.Transparent),
        center = Offset(-size.width * 0.20f, size.height * 0.91f), radius = radius.coerceAtLeast(1f)
    )
    val upperRight = Brush.radialGradient(
        listOf(tint.copy(alpha = strength * 0.15f), Color.Transparent),
        center = Offset(size.width * 1.22f, size.height * 0.18f), radius = radius.coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(Color.Black)
        if (choice.preset != HomeBackdropPreset.BLACK && strength > 0f) {
            drawRect(lowerLeft)
            drawRect(upperRight)
        }
    }
}

@Composable
fun HomeBackdropDialog(
    initial: HomeBackdropChoice,
    onSave: (HomeBackdropChoice) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    com.cagri.real.ui.components.RealAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101014),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Ana ekran arka planı") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HomeBackdropPreset.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { preset ->
                            val selected = draft.preset == preset
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(105.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .homeBackdrop(draft.copy(preset = preset))
                                    .border(if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                                        RoundedCornerShape(18.dp))
                                    .selectable(selected, role = Role.RadioButton,
                                        onClick = { draft = draft.copy(preset = preset) })
                                    .padding(14.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.width(38.dp).height(5.dp).background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp)))
                                        repeat(2) {
                                            Box(Modifier.fillMaxWidth().height(24.dp).background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(7.dp)))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(preset.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                Text("Kartların arkasında sabit, hafif bir renk. Yalnızca ana ekranda kullanılır.", color = Color.White.copy(alpha = 0.6f))
                if (draft.preset != HomeBackdropPreset.BLACK) {
                    Text("Yoğunluk · ${(draft.intensity * 100).toInt()}%")
                    Slider(value = draft.intensity, onValueChange = { draft = draft.copy(intensity = it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft); onDismiss() }) { Text("Kaydet", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = Color.White.copy(alpha = 0.6f)) } }
    )
}
