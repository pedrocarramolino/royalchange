// Host de escritorio (JVM). En esta etapa es el entorno de desarrollo: Compose Hot Reload,
// ventana redimensionable para probar layouts móvil/tablet/escritorio y ciclo de pruebas rápido.
// El empaquetado nativo (MSI/DMG) se configurará si se decide distribuir la app de escritorio.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.data)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.royalchance.desktop.MainKt"
    }
}
