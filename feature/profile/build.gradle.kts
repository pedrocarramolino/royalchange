// Progreso del jugador: nivel, estadísticas, racha y logros.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // Solo en tests: repositorios en memoria como dobles realistas.
            implementation(projects.data)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.profile.resources"
}
