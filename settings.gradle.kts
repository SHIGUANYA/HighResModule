pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.google.com") }  // 必须第一位
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "HighResModule"
include(":app")
