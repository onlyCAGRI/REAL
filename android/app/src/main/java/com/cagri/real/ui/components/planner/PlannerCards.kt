package com.cagri.real.ui.components.planner

import com.cagri.real.i18n.t
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
            PlannerWorldClock(now, "Europe/Istanbul", t("Türkiye"))
            PlannerWorldClock(now, "Europe/Berlin", t("Almanya"))
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
    val endLabel = lastDay?.let { t("Son gün {0} {1}", it.dayOfMonth, it.format(java.time.format.DateTimeFormatter.ofPattern("MMM", PlannerLocale))) } ?: t("Bitiş")
    val durationStr = if (event.isDateOnly24Hour) "24h" else durationMinutes?.let { t("{0} min", it) } ?: t("Süre ekle")
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
