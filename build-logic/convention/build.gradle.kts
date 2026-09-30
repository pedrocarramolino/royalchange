plugins {
    `kotlin-dsl`
}

group = "com.royalchance.buildlogic"

dependencies {
    // compileOnly: en ejecución se usan los plugins declarados en el build.gradle.kts raíz,
    // así todos los módulos comparten exactamente las mismas versiones.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.royalchance.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = libs.plugins.royalchance.kmp.compose.get().pluginId
            implementationClass = "KmpComposeConventionPlugin"
        }
    }
}
