pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// AGP and Kotlin match android/gradle/libs.versions.toml (docs/DECISIONS.md, "Android build
// stack"), so the composite build below loads one AGP version only.
plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}

include(":app")

// natlas-core is not on Maven Central yet. Build it from source in this repository: the
// plugin depends on io.github.baffaabba:natlas-core:0.1.0 and Gradle substitutes the
// :natlas-core project of ../../../../android (repo root /android).
includeBuild("../../../../android") {
    dependencySubstitution {
        substitute(module("io.github.baffaabba:natlas-core")).using(project(":natlas-core"))
    }
}
