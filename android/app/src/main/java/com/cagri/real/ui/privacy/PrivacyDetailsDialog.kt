package com.cagri.real.ui.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.R
import com.cagri.real.ui.components.LiquidGlassTokens
import com.cagri.real.ui.components.LocalLiquidReduceMotion
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.theme.RealType

/** Reading never grants consent. A compact fixed header leaves the body free to scroll. */
@Composable
internal fun PrivacyDetailsDialog(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize().safeDrawingPadding()
                .padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.cagri.real.ui.components.RealBackButton(onBack, description = stringResource(R.string.real_privacy_back))
                    Spacer(Modifier.weight(1f))
                    Image(painterResource(R.drawable.real_home_wordmark), contentDescription = null,
                        modifier = Modifier.size(width = 54.dp, height = 22.dp))
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = RealType.editorialSection, color = Color.White,
                        modifier = Modifier.semantics { heading() })
                    Text(subtitle, style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .55f))
                    content()
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/** Shared with the new data-use page; the toggle only owns its header, not its actions. */
@Composable
internal fun PrivacyDisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val reduce = LocalLiquidReduceMotion.current
    val expansionDescription = stringResource(if (expanded) R.string.real_privacy_expanded else R.string.real_privacy_collapsed)
    Column(Modifier.fillMaxWidth().background(LiquidGlassTokens.contentColor, RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().semantics {
            stateDescription = expansionDescription
        }.clickable(interactionSource = remember { MutableInteractionSource() },
            indication = null, role = Role.Button, onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = RealType.cardTitle, color = Color.White)
                summary?.let { Text(it, style = RealType.cardMetaPrimary, color = Color.White.copy(alpha = .55f)) }
            }
            Text(if (expanded) "−" else "+", style = RealType.cardMetaPrimary,
                color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(start = 12.dp))
        }
        AnimatedVisibility(expanded,
            enter = if (reduce) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduce) ExitTransition.None else shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
