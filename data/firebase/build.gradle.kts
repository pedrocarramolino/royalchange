import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Repositorios respaldados por Firebase con los SDK OFICIALES de cada plataforma:
// Android (com.google.firebase) y web (paquete npm "firebase"). La lógica común vive en commonMain.
// No tiene target JVM: el escritorio de desarrollo usa los repositorios en memoria de :data.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

// Genera FirebaseProjectConfig.kt a partir de firebase.properties (valores públicos del proyecto).
val generateFirebaseConfig = tasks.register("generateFirebaseConfig") {
    val propertiesText = providers.fileContents(rootProject.layout.projectDirectory.file("firebase.properties")).asText
    val emulatorsOverride = providers.gradleProperty("royalchance.firebase.emulators").orElse("")
    val outputDir = layout.buildDirectory.dir("generated/firebaseConfig/kotlin")
    inputs.property("properties", propertiesText)
    inputs.property("emulators", emulatorsOverride)
    outputs.dir(outputDir)
    doLast {
        val props = Properties().apply { load(propertiesText.get().reader()) }
        fun value(key: String): String = props.getProperty(key)?.trim().orEmpty()
        val cloudProjectId = value("projectId")
        require(cloudProjectId.isNotEmpty()) { "firebase.properties: falta projectId" }
        val useEmulators = emulatorsOverride.get().toBooleanStrictOrNull() ?: cloudProjectId.startsWith("demo-")
        // Con emuladores se usa un proyecto "demo-": el SDK nunca puede llegar a la nube por error.
        val projectId = if (useEmulators) value("emulatorProjectId").ifEmpty { "demo-royalchance" } else cloudProjectId
        require(!useEmulators || projectId.startsWith("demo-")) { "firebase.properties: emulatorProjectId debe empezar por demo-" }
        // Los emuladores no validan el ID de la app Android; la nube sí (se comprueba al arrancar).
        val androidAppId = value("androidAppId").ifEmpty { if (useEmulators) "1:000000000000:android:0000000000000000" else "" }
        val file = outputDir.get().file("com/royalchance/data/firebase/FirebaseProjectConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.royalchance.data.firebase
            |
            |// Generado desde firebase.properties por la tarea generateFirebaseConfig. No editar.
            |public object FirebaseProjectConfig {
            |    public const val PROJECT_ID: String = "$projectId"
            |    public const val API_KEY: String = "${value("apiKey")}"
            |    public const val AUTH_DOMAIN: String = "${value("authDomain")}"
            |    public const val MESSAGING_SENDER_ID: String = "${value("messagingSenderId")}"
            |    public const val WEB_APP_ID: String = "${value("webAppId")}"
            |    public const val ANDROID_APP_ID: String = "$androidAppId"
            |    public const val ANDROID_API_KEY: String = "${value("androidApiKey").ifEmpty { value("apiKey") }}"
            |    public const val USE_EMULATORS: Boolean = $useEmulators
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    android {
        namespace = "com.royalchance.data.firebase"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Los tests usan dobles de las pasarelas: no necesitan navegador.
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateFirebaseConfig)
            dependencies {
                api(projects.domain)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        wasmJsMain {
            // Todo el código web de este módulo es interoperabilidad con el SDK de JavaScript.
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
            dependencies {
                implementation(npm("firebase", libs.versions.firebase.js.get()))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
