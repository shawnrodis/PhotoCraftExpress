pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Local Maven mirror (~/workspace/tools/local-maven): Gradle's Java
        // networking is blocked in this sandbox, so the ML Kit closure was
        // fetched with curl into this repo and the build resolves offline
        // from it. It is consulted first; everything else comes from the
        // normal Google/Maven Central repos (and the Gradle cache).
        maven { url = uri("../tools/local-maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "PhotoCraftExpress"
include(":app")
