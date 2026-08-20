package com.jacksonfdam.slipgate.host.controls

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.InputExtension

/**
 * What a control looks like, supplied by whoever is placing the pad.
 *
 * The pad knows where a button goes and what it does; it does not know how the interface draws. That
 * lives in the design system, which this module cannot see — and should not, or every glyph would have
 * to be authored twice.
 */
public class PadDecor(
    public val action: @Composable (GateAction) -> Unit,
    public val extension: @Composable (InputExtension) -> Unit,
) {
    public companion object {
        /** The labels the pad has always drawn: what a caller with no design system gets. */
        public val Labels: PadDecor =
            PadDecor(
                action = { action -> BasicText(text = labelFor(action), style = labelStyle) },
                extension = { extension -> BasicText(text = extension.label, style = labelStyle) },
            )
    }
}

/** What the default decor writes on a button: the label the placement table already carries. */
internal fun labelFor(action: GateAction): String = placements[action]?.label ?: ""

internal val labelStyle =
    TextStyle(
        color = Color.White.copy(alpha = LABEL_ALPHA),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
