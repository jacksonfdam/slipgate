package com.jacksonfdam.slipgate.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ember = Color(0xFFFF5A1F)
private val Ash = Color(0xFF0B0B0D)
private val Bone = Color(0xFFE8E4DC)
private val Slate = Color(0xFF16161A)

private val SlipgateDarkColors =
    darkColorScheme(
        primary = Ember,
        onPrimary = Ash,
        background = Ash,
        onBackground = Bone,
        surface = Slate,
        onSurface = Bone,
    )

private val SlipgateLightColors =
    lightColorScheme(
        primary = Ember,
        onPrimary = Bone,
        background = Bone,
        onBackground = Ash,
        surface = Color(0xFFF4F1EA),
        onSurface = Ash,
    )

/**
 * Slipgate palette. Dark is the intended presentation; light exists so the shell stays
 * legible when the platform forces it.
 */
@Composable
public fun SlipgateTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SlipgateDarkColors else SlipgateLightColors,
        content = content,
    )
}
