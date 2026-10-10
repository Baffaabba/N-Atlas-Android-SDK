import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The server address and key come from android/local.properties (git-ignored), never from
// source. Either spelling works:
//   natlas.baseUrl=https://...      or  NATLAS_BASE_URL=https://...
//   natlas.apiKey=...               or  NATLAS_API_KEY=...
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localValue(vararg keys: String): String =
    keys.firstNotNullOfOrNull { localProps.getProperty(it)?.trim()?.takeIf(String::isNotEmpty) } ?: ""

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.baffaabba.natlas.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.baffaabba.natlas.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("natlasVersion").get()

        buildConfigField("String", "NATLAS_BASE_URL", quoted(localValue("natlas.baseUrl", "NATLAS_BASE_URL")))
        buildConfigField("String", "NATLAS_API_KEY", quoted(localValue("natlas.apiKey", "NATLAS_API_KEY")))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(project(":natlas-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
}
