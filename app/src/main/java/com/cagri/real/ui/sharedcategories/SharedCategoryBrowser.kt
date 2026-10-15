package com.cagri.real.ui.sharedcategories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.sharedcategories.*
import com.cagri.real.ui.connections.*
import com.cagri.real.ui.components.*
import com.cagri.real.ui.components.planner.*
import com.cagri.real.ui.DesignStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared view is isolated from MainActivity reminders and from the owner's local numeric IDs. */
@Composable
fun SharedCategoryBrowser(reduceAnimations: Boolean, onDismiss: () -> Unit) {
    val (dailyView, _) = rememberPlannerDailyViewPreference()
    val context = LocalContext.current
    val user = currentConnectionsUser()
    val api = remember(user?.id) { user?.let { SharedCategoryApi(context.applicationContext, it.id) } }
    val scope = rememberCoroutineScope()
    var items by remember(api) { mutableStateOf<List<SharedCategory>>(emptyList()) }
    var detail by remember(api) { mutableStateOf<SharedCategoryDetail?>(null) }
    var card by remember(api) { mutableStateOf<SharedCategoryGoal?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var showMembers by remember { mutableStateOf(false) }
    suspend fun load() { items = checkNotNull(api).list().filterNot { it.isOwner } }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Kategori yüklenemedi." }
            finally { busy = false }
        }
    }
    LaunchedEffect(api) {
        if (api == null) return@LaunchedEffect
        items = api.cachedList().filterNot { it.isOwner }
        busy = true
        try { load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message }
        finally { busy = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { when { card != null -> card = null; detail != null -> detail = null; else -> onDismiss() } }
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            val current = detail
            if (current == null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        com.cagri.real.ui.components.RealBackButton(onDismiss)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { action { load() } }, enabled = !busy) { Text("Yenile", color = Color.White) }
                    }
                    Text("Paylaşılan kategoriler", style = com.cagri.real.ui.theme.RealType.editorialSection, color = Color.White)
                    if (items.isEmpty() && !busy) Text("Henüz bir kategoriye eklenmedin.", color = Color.Gray)
                    items.forEach { item ->
                        ConnectionCard {
                            Text(item.category.name, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { action { detail = checkNotNull(api).detail(item.id) } }.padding(12.dp))
                        }
                    }
                }
            } else {
                val today = LocalDate.now(); val zone = ZoneId.systemDefault()
                val ui = remember(current, today) {
                    val activity = List(7) { index ->
                        val day = today.minusDays((today.dayOfWeek.value - 1 - index).toLong())
                        current.goals.count { it.card.completedAt?.let { at -> Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == day } == true }
                    }
                    PlannerCategoryDetailUi(
                        categoryId = current.item.category.id, name = current.item.category.name,
                        dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d · yyyy", Locale.ENGLISH)),
                        accent = plannerCategoryDetailAccent(current.item.category),
                        goals = current.goals.sortedBy { it.card.createdAt }.map { goal ->
                            val r = goal.card; val total = r.checklistItems.size; val done = r.checklistItems.count { it.isCompleted }
                            PlannerGoalUi(r.id, r.text, if (total > 0) "$done/$total" else "", r.isCompleted,
                                if (!r.isCompleted && total > 0) done.toFloat() / total else null)
                        }, weekActivity = activity, todayIndex = today.dayOfWeek.value - 1,
                        members = current.members.map { it.name }, linkText = current.item.link
                    )
                }
                PlannerCategoryDetailScreen(ui = ui, reduceAnimations = reduceAnimations, readOnly = true,
                    categoryColorBackground = dailyView == PlannerDailyView.NewPlus,
                    onBack = { detail = null }, onDone = { detail = null },
                    icon = { m, c -> CategoryIconGlyph(CategoryIcon.fromKey(current.item.category.iconKey), m, c) },
                    onOpenGoal = { id -> card = current.goals.firstOrNull { it.card.id == id } },
                    onStartNext = { id -> card = current.goals.firstOrNull { it.card.id == id } },
                    onAddMember = { showMembers = true }, onSend = { showMembers = true })
                TextButton(onClick = { action { detail = checkNotNull(api).detail(current.item.id) } },
                    enabled = !busy, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp)) { Text("Yenile", color = Color.White) }
            }
            if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.align(androidx.compose.ui.Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
            error?.let { ConnectionNote(it, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp)) }
        }
        if (showMembers && detail != null) {
            val current = detail!!
            com.cagri.real.ui.components.RealAlertDialog(onDismissRequest = { showMembers = false }, containerColor = Color(0xFF151519),
                title = { Text("Members", color = Color.White) },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    current.members.forEach { Text(it.name + if (it.isOwner) " · Owner" else "", color = Color.White) }
                    Text("Üye ekleme ve çıkarma kategori sahibine aittir.", color = Color.Gray)
                } },
                confirmButton = { TextButton(onClick = { showMembers = false }) { Text("Bitti") } },
                dismissButton = { TextButton(onClick = { showMembers = false; action {
                    checkNotNull(api).remove(current.item.id, checkNotNull(user).id); detail = null; load()
                } }) { Text("Kategoriden ayrıl") } })
        }
        card?.let { SharedGoalDetailDialog(checkNotNull(api), checkNotNull(detail).item.id, it) { card = null } }
    }
}
