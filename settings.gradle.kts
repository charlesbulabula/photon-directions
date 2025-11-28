pluginManagement {
    repositories {
        google()            // ✅ Needed for com.android.application, Google plugins
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

rootProject.name = "PhotonDirections"
include(":app")
