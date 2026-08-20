package com.jacksonfdam.slipgate.ui

import com.jacksonfdam.slipgate.host.audio.AudioOutput
import com.jacksonfdam.slipgate.host.audio.openAudioOutput
import com.jacksonfdam.slipgate.host.gamedata.GameDataAcquisition
import com.jacksonfdam.slipgate.host.gamedata.GameDataStore
import com.jacksonfdam.slipgate.host.gamedata.RemoteShelf
import com.jacksonfdam.slipgate.host.graphics.backend.classic.ClassicBackend
import com.jacksonfdam.slipgate.host.graphics.backend.skia.skiaBackend
import com.jacksonfdam.slipgate.host.graphics.core.BackendSelector
import com.jacksonfdam.slipgate.host.graphics.core.CrtSettings
import com.jacksonfdam.slipgate.host.runtime.Gate
import com.jacksonfdam.slipgate.host.runtime.GateRegistry
import com.jacksonfdam.slipgate.ui.audio.InterfaceAudio
import com.jacksonfdam.slipgate.ui.data.RemoteShelfController
import com.jacksonfdam.slipgate.ui.settings.SettingsController
import com.jacksonfdam.slipgate.ui.settings.SettingsStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The shell's own wiring. Gates arrive from the entry point rather than from here: nothing under
 * `host` or `ui` may name a game module, which is what keeps a new gate an addition to one file.
 */
internal fun uiModule(gates: List<Gate>): Module =
    module {
        single { GateRegistry(gates = gates) }
        single { GameDataAcquisition(store = get<GameDataStore>()) }
        single { SettingsController(store = get<SettingsStore>()) }
        // One per shell rather than one per screen: the answer to "what does my shelf hold" is the
        // same on the rack, in Settings and on a gate's data screen, and asking three times over a
        // home tunnel is three times the wait for the same list.
        single { RemoteShelf() }
        single { RemoteShelfController(shelf = get()) }
        single { InterfaceAudio(output = get<AudioOutput>()) }
        // Candidates are listed in preference order; the classic path always works and so
        // always comes last. The shader backends are appended as they land.
        // Preference order: the shader path where it exists, the classic path everywhere else.
        // skiaBackend() is null on Android below API 33, which is a platform fact rather than a
        // failure, so the list simply gets shorter.
        single { CrtSettings.Default }
        single {
            BackendSelector(candidates = listOfNotNull(skiaBackend(get()), ClassicBackend()))
        }
        // The output lives as long as the shell does. Opening one per session would fight the
        // platform for the device on every gate change, and closing one is the shell's business
        // rather than a session's.
        single<AudioOutput> { openAudioOutput() }
        single { SessionHosts(audio = get(), store = get()) }
    }

/** Every Koin module the shell needs, in the order the entry points should load them. */
public fun slipgateModules(gates: List<Gate>): List<Module> = listOf(uiModule(gates), platformModule)
