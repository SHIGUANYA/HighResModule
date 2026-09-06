pluginManagement {
    repositories {
        // Google Maven 必须第一位，且显式写 URL
        maven { url = uri("https://maven.google.com") }
        // 其它仓库
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "HighResModule"
include(":app")
