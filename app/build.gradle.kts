import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

repositories {
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://jogamp.org/deployment/maven") }
    mavenCentral()
    google()
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.anvlabs.vissora.generated.resources"
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-cio:3.3.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
                implementation("org.json:json:20250517")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("io.coil-kt.coil3:coil-compose:3.6.3")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
                implementation("org.bytedeco:opencv:4.10.0-1.5.11")
                implementation("org.bytedeco:opencv:4.10.0-1.5.11:windows-x86_64")
                implementation("org.bytedeco:openblas:0.3.28-1.5.11")
                implementation("org.bytedeco:openblas:0.3.28-1.5.11:windows-x86_64")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-cio:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                val media3_version = "1.3.0"
                implementation("androidx.media3:media3-exoplayer:$media3_version")
                implementation("androidx.media3:media3-exoplayer-rtsp:$media3_version")
                implementation("androidx.media3:media3-ui:$media3_version")
            }
        }
    }
}

android {
    namespace = "org.anvlabs.vissora"
    compileSdkExtension = 19
    compileSdk = 36
    ndkVersion = "30.0.16138531"

    defaultConfig {
        applicationId = "org.anvlabs.vissora"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("x86", "arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("anvkey.jks")
            storePassword = "a1n2v3l4"
            keyAlias = "anvpy"
            keyPassword = "a1n2v3l4"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            /*proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )*/
        }
    }

    buildToolsVersion = "36.1.0"

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


compose.desktop {
    application {
        mainClass = "org.anvlabs.vissora.MainKt"
        jvmArgs += listOf(
            "-Djava.library.path=${project.file("native/windows/libs").absolutePath}"
        )
        nativeDistributions {
            targetFormats(
                //org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                //org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "GodView"
            packageVersion = "1.0.0"
        }
    }
}