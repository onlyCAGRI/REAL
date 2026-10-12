package com.cagri.real.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.MoodTrackerEntry
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.theme.RealFont

/**
 * Android launcher widget'ı olacak tasarımların kapalı kutusu. Kapalıyken içerik hiç compose edilmez
 * (AnimatedVisibility çıkış bitince içeriği composition'dan kaldırır): animasyon / veri yükü yok.
 */
@Composable
internal fun YasamLauncherBox(
    reminders: List<ReminderItem>,
    moodEntries: List<MoodTrackerEntry>,
    reduceAnimations: Boolean,
    onToggleReminder: ((Long) -> Unit)?
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp).fillMaxWidth().yasamGlass(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = { open = !open })
                .semantics { stateDescription = if (open) "Açık" else "Kapalı" }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Launcher widget tasarımları", color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, fontFamily = RealFont, modifier = Modifier.weight(1f)
            )
            YasamIcon(if (open) YasamGlyph.ChevronUp else YasamGlyph.ChevronDown, YasamMuted, 16.dp)
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceAnimations) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduceAnimations) ExitTransition.None else shrinkVertically() + fadeOut()
        ) {
            YasamLauncherCards(reminders, moodEntries, reduceAnimations, onToggleReminder)
        }
    }
}

@Composable
internal fun YasamLauncherCards(
    reminders: List<ReminderItem>,
    moodEntries: List<MoodTrackerEntry>,
    reduceAnimations: Boolean,
    onToggleReminder: ((Long) -> Unit)?
) {
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Önizleme tasarımları", color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        MoodMonthChartCard(
            entries = moodEntries, reduceAnimations = reduceAnimations,
            modifier = Modifier.fillMaxWidth(), enterDelayMillis = 0
        )
        WeekProgressCard(
            reminders = reminders, reduceAnimations = reduceAnimations,
            modifier = Modifier.fillMaxWidth(), enterDelayMillis = 90
        )
        DrinksWeekCard(
            reduceAnimations = reduceAnimations,
            modifier = Modifier.fillMaxWidth(), enterDelayMillis = 180
        )
        Text("Yeni tasarımlar", color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        WidgetReferenceSection(
            reminders = reminders, reduceAnimations = reduceAnimations,
            modifier = Modifier.fillMaxWidth(), onToggleReminder = onToggleReminder
        )
    }
}
