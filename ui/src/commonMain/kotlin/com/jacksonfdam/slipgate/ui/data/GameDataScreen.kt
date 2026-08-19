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

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/** Megabytes, because a byte count of a 40 megabyte download tells a player nothing. */
private fun describe(state: AcquisitionState.Working): String {
    val received = state.received / BYTES_PER_MEGABYTE
    val total = state.total?.let { it / BYTES_PER_MEGABYTE }
    return if (total == null) "$received MB so far" else "$received of $total MB"
}
