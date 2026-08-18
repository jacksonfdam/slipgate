package com.jacksonfdam.slipgate.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.jacksonfdam.slipgate.ui.SlipgateApp
import com.jacksonfdam.slipgate.ui.slipgateModules
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(slipgateModules()) }
    ComposeViewport { SlipgateApp() }
}
