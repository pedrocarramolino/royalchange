// Dobles de prueba compartidos. Solo se usa como dependencia de test (commonTest) de otros módulos.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}
