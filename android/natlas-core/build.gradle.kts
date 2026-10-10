import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = providers.gradleProperty("natlasGroup").get()
version = providers.gradleProperty("natlasVersion").get()

android {
    namespace = "io.github.baffaabba.natlas"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // JVM unit tests never touch real Android classes; stubs return defaults.
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Keep the published metadata readable by apps (and the Flutter plugin)
        // that still build with Kotlin 2.1.
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "natlas-core"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("N-ATLAS SDK for Android")
                description.set(
                    "Kotlin client for the N-ATLAS models: speech-to-text in Hausa, Igbo, " +
                        "Yoruba and Nigerian English, and chat with N-ATLaS. " +
                        "N-ATLAS is an initiative of the Federal Ministry of Communications, " +
                        "Innovation and Digital Economy, and powered by Awarri Technologies."
                )
                url.set("https://github.com/Baffaabba/N-Atlas-SDK")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        comments.set(
                            "Covers this SDK's code only. The N-ATLAS models it calls keep their " +
                                "own licence (Open-Source Research and Innovation License)."
                        )
                    }
                }
            }
        }
    }
}
