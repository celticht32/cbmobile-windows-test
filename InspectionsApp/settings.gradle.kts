pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://mobile.maven.couchbase.com/maven2/dev/") }   // EE resolves from the Couchbase Maven repo
    }
}
rootProject.name = "InspectionsApp"
include(":app")
