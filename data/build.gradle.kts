// Implementaciones de los repositorios del dominio. Único módulo que conocerá Firebase (Fase 4).
plugins {
    alias(libs.plugins.royalchance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
