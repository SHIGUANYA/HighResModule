pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://maven.google.com") }  // 关键：显式加上 Google Maven
    }
}
rootProject.name = "HighResModule"
include(":app")
