package com.cagri.real.ui.subscription

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.R
import com.cagri.real.ui.components.*
import com.cagri.real.ui.privacy.PrivacyDetailsDialog
import com.cagri.real.ui.privacy.privacyDocumentUrl
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.launch

/** Store-neutral presentation callbacks; Android's route must supply Google Play Billing. */
@Composable
internal fun RealProDialog(
    state: ProStoreUiState,
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    reduceAnimations: Boolean = false,
    onPurchase: ((offerKey: String) -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    accountContent: @Composable ColumnScope.() -> Unit = {}
) {
    val reduce = reduceAnimations || LocalLiquidReduceMotion.current
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    var closing by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var documentFailed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val privacyUrl = privacyDocumentUrl("/privacy")
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (!reduce) reveal.animateTo(0f, tween(200))
                latestDismiss()
            }
        }
    }
    LaunchedEffect(reduce) {
        if (reduce) reveal.snapTo(1f)
        else reveal.animateTo(1f, tween(420, easing = CubicBezierEasing(.16f, 1f, .3f, 1f)))
    }
    val selected = state.offers.singleOrNull { it.key == selectedKey }
        ?: state.offers.firstOrNull { it.isComplete }
    val purchasable = state.purchasableOffer(selected?.key)
    val status = stringResource(when {
        state.checkout == ProCheckoutState.OpeningStore -> R.string.real_pro_opening
        state.checkout == ProCheckoutState.Pending -> R.string.real_pro_pending
        state.checkout == ProCheckoutState.Verifying -> R.string.real_pro_verifying
        state.checkout == ProCheckoutState.Restoring -> R.string.real_pro_restoring
        state.verifiedProActive -> R.string.real_pro_active
        state.catalog == ProCatalogState.Loading -> R.string.real_pro_loading
        state.catalog == ProCatalogState.Unavailable -> R.string.real_pro_unavailable
        state.catalog == ProCatalogState.NotConfigured -> R.string.real_pro_soon
        else -> R.string.real_pro_store_note
    })
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight * .96f)
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 72.dp.toPx()
                }
                .clip(shape).background(LiquidGlassTokens.contentColor)
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f), radius = size.width))
                }
                .border(.75.dp, Brush.verticalGradient(listOf(
                    LiquidGlassTokens.contentEdgeTop, LiquidGlassTokens.contentEdgeBottom)), shape)) {
                val closeLabel = stringResource(R.string.real_pro_close)
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(Modifier.align(Alignment.Center).size(38.dp, 4.dp)
                        .background(Color.White.copy(alpha = .22f), CircleShape))
                    Box(Modifier.align(Alignment.CenterEnd).size(48.dp)
                        .semantics { contentDescription = closeLabel }
                        .clickable(role = Role.Button, onClick = dismiss), contentAlignment = Alignment.Center) {
                        ActionIcon(ActionGlyph.Close, Modifier.size(18.dp), color = Color.White.copy(alpha = .7f))
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    // Reuse the permission panel's chrome and R; no perpetual animation.
                    PermissionHero(PermissionGlyph.Brand, animate = false)
                    Text(stringResource(R.string.real_pro_title), style = RealType.editorialSection.copy(
                        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-.9).sp),
                        color = Color(0xFFF3F1F4), modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.real_pro_intro), style = RealType.cardBodyProminent,
                        color = Color(0xFF9B9BA4), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    ProFeature(ActionGlyph.Mic, R.string.real_pro_voice)
                    ProFeature(ActionGlyph.MoodGood, R.string.real_pro_mood)
                    ProFeature(ActionGlyph.Video, R.string.real_pro_discovery)
                    ProFeature(ActionGlyph.Calendar, R.string.real_pro_goals)
                    ProFeature(ActionGlyph.Theme, R.string.real_pro_colors)
                    ProAction(stringResource(R.string.real_pro_details), { showDetails = true })
                    Spacer(Modifier.height(8.dp))
                    if (state.catalog == ProCatalogState.Ready && !state.verifiedProActive) {
                        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.offers.filter { it.isComplete }.forEach { offer ->
                                val chosen = selected?.key == offer.key
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = if (chosen) .12f else .04f))
                                    .border(1.dp, Color.White.copy(alpha = if (chosen) .65f else .12f), RoundedCornerShape(20.dp))
                                    .selectable(selected = chosen, role = Role.RadioButton,
                                        enabled = state.checkout == ProCheckoutState.Idle,
                                        onClick = { selectedKey = offer.key }).padding(16.dp)) {
                                    Text(offer.title, style = RealType.cardTitle, color = Color.White)
                                    Spacer(Modifier.height(4.dp))
                                    Text(offer.priceText, style = RealType.cardBodyProminent, color = Color.White)
                                    Text(offer.termsText, style = RealType.cardMetaPrimary,
                                        color = Color.White.copy(alpha = .7f))
                                }
                            }
                        }
                    }
                    Text(status, style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .7f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    accountContent()
                    state.notice?.let { Text(it, style = RealType.cardMetaPrimary, color = Color(0xFFFFC9BD),
                        textAlign = TextAlign.Center) }
                    if (documentFailed) Text(stringResource(R.string.real_pro_open_failed),
                        style = RealType.cardMetaPrimary, color = Color(0xFFFFC9BD))
                    ProAction(stringResource(R.string.real_pro_restore), { onRestore?.invoke() },
                        enabled = state.canRestore && onRestore != null && !closing)
                    ProAction(stringResource(R.string.real_pro_manage), onManage)
                    ProAction(stringResource(R.string.real_pro_privacy), {
                        documentFailed = runCatching { uriHandler.openUri(privacyUrl) }.isFailure
                    })
                    if (isSecureDocumentUrl(state.termsUrl)) ProAction(stringResource(R.string.real_pro_terms), {
                        state.termsUrl?.let { url -> documentFailed = runCatching { uriHandler.openUri(url) }.isFailure }
                    })
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 12.dp)) {
                    if (state.verifiedProActive) {
                        ProAction(stringResource(if (state.storeSubscription) R.string.real_pro_manage else R.string.real_access_done),
                            if (state.storeSubscription) onManage else dismiss, filled = true, enabled = !closing)
                    } else {
                        ProAction(stringResource(R.string.real_pro_buy), {
                            // Recheck at dispatch; pending/cancelled purchases never activate Pro here.
                            state.purchasableOffer(selected?.key)?.let { onPurchase?.invoke(it.key) }
                        }, filled = true, enabled = purchasable != null && onPurchase != null && !closing)
                    }
                    ProAction(stringResource(R.string.real_pro_later), dismiss)
                }
            }
        }
    }
    if (showDetails) PrivacyDetailsDialog(
        title = stringResource(R.string.real_pro_title),
        subtitle = stringResource(R.string.real_pro_details_subtitle),
        onBack = { showDetails = false }
    ) {
        listOf(R.string.real_pro_detail_ai, R.string.real_pro_detail_free, R.string.real_pro_detail_limits,
            R.string.real_pro_detail_store, R.string.real_pro_detail_data).forEach {
            Text(stringResource(it), style = RealType.cardBodyProminent, color = Color.White.copy(alpha = .8f))
        }
    }
}

@Composable
private fun ProFeature(glyph: ActionGlyph, label: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(glyph, Modifier.size(22.dp), color = Color.White.copy(alpha = .8f))
        Text(stringResource(label), style = RealType.cardBodyProminent, color = Color(0xFFF3F1F4))
    }
}

@Composable
private fun ProAction(text: String, onClick: () -> Unit, enabled: Boolean = true, filled: Boolean = false) {
    val shape = RoundedCornerShape(27.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = if (filled) 54.dp else 48.dp).clip(shape)
        .background(if (filled) Color.White.copy(alpha = if (enabled) 1f else .28f) else Color.Transparent)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = RealType.actionLabel, textAlign = TextAlign.Center,
            color = if (filled) Color.Black else Color.White.copy(alpha = if (enabled) .75f else .3f))
    }
}
