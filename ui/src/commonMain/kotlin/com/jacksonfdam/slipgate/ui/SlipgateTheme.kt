package com.jacksonfdam.slipgate.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.jacksonfdam.slipgate.ui.design.AccentRamp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalAccentRamp
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.TypeScale

/**
 * Slipgate is dark-only by design — it is a console interface, not a document reader.
 * The chrome is fixed; the accent ramp is the one thing that changes, sampled from the
 * mounted gate's palette and defaulting to the neutral steel fallback before any data.
 */
@Composable
public fun SlipgateTheme(
    accent: AccentRamp = AccentRamp.Steel,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme =
        remember(accent) {
            darkColorScheme(
                primary = accent.base,
                onPrimary = ColorTokens.Void,
                secondary = ColorTokens.Muted,
                onSecondary = ColorTokens.Void,
                background = ColorTokens.Void,
                onBackground = ColorTokens.Text,
                surface = ColorTokens.Surface,
                onSurface = ColorTokens.Text,
                surfaceVariant = ColorTokens.Recess,
                onSurfaceVariant = ColorTokens.Muted,
                outline = ColorTokens.Edge,
            )
        }
    CompositionLocalProvider(
        LocalAccentRamp provides accent,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SlipgateTypography,
            content = content,
        )
    }
}

private val SlipgateTypography =
    Typography(
        displaySmall = TypeScale.Display,
        headlineSmall = TypeScale.Headline,
        titleMedium = TypeScale.Headline,
        bodyMedium = TypeScale.Body,
        bodyLarge = TypeScale.Body,
        labelMedium = TypeScale.Label,
        labelSmall = TypeScale.Data,
    )
