// Declara los plugins una sola vez (apply false) para que todos los módulos
// y los convention plugins compartan el mismo classpath y las mismas versiones.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Informe de cobertura conjunto de todos los módulos: ./gradlew koverHtmlReport
    alias(libs.plugins.kover)
}

// Módulos con objetivo JVM (Kover mide los tests de JVM). data:firebase no tiene objetivo JVM: sus
// tests se ejecutan en Android y Wasm, pero no entran en este informe.
dependencies {
    kover(project(":core:audio"))
    kover(project(":core:common"))
    kover(project(":core:designsystem"))
    kover(project(":core:testing"))
    kover(project(":core:ui"))
    kover(project(":domain"))
    kover(project(":data"))
    kover(project(":engine:cards"))
    kover(project(":engine:blackjack"))
    kover(project(":engine:roulette"))
    kover(project(":engine:slots"))
    kover(project(":engine:dice"))
    kover(project(":engine:poker"))
    kover(project(":feature:auth"))
    kover(project(":feature:blackjack"))
    kover(project(":feature:roulette"))
    kover(project(":feature:slots"))
    kover(project(":feature:dice"))
    kover(project(":feature:poker"))
    kover(project(":feature:lobby"))
    kover(project(":feature:profile"))
    kover(project(":feature:history"))
    kover(project(":feature:settings"))
    kover(project(":shared"))
}

kover {
    reports {
        filters {
            excludes {
                // Código generado: recursos de Compose y lambdas de componibles sin estado.
                packages("*.resources")
                classes("*ComposableSingletons*", "*.BuildConfig")
            }
        }
    }
}
