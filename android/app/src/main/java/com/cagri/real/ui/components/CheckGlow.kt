package com.cagri.real.ui.components

import com.cagri.real.i18n.I18n
import com.cagri.real.i18n.t
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import com.cagri.real.data.CheckRequest
import com.cagri.real.data.ReminderItem
import java.time.LocalDate

/**
 * Kontrol ettir: a card someone asked you to look at glows from its right edge, breathing slowly (2.8 s), until you
 * open it. "Seen" is kept on this phone per request, so a new request on the same card lights it again.
 */
object CheckRequests {
    val SelfColor = Color(0xFF6FD6FF)
    val OtherColor = Color(0xFFFFB86F)
    private const val PREFS = "real_check_seen"
    private const val KEEP = 400
    private val seen = mutableStateOf<Set<String>?>(null)

    private fun key(r: ReminderItem) = "${r.id}:${r.checkRequest?.at}"

    private fun seenSet(context: Context): Set<String> = seen.value ?: context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("seen", emptySet()).orEmpty().toSet().also { seen.value = it }

    /** Opening the card puts its light out (on this phone). */
    fun markSeen(context: Context, r: ReminderItem) {
        if (r.checkRequest == null) return
        val current = seenSet(context)
        val k = key(r)
        if (k in current) return
        val next = (current + k).toList().takeLast(KEEP).toSet()
        seen.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("seen", next).apply()
    }

    /** The light this viewer should see on [r], or null: the card's category colour, else blue / amber. */
    fun glow(context: Context, r: ReminderItem, myUid: String?, categoryColor: Color? = null, today: LocalDate = LocalDate.now()): Color? {
        val c = r.checkRequest ?: return null
        if (r.isCompleted || r.isDivider) return null
        if (c.startEpochDay != null && today.toEpochDay() < c.startEpochDay) return null
        val forMe = if (r.sharedCardId == null) CheckRequest.ME in c.to || (myUid != null && myUid in c.to)
            else myUid != null && (myUid in c.to || (CheckRequest.EVERYONE in c.to && c.from != myUid) ||
                (CheckRequest.ME in c.to && c.from == myUid))
        if (!forMe || key(r) in seenSet(context)) return null
        // The light blinks in the card's category colour (a colour picked in older versions is ignored).
        categoryColor?.let { return it }
        val self = c.from == null || c.from == myUid || (r.sharedCardId == null && CheckRequest.ME in c.to)
        return if (self) SelfColor else OtherColor
    }
}

/** The breathing light along the card's right edge (fills ~34 % of the width, 12 % ↔ 55 %). */
@Composable
fun Modifier.checkGlow(color: Color?, reduceAnimations: Boolean): Modifier {
    if (color == null) return this
    val alpha = if (reduceAnimations) null else rememberInfiniteTransition(label = "check-glow").animateFloat(
        0.12f, 0.55f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "check-glow-alpha"
    )
    return drawWithContent {
        drawContent()
        val a = alpha?.value ?: 0.55f
        val start = size.width * (1f - 0.34f)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent, 0.62f to color.copy(alpha = 0.35f * a), 1f to color.copy(alpha = a),
                startX = start, endX = size.width
            ),
            topLeft = Offset(start, 0f), size = androidx.compose.ui.geometry.Size(size.width - start, size.height)
        )
    }
}

/** One person the request can go to ([CheckRequest.ME] for yourself, [CheckRequest.EVERYONE] for all members). */
data class CheckTarget(val key: String, val label: String)

/**
 * Kontrol ettir (from the selection bar): who should look and from when. The light takes the card's category colour
 * (blue for yourself / amber for someone else when it has none). [canClear]: some selected card already has a request ("Kontrolü kaldır").
 */
@Composable
fun CheckRequestSheet(targets: List<CheckTarget>, canClear: Boolean, onDismiss: () -> Unit,
                      onSave: (target: String, startEpochDay: Long?, colorArgb: Int?) -> Unit, onClear: () -> Unit) {
    var target by androidx.compose.runtime.remember { mutableStateOf(targets.first().key) }
    var startDay by androidx.compose.runtime.remember { mutableStateOf<Long?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val dayFormat = java.time.format.DateTimeFormatter.ofPattern("d MMM", I18n.locale)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().background(Color(0xFF16161A), androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Text(t("Kontrol ettir"), color = Color(0xFFF5F5F8), fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            SheetLabel(t("Kime"))
            ChipRow { targets.forEach { t -> SheetChip(t.label, target == t.key) { target = t.key } } }
            SheetLabel(t("Ne zaman yansın"))
            ChipRow {
                SheetChip(t("Şimdi"), startDay == null) { startDay = null }
                SheetChip(startDay?.let { t("Tarih · {0}", java.time.LocalDate.ofEpochDay(it).format(dayFormat)) } ?: t("Tarih seç"), startDay != null) {
                    val today = java.time.LocalDate.now()
                    android.app.DatePickerDialog(context, { _, y, m, d -> startDay = java.time.LocalDate.of(y, m + 1, d).toEpochDay() },
                        today.year, today.monthValue - 1, today.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                }
            }
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                SheetButton(if (canClear) t("Kontrolü kaldır") else t("Vazgeç"), filled = false, modifier = Modifier.weight(1f)) { if (canClear) onClear() else onDismiss() }
                SheetButton(t("Işığı yak"), filled = true, modifier = Modifier.weight(1f)) { onSave(target, startDay, null) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) = androidx.compose.material3.Text(text.uppercase(com.cagri.real.i18n.I18n.locale), color = Color(0xFF8C8C95),
    fontSize = 11.sp, letterSpacing = 0.5.sp)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) = androidx.compose.foundation.layout.FlowRow(
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { content() }

@Composable
private fun SheetChip(label: String, selected: Boolean, swatch: Brush? = null, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(19.dp)
    androidx.compose.foundation.layout.Row(
        Modifier.height(38.dp).clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f), shape)
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        swatch?.let { androidx.compose.foundation.layout.Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(it)) }
        if (label.isNotEmpty()) androidx.compose.material3.Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp)
    androidx.compose.foundation.layout.Box(modifier.height(46.dp).clip(shape)
        .background(if (filled) Color.White else Color.White.copy(alpha = 0.08f))
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text(text, color = if (filled) Color.Black else Color(0xFFE9E9EF), fontSize = 14.sp,
            fontWeight = if (filled) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}
