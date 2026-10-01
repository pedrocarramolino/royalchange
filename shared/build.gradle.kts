// Raíz de composición compartida: App(), navegación entre features e inyección de dependencias.
// Es el único módulo que conoce a la vez las features y las implementaciones de :data.
plugins {
    alias(libs.plugins.royalchance.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.data)
            implementation(projects.feature.auth)
            implementation(projects.feature.blackjack)
            implementation(projects.feature.roulette)
            implementation(projects.feature.slots)
            implementation(projects.feature.dice)
            implementation(projects.feature.poker)
            implementation(projects.feature.lobby)
            implementation(projects.feature.profile)
            implementation(projects.feature.history)
            implementation(projects.feature.settings)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.navigation3.ui)
            implementation(libs.kotlinx.serialization.core)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.shared.resources"
}
