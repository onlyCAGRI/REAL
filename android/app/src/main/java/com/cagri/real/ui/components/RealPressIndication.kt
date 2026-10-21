package com.cagri.real.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App-wide press feedback for every foundation clickable that uses the default indication:
 * a calm iOS-style highlight instead of the spreading Material ripple. Drawn on top of the
 * element exactly where a ripple would be (inside any preceding clip), so it suits every surface.
 */
object RealPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RealPressNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x5EA1
}

private val PressHighlight = Color.White.copy(alpha = 0.08f)

private class RealPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val progress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            var pressJob: Job? = null
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        presses += interaction
                        pressJob = launch { progress.animateTo(1f, tween(70, easing = RealMotion.Standard)) { invalidateDraw() } }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val press = if (interaction is PressInteraction.Release) interaction.press
                            else (interaction as PressInteraction.Cancel).press
                        presses -= press
                        if (presses.isEmpty()) {
                            val sinking = pressJob
                            launch {
                                // A quick tap still shows the full highlight before it fades.
                                sinking?.join()
                                if (presses.isEmpty()) progress.animateTo(0f, tween(220, easing = RealMotion.Standard)) { invalidateDraw() }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = progress.value
        if (p > 0.001f) drawRect(PressHighlight, alpha = p)
    }
}
