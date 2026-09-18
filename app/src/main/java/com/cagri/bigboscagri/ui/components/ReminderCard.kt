package com.cagri.bigboscagri.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.bigboscagri.data.ReminderItem
import com.cagri.bigboscagri.ui.reminderDate

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReminderCard(
    reminder: ReminderItem,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    reduceAnimations: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    val accentBrush = remember(reminder.accentStart, reminder.accentEnd, reminder.isCompleted) {
        reminderAccentBrush(reminder.accentStart, reminder.accentEnd, reminder.isCompleted)
    }
    val textColor by animateColorAsState(
        if (reminder.isCompleted) Color(0xFF808089) else Color(0xFFF3F3F5),
        if (reduceAnimations) snap() else tween(200),
        label = "completionColor"
    )

    val sharedBoundsModifier = if (!reduceAnimations && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "reminder-card-${reminder.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(320, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)) },
                clipInOverlayDuringTransition = OverlayClip(shape)
            )
        }
    } else Modifier

    val borderColor = when {
        isSelected -> Color(0xFF94B8F7).copy(alpha = 0.65f)
        accentBrush != null -> Color.White.copy(alpha = 0.10f)
        else -> Color(0xFF222225)
    }
    val borderWidth = if (isSelected) 1.25.dp else 0.75.dp
    val selectionOverlay = if (isSelected) Color(0xFF94B8F7).copy(alpha = 0.07f) else Color.Transparent

    Row(
        modifier
            .then(sharedBoundsModifier)
            .fillMaxWidth()
            .clip(shape)
            .then(if (accentBrush == null) Modifier.background(Color(0xFF141416)) else Modifier.background(accentBrush))
            .then(if (isSelected) Modifier.background(selectionOverlay) else Modifier)
            .border(borderWidth, borderColor, shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç") else "Hatırlatıcıyı aç",
                onLongClickLabel = if (!isSelectionMode) "Çoklu seçim" else null,
                onLongClick = {
                    if (!isSelectionMode) onLongClick() else onSelectToggle()
                },
                onClick = {
                    if (isSelectionMode) onSelectToggle() else onOpen()
                }
            )
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .then(
                    if (isSelectionMode) {
                        Modifier.clickable(role = Role.Checkbox, onClick = onSelectToggle)
                    } else {
                        Modifier.toggleable(value = reminder.isCompleted, role = Role.Checkbox, onValueChange = { onToggle() })
                    }
                )
                .semantics {
                    contentDescription = if (isSelectionMode) (if (isSelected) "Seçimi kaldır" else "Seç")
                        else if (reminder.isCompleted) "Tamamlamayı geri al" else "Tamamla"
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(20.dp)
                    .background(if (reminder.isCompleted) Color(0xFF808089) else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (reminder.isCompleted) Color.Transparent else Color(0xFF626269), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (reminder.isCompleted) ActionIcon(ActionGlyph.Check, Modifier.size(14.dp), Color(0xFF1B1B1F))
            }
        }
        Column(Modifier.weight(1f).padding(top = 11.dp, bottom = 10.dp, start = 2.dp)) {
            Text(
                reminder.text, color = textColor, fontSize = 16.sp, lineHeight = 23.sp,
                textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (!reminder.isCompleted) reminder.alertAt?.let {
                val isOverdue = it < System.currentTimeMillis()
                val dateColor = if (isOverdue) Color(0xFFF87171) else Color(0xFF94B8F7)
                val alertText = if (reminder.hasRepeat) "${reminderDate(it)} • ${reminder.repeatRule.displayName}" else reminderDate(it)
                Text(alertText, color = dateColor, fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp))
            }
            reminder.completedAt?.let {
                Text("Tamamlandı: ${reminderDate(it)}", color = Color(0xFF92929A), fontSize = 11.sp,
                    lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        if (reminder.isImportant) {
            ActionIcon(ActionGlyph.Star, Modifier.padding(start = 8.dp, top = 15.dp).size(14.dp),
                color = Color(0xFFE8C975), filled = true)
        }
        if (isSelectionMode) {
            Box(
                Modifier
                    .padding(start = 10.dp, end = 2.dp, top = 11.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) Color(0xFF94B8F7) else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) Color(0xFF94B8F7) else Color.White.copy(alpha = 0.30f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    ActionIcon(ActionGlyph.Check, Modifier.size(13.dp), Color(0xFF0F172A))
                }
            }
        }
    }
}
