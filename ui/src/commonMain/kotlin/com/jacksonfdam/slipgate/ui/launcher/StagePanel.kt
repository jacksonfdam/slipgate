package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

/**
 * The selected gate's stage: the portrait surface with the gate's identity composed onto
 * its lower-left corner, so the panel is one flexible surface that survives a short
 * landscape screen. The portrait is a composed gradient placeholder until the live
 * portrait shaders drive it.
 *
 * Give the panel a bounded height (a weight) on bounded screens; [keepAspect] is for
 * unbounded columns, where the panel must size itself.
 */
@Composable
public fun StagePanel(
    card: GateCard,
    modifier: Modifier = Modifier,
    keepAspect: Boolean = false,
) {
    val accent = accentRamp
    val glow = if (card.isPlayable) accent.dim else accent.dim.copy(alpha = DIMMED_GLOW_ALPHA)
    Box(
        modifier =
            modifier
                .then(if (keepAspect) Modifier.aspectRatio(PORTRAIT_ASPECT) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .background(ColorTokens.Recess)
                .border(1.dp, ColorTokens.Edge, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = card.descriptor.title,
                style = TypeScale.Display,
                color = ColorTokens.Text,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip(text = card.descriptor.engine.uppercase())
                StatChip(
                    text =
                        when (card.availability) {
                            GateAvailability.Installed -> "READY"
                            is GateAvailability.NeedsData -> "ADD GAME FILES"
                            is GateAvailability.UserSuppliedOnly -> "NEEDS YOUR FILES"
                        },
                    emphasized = card.isPlayable,
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    emphasized: Boolean = false,
) {
    val accent = accentRamp
    Text(
        text = text,
        style = TypeScale.Label,
        color = if (emphasized) accent.hot else ColorTokens.Muted,
        modifier =
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(ColorTokens.Surface)
                .border(1.dp, ColorTokens.Edge, RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private const val PORTRAIT_ASPECT = 16f / 7f
private const val DIMMED_GLOW_ALPHA = 0.35f
