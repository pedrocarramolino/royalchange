// Sistema de diseño "Noir & Oro": tema, tokens, tipografía, iconos y componentes genéricos.
// No conoce el dominio: los componentes con conocimiento del negocio viven en :core:ui.
plugins {
    alias(libs.plugins.royalchance.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.core.designsystem.resources"
}
