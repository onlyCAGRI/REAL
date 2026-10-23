package com.cagri.real.ui.components

import com.cagri.real.i18n.t
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
        Text(t("Önizleme tasarımları"), color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
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
        Text(t("Yeni tasarımlar"), color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        WidgetReferenceSection(
            reminders = reminders, reduceAnimations = reduceAnimations,
            modifier = Modifier.fillMaxWidth(), onToggleReminder = onToggleReminder
        )
    }
}
