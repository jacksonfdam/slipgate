package com.jacksonfdam.slipgate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksonfdam.slipgate.host.runtime.BackendResolver
import com.jacksonfdam.slipgate.host.runtime.GateHost
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.host.runtime.GateSession
import com.jacksonfdam.slipgate.host.runtime.InputProfile
import com.jacksonfdam.slipgate.host.runtime.MountedGameData
import com.jacksonfdam.slipgate.ui.gate.GateSurface
import org.koin.compose.koinInject

/**
 * Root of the shell. Until the launcher exists it opens the first registered gate directly,
 * which is enough to prove the session, framebuffer and palette path on every platform.
 */
@Composable
public fun SlipgateApp(
    platformInfo: PlatformInfo = koinInject(),
    registry: GateRegistry = koinInject(),
    resolver: BackendResolver = koinInject(),
    host: GateHost = koinInject(),
) {
    var session by remember { mutableStateOf<GateSession?>(null) }
    var inputProfile by remember { mutableStateOf<InputProfile?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(registry) {
        val gate = registry.gates.firstOrNull()
        if (gate == null) {
            failure = "no gates registered"
            return@LaunchedEffect
        }
        resolver
            .factoryFor(gate)
            .onSuccess { factory ->
                inputProfile = gate.inputProfile()
                session = factory.create(MountedGameData.Empty, host)
            }.onFailure { error -> failure = error.message }
    }

    SlipgateTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val current = session
            if (current == null) {
                BootScreen(message = failure ?: "opening gate", platformName = platformInfo.name)
            } else {
                GateSurface(
                    session = current,
                    inputProfile = inputProfile ?: InputProfile(actions = emptySet()),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BootScreen(
    message: String,
    platformName: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SLIPGATE",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = platformName,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
