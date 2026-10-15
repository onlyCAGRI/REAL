package com.cagri.real.ui.components.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.cagri.real.ui.components.ProfileAvatar
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.theme.RealType

@Composable
internal fun PlannerTodayHeader(now: Instant, zone: ZoneId) {
    val day = now.atZone(zone).toLocalDate()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plannerDayLabel(day), color = Color(0xFF8C8C95), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(day.format(DateTimeFormatter.ofPattern("dd.MM")), color = Color(0xFFF3F1F4),
                fontSize = 58.sp, lineHeight = 61.sp, letterSpacing = (-2.6).sp, fontWeight = FontWeight.Normal, maxLines = 1)
            Text(plannerMonthLabel(day), color = Color(0xFFF3F1F4), fontSize = 58.sp, lineHeight = 60.sp,
                letterSpacing = (-2.2).sp, fontWeight = FontWeight.Normal)
        }
        Box(Modifier.width(1.dp).height(98.dp).background(Color(0xFF3A3A42)))
        Column(Modifier.width(118.dp).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PlannerWorldClock(now, "Europe/Istanbul", "Türkiye")
            PlannerWorldClock(now, "Europe/Berlin", "Almanya")
        }
    }
}

@Composable
private fun PlannerWorldClock(now: Instant, zone: String, label: String) {
    Column {
        Text(
            now.atZone(ZoneId.of(zone)).format(PlannerTimeFormat), color = Color(0xFFF3F1F4),
            fontSize = 22.sp, letterSpacing = (-0.6).sp, fontWeight = FontWeight.Normal
        )
        Text(label, color = Color(0xFF85858E), fontSize = 12.sp)
    }
}

/** "Next 24 hours" heading in the same type as the new daily view, plus a one-line summary. */
@Composable
internal fun PlannerNext24Title(count: Int, nextStartMillis: Long?, now: Instant) {
    val summary = when {
        count == 0 -> "Nothing planned"
        nextStartMillis == null -> "$count ${if (count == 1) "plan" else "plans"}"
        else -> {
            val minutes = ((nextStartMillis - now.toEpochMilli()) / 60_000L).coerceAtLeast(0L)
            val countdown = when {
                minutes < 1L -> "now"
                minutes < 60L -> "${minutes}m"
                else -> "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
            }
            "$count ${if (count == 1) "plan" else "plans"}  ·  next in $countdown"
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
        Text(
            "Next 24 hours",
            style = RealType.editorialSection.copy(color = Color(0xFFF3F1F4))
        )
        Text(
            summary,
            style = RealType.editorialSubtitle.copy(fontSize = 13.sp),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
internal fun PlannerNext24Card(
    event: PlannerEvent,
    cardColor: Color,
    zone: ZoneId,
    ownerName: String,
    onClick: () -> Unit,
    onToggleDuration: (() -> Unit)? = null,
    /** When given, a running event shows how far along it is (same progress language as the new daily view). */
    now: Instant? = null
) {
    val durationMinutes = event.details?.durationMinutes
    val startTimeStr = if (event.isDateOnly24Hour) "All Day" else formatTimeAmPm(event.startMillis, zone)
    val endMillis = event.endMillis
    // Bitiş on a later day: the card keeps its own duration ("60 min") and names the last day under End.
    val lastDay = event.lastDay(zone).takeIf { event.isMultiDay(zone) }
    val endTimeStr = when {
        !event.isDateOnly24Hour && endMillis != null -> formatTimeAmPm(endMillis, zone)
        lastDay != null && !event.isDateOnly24Hour && event.spanEndMillis != null -> formatTimeAmPm(event.spanEndMillis, zone)
        else -> "—"
    }
    val endLabel = lastDay?.let { "Son gün ${it.dayOfMonth} ${it.format(java.time.format.DateTimeFormatter.ofPattern("MMM", PlannerLocale))}" } ?: "End"
    val durationStr = if (event.isDateOnly24Hour) "24h" else durationMinutes?.let { "${it} min" } ?: "Süre ekle"
    val surface = cardColor
    val ink = if (cardColor.luminance() > 0.45f) PlannerInk else Color.White

    val span = if (!event.isDateOnly24Hour && endMillis != null && endMillis > event.startMillis) endMillis - event.startMillis else 0L
    val targetProgress = if (span > 0L && now != null) {
        ((now.toEpochMilli() - event.startMillis).toFloat() / span).coerceIn(0f, 1f)
    } else 0f
    val progress = animateFloatAsState(targetProgress, tween(600), label = "next24-progress").value

    Column(
        Modifier
            .fillMaxWidth()
            .plannerEventMotion(event, "next24", onClick)
            .clip(RoundedCornerShape(32.dp))
            .background(surface)
            .padding(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val reminderStyle = if (event.reminder.isCompleted) {
                RealType.editorialItemCompleted.copy(color = ink.copy(alpha = 0.5f))
            } else {
                RealType.editorialItem.copy(color = ink)
            }
            Text(
                event.reminder.text,
                style = reminderStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            // Only the owner's avatar: other people / "+N" no longer appear on 24 hour cards.
            ProfileAvatar(Modifier.size(32.dp).border(1.5.dp, Color.White.copy(alpha = 0.70f), CircleShape))
        }

        Spacer(Modifier.height(26.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    startTimeStr,
                    color = ink,
                    fontSize = 22.sp,
                    letterSpacing = (-0.6).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text("Start", color = ink.copy(alpha = 0.55f), fontSize = 12.sp)
            }

            val pillModifier = if (onToggleDuration != null) {
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleDuration
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            } else {
                Modifier
            }

            Box(pillModifier, contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ink.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(durationStr, color = ink.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1)
                }
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    endTimeStr,
                    color = ink,
                    fontSize = 22.sp,
                    letterSpacing = (-0.6).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(endLabel, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
            }
        }

        if (span > 0L && now != null) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(ink))
            }
        }
    }
}

@Composable
internal fun PlannerTaskCard(
    event: PlannerEvent,
    index: Int,
    zone: ZoneId,
    ownerName: String,
    onClick: () -> Unit,
    onEditTime: (() -> Unit)? = null,
    onEditDuration: (() -> Unit)? = null,
    editorExpanded: Boolean = false,
    onToggleEditor: (() -> Unit)? = null
) {
    val color = PlannerPastels[event.details?.colorIndex ?: (index % PlannerPastels.size)]
    val editorArrowRotation = animateFloatAsState(
        targetValue = if (editorExpanded) -90f else 90f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "plannerEditorArrow"
    ).value
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color)
        .clickable(role = Role.Button, onClick = onClick).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(event.reminder.text, color = PlannerInk, fontSize = 25.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                textDecoration = if (event.reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ProfileAvatar(Modifier.size(32.dp).border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape))
        }
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                Text(
                    if (event.isDateOnly24Hour) "24 hour" else Instant.ofEpochMilli(event.startMillis).atZone(zone).format(PlannerTimeFormat),
                    color = PlannerInk, fontSize = if (onToggleEditor != null) 17.sp else 21.sp
                )
                Text(
                    if (event.isDateOnly24Hour) "Takvim" else "Başlangıç",
                    color = PlannerInk.copy(alpha = 0.66f), fontSize = if (onToggleEditor != null) 9.sp else 11.sp
                )
            }
            if (event.details?.durationMinutes != null) {
                Text(
                    "${event.details.durationMinutes} dk",
                    color = PlannerInk.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    if (event.isDateOnly24Hour) "—" else event.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(PlannerTimeFormat) } ?: "—",
                    color = PlannerInk, fontSize = if (onToggleEditor != null) 17.sp else 21.sp
                )
                Text(
                    if (event.reminder.isCompleted) "Tamamlandı" else if (event.isDateOnly24Hour) "Saat yok" else "Bitiş",
                    color = PlannerInk.copy(alpha = 0.66f),
                    fontSize = if (onToggleEditor != null) 9.sp else 11.sp
                )
            }
        }
        if (onToggleEditor != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 26.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlannerInk.copy(alpha = if (editorExpanded) 0.22f else 0.13f))
                        .border(0.7.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onToggleEditor)
                        .semantics { contentDescription = if (editorExpanded) "Saat ve süre düzenleyicisini kapat" else "Saat ve süreyi düzenle" },
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(
                        ActionGlyph.ChevronRight,
                        Modifier.size(15.dp).rotate(editorArrowRotation),
                        PlannerInk.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlannerPeople(people: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), modifier = Modifier.semantics {
        contentDescription = people.joinToString(", ")
    }) {
        people.take(2).forEachIndexed { index, name ->
            Box(Modifier.size(32.dp).clip(CircleShape).background(if (index == 0) Color(0xFFE9E0D3) else Color(0xFF596361))
                .border(1.5.dp, Color.White.copy(alpha = 0.65f), CircleShape), contentAlignment = Alignment.Center) {
                Text(plannerInitials(name), fontSize = 11.sp, color = if (index == 0) PlannerInk else Color.White, fontWeight = FontWeight.Medium)
            }
        }
        if (people.size > 2) Box(Modifier.size(32.dp).clip(CircleShape).background(PlannerInk), contentAlignment = Alignment.Center) {
            Text("+${people.size - 2}", color = Color.White, fontSize = 10.sp)
        }
    }
}

