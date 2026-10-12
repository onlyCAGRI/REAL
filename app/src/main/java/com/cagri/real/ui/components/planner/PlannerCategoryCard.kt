package com.cagri.real.ui.components.planner

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import com.cagri.real.ui.components.ReminderColorChoices
import com.cagri.real.ui.theme.RealType

private const val PLANNER_COLOR_PREF = "planner_card_color_index_v1"
private const val DEFAULT_PLANNER_COLOR_INDEX = 17 // Lavanta
private val PlannerHomeInk = Color(0xFFF3F1F4)
private val PlannerHomeDark = Color(0xFF171719)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannerCategoryCard(
    hour24Count: Int, modifier: Modifier = Modifier, enabled: Boolean,
    globalIndex: Int, visibleCategoryCount: Int, localYs: FloatArray,
    listState: LazyListState?, topInsetPx: Float, stackOffsetPx: Float,
    extendUnderNext: Boolean, onClick: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        com.cagri.real.platform.android.sync.SyncLocal.preferences(context, "real_ui", Context.MODE_PRIVATE)
    }
    var colorIndex by remember {
        mutableIntStateOf(
            preferences.getInt(PLANNER_COLOR_PREF, DEFAULT_PLANNER_COLOR_INDEX)
                .coerceIn(ReminderColorChoices.indices)
        )
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PLANNER_COLOR_PREF) colorIndex = preferences.getInt(PLANNER_COLOR_PREF, DEFAULT_PLANNER_COLOR_INDEX).coerceIn(ReminderColorChoices.indices)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var showPalette by remember { mutableStateOf(false) }
    var localY by remember { mutableFloatStateOf(0f) }

    val accent = ReminderColorChoices.getOrNull(colorIndex)?.color
        ?: ReminderColorChoices[DEFAULT_PLANNER_COLOR_INDEX].color
    val shape = RoundedCornerShape(26.dp)
    val backgroundBrush = remember(accent) {
        Brush.linearGradient(
            listOf(
                lerp(accent, PlannerHomeDark, 0.76f),
                Color(0xFF20201F),
                lerp(accent, Color(0xFF101113), 0.86f)
            )
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .onPlaced {
                localY = it.positionInParent().y
                if (globalIndex < localYs.size) localYs[globalIndex] = localY
            }
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.58f
                translationY = 0f
                if (listState != null && localYs.size == visibleCategoryCount) {
                    val container = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "categories" }
                    val lastY = localYs.lastOrNull() ?: 0f
                    if (container != null && lastY > 0f) {
                        val natural = container.offset + localY
                        val pinned = topInsetPx + globalIndex * stackOffsetPx
                        val limit = container.offset + lastY -
                            (visibleCategoryCount - 1 - globalIndex) * stackOffsetPx
                        translationY = minOf(maxOf(natural, pinned), limit) - natural
                    }
                }
            }
            .clip(shape)
            .background(backgroundBrush)
            .border(0.8.dp, accent.copy(alpha = 0.30f), shape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = { showPalette = true }
            )
            .heightIn(min = 112.dp)
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = 16.dp,
                bottom = 46.dp + if (extendUnderNext) 40.dp else 0.dp
            )
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Planım",
                color = PlannerHomeInk,
                style = RealType.categoryTitleMoods,
                modifier = Modifier.weight(1f).offset(x = 10.dp)
            )
            PlannerCategoryAccentBars()
        }
        Spacer(Modifier.height(13.dp))
        Text(
            "24 hour · $hour24Count plan · Takvim",
            color = PlannerHomeInk.copy(alpha = 0.72f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }

    if (showPalette) {
        PlannerColorDialog(
            selectedIndex = colorIndex,
            onSelect = { selected ->
                colorIndex = selected
                com.cagri.real.platform.android.sync.SyncLocal.saveUiPreference(preferences,
                    preferences.edit().putInt(PLANNER_COLOR_PREF, selected))
                showPalette = false
            },
            onDismiss = { showPalette = false }
        )
    }
}

/** Matches the earlier REAL Planım treatment: three quiet vertical pastel marks, no calendar icon. */
@Composable
private fun PlannerCategoryAccentBars() {
    val today = remember { LocalDate.now() }
    val monthLabel = remember(today) {
        today.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("tr-TR"))
            .uppercase(Locale.forLanguageTag("tr-TR"))
            .replace(".", "")
    }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(end = 2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Color(0xFFCDBFE6),
                Color(0xFFD8A5AE),
                Color(0xFFA8D5D7)
            ).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .width(10.dp)
                        .height(when (index) {
                            0 -> 29.dp
                            1 -> 32.dp
                            else -> 27.dp
                        })
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.92f))
                )
            }
        }
        Text(
            text = "${today.dayOfMonth} $monthLabel",
            color = PlannerHomeInk.copy(alpha = 0.34f),
            fontSize = 9.sp
        )
    }
}

@Composable
private fun PlannerColorDialog(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xF51A1A1E))
                .border(0.8.dp, Color.White.copy(alpha = 0.13f), shape)
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Planım rengi",
                color = Color(0xFFF3F3F6),
                fontSize = 16.sp
            )
            Text(
                text = "Kartın ana tonunu seç",
                color = Color(0xFFA9A9B1),
                fontSize = 12.sp
            )
            ReminderColorChoices.chunked(5).forEachIndexed { rowIndex, rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowChoices.forEachIndexed { columnIndex, choice ->
                        val index = rowIndex * 5 + columnIndex
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (index == selectedIndex) 0.10f else 0.03f))
                                .border(
                                    width = if (index == selectedIndex) 1.8.dp else 0.7.dp,
                                    color = if (index == selectedIndex) Color.White.copy(alpha = 0.82f)
                                    else Color.White.copy(alpha = 0.10f),
                                    shape = CircleShape
                                )
                                .clickable { onSelect(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (index == selectedIndex) 27.dp else 25.dp)
                                    .clip(CircleShape)
                                    .background(choice.color)
                                    .border(0.6.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                            )
                        }
                    }
                    repeat(5 - rowChoices.size) { Spacer(Modifier.size(42.dp)) }
                }
            }
        }
    }
}
