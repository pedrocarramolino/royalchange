package com.royalchance.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.royalchance.shared.App
import com.royalchance.shared.AppGraph

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = AppGraph.inMemory()
    ComposeViewport(viewportContainerId = "composeTarget") {
        App(graph)
    }
}
