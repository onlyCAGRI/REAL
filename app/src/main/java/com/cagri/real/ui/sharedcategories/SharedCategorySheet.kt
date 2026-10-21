package com.cagri.real.ui.sharedcategories

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.data.CustomCategory
import com.cagri.real.sharedcategories.SharedCategorySync
import com.cagri.real.sharedcategories.SharedMember
import com.cagri.real.ui.components.RealMotion
import com.cagri.real.ui.components.RealSheetHandle
import com.cagri.real.ui.components.RealSpinner
import com.cagri.real.ui.components.realSheetSurface
import com.cagri.real.ui.connections.ConnectionAvatar
import com.cagri.real.ui.connections.ConnectionPeoplePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Ortak kategori → Kişiler (the friend button in the category's ••• menu). The owner adds people from Kişilerim,
 * gives each Tam (add, edit, complete, delete) or Sınırlı (view only) access and can remove them; a member sees who is
 * in it and can leave. Sharing the first time moves the cards to the server behind a "Kategori paylaşılıyor" screen;
 * removing the last person brings them back ("Kategori sana dönüyor").
 */
@Composable
internal fun SharedCategorySheet(
    category: CustomCategory,
    /** Categories filled from a phone calendar are never shared. */
    calendarLinked: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<SharedMember>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) } // full-screen "Kategori paylaşılıyor" text
    var movedCount by remember { mutableIntStateOf(0) }
    var newAccess by remember { mutableStateOf("full") }
    var leaving by remember { mutableStateOf(false) }
    // The category as it is now (it becomes shared during this sheet).
    var current by remember { mutableStateOf(category) }
    val isOwner = current.sharedId == null || current.sharedRole == "owner"

    suspend fun refresh() {
        members = if (current.sharedId == null) emptyList() else SharedCategorySync.members(current)
    }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { error = e.message ?: "İşlem tamamlanamadı." }
            finally { busy = false; working = null }
        }
    }
    LaunchedEffect(current.sharedId) { runCatching { refresh() }.onFailure { error = it.message } }

    Dialog(onDismissRequest = { if (working == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        BackHandler(enabled = working != null) {}
        val sheetIn = remember { Animatable(0f) }
        LaunchedEffect(Unit) { sheetIn.animateTo(1f, tween(420, easing = RealMotion.Emphasized)) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f * sheetIn.value))
                .clickable(remember { MutableInteractionSource() }, indication = null) { if (working == null) onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 700.dp)
                    .graphicsLayer { translationY = size.height * (1f - sheetIn.value) }
                    .realSheetSurface().navigationBarsPadding().imePadding()
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RealSheetHandle()
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Kişiler", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    if (calendarLinked && current.sharedId == null) {
                        Text("Takvime bağlı kategoriler paylaşılamaz.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        return@Column
                    }

                    // Everyone in the category.
                    members?.takeIf { it.isNotEmpty() }?.let { list ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f))) {
                            list.forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ConnectionAvatar(m.name, key = m.uid, size = 38.dp, pictureVersion = m.pictureVersion)
                                    Text(m.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    when {
                                        m.isOwner -> Text("Sahibi", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                        isOwner -> {
                                            AccessToggle(m.access) { access -> act {
                                                SharedCategorySync.setAccess(current, m.uid, access); refresh()
                                            } }
                                            Text("Çıkar", color = Color(0xFFFFB4AB), fontSize = 13.sp,
                                                modifier = Modifier.padding(start = 8.dp).clip(CircleShape).clickable(enabled = !busy) {
                                                    act {
                                                        val empty = SharedCategorySync.removeMember(current, m.uid)
                                                        if (empty) {
                                                            working = "Kategori sana dönüyor"
                                                            SharedCategorySync.release(context, current)
                                                            current = current.copy(sharedId = null, sharedRole = null)
                                                        }
                                                        refresh()
                                                    }
                                                }.padding(horizontal = 8.dp, vertical = 6.dp))
                                        }
                                        else -> Text(if (m.access == "view") "Sınırlı" else "Tam", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (isOwner) {
                        if ((members?.count { !it.isOwner } ?: 0) < 10) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Yeni kişi", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                AccessToggle(newAccess) { newAccess = it }
                            }
                            ConnectionPeoplePicker(enabled = !busy, actionLabel = "Ekle") { person ->
                                if (members?.any { it.uid == person.uid } == true) return@ConnectionPeoplePicker
                                busy = true; error = null
                                try {
                                    if (current.sharedId == null) {
                                        working = "Kategori paylaşılıyor"; movedCount = 0
                                        SharedCategorySync.share(context, current, person.uid, newAccess) { movedCount = it }
                                        current = com.cagri.real.data.CategoryRepository(context).load().firstOrNull { it.id == current.id } ?: current
                                    } else SharedCategorySync.addMember(current, person.uid, newAccess)
                                    refresh()
                                } catch (c: CancellationException) { throw c }
                                catch (e: Exception) { error = e.message ?: "Kişi eklenemedi." }
                                finally { busy = false; working = null }
                            }
                        } else {
                            Text("Bir kategoride en fazla 10 kişi olabilir.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    } else if (!leaving) {
                        Text("Kategoriden ayrıl", color = Color(0xFFFFB4AB), fontSize = 15.sp,
                            modifier = Modifier.clip(CircleShape).clickable(enabled = !busy) { leaving = true }.padding(vertical = 8.dp))
                    } else {
                        Text("Kartlar kategoride kalır.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SheetButton("Kopyasını sakla", filled = true, enabled = !busy, modifier = Modifier.weight(1f)) {
                                act { SharedCategorySync.leave(context, current, keepCopy = true); onDismiss() }
                            }
                            SheetButton("Benden kaldır", filled = false, enabled = !busy, modifier = Modifier.weight(1f)) {
                                act { SharedCategorySync.leave(context, current, keepCopy = false); onDismiss() }
                            }
                        }
                    }
                    error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 13.sp) }
                }
            }
            // "Kategori paylaşılıyor" / "Kategori sana dönüyor": like installing an update, nothing else to touch.
            working?.let { text ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RealSpinner(Modifier.size(34.dp), color = Color.White, strokeWidth = 2.5.dp)
                        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        if (movedCount > 0) {
                            com.cagri.real.ui.components.RollingNumberText(movedCount, color = Color.White.copy(alpha = 0.6f),
                                style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
                        }
                    }
                }
            }
        }
    }
}

/** Tam / Sınırlı. */
@Composable
private fun AccessToggle(access: String, onChange: (String) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).padding(3.dp)) {
        listOf("full" to "Tam", "view" to "Sınırlı").forEach { (key, label) ->
            val on = access == key
            Text(label, color = if (on) Color.Black else Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                    .clickable { if (!on) onChange(key) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
        .background(if (filled) Color.White else Color.White.copy(alpha = 0.07f))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
