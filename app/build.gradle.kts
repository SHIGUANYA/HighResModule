plugins {
    id("com.android.application") version "8.2.0"
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

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/xposed-api.jar"))
}
