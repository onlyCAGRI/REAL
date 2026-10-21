package com.cagri.real.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One option in a discovery step (a genre, a feeling, a platform, an era). */
internal data class StepOption(val key: String, val label: String)

/**
 * One question of the discovery picker. [max] options at most; an [optional] step can be skipped. [groups] splits a
 * long list under small headings (themes); [top] is shown above the options (Film / Dizi choice).
 */
internal class DiscoveryStep(
    val title: String,
    val subtitle: String,
    val max: Int,
    val optional: Boolean,
    val groups: List<Pair<String?, List<StepOption>>>,
    val top: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val keys: Set<String> get() = groups.flatMap { g -> g.second.map { it.key } }.toSet()
}

/**
 * Discovery picker, one question at a time (Apple-style setup): a few careful choices instead of many. Options that
 * rarely go with what is chosen ([rare]) are dimmed but still work, with a one-line note; a full step dims the rest
 * and a tap explains the limit. The last step's button asks for suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoveryStepper(
    steps: List<DiscoveryStep>,
    selection: Set<String>,
    onSelection: (Set<String>) -> Unit,
    accent: Color,
    loading: Boolean,
    enabled: Boolean,
    reduceAnimations: Boolean,
    doneLabel: String,
    modifier: Modifier = Modifier,
    rare: (chosen: Set<String>, key: String) -> Boolean = { _, _ -> false },
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var hint by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    BackHandler(enabled = step > 0) { step--; hint = null }
    LaunchedEffect(step) { hint = null }
    Column(modifier.fillMaxWidth()) {
        // Progress: one short line per question.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                val color by animateColorAsState(if (i <= step) Color.White else Color(0xFF333336), if (reduceAnimations) snap() else tween(250), label = "stepDot")
                Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        AnimatedContent(step, modifier = Modifier.weight(1f), label = "discoveryStep",
            transitionSpec = {
                if (reduceAnimations) fadeIn(snap()) togetherWith fadeOut(snap())
                else {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it * dir / 4 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it * dir / 4 } + fadeOut(tween(160)))
                }
            }) { index ->
            val s = steps[index]
            val chosen = selection.filter { it in s.keys }.toSet()
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(s.title, color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp))
                Text(s.subtitle, color = Color(0xFF8A8A92), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                s.top?.invoke(this)
                s.groups.forEach { (heading, options) ->
                    if (heading != null) Text(heading, color = Color(0xFF8A8A92), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { o ->
                            val on = o.key in chosen
                            val full = !on && chosen.size >= s.max && s.max > 1
                            val isRare = !on && rare(selection, o.key)
                            StepChip(o.label, on, dim = full || isRare, accent = accent, enabled = !loading) {
                                when {
                                    on -> { onSelection(selection - o.key); hint = null }
                                    full -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        hint = if (s.max == 2) "En iyi sonuçlar için 2 seçim yeterli" else "En fazla ${s.max} seçim"
                                    }
                                    else -> {
                                        // A single-choice step swaps its choice.
                                        val base = if (s.max == 1) selection - chosen else selection
                                        hint = if (isRare) {
                                            val other = s.groups.flatMap { it.second }.firstOrNull { it.key in chosen }?.label
                                                ?: steps.flatMap { st -> st.groups.flatMap { it.second } }.firstOrNull { it.key in selection }?.label
                                            if (other != null) "${o.label} ile $other birlikte nadir" else null
                                        } else null
                                        onSelection(base + o.key)
                                    }
                                }
                            }
                        }
                    }
                }
                Text(hint ?: "", color = Color(0xFF9A9AA5), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp).heightIn(min = 16.dp))
            }
        }
        val s = steps[step]
        val chosenHere = selection.count { it in s.keys }
        val last = step == steps.lastIndex
        val canGo = s.optional || chosenHere > 0
        val label = when {
            !last && chosenHere == 0 && s.optional -> "Atla"
            !last -> "Devam"
            chosenHere == 0 && s.optional -> "Atla ve $doneLabel"
            else -> doneLabel
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            DiscoveryLoadingButton(label.replaceFirstChar { it.uppercase() }, loading && last, enabled && canGo, accent, reduceAnimations) {
                if (last) onDone() else step++
            }
        }
    }
}

@Composable
private fun StepChip(label: String, selected: Boolean, dim: Boolean, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val fade by animateFloatAsState(if (dim) 0.32f else 1f, tween(200), label = "chipDim")
    Row(Modifier.heightIn(min = 44.dp).alpha(fade).liquidChrome(corners = 24.dp, selected = selected)
        .then(if (selected) Modifier.background(accent.copy(alpha = .28f), shape).border(1.dp, accent.copy(alpha = .9f), shape) else Modifier)
        .semantics { this.selected = selected }
        .clip(shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Pairs that rarely make a good deck together (both directions). Dimmed, never forbidden. */
internal object DiscoveryRarePairs {
    private fun pairs(vararg p: Pair<String, String>) = p.flatMap { listOf(it, it.second to it.first) }.toSet()
    val film = pairs(
        "family" to "horror", "family" to "war", "family" to "crime", "family" to "thriller",
        "documentary" to "fantasy", "documentary" to "science-fiction", "documentary" to "horror", "documentary" to "animation",
        "documentary" to "anime", "documentary" to "romance", "animation" to "war", "animation" to "horror", "history" to "science-fiction",
        "romance" to "horror", "war" to "comedy",
    )
    val game = pairs(
        "card" to "racing", "card" to "shooter", "card" to "fighting", "card" to "sports", "card" to "platformer",
        "sports" to "metroidvania", "sports" to "horror", "sports" to "visual-novel", "sports" to "roguelike", "sports" to "souls-like",
        "racing" to "puzzle", "racing" to "rpg", "racing" to "horror", "racing" to "visual-novel", "racing" to "metroidvania",
        "rhythm" to "strategy", "rhythm" to "rpg", "rhythm" to "management", "visual-novel" to "shooter", "visual-novel" to "fighting",
        "management" to "fighting", "management" to "shooter",
    )
    val music = pairs(
        "classical" to "hiphop", "classical" to "metal", "classical" to "reggae", "classical" to "latin", "classical" to "dance",
        "jazz" to "metal", "metal" to "reggae", "metal" to "rnb", "metal" to "latin", "country" to "electronic", "blues" to "dance",
    )
    fun rare(table: Set<Pair<String, String>>, chosen: Set<String>, key: String) = chosen.any { (it to key) in table }
}
