package com.jacksonfdam.slipgate.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.runtime.DataEntry
import com.jacksonfdam.slipgate.host.runtime.DataSource

/**
 * The screen a player meets when a gate has no data yet.
 *
 * It offers what the gate itself declared and nothing more: a gate with no free replacement shows no
 * download button, which is how Hexen's card ends up honest instead of showing a control that could
 * never work.
 */
@Composable
internal fun GameDataScreen(
    gateTitle: String,
    engine: String,
    entry: DataEntry,
    state: AcquisitionState,
    onDownload: (DataSource.FreeDownload) -> Unit,
    onSupply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state is AcquisitionState.Working

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = gateTitle.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "$engine needs its game data before it can run.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = explain(entry, engine),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = EXPLANATION_WIDTH.dp),
        )

        entry.sources.forEach { source ->
            when (source) {
                is DataSource.FreeDownload -> {
                    Button(
                        onClick = { onDownload(source) },
                        enabled = !working,
                        modifier = Modifier.widthIn(min = 240.dp),
                    ) {
                        Text("Download ${source.displayName}")
                    }
                }

                DataSource.UserSupplied -> {
                    OutlinedButton(
                        onClick = onSupply,
                        enabled = !working,
                        modifier = Modifier.widthIn(min = 240.dp),
                    ) {
                        Text("Choose my own file")
                    }
                }
            }
        }

        Progress(state)
    }
}

@Composable
private fun Progress(state: AcquisitionState) {
    // Nothing to draw while waiting or once installed: the screen is about to be replaced.
    when (state) {
        is AcquisitionState.Working -> {
            val fraction = state.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = describe(state),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        is AcquisitionState.Problem -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        AcquisitionState.Installed, AcquisitionState.Waiting -> {
            // Nothing to draw: either the player has not started, or the gate is about to open.
        }
    }
}

private const val EXPLANATION_WIDTH = 460
private const val BYTES_PER_MEGABYTE = 1024 * 1024

/**
 * Why this screen offers what it offers.
 *
 * A gate with a free replacement says that the replacement is a replacement — Freedoom is not Doom and
 * Blasphemer is not Heretic, and a player who thinks they downloaded the original will wonder why the
 * levels are unfamiliar. A gate without one says so outright: Hexen has no free equivalent, and an
 * honest dead end reads better than a button that could never work.
 *
 * Decided from the sources the gate declared rather than from its name, so a gate that gains or loses a
 * replacement changes this line by changing its own requirements.
 */
internal fun explain(
    entry: DataEntry,
    engine: String,
): String {
    val free = entry.sources.filterIsInstance<DataSource.FreeDownload>().firstOrNull()
    return if (free == null) {
        "$engine has no freely licensed replacement, so it runs from your own copy. " +
            "Point Slipgate at the ${entry.displayName} from a copy you own; it stays on this device."
    } else {
        "${free.displayName} is a freely licensed replacement rather than $engine itself: " +
            "different levels and art, the same game to play. Your own ${entry.displayName} also works."
    }
}

/** Megabytes, because a byte count of a 40 megabyte download tells a player nothing. */
private fun describe(state: AcquisitionState.Working): String {
    val received = state.received / BYTES_PER_MEGABYTE
    val total = state.total?.let { it / BYTES_PER_MEGABYTE }
    return if (total == null) "$received MB so far" else "$received of $total MB"
}
