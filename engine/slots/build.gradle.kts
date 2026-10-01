// Motor de tragaperras: rodillos, líneas de premio y tabla de pagos. Kotlin puro, sin UI ni datos.
plugins {
    alias(libs.plugins.royalchance.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
