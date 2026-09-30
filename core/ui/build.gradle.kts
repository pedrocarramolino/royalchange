// Componentes de UI con conocimiento del dominio (avatares, país…), compartidos entre features.
plugins {
    alias(libs.plugins.royalchance.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.domain)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.core.ui.resources"
}
