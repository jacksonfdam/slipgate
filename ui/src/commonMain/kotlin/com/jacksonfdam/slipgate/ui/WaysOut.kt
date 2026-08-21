package com.jacksonfdam.slipgate.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.ui.data.GameDataStage
import com.jacksonfdam.slipgate.ui.data.RemoteShelfController

// The two stages a player can reach that have nothing else to press.
//
// Every other screen is navigable by what is already on it: the rack has its rail, a running gate has
// its menu, and Settings and Credits sit inside the rail. These two are ends of the line, and only one
// of the three platforms has a back gesture to rescue a player from an end of the line — so both draw
// their own way back rather than counting on the platform to provide one.

/**
 * A gate waiting on its data, with the way back out of it.
 *
 * Back belongs to the rack here rather than to the platform: Android's own gesture would leave the
 * app from this screen, and iOS has no gesture to leave anything with. The rack is read again on the
 * way out, so a shelf that changed while the player was here is the shelf they come back to.
 */
@Composable
internal fun NeedsDataStage(
    stage: Stage.NeedsData,
    acquisition: GameDataAcquisition,
    remoteShelf: RemoteShelfController,
    onInstalled: () -> Unit,
    onBack: () -> Unit,
) {
    SystemBack(enabled = true, onBack = onBack)
    GameDataStage(
        gate = stage.gate,
        entry = stage.entry,
        acquisition = acquisition,
        remoteShelf = remoteShelf,
        onInstalled = onInstalled,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * A gate that would not open, and the way back to the rack from it.
 *
 * The reason is drawn with an escape for the same reason the data screen is: a screen with nothing on
 * it but a sentence is a dead end on the two platforms with no back gesture of their own.
 */
@Composable
internal fun StuckStage(
    message: String,
    platformName: String,
    onBack: () -> Unit,
) {
    SystemBack(enabled = true, onBack = onBack)
    BootScreen(message = message, platformName = platformName, onBack = onBack)
}
