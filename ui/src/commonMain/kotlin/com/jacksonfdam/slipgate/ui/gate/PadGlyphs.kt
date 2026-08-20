package com.jacksonfdam.slipgate.ui.gate

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacksonfdam.slipgate.host.controls.PadDecor
import com.jacksonfdam.slipgate.host.runtime.GateAction
import com.jacksonfdam.slipgate.host.runtime.InputExtension
import com.jacksonfdam.slipgate.ui.design.IconGlyph
import com.jacksonfdam.slipgate.ui.design.SlipgateIcon

private val GLYPH_SIZE = 26.dp
private const val GLYPH_ALPHA = 0.88f

private val fallbackLabel =
    TextStyle(
        color = Color.White.copy(alpha = GLYPH_ALPHA),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )

/**
 * The pad, drawn in the interface's own alphabet.
 *
 * The control layer knows where a button goes and what it does; what it looks like belongs to the
 * design system, which that module cannot see. This is the bridge: one glyph per action, and one per
 * engine-specific control that has a glyph to give it.
 *
 * A control with no glyph keeps its words. That is not a gap to fill blindly — a label a player can
 * read beats a picture they have to guess at, and the ones without glyphs are the ones no drawing
 * would explain.
 */
internal fun padGlyphs(): PadDecor =
    PadDecor(
        action = { action -> ActionGlyph(action) },
        extension = { extension -> ExtensionGlyph(extension) },
    )

@Composable
private fun ActionGlyph(action: GateAction) {
    val glyph =
        when (action) {
            GateAction.Fire -> IconGlyph.Fire
            GateAction.Use -> IconGlyph.Use
            GateAction.Jump -> IconGlyph.Jump
            GateAction.Crouch -> IconGlyph.Crouch
            GateAction.NextWeapon -> IconGlyph.WeaponNext
            GateAction.PreviousWeapon -> IconGlyph.WeaponPrevious
            GateAction.Map -> IconGlyph.Map
            GateAction.Menu -> IconGlyph.Menu
            GateAction.Confirm -> IconGlyph.Enter
        }
    SlipgateIcon(
        glyph = glyph,
        size = GLYPH_SIZE,
        tint = Color.White.copy(alpha = GLYPH_ALPHA),
        modifier = Modifier,
    )
}

/**
 * Extensions are named by the gate that invented them, so the glyph is chosen from what the name ends
 * with: a gate that adds an inventory calls it an inventory, whichever engine it is.
 */
@Composable
private fun ExtensionGlyph(extension: InputExtension) {
    val glyph =
        when {
            extension.key.endsWith("inventory.previous") -> IconGlyph.ItemPrevious
            extension.key.endsWith("inventory.next") -> IconGlyph.ItemNext
            extension.key.endsWith("inventory.use") -> IconGlyph.ItemUse
            extension.key.endsWith("fly.up") -> IconGlyph.FlyUp
            extension.key.endsWith("fly.down") -> IconGlyph.FlyDown
            else -> null
        }
    if (glyph == null) {
        BasicText(text = extension.label, style = fallbackLabel)
        return
    }
    SlipgateIcon(
        glyph = glyph,
        size = GLYPH_SIZE,
        tint = Color.White.copy(alpha = GLYPH_ALPHA),
    )
}
