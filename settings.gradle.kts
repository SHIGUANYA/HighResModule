pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://maven.google.com") }
    }
}
rootProject.name = "HighResModule"
include(":app")
