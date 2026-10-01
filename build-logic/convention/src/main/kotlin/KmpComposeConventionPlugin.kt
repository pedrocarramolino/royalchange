import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.royalchance.buildlogic.JVM_TARGET
import com.royalchance.buildlogic.WasmTestEnvironment
import com.royalchance.buildlogic.androidNamespace
import com.royalchance.buildlogic.configureCommonTargets
import com.royalchance.buildlogic.libs
import com.royalchance.buildlogic.library
import com.royalchance.buildlogic.pluginId
import com.royalchance.buildlogic.version
import org.gradle.api.Plugin
import org.jetbrains.compose.ComposePlugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo con UI Compose Multiplatform (shared, designsystem, feature:*):
 * Android (biblioteca KMP de AGP 9), escritorio JVM y web (Wasm).
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.pluginId("kotlin-multiplatform"))
                apply(libs.pluginId("android-kotlin-multiplatform-library"))
                apply(libs.pluginId("compose-multiplatform"))
                apply(libs.pluginId("compose-compiler"))
                // Cobertura de los tests de JVM (informe conjunto en la raíz).
                apply(libs.pluginId("kover"))
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureCommonTargets(WasmTestEnvironment.Browser)

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    namespace = androidNamespace()
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    compilerOptions { jvmTarget.set(JVM_TARGET) }
                    // Necesario para que Compose Resources empaquete strings, fuentes e imágenes en Android.
                    androidResources { enable = true }
                }

                // Tests de UI en escritorio (JVM): rápidos y medidos por Kover.
                sourceSets.getByName("jvmTest").dependencies {
                    implementation(libs.library("compose-ui-test"))
                    implementation(ComposePlugin.DesktopDependencies.currentOs)
                    implementation(libs.library("kotlinx-coroutines-swing"))
                }

                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.library("compose-runtime"))
                    implementation(libs.library("compose-foundation"))
                    implementation(libs.library("compose-ui"))
                    implementation(libs.library("compose-material3"))
                    implementation(libs.library("compose-components-resources"))
                    implementation(libs.library("compose-ui-tooling-preview"))
                }
            }

            dependencies {
                // Previsualizaciones de Android Studio; no llega al APK de release.
                add("androidRuntimeClasspath", libs.library("compose-ui-tooling"))
            }
        }
    }
}
