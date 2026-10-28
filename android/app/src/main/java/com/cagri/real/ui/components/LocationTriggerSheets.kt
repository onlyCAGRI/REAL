package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.data.LocationTrigger
import com.cagri.real.data.ReminderLocation

private val SheetBg = Color(0xFF151519)
private val RowBg = Color(0xFF1D1D21)
private val RowBorder = Color(0xFF303037)
private val Muted = Color(0xFF92929B)
private val Accent = Color(0xFF91B9FF)

/** Bottom-anchored dark panel shared by the two sheets. */
@Composable
private fun BottomPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
            contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding()
                    .clip(RoundedCornerShape(30.dp)).background(SheetBg)
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) { content() }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, glyph: ActionGlyph, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RowBg)
            .border(0.8.dp, RowBorder, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(glyph, Modifier.size(22.dp), Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF4F4F6), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Composer pin: plain place on the card, or a reminder that fires at the place. */
@Composable
fun LocationModeSheet(visible: Boolean, onPlain: () -> Unit, onTrigger: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    BottomPanel(onDismiss) {
        Text(t("Konum"), color = Color(0xFFF4F4F6), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        ChoiceRow(t("Konum ekle"), t("Yer karta eklenir, haritada görünür."), ActionGlyph.Pin, onPlain)
        Spacer(Modifier.height(10.dp))
        ChoiceRow(t("Konuma varınca hatırlat"), t("O yere varınca ya da oradan ayrılınca bir kere hatırlatır."), ActionGlyph.Bell, onTrigger)
    }
}

/** After the place is picked: arrive or leave, and how close counts. */
@Composable
fun LocationTriggerSheet(
    place: ReminderLocation?,
    initial: LocationTrigger?,
    onSave: (LocationTrigger) -> Unit,
    onCancel: () -> Unit
) {
    if (place == null) return
    val lat = place.latitude
    val lng = place.longitude
    var onExit by remember(place) { mutableStateOf(initial?.onExit ?: false) }
    var radius by remember(place) { mutableIntStateOf(initial?.radiusMeters ?: LocationTrigger.DEFAULT_RADIUS) }
    // "Ayrılınca" leaves out the circles small enough for GPS drift to cross while the phone lies still.
    val radii = if (onExit) LocationTrigger.RADIUS_CHOICES.filter { it >= LocationTrigger.EXIT_MIN_RADIUS } else LocationTrigger.RADIUS_CHOICES
    if (onExit && radius < LocationTrigger.EXIT_MIN_RADIUS) radius = LocationTrigger.EXIT_MIN_RADIUS
    BottomPanel(onCancel) {
        Text(place.placeName, color = Color(0xFFF4F4F6), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text(t("Bir kere hatırlatır, sonra konum takibi biter."), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Segmented(listOf(t("Varınca"), t("Ayrılınca")), if (onExit) 1 else 0) { onExit = it == 1 }
        Spacer(Modifier.height(14.dp))
        Text(t("Mesafe"), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Segmented(radii.map { "$it m" }, radii.indexOf(radius).coerceAtLeast(0)) {
            radius = radii[it]
        }
        if (onExit) {
            Spacer(Modifier.height(10.dp))
            ExitOutsideNote(lat, lng, radius, Muted)
        }
        Spacer(Modifier.height(20.dp))
        val ready = lat != null && lng != null
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
                .background(if (ready) Color(0xFFF3F3F5) else Color(0xFF4A4A50))
                .clickable(enabled = ready, role = Role.Button) {
                    onSave(LocationTrigger(place.placeName, lat!!, lng!!, radius, onExit))
                }
                .semantics { contentDescription = t("Hatırlatmayı kaydet") },
            contentAlignment = Alignment.Center
        ) {
            Text(if (ready) t("Kaydet") else t("Bu yerin koordinatı yok"), color = if (ready) Color(0xFF111114) else Color(0xFF9A9AA2),
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RowBg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color(0xFFF4F4F6) else Color(0xFFB1B1BA), fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

/**
 * "Ayrılınca" set while the phone is already outside the place: it only fires after coming in and leaving again,
 * so say so. Uses the cached position only; nothing is shown without one.
 */
@Composable
internal fun ExitOutsideNote(lat: Double?, lng: Double?, radiusMeters: Int, color: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var outside by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(lat, lng, radiusMeters) {
        if (lat == null || lng == null) { outside = false; return@LaunchedEffect }
        val here = com.cagri.real.notifications.LocationReminders.lastKnown(context) ?: run { outside = false; return@LaunchedEffect }
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(here.first, here.second, lat, lng, distance)
        outside = distance[0] > maxOf(radiusMeters, LocationTrigger.EXIT_MIN_RADIUS)
    }
    if (outside) Text(t("Şu an bu yerin dışındasın: önce gelip sonra ayrıldığında hatırlatır."), color = color, fontSize = 12.sp)
}

/** Composer chip text: "Market · varınca". */
fun LocationTrigger.label(): String = "$placeName · ${if (onExit) t("ayrılınca") else t("varınca")}"
