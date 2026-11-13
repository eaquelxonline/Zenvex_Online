// Zenvex Online — Gradle ayarları. Yalnız resmî depolar: Google Maven (AGP, AndroidX) ve Maven Central.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZenvexOnline"
include(":Android")
