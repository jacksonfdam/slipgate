package com.jacksonfdam.slipgate.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The selected gate's stage: the portrait surface with the gate's identity underneath.
 * The portrait is a composed gradient placeholder until the live portrait shaders drive
 * it; everything around it already speaks the final layout.
 */
@Composable
public fun StagePanel(
    card: GateCard,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PortraitPlaceholder(
            dimmed = !card.isPlayable,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PORTRAIT_ASPECT),
        )
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

/** Static composed stand-in for the live portal: recess floor with a low accent glow. */
@Composable
internal fun PortraitPlaceholder(
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = accentRamp
    val glow = if (dimmed) accent.dim.copy(alpha = 0.35f) else accent.dim
    Box(
        modifier =
            modifier
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
