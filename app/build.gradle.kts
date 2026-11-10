import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.xvideodownload"
    // 本机安装的是 android-36.1 平台，用 compileSdkMinor 精确匹配，避免再下载 android-36
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.example.xvideodownload"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    // 2026.02.00 BOM 对应 compose 1.10.3 / material3 1.4.0，minCompileSdk=35~36，
    // 与本机 AGP 8.13.2 + compileSdk 36.1 兼容；更新的 2026.09.00 要求 AGP 9.1 / SDK 37。
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
