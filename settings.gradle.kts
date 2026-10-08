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

rootProject.name = "MutCube"
include(":app")
include(":core:model")
include(":core:database")
include(":core:ai")
include(":core:context")
include(":core:security")
include(":core:extensions")
include(":feature:chat")
include(":template:runtime")
include(":template:core")
include(":template:builtin")
include(":template:ui")
