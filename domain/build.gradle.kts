// Dominio: modelos, contratos de repositorio y reglas de negocio. Kotlin puro, sin UI ni datos.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
