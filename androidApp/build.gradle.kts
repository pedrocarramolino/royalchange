import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Host Android. AGP 9 incluye Kotlin: no se aplica el plugin kotlin-android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.royalchance.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Identificador en Google Play: no puede cambiarse después de la primera publicación.
        applicationId = "com.royalchance.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Debe coincidir con AppInfo.VERSION (shared). versionCode sube en cada publicación.
        versionCode = 1
        versionName = "1.0.0"
    }

    // Firma de publicación: se lee de keystore.properties (no se versiona; ver README). Sin ese
    // archivo el build de release sale sin firmar, para firmarlo después o con Play App Signing.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8: elimina y ofusca el código no usado y quita recursos sin referencias.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.data)
    implementation(projects.data.firebase)
    implementation(libs.androidx.activity.compose)
}
