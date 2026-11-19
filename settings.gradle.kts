pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "YunTV"

include(":app")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:security")
include(":core:ui")
include(":data:baidu")
include(":data:cover")
include(":feature:auth")
include(":feature:browser")
include(":feature:player")
include(":feature:settings")
