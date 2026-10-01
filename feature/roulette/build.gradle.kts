// Mesa de ruleta: tapete, rueda animada, ViewModel y conexión con la economía. La lógica vive en :engine:roulette.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.engine.roulette)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            // Solo en tests: repositorios en memoria como dobles realistas.
            implementation(projects.data)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.roulette.resources"
}
