import com.royalchance.buildlogic.WasmTestEnvironment
import com.royalchance.buildlogic.configureCommonTargets
import com.royalchance.buildlogic.libs
import com.royalchance.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo Kotlin puro (sin Compose ni Android): core, domain y motores de juego.
 * Al no depender de la UI, su lógica se prueba sin interfaz y podrá ejecutarse en un servidor.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
            // Cobertura de los tests de JVM (informe conjunto en la raíz: ./gradlew koverHtmlReport).
            pluginManager.apply(libs.pluginId("kover"))

            extensions.configure<KotlinMultiplatformExtension> {
                configureCommonTargets(WasmTestEnvironment.NodeJs)
            }
        }
    }
}
