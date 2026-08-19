package com.jacksonfdam.slipgate.ui.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The strict five-step type scale. System faces only — the display wordmark is drawn from
 * SDF letterforms, everything else sets the system stack with intent: tight tracking on
 * uppercase eyebrows, generous line height on body, and monospace reserved strictly for
 * machine-generated values (numbers, versions, benchmark figures, file sizes), never prose.
 */
public object TypeScale {
    /** Screen and section titles. */
    public val Display: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp,
        )

    /** Card titles and panel headers. */
    public val Headline: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        )

    /** Running text. Generous line height on purpose. */
    public val Body: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
        )

    /** Uppercase eyebrows and small labels. Track tight, set in caps by the caller. */
    public val Label: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.4.sp,
        )

    /** Machine-generated values only: numbers, versions, benchmark figures, file sizes. */
    public val Data: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        )
}
