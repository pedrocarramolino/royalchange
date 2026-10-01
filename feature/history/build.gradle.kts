// Historial de partidas y estadísticas por juego a partir del libro contable.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.history.resources"
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // Solo en tests: repositorios en memoria como dobles realistas.
            implementation(projects.data)
        }
    }
}
