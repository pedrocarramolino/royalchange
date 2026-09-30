import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Host web: la PWA. Genera index.html + webApp.js + .wasm listos para Firebase Hosting.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.ui)
        }
    }
}
