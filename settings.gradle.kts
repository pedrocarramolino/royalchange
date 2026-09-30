rootProject.name = "RoyalChance"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Sin repositoriesMode estricto: el plugin de Kotlin/Wasm registra sus propios
// repositorios para descargar Node.js y Binaryen, y un modo estricto los bloquearía.
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Hosts por plataforma
include(":androidApp")
include(":desktopApp")
include(":webApp")

// Raíz de composición compartida (UI + navegación + DI)
include(":shared")

// Núcleo
include(":core:common")
include(":core:designsystem")
include(":core:testing")
include(":core:ui")

// Dominio (contratos y reglas) y datos (implementaciones)
include(":domain")
include(":data")

// Funcionalidades
include(":feature:auth")
include(":feature:lobby")
include(":feature:profile")
include(":feature:history")
include(":feature:settings")
