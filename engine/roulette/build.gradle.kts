// Motor de ruleta europea: apuestas, giro y pagos. Kotlin puro, sin UI ni datos.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
