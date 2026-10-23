package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.ReminderItem
import com.cagri.real.ui.motion.RealEntranceMotion
import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.theme.RealFont

private val Mono = FontFamily.Monospace
private val Hairline = Color.White.copy(alpha = 0.10f)

/** Bir seferde gösterilen satır sayısı; fazlası "DAHA FAZLA" ile 30'ar 30'ar açılır (liste kaydırma içinde Column). */
private const val PAGE = 30

/** Günlük satırları 55 ms arayla kayarak gelir; çok uzun listede gecikme 12. satırda sabitlenir. */
private const val ROW_STAGGER_MS = 55
private const val ROW_STAGGER_CAP = 12

/**
 * Yaşam içindeki "Günlüklerim" modunun gövdesi (hero kutunun altı). [journals] yalnız günlüğü olan kartlardır
 * (`journal != null`); en yeniden eskiye sıralanır. Bağımsız günlüklerin ("Günlük · …") başlığı gösterilmez:
 * satır yalnız tarih etiketi + metindir. Dokunma [onOpen] ile kartın detayını açar (ReminderScreen).
 */
@Composable
internal fun YasamJournalList(
    journals: List<ReminderItem>,
    entrance: RealEntranceMotion?,
    reduceAnimations: Boolean,
    onWrite: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Giriş saatinin kayması (ms): hero cümlesinin kelimeleri önce, liste sonra açılır. */
    baseMs: Int = 0
) {
    val sorted = remember(journals) { journals.sortedByDescending { it.journal?.createdAt ?: it.createdAt } }
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }
    Column(modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp)) {
        if (sorted.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .then(if (entrance != null) Modifier.realEntrance(entrance, baseMs, 460, slideY = 14.dp) else Modifier)
            ) {
                Text(
                    t("Henüz günlük yok"), color = Color.White.copy(alpha = 0.85f), fontSize = 19.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.Light, fontFamily = YasamThin, modifier = Modifier.padding(vertical = 12.dp)
                )
                YasamTextAction(t("BUGÜN YAZ  +"), YasamJournalTone, onWrite)
            }
        } else {
            Box(if (entrance != null) Modifier.realEntrance(entrance, baseMs, 460, slideY = 14.dp) else Modifier) {
                YasamTextAction(t("BUGÜN YAZ  +"), YasamJournalTone, onWrite)
            }
            sorted.take(shown).forEachIndexed { index, reminder ->
                val journal = reminder.journal ?: return@forEachIndexed
                val whenLabel = yasamJournalWhenLabel(journal.createdAt)
                key(reminder.id) {
                    val text = journal.text.trim()
                    val moodLabel = journal.mood?.label
                    val rowModifier =
                        if (entrance != null) Modifier.realEntrance(
                            entrance, baseMs + (index + 1).coerceAtMost(ROW_STAGGER_CAP) * ROW_STAGGER_MS, 460, slideY = 14.dp
                        ) else Modifier
                    Column(rowModifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable(role = Role.Button) { onOpen(reminder.id) }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = buildString {
                                        append(whenLabel.replace("·", ""))
                                        if (moodLabel != null) append(", ").append(moodLabel)
                                        if (text.isNotEmpty()) append(". ").append(text)
                                        append(t(". Günlüğü aç"))
                                    }
                                }
                                .padding(vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(whenLabel, color = YasamMuted, fontSize = 10.sp, fontFamily = Mono, letterSpacing = 1.2.sp)
                                if (journal.mood != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(yasamMoodTone(journal.mood)))
                                }
                            }
                            if (text.isNotEmpty()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 19.sp,
                                    fontWeight = FontWeight.Light, fontFamily = YasamThin, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            if (sorted.size > shown) YasamTextAction(t("DAHA FAZLA GÖSTER  ›"), YasamMuted, { shown += PAGE })
        }
    }
}
