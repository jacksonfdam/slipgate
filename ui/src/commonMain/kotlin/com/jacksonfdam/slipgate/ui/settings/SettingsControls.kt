package com.jacksonfdam.slipgate.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

/**
 * The controls Settings is built from: a titled group, a switch, a slider, and a row of choices.
 *
 * Kept together because they are the vocabulary of one screen rather than a component library, and
 * because a settings screen that invents a new control per row is a settings screen nobody can scan.
 */
@Composable
internal fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = title.uppercase(), style = TypeScale.Label, color = accentRamp.hot)
        content()
    }
}

@Composable
internal fun Toggle(
    label: String,
    explanation: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.widthIn(max = EXPLANATION_WIDTH.dp)) {
            Text(text = label, style = TypeScale.Body, color = ColorTokens.Text)
            Text(text = explanation, style = TypeScale.Label, color = ColorTokens.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Amount(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = TypeScale.Body, color = ColorTokens.Text)
            Text(
                text = percent(value),
                style = TypeScale.Data,
                color = ColorTokens.Muted,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
internal fun <T> Choice(
    label: String,
    explanation: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = TypeScale.Body, color = ColorTokens.Text)
        Text(text = explanation, style = TypeScale.Label, color = ColorTokens.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Pill(
                    text = name(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
internal fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ramp = accentRamp
    Text(
        text = text,
        style = TypeScale.Label,
        color = if (selected) ramp.hot else ColorTokens.Muted,
        modifier =
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(ColorTokens.Surface)
                .border(
                    width = 1.dp,
                    color = if (selected) ramp.base else ColorTokens.Edge,
                    shape = RoundedCornerShape(2.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

internal fun percent(value: Float): String = "${(value * PERCENT).toInt()}%"

private const val PERCENT = 100
private const val EXPLANATION_WIDTH = 420
