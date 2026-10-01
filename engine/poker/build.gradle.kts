// Motor de Texas Hold'em No-Limit: evaluador de manos, reglas de apuestas, botes y bots. Kotlin puro.
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
