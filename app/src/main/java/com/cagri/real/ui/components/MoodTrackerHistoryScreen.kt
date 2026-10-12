package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cagri.real.data.MoodScope
import com.cagri.real.data.MoodTrackerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home Mood Tracker landing page.
 *
 * This screen deliberately separates “history” from the check-in flow. Tapping the permanent
 * Mood Tracker category card lands here; “Yeni mood ekle” then opens the existing Mood V2 flow.
 */
@Composable
fun MoodTrackerHistoryScreen(
    entries: List<MoodTrackerEntry>,
    onDismiss: () -> Unit,
    onAddMood: () -> Unit,
    reduceAnimations: Boolean = false
) {
    BackHandler(onBack = onDismiss)

    val sortedEntries = remember(entries) { entries.sortedByDescending { it.details.loggedAt } }
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    val dayGroups = remember(sortedEntries, zoneId) {
        sortedEntries.groupBy { entry ->
            Instant.ofEpochMilli(entry.details.loggedAt).atZone(zoneId).toLocalDate()
        }
    }
    val latest = sortedEntries.firstOrNull()
    val accent = latest?.let { moodV2Tint(it.mood) } ?: Color(0xFF9AA8FF)
    val topTone = remember(accent) { lerp(Color.Black, accent, 0.20f) }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(80f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(topTone, Color(0xFF08090C), Color.Black)
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RealBackButton(onDismiss)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Moods",
                    color = Color(0xFFF4F4F6),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(58.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "mood_tracker_add") {
                    MoodTrackerAddCard(
                        accent = accent,
                        reduceAnimations = reduceAnimations,
                        onClick = onAddMood
                    )
                }

                item(key = "mood_tracker_history_title") {
                    Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        Text(
                            text = "Geçmiş izlerin",
                            color = Color(0xFFF3F3F5),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (sortedEntries.isEmpty()) "Henüz bir mood kaydın yok." else "Moodların günlere göre birlikte gösteriliyor.",
                            color = Color(0xFF85858E),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                dayGroups.forEach { (date, dayEntries) ->
                    item(key = "mood_day_${date}") {
                        MoodHistoryDayHeader(
                            date = date,
                            count = dayEntries.size,
                            today = today
                        )
                    }
                    items(dayEntries, key = { "mood_history_${it.id}" }) { entry ->
                        MoodHistoryEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodTrackerAddCard(
    accent: Color,
    reduceAnimations: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val brush = remember(accent) {
        Brush.linearGradient(
            listOf(
                lerp(Color(0xFF171923), accent, 0.30f),
                lerp(Color(0xFF111217), accent, 0.13f),
                Color(0xFF111217)
            )
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, targetScale = 0.985f, reduceAnimations = reduceAnimations)
            .clip(shape)
            .background(brush)
            .border(0.8.dp, accent.copy(alpha = 0.38f), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Yeni bir iz bırak", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                "Şu an nasıl hissediyorsun ya da bugünün havası nasıldı?",
                color = Color(0xFFC2C2C9),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text("＋", color = Color.White, fontSize = 28.sp)
    }
}

@Composable
private fun MoodHistoryDayHeader(
    date: LocalDate,
    count: Int,
    today: LocalDate
) {
    val locale = remember { Locale.forLanguageTag("tr-TR") }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM · EEEE", locale) }
    val title = when (date) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        else -> date.format(dateFormatter)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFFF0F0F3),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count iz",
            color = Color(0xFF777780),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MoodHistoryEntryCard(entry: MoodTrackerEntry) {
    val accent = moodV2Tint(entry.mood)
    val shape = RoundedCornerShape(26.dp)
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("tr-TR")) }
    val timestamp = remember(entry.details.loggedAt) {
        Instant.ofEpochMilli(entry.details.loggedAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
    val descriptors = entry.details.descriptors.take(3).map { moodDescriptorLabelV2(it) }
    val factors = entry.details.factors.take(3).map { moodFactorLabelV2(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(Color(0xFF17181D), accent, 0.18f),
                        Color(0xFF111217)
                    )
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.13f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoodV2Face(mood = entry.mood, size = 58.dp, tint = accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (entry.details.scope == MoodScope.MOMENT) "Anlık his" else "Günün havası",
                    color = accent.copy(alpha = 0.94f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(timestamp, color = Color(0xFF74747D), fontSize = 10.sp)
            }
            Text(
                text = moodV2FamilyTitle(entry.mood),
                color = Color(0xFFF4F4F6),
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (descriptors.isNotEmpty()) {
                Text(
                    descriptors.joinToString(" · "),
                    color = Color(0xFFC3C3CA),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (factors.isNotEmpty()) {
                Text(
                    factors.joinToString(" · "),
                    color = Color(0xFF7F7F88),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
