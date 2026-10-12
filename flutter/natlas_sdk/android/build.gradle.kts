group = "io.github.baffaabba.natlas_sdk"
version = "0.1.0"

// Used only when this module is opened on its own. Inside an app build, the app's AGP and
// Kotlin versions apply.
buildscript {
    val kotlinVersion = "2.2.21"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // natlas-core is not on Maven Central yet. Apps that don't use a composite build
        // run `./gradlew :natlas-core:publishToMavenLocal` in N-Atlas-Android-SDK/android.
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

// The Flutter Gradle plugin applies the Kotlin plugin when the app's AGP has no built-in
// Kotlin (AGP 8), so it isn't listed here; that keeps this file valid for AGP 9 apps too.
plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.baffaabba.natlas_sdk"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    // All SDK logic lives here; the plugin only forwards calls.
    implementation("io.github.baffaabba:natlas-core:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
