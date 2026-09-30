import com.royalchance.buildlogic.libs
import com.royalchance.buildlogic.library
import com.royalchance.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo de funcionalidad (feature:*): pantallas + ViewModels + rutas de navegación.
 *
 * Aplica la configuración de Compose y añade lo que toda feature necesita. Por diseño NO depende
 * de `:data`: una pantalla solo conoce contratos de `:domain`, nunca la implementación de los datos.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.pluginId("royalchance-kmp-compose"))
                apply(libs.pluginId("kotlin-serialization"))
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(":core:designsystem"))
                    implementation(project(":core:ui"))
                    implementation(project(":domain"))
                    implementation(libs.library("lifecycle-viewmodel-compose"))
                    implementation(libs.library("lifecycle-runtime-compose"))
                    implementation(libs.library("navigation3-ui"))
                    implementation(libs.library("kotlinx-serialization-core"))
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(project(":core:testing"))
                    implementation(libs.library("kotlinx-coroutines-test"))
                }
            }
        }
    }
}
