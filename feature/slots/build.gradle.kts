// Tragaperras: rodillos animados, ViewModel y conexión con la economía. La lógica vive en :engine:slots.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.engine.slots)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            // Solo en tests: repositorios en memoria como dobles realistas.
            implementation(projects.data)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.slots.resources"
}
