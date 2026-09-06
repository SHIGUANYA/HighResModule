plugins {
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.0"
}

android {
    namespace = "com.hook.highres"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hook.highres"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }  // ★ 必须：JitPack 托管 XposedBridge
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    compileOnly("com.github.rovo89:XposedBridge:82")  // ★ 关键：从 JitPack 拉取 XposedBridge (v82 存在)
}
