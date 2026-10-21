package com.cagri.real.ui.announcements

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.R
import com.cagri.real.platform.android.announcements.*
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.ui.components.LiquidGlassTokens
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.theme.RealType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable
private fun Inbox(uid: String, reduceAnimations: Boolean, onAdd: suspend (String) -> Long?) {
    val context = LocalContext.current
    val repo = remember(uid) { AnnouncementRepository(context, uid) }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RealAnnouncement>>(emptyList()) }
    var opened by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RealAnnouncement?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }
    var addFailed by remember { mutableStateOf(false) }
    LaunchedEffect(repo, owner, refreshKey) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            try {
                entries = repo.cached()
                entries = repo.refresh()
                failed = false
                selected = selected?.let { current -> entries.find { it.id == current.id } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { loading = false }
            awaitCancellation()
        }
    }
    val unread = entries.count { !it.read }
    val description = stringResource(R.string.real_news_unread, unread)
    if (entries.isNotEmpty()) Box(Modifier.size(44.dp).clip(CircleShape)
        .background(LiquidGlassTokens.staticTop)
        .border(.7.dp, LiquidGlassTokens.edgeTop, CircleShape)
        .clickable { opened = true }.semantics { contentDescription = description }, Alignment.Center) {
        Text(if (unread > 0) unread.coerceAtMost(99).toString() else entries.size.coerceAtMost(99).toString(),
            color = if (unread > 0) Color(0xFFB6CEFF) else Color.White.copy(alpha = .65f), style = RealType.cardTitle)
    }
    if (opened) {
        val reduce = reduceAnimations || LocalLiquidReduceMotion.current
        val show: (RealAnnouncement) -> Unit = { item ->
            selected = item; added = false; addFailed = false
            entries = entries.map { if (it.id == item.id) it.copy(read = true) else it }
            scope.launch {
                try {
                    val wasAdded = repo.added(item.id)
                    if (selected?.id == item.id) added = wasAdded
                    if (!item.read) repo.read(item.id)
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true }
            }
        }
        Dialog(onDismissRequest = { if (!adding) { opened = false; selected = null } },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(32.dp)).newsSurface().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.real_news_title), style = RealType.cardTitle, color = Color.White)
                        if (selected != null) com.cagri.real.ui.components.RealBackButton(
                            { selected = null }, description = stringResource(R.string.real_news_back), enabled = !adding
                        ) else ConnectionPill(stringResource(R.string.real_news_close), { opened = false }, enabled = !adding)
                    }
                    Spacer(Modifier.height(16.dp))
                    val current = selected
                    if (current != null) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(current.title, style = RealType.cardTitle, color = Color.White)
                            Text(current.body, style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .85f))
                            if (addFailed) Text(stringResource(R.string.real_news_add_failed), color = Color(0xFFFFB9B9))
                        }
                        Spacer(Modifier.height(18.dp))
                        ConnectionPill(stringResource(if (added) R.string.real_news_added else R.string.real_news_add), {
                            if (!adding && !added && AndroidBackend.auth.currentUser()?.id == uid) {
                                adding = true
                                scope.launch {
                                    try {
                                        if (repo.added(current.id)) added = true
                                        else {
                                            val id = onAdd(current.title + "\n\n" + current.body)
                                            if (id != null) { repo.rememberAdded(current.id); added = true } else addFailed = true
                                        }
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { addFailed = true }
                                    finally { adding = false }
                                }
                            }
                        }, enabled = !adding && !added)
                    } else {
                        if (failed) Text(stringResource(R.string.real_news_offline), color = Color.White.copy(alpha = .6f), style = RealType.cardMetaPrimary)
                        if (entries.isEmpty()) Text(stringResource(if (loading) R.string.real_news_loading else R.string.real_news_empty), color = Color.White.copy(alpha = .6f))
                        else AnnouncementStack(entries, expanded, reduce, { expanded = !expanded }, show)
                        Spacer(Modifier.height(12.dp))
                        ConnectionPill(stringResource(R.string.real_news_refresh), { refreshKey++ }, enabled = !loading)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AnnouncementStack(entries: List<RealAnnouncement>, expanded: Boolean,
    reduce: Boolean, onExpand: () -> Unit, onOpen: (RealAnnouncement) -> Unit) {
    // Native Compose spring, no WebView/JS, no continuous animation or background timer.
    val reveal = remember { Animatable(0f) }
    var showingList by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, reduce) {
        if (expanded) showingList = true
        if (reduce) reveal.snapTo(if (expanded) 1f else 0f)
        else reveal.animateTo(if (expanded) 1f else 0f, spring(dampingRatio = .9f, stiffness = 340f))
        if (!expanded) showingList = false
    }
    if (!showingList) {
        Box(Modifier.fillMaxWidth().height(154.dp).clickable(onClick = onExpand)) {
            for (i in entries.take(3).indices.reversed()) {
                val item = entries[i]
                Box(Modifier.fillMaxWidth().padding(top = (i * 10).dp)
                    .graphicsLayer { scaleX = 1f - i * .045f; alpha = 1f - i * .22f }
                    .height(124.dp).clip(RoundedCornerShape(24.dp)).newsSurface().padding(18.dp)) {
                    if (i == 0) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, color = Color.White, style = RealType.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.real_news_expand, entries.size), color = Color.White.copy(alpha = .55f), style = RealType.cardMetaPrimary)
                    }
                }
            }
        }
    } else {
        ConnectionPill(stringResource(R.string.real_news_collapse), onExpand)
        LazyColumn(Modifier.weight(1f, fill = false).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    alpha = reveal.value.coerceIn(0f, 1f)
                    translationY = (1f - reveal.value) * -24.dp.toPx()
                    scaleX = .96f + .04f * reveal.value
                }.clip(RoundedCornerShape(24.dp)).newsSurface().clickable { onOpen(item) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!item.read) Box(Modifier.size(6.dp).background(Color(0xFFB6CEFF), CircleShape))
                        Text(item.title, style = RealType.cardTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(item.body, style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
private fun Modifier.newsSurface() = background(Brush.verticalGradient(listOf(LiquidGlassTokens.fallbackTop, LiquidGlassTokens.fallbackBottom)))
    .border(.7.dp, Brush.verticalGradient(listOf(LiquidGlassTokens.edgeTop, LiquidGlassTokens.contentEdgeBottom)), RoundedCornerShape(24.dp))
