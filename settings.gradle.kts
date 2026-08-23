pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.plasmovoice.com/releases")
        mavenCentral()
    }
}

rootProject.name = "plasmo-telegram-voice"
