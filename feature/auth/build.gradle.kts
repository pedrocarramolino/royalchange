// Acceso: bienvenida, inicio de sesión, registro completo, perfil, recuperación y documentos legales.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // Solo en tests: el repositorio en memoria actúa como doble realista del AuthRepository.
            implementation(projects.data)
        }
    }
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.auth.resources"
}
