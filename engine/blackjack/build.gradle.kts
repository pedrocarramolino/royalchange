// Motor de Blackjack: (estado, acción) → (nuevo estado, eventos). Kotlin puro, sin UI ni datos.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.engine.cards)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
