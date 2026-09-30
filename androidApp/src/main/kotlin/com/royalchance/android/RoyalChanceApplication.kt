package com.royalchance.android

import android.app.Application
import com.royalchance.shared.AppGraph

/** Mantiene una única raíz de dependencias durante toda la vida del proceso (sobrevive a rotaciones). */
class RoyalChanceApplication : Application() {
    val graph: AppGraph by lazy { AppGraph.inMemory() }
}
