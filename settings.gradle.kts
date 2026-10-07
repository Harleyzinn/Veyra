pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Veyra"
include(":app", ":core:model", ":core:data", ":core:designsystem", ":core:integration")
include(":core:cloud")
include(":feature:productivity", ":feature:finance", ":feature:notes", ":feature:weather", ":feature:habits", ":feature:studies", ":feature:life", ":feature:automation", ":feature:assistant")
