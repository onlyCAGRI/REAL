package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDateSheet(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val initial = initialMillis?.takeIf { it > now } ?: (now + 60L * 60L * 1000L)
    val initialZoned = remember(initial) { Instant.ofEpochMilli(initial).atZone(zone) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialZoned.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli()
    )
    val timeState = rememberTimePickerState(
        initialHour = initialZoned.hour,
        initialMinute = initialZoned.minute,
        is24Hour = true
    )
    var tab by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF232325),
        contentColor = Color(0xFFF4F4F6),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(Color(0xFF76767D), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateTab(
                    title = "Tarih",
                    subtitle = selectedDateLabel(dateState.selectedDateMillis, zone),
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    modifier = Modifier.weight(1f)
                )
                DateTab(
                    title = "Süre",
                    subtitle = "%02d:%02d".format(timeState.hour, timeState.minute),
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            if (tab == 0) {
                DatePicker(
                    state = dateState,
                    modifier = Modifier.fillMaxWidth(),
                    title = null,
                    headline = null,
                    showModeToggle = false
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timeState)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "İptal et",
                    color = Color(0xFFEAEAF0),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(16.dp)
                )
                Box(Modifier.size(width = 1.dp, height = 28.dp).background(Color.White.copy(alpha = 0.12f)))
                Text(
                    "Tamamlandı",
                    color = Color(0xFFF6F6F8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val selectedDateMillis = dateState.selectedDateMillis ?: now
                        val date = Instant.ofEpochMilli(selectedDateMillis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        val local = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                        val selected = local.atZone(zone).toInstant().toEpochMilli()
                        onConfirm(maxOf(selected, System.currentTimeMillis() + 60_000L))
                    }.padding(16.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DateTab(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(if (selected) Color(0xFF303034) else Color.Transparent, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = if (selected) Color(0xFF69A0FF) else Color(0xFFA4A4AC), fontSize = 12.sp)
    }
}

private fun selectedDateLabel(millis: Long?, zone: ZoneId): String {
    val date = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        ?: LocalDate.now(zone)
    val months = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]}"
}
