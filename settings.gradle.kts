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

rootProject.name = "SolidLink"

include(":app")
include(":core:common")
include(":core:domain")
include(":core:crypto")
include(":core:protocol")
include(":core:transfer")
include(":data:db")
include(":data:files")
include(":transport:api")
include(":transport:lan-nsd")
include(":transport:wifi-direct")
include(":transport:wifi-aware")
include(":transport:nearby")
include(":platform:transfer-service")
include(":feature:transfer")
include(":testkit")
