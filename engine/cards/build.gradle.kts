// Cartas y mazos compartidos por los motores de juego (Blackjack, Póker). Kotlin puro.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
