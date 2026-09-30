package com.royalchance.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Bytecode para Android y escritorio. Android 8.0+ lo admite sin configuración adicional. */
internal val JVM_TARGET = JvmTarget.JVM_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).get().get().pluginId

/** Entorno en el que se ejecutan los tests de Kotlin/Wasm de un módulo. */
internal enum class WasmTestEnvironment {
    /** Módulos sin UI: Node.js, rápido y sin navegador. */
    NodeJs,

    /** Módulos con Compose: necesitan un navegador (Chrome headless). */
    Browser,
}

/**
 * Targets comunes a todos los módulos KMP del proyecto:
 * - `jvm`: escritorio (entorno de desarrollo). Los módulos puros también llegan a Android
 *   a través de esta variante, sin necesitar el plugin de Android.
 * - `wasmJs`: la PWA.
 *
 * iOS se añadirá en este único punto cuando haya un Mac disponible.
 */
internal fun KotlinMultiplatformExtension.configureCommonTargets(wasmTestEnvironment: WasmTestEnvironment) {
    jvm {
        compilerOptions { jvmTarget.set(JVM_TARGET) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        when (wasmTestEnvironment) {
            WasmTestEnvironment.NodeJs -> nodejs()
            WasmTestEnvironment.Browser -> {
                browser()
                // Los tests de UI de Compose en Wasm necesitan un bundle ejecutable para cargar
                // el runtime gráfico (Skiko). Compose 1.12 lo verifica antes de ejecutarlos (CMP-4906).
                binaries.executable()
            }
        }
    }

    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
    }
}

/** `:core:common` → `com.royalchance.core.common` */
internal fun Project.androidNamespace(): String =
    "com.royalchance." + path.removePrefix(":").split(':').joinToString(".") { it.replace('-', '_') }
