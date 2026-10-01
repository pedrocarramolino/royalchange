import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
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
