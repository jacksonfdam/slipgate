package com.jacksonfdam.slipgate.ui.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.ui.design.ColorTokens
import com.jacksonfdam.slipgate.ui.design.LocalReducedMotion
import com.jacksonfdam.slipgate.ui.design.TypeScale
import com.jacksonfdam.slipgate.ui.design.accentRamp

/**
 * Credits, as a scroller that credits itself to the demoscene it borrows the form from.
 *
 * This screen is a legal requirement as much as a courtesy: the engines are GPLv2 and their notice has
 * to be readable, so the licence text is in the scroll rather than behind a link. It scrolls by itself
 * and stops when touched, because someone reading a licence should not have to chase it.
 */
@Composable
internal fun CreditsScreen(
    entries: List<CreditEntry> = credits(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    var paused by remember { mutableStateOf(false) }

    if (!reducedMotion) {
        LaunchedEffect(paused) {
            while (!paused) {
                listState.scrollBy(SCROLL_STEP)
                androidx.compose.runtime.withFrameNanos { }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(Unit) {
                    // Touching the scroll stops it; nobody should have to read a licence in motion.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            paused = true
                        }
                    }
                },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(entries) { entry -> CreditBlock(entry) }
    }
}

@Composable
private fun CreditBlock(entry: CreditEntry) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = TEXT_WIDTH.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = entry.heading.uppercase(),
            style = TypeScale.Label,
            color = accentRamp.hot,
        )
        entry.lines.forEach { line ->
            Text(
                text = line,
                style = if (entry.monospaced) TypeScale.Data else TypeScale.Body,
                color = if (entry.monospaced) ColorTokens.Muted else ColorTokens.Text,
            )
        }
    }
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollBy(pixels: Float) {
    scroll { scrollBy(pixels) }
}

private const val SCROLL_STEP = 0.6f
private const val TEXT_WIDTH = 640
